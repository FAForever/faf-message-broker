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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

    private final ThreadPoolExecutor outgoingExecutor;
    private final ThreadPoolExecutor incomingExecutor;
    private final Lock authenticationLock = new ReentrantLock(true);
    private final Lock connectionLock = new ReentrantLock();
    private final CountDownLatch authenticatedLatch = new CountDownLatch(1);

    private final Timer outboundSendTimer;
    private final Timer inboundSendTimer;
    private final LongTaskTimer connectionTimer;
    private final Counter inboundDroppedMessageCounter;
    private final Counter outboundDroppedMessageCounter;

    private UserAgent userAgent = NO_USERAGENT;
    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    private boolean registered;
    private @Nullable LobbyConnection connection;
    private @Nullable Integer playerId;
    private @Nullable String remoteIp;
    private LongTaskTimer.@Nullable Sample connectionSample;

    public SessionHandler(MessageBroker messageBroker, FAFProperties fafProperties,
                          @RestClient PolicyClient policyClient, JWTParser jwtParser, MeterRegistry meterRegistry) {
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
        this.messageBroker = messageBroker;
        this.policyClient = policyClient;
        this.jwtParser = jwtParser;
        this.usePolicyServer = fafProperties.usePolicyServer();
        this.incomingExecutor = createMessageExecutor(fafProperties.maxMessageQueueSize(), Direction.INBOUND);
        this.outgoingExecutor = createMessageExecutor(fafProperties.maxMessageQueueSize(), Direction.OUTBOUND);
    }

    private ThreadPoolExecutor createMessageExecutor(int queueSize, Direction direction) {
        String type = switch (direction) {
            case INBOUND -> "inbound";
            case OUTBOUND -> "outbound";
        };
        Counter counter = switch (direction) {
            case INBOUND -> inboundDroppedMessageCounter;
            case OUTBOUND -> outboundDroppedMessageCounter;
        };
        ThreadFactory threadFactory = Thread.ofVirtual().name(sessionId + "-" + type + "-", 0).uncaughtExceptionHandler((_, _) -> {}).factory();
        return new ThreadPoolExecutor(0, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueSize),
                                      threadFactory, (_, executor) -> {
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(sessionId));
            if (playerId != null) {
                MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(playerId));
            }
            if (executor.isShutdown()) {
                LOG.info("Dropping {} message queued after session closed", type);
            } else {
                LOG.warn("Unable to queue {} message due to full capacity", type);
                counter.increment();
            }
            MDC.remove(MDCInterceptor.SESSION_ID);
            MDC.remove(MDCInterceptor.PLAYER_ID);
        }) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(sessionId));
                if (playerId != null) {
                    MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(playerId));
                }
                super.beforeExecute(t, r);
            }

            @Override
            protected void afterExecute(Runnable r, @Nullable Throwable t) {
                super.afterExecute(r, t);
                if (t != null) {
                    LOG.warn("Unable to send {} message", type, t);
                }
                MDC.remove(MDCInterceptor.SESSION_ID);
                MDC.remove(MDCInterceptor.PLAYER_ID);
            }
        };
    }

    @MDCPopulated
    public void setConnection(LobbyConnection connection) {
        if (this.connection != null) {
            throw new IllegalStateException("Attempting to start a connection that has already been started");
        }
        if (outgoingExecutor.isShutdown()) {
            throw new IllegalStateException("Attempting to set connection when closed");
        }
        this.connection = connection;
        remoteIp = connection.remoteIp();
        connectionSample = connectionTimer.start();
        LOG.info("Connection established");
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

        authenticationLock.lock();
        try {
            if (playerId != null && registered) {
                messageBroker.unregisterSession(playerId, this);
            }
            incomingExecutor.shutdown();
            outgoingExecutor.shutdown();
        } finally {
            authenticationLock.unlock();
        }

        try {
            if (authenticatedLatch.getCount() > 0 || !incomingExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                incomingExecutor.shutdownNow();
            }
            if (!outgoingExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                outgoingExecutor.shutdownNow();
            }
        } catch (InterruptedException _) {
            LOG.warn("Interrupted while waiting for queued messages to drain");
            incomingExecutor.shutdownNow();
            outgoingExecutor.shutdownNow();
        }

        connectionLock.lock();
        try {
            LobbyConnection connection = this.connection;
            if (connection != null) {
                connection.close();
                this.connection = null;
            }
            LongTaskTimer.Sample connectionSample = this.connectionSample;
            if (connectionSample != null) {
                connectionSample.stop();
                this.connectionSample = null;
            }
        } finally {
            connectionLock.unlock();
        }
        LOG.info("Session closed");
    }

    public void send(ProtocolMessage.Outgoing message) {
        outgoingExecutor.execute(() -> {
            LobbyConnection connection = this.connection;
            if (connection == null) {
                throw new IllegalStateException("No connection set for session");
            }
            outboundSendTimer.record(() -> connection.send(message));
        });
        if (isKick(message)) {
            closeLater();
        }
    }

    private void closeLater() {
        Thread.ofVirtual().name("session-" + sessionId + "-close").start(this::close);
    }

    private boolean isKick(ProtocolMessage.Outgoing message) {
        return message instanceof PassthroughMessage.Lobby(_, String command, ObjectNode payload) &&
               "notice".equals(command) &&
               payload.findValue("style") instanceof StringNode value &&
               value.stringValue().equalsIgnoreCase("kick");
    }

    @MDCPopulated
    @Timed(value = "faf.messages.inbound", histogram = true)
    public void accept(@MeterTag(value = "command",
                                 resolver = ProtocolMessageCommandResolver.class) ProtocolMessage.Incoming message) {
        switch (message) {
            case ConnectionMessage.Incoming connectionMessage -> handleConnectionMessage(connectionMessage);
            case PassthroughMessage passthrough -> incomingExecutor.execute(() -> {
                try {
                    authenticatedLatch.await();
                } catch (InterruptedException e) {
                    LOG.info("Message sending interrupted before authentication");
                    Thread.currentThread().interrupt();
                    return;
                }
                Integer playerId = this.playerId;
                if (playerId == null) {
                    throw new IllegalStateException("No playerId set for session");
                }
                inboundSendTimer.record(() -> messageBroker.handleInboundMessage(
                        new InboundPassthroughMessage(sessionId, playerId, passthrough)));
            });
        }
    }

    private void handleConnectionMessage(ConnectionMessage.Incoming message) {
        if (incomingExecutor.isShutdown()) {
            LOG.warn("Incoming message received on connection that was closed");
            return;
        }
        switch (message) {
            case ConnectionMessage.Ping(String requestId) -> send(new ConnectionMessage.Pong(requestId));
            case ConnectionMessage.Pong _ -> {}
            case ConnectionMessage.SessionRequest(String requestId, String agent, String version) -> {
                setUserAgent(new UserAgent(agent, version));
                send(new ConnectionMessage.SessionResponse(requestId, sessionId));
            }
            case ConnectionMessage.AuthenticateRequest(String requestId, String token, String uniqueId) -> {
                JsonWebToken jwt;
                try {
                    jwt = jwtParser.parse(token);
                } catch (ParseException e) {
                    LOG.warn("Unable to parse jwt", e);
                    send(new ConnectionMessage.LoginFailureResponse(requestId, "Invalid access token provided"));
                    closeLater();
                    return;
                }

                if (!jwt.claim("scp").map(Object::toString).map(scopes -> scopes.contains("lobby")).orElse(false)) {
                    LOG.warn("Token without lobby scope received");
                    send(new ConnectionMessage.LoginFailureResponse(requestId,
                                                                    "Token does not have permission to login to the lobby server"));
                    closeLater();
                    return;
                }

                int playerId = Integer.parseInt(jwt.getSubject());
                if (usePolicyServer) {
                    try {
                        policyClient.sendVerificationDetails(new PolicyContents(sessionId, playerId, uniqueId));
                    } catch (RuntimeException e) {
                        LOG.warn("Unable to send policy server verification details", e);
                    }
                }

                authenticationLock.lock();
                try {
                    if (outgoingExecutor.isShutdown()) {
                        return;
                    }

                    if (this.playerId != null) {
                        LOG.warn("Player already set for session");
                        send(new ConnectionMessage.LoginFailureResponse(requestId,
                                                                        "Connection previously authenticated"));
                        closeLater();
                        return;
                    }

                    messageBroker.registerSession(playerId, this);
                    this.playerId = playerId;
                    registered = true;
                    authenticatedLatch.countDown();
                } finally {
                    authenticationLock.unlock();
                }
            }
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
        return outgoingExecutor.getQueue().size();
    }

    public int numInboundQueuedMessages() {
        return incomingExecutor.getQueue().size();
    }

    private enum Direction {
        INBOUND, OUTBOUND
    }
}
