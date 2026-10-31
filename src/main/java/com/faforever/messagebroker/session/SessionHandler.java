package com.faforever.messagebroker.session;

import com.faforever.messagebroker.config.FAFProperties;
import com.faforever.messagebroker.endpoint.connection.LobbyConnection;
import com.faforever.messagebroker.interceptor.cdi.MDCInterceptor;
import com.faforever.messagebroker.interceptor.cdi.MDCPopulated;
import com.faforever.messagebroker.message.MessageBroker;
import com.faforever.messagebroker.message.client.ConnectionMessage;
import com.faforever.messagebroker.message.client.PassthroughMessage;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.micrometer.ProtocolMessageCommandResolver;
import com.faforever.messagebroker.policy.PolicyClient;
import com.faforever.messagebroker.policy.PolicyContents;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.Duration;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Dependent
public class SessionHandler {

    private static final UserAgent NO_USERAGENT = new UserAgent(null, null);

    private final MessageBroker messageBroker;

    private final PolicyClient policyClient;
    private final JWTParser jwtParser;

    private final boolean usePolicyServer;
    private final BlockingQueue<PassthroughMessage> incomingQueue;
    private final BlockingQueue<ProtocolMessage.Outgoing> outgoingQueue;

    private final Timer outboundSendTimer;
    private final Timer inboundSendTimer;
    private final LongTaskTimer connectionTimer;
    private final Counter inboundDroppedMessageCounter;
    private final Counter outboundDroppedMessageCounter;

    private UserAgent userAgent = NO_USERAGENT;
    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);
    private final Lock closeLock = new ReentrantLock(true);

    private @Nullable Integer playerId;
    private @Nullable String remoteIp;
    private @Nullable Thread outgoingDrainThread;
    private @Nullable Thread incomingDrainThread;
    private LongTaskTimer.@Nullable Sample connectionSample;
    private boolean closed;

    public SessionHandler(MessageBroker messageBroker, FAFProperties fafProperties,
                          @RestClient PolicyClient policyClient, JWTParser jwtParser, MeterRegistry meterRegistry) {
        this.messageBroker = messageBroker;
        this.policyClient = policyClient;
        this.jwtParser = jwtParser;
        this.usePolicyServer = fafProperties.usePolicyServer();
        this.incomingQueue = new ArrayBlockingQueue<>(fafProperties.maxMessageQueueSize(), true);
        this.outgoingQueue = new ArrayBlockingQueue<>(fafProperties.maxMessageQueueSize(), true);
        this.outboundSendTimer = Timer.builder("faf.send")
                                      .tag("direction", "outbound")
                                      .publishPercentileHistogram()
                                      .register(meterRegistry);
        this.inboundSendTimer = Timer.builder("faf.send")
                                     .tag("direction", "inbound")
                                     .publishPercentileHistogram()
                                     .register(meterRegistry);
        this.connectionTimer = LongTaskTimer.builder("faf.connections")
                                            .minimumExpectedValue(Duration.ofMillis(100))
                                            .maximumExpectedValue(Duration.ofHours(1))
                                            .publishPercentileHistogram()
                                            .register(meterRegistry);
        this.outboundDroppedMessageCounter = meterRegistry.counter("faf.messages.dropped", "direction", "outbound");
        this.inboundDroppedMessageCounter = meterRegistry.counter("faf.messages.dropped", "direction", "inbound");
    }

    @MDCPopulated
    public void setConnection(LobbyConnection connection) {
        if (outgoingDrainThread != null) {
            throw new IllegalStateException("Attempting to start a connection that has already been started");
        }
        closeLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Attempting to start a connection that has already been closed");
            }
            connectionSample = connectionTimer.start();
            outgoingDrainThread = Thread.ofVirtual()
                                        .name("session-outgoing-" + sessionId)
                                        .start(() -> drainOutgoingTo(connection));
            remoteIp = connection.remoteIp();
            LOG.info("Session established");
        } finally {
            closeLock.unlock();
        }
    }

    private void setUserAgent(UserAgent userAgent) {
        if (this.userAgent != NO_USERAGENT) {
            LOG.warn("User agent already set for session");
            return;
        }
        LOG.info("Detected user agent {}", userAgent);
        this.userAgent = userAgent;
    }

    @PreDestroy
    void ensureClosed() {
        close();
    }

    @MDCPopulated
    public void close() {
        LOG.debug("Closing session");

        closeLock.lock();
        Thread incomingDrainThread = this.incomingDrainThread;
        Thread outgoingDrainThread = this.outgoingDrainThread;
        try {
            Integer playerId = this.playerId;
            if (playerId != null) {
                messageBroker.unregisterSession(playerId, this);
                this.playerId = null;
            }

            closed = true;
            if (incomingDrainThread != null) {
                incomingDrainThread.interrupt();
            }

            if (outgoingDrainThread != null) {
                outgoingDrainThread.interrupt();
            }
        } finally {
            closeLock.unlock();
        }

        try {
            if (incomingDrainThread != null) {
                if (incomingDrainThread.join(Duration.ofMinutes(1))) {
                    this.incomingDrainThread = null;
                }
            }

            if (outgoingDrainThread != null) {
                if (outgoingDrainThread.join(Duration.ofMinutes(1))) {
                    this.outgoingDrainThread = null;
                }
            }
        } catch (InterruptedException exception) {
            LOG.warn("Interrupted while waiting for drain threads to finish");
        }

        if (connectionSample != null) {
            connectionSample.stop();
        }
        LOG.info("Session closed");
    }

    public void send(ProtocolMessage.Outgoing message) {
        if (closed) {
            LOG.warn("Attempted to queue outgoing message on connection that was closed");
            return;
        }
        if (!outgoingQueue.offer(message)) {
            LOG.warn("Unable to queue outbound message to due to full capacity");
            outboundDroppedMessageCounter.increment();
        }
        if (message instanceof PassthroughMessage.Lobby(_, String command, ObjectNode payload) &&
            "notice".equals(command) &&
            payload.findValue("style") instanceof StringNode value &&
            value.stringValue().equalsIgnoreCase("kick")) {
            Thread.startVirtualThread(this::close);
        }
    }

    @MDCPopulated
    @Timed(value = "faf.messages.inbound", histogram = true)
    public void accept(@MeterTag(value = "command",
                                 resolver = ProtocolMessageCommandResolver.class) ProtocolMessage.Incoming message) {
        if (closed) {
            LOG.warn("Incoming message received on connection that was closed");
            return;
        }

        switch (message) {
            case ConnectionMessage.Incoming connectionMessage -> handleConnectionMessage(connectionMessage);
            case PassthroughMessage passthrough -> {
                if (!incomingQueue.offer(passthrough)) {
                    LOG.warn("Unable to queue inbound message due to full capacity");
                    inboundDroppedMessageCounter.increment();
                }
            }
        }
    }

    private void handleConnectionMessage(ConnectionMessage.Incoming message) {
        switch (message) {
            case ConnectionMessage.Ping(String requestId) -> send(new ConnectionMessage.Pong(requestId));
            case ConnectionMessage.Pong _ -> {}
            case ConnectionMessage.SessionRequest(String requestId, String agent, String version) -> {
                setUserAgent(new UserAgent(agent, version));
                send(new ConnectionMessage.SessionResponse(requestId, sessionId));
            }
            case ConnectionMessage.AuthenticateRequest(String requestId, String token, String uniqueId) -> {
                if (this.playerId != null) {
                    LOG.warn("Player already set for session");
                    send(new ConnectionMessage.LoginFailureResponse(requestId, "Connection previously authenticated"));
                    close();
                    return;
                }

                JsonWebToken jwt;
                try {
                    jwt = jwtParser.parse(token);
                } catch (ParseException e) {
                    LOG.warn("Unable to parse jwt", e);
                    send(new ConnectionMessage.LoginFailureResponse(requestId, "Invalid access token provided"));
                    close();
                    return;
                }

                if (!jwt.claim("scp").map(Object::toString).map(scopes -> scopes.contains("lobby")).orElse(false)) {
                    LOG.warn("Token without lobby scope received");
                    send(new ConnectionMessage.LoginFailureResponse(requestId,
                                                                    "Token does not have permission to login to the lobby server"));
                    close();
                    return;
                }

                int playerId = Integer.parseInt(jwt.getSubject());
                if (usePolicyServer) {
                    policyClient.sendVerificationDetails(new PolicyContents(sessionId, playerId, uniqueId));
                }

                closeLock.lock();
                try {
                    if (!closed && this.playerId == null) {
                        messageBroker.registerSession(playerId, this);
                        this.playerId = playerId;
                        incomingDrainThread = Thread.ofVirtual()
                                                    .name("session-incoming-" + sessionId)
                                                    .start(() -> drainIncomingAs(playerId));
                    }
                } finally {
                    closeLock.unlock();
                }
            }
        }
    }

    private void drainIncomingAs(int playerId) {
        MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(sessionId));
        MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(playerId));
        try {
            try {
                while (!Thread.interrupted()) {
                    PassthroughMessage message = incomingQueue.take();
                    inboundSendTimer.record(() -> messageBroker.handleInboundMessage(
                            new InboundPassthroughMessage(sessionId, playerId, message)));
                }
            } catch (InterruptedException _) {}

            PassthroughMessage message;
            while ((message = incomingQueue.poll()) != null) {
                PassthroughMessage messageToSend = message;
                inboundSendTimer.record(() -> messageBroker.handleInboundMessage(
                        new InboundPassthroughMessage(sessionId, playerId, messageToSend)));
            }
        } catch (RuntimeException exception) {
            LOG.warn("Unable to drain inbound queue some messages may not have been sent", exception);
        } finally {
            LOG.info("Session incoming closed");
            MDC.remove(MDCInterceptor.SESSION_ID);
            MDC.remove(MDCInterceptor.PLAYER_ID);

            close();
        }
    }

    private void drainOutgoingTo(LobbyConnection connection) {
        try {
            try {
                while (!Thread.interrupted()) {
                    ProtocolMessage.Outgoing message = outgoingQueue.take();
                    outboundSendTimer.record(() -> connection.send(message));
                }
            } catch (InterruptedException _) {}

            ProtocolMessage.Outgoing message;
            while ((message = outgoingQueue.poll()) != null) {
                ProtocolMessage.Outgoing messageToSend = message;
                outboundSendTimer.record(() -> connection.send(messageToSend));
            }
        } catch (RuntimeException exception) {
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(sessionId));
            LOG.warn("Unable to drain outbound queue some messages may not have been sent", exception);
            MDC.remove(MDCInterceptor.SESSION_ID);
        } finally {
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(sessionId));
            LOG.info("Session outgoing closed");
            MDC.remove(MDCInterceptor.SESSION_ID);

            connection.close();
        }
    }

    public UserAgent userAgent() {
        return userAgent;
    }

    public @Nullable Integer playerId() {
        return playerId;
    }

    public long sessionId() {
        return sessionId;
    }

    public @Nullable String remoteIp() {
        return remoteIp;
    }

    public int numOutboundQueuedMessages() {
        return outgoingQueue.size();
    }

    public int numInboundQueuedMessages() {
        return incomingQueue.size();
    }
}
