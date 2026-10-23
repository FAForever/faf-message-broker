package com.faforever.messagebroker.endpoint.session;

import com.faforever.messagebroker.config.FAFProperties;
import com.faforever.messagebroker.endpoint.connection.LobbyConnection;
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
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.Nullable;

import java.util.Queue;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;

@JBossLog
@Dependent
public class SessionHandler {

    private static final UserAgent NO_USERAGENT = new UserAgent(null, null);

    private final MessageBroker messageBroker;

    private final PolicyClient policyClient;
    private final JWTParser jwtParser;

    private @Nullable LobbyConnection connection;
    private @Nullable Integer playerId;
    private UserAgent userAgent = NO_USERAGENT;

    private final boolean usePolicyServer;
    private final Queue<PassthroughMessage> beforeAuthQueue;

    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    public SessionHandler(MessageBroker messageBroker, FAFProperties fafProperties,
                          @RestClient PolicyClient policyClient, JWTParser jwtParser) {
        this.messageBroker = messageBroker;
        this.policyClient = policyClient;
        this.jwtParser = jwtParser;
        this.usePolicyServer = fafProperties.usePolicyServer();
        this.beforeAuthQueue = new ArrayBlockingQueue<>(fafProperties.numberOfMessagesToQueueBeforeAuth());
    }

    @MDCPopulated
    public void setConnection(LobbyConnection connection) {
        LOG.trace("Establishing connection");
        if (this.connection != null) {
            throw new IllegalStateException("Connection already set for session");
        }
        this.connection = connection;
        this.connection.startDrain();
        LOG.debug("Connection established");
    }

    private void setUserAgent(UserAgent userAgent) {
        if (this.userAgent != NO_USERAGENT) {
            LOG.warn("User agent already set for session");
            return;
        }
        LOG.infof("Detected user agent %s", userAgent);
        this.userAgent = userAgent;
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
        if (connection == null) {
            return null;
        }
        return connection.remoteIp();
    }

    @MDCPopulated
    @PreDestroy
    public void close() {
        LOG.trace("Closing connection");
        if (playerId != null) {
            messageBroker.unregisterSession(playerId, this);
            playerId = null;
        }
        if (connection != null) {
            connection.close();
            connection = null;
        }
        LOG.debug("Connection closed");
    }

    public void send(ProtocolMessage.Outgoing message) {
        if (connection == null || connection.isClosed()) {
            LOG.warn("Session has no open connection");
            return;
        }
        connection.queue(message);
    }

    @MDCPopulated
    @Timed(value = "faf.messages.inbound", histogram = true)
    public void handleMessage(@MeterTag(value = "command",
                                        resolver = ProtocolMessageCommandResolver.class) ProtocolMessage.Incoming message) {
        switch (message) {
            case ConnectionMessage.Incoming connectionMessage -> handleConnectionMessage(connectionMessage);
            case PassthroughMessage passthrough -> {
                if (playerId == null) {
                    LOG.info("Received message before authentication queuing until after authentication");
                    if (!beforeAuthQueue.offer(passthrough)) {
                        LOG.warn("Unable to queue message before auth due to full capacity");
                    }
                    return;
                }
                messageBroker.handleInboundMessage(new InboundPassthroughMessage(sessionId, playerId, passthrough));
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
                    send(new ConnectionMessage.LoginFailureResponse(requestId,
                                                                    "Connection previously authenticated"));
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
                drainBeforeAuth(playerId);
            }
        }
    }

    private void drainBeforeAuth(int playerId) {
        PassthroughMessage beforeAuthMessage;
        while ((beforeAuthMessage = beforeAuthQueue.poll()) != null) {
            messageBroker.handleInboundMessage(new InboundPassthroughMessage(sessionId, playerId, beforeAuthMessage));
        }
    }
}
