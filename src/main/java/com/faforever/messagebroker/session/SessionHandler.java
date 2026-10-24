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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.Nullable;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Slf4j
@Dependent
public class SessionHandler {

    private static final UserAgent NO_USERAGENT = new UserAgent(null, null);

    private final MessageBroker messageBroker;
    private final ExecutorService drainExecutor;

    private final PolicyClient policyClient;
    private final JWTParser jwtParser;

    private final boolean usePolicyServer;
    private final BlockingQueue<PassthroughMessage> incomingQueue;
    private final BlockingQueue<ProtocolMessage.Outgoing> outgoingQueue;

    private final Timer outboundSendTimer;
    private final Timer inboundSendTimer;
    private final Counter inboundDroppedMessageCounter;
    private final Counter outboundDroppedMessageCounter;

    private @Nullable String remoteIp;
    private @Nullable Integer playerId;
    private UserAgent userAgent = NO_USERAGENT;
    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    private @Nullable Future<?> outgoingDrainFuture;
    private @Nullable Future<?> incomingDrainFuture;

    public SessionHandler(MessageBroker messageBroker, FAFProperties fafProperties,
                          @RestClient PolicyClient policyClient, JWTParser jwtParser,
                          @VirtualThreads ExecutorService drainExecutor, MeterRegistry meterRegistry) {
        this.messageBroker = messageBroker;
        this.policyClient = policyClient;
        this.jwtParser = jwtParser;
        this.drainExecutor = drainExecutor;
        this.usePolicyServer = fafProperties.usePolicyServer();
        this.incomingQueue = new ArrayBlockingQueue<>(fafProperties.maxMessageQueueSize(), true);
        this.outgoingQueue = new ArrayBlockingQueue<>(fafProperties.maxMessageQueueSize(), true);
        this.outboundSendTimer = meterRegistry.timer("faf.send", "direction", "outbound");
        this.inboundSendTimer = meterRegistry.timer("faf.send", "direction", "inbound");
        this.outboundDroppedMessageCounter = meterRegistry.counter("faf.messages.dropped", "direction", "outbound");
        this.inboundDroppedMessageCounter = meterRegistry.counter("faf.messages.dropped", "direction", "inbound");
    }

    @MDCPopulated
    public void setConnection(LobbyConnection connection) {
        if (outgoingDrainFuture != null) {
            throw new IllegalStateException("Attempting to start a connection that has already been started");
        }
        outgoingDrainFuture = drainExecutor.submit(() -> drainOutgoingTo(connection));
        remoteIp = connection.remoteIp();
        LOG.info("Session established");
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
        LOG.debug("Session closing");
        Integer existingPlayerId = playerId;
        if (existingPlayerId != null) {
            messageBroker.unregisterSession(existingPlayerId, this);
            playerId = null;
        }

        Future<?> existingOutgoingDrainFuture = outgoingDrainFuture;
        if (existingOutgoingDrainFuture != null) {
            existingOutgoingDrainFuture.cancel(true);
        } else {
            outgoingDrainFuture = CompletableFuture.completedFuture(null);
        }

        Future<?> existingIncomingDrainFuture = incomingDrainFuture;
        if (existingIncomingDrainFuture != null) {
            existingIncomingDrainFuture.cancel(true);
        } else {
            incomingDrainFuture = CompletableFuture.completedFuture(null);
        }
    }

    public void send(ProtocolMessage.Outgoing message) {
        if (outgoingDrainFuture != null && (outgoingDrainFuture.isDone() || outgoingDrainFuture.isCancelled())) {
            LOG.warn("Attempted to queue outgoing message on connection that was closed");
            return;
        }
        if (!outgoingQueue.offer(message)) {
            LOG.warn("Unable to queue outbound message to due to full capacity");
            outboundDroppedMessageCounter.increment();
        }
    }

    @MDCPopulated
    @Timed(value = "faf.messages.inbound", histogram = true)
    public void accept(@MeterTag(value = "command",
                                 resolver = ProtocolMessageCommandResolver.class) ProtocolMessage.Incoming message) {
        switch (message) {
            case ConnectionMessage.Incoming connectionMessage -> handleConnectionMessage(connectionMessage);
            case PassthroughMessage passthrough -> {
                if (incomingDrainFuture != null && (incomingDrainFuture.isDone() || incomingDrainFuture.isCancelled())) {
                    LOG.warn("Attempted to queue incoming message on connection that was closed");
                    return;
                }

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
                messageBroker.registerSession(playerId, this);
                this.playerId = playerId;
                incomingDrainFuture = drainExecutor.submit(() -> drainIncomingAs(playerId));
            }
        }
    }

    private void drainIncomingAs(int playerId) {
        try {
            try {
                while (!Thread.currentThread().isInterrupted()) {
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
            LOG.atWarn()
               .setCause(exception)
               .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
               .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
               .log("Unable to drain inbound queue some messages may not have been sent");
        } finally {
            LOG.atInfo()
               .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
               .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
               .log("Session incoming closed");
        }
    }

    private void drainOutgoingTo(LobbyConnection connection) {
        try {
            try {
                while (!Thread.currentThread().isInterrupted()) {
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
            LoggingEventBuilder loggingEventBuilder = LOG.atWarn()
                                                         .setCause(exception)
                                                         .setMessage(
                                                                 "Unable to drain outbound queue some messages may not have been sent")
                                                         .addKeyValue(MDCInterceptor.SESSION_ID, sessionId);
            if (playerId != null) {
                loggingEventBuilder = loggingEventBuilder.addKeyValue(MDCInterceptor.PLAYER_ID, playerId);
            }
            loggingEventBuilder.log();
        } finally {
            LoggingEventBuilder loggingEventBuilder = LOG.atInfo()
                                                         .setMessage("Session outgoing closed")
                                                         .addKeyValue(MDCInterceptor.SESSION_ID, sessionId);
            if (playerId != null) {
                loggingEventBuilder = loggingEventBuilder.addKeyValue(MDCInterceptor.PLAYER_ID, playerId);
            }
            loggingEventBuilder.log();
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
