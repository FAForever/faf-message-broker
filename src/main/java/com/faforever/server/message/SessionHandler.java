package com.faforever.server.message;

import com.faforever.server.config.FAFProperties;
import com.faforever.server.endpoint.connection.LobbyConnection;
import com.faforever.server.endpoint.connection.UserAgent;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.PassthroughMessage;
import com.faforever.server.message.client.ProtocolMessage;
import com.faforever.server.message.service.InboundPassthroughMessage;
import com.faforever.server.policy.PolicyClient;
import com.faforever.server.policy.PolicyContents;
import com.faforever.server.utils.NoThrowCloseable;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.MDC;
import org.jspecify.annotations.Nullable;

import java.util.SplittableRandom;

@JBossLog
@Dependent
public class SessionHandler {

    private final MessageBroker messageBroker;

    private final FAFProperties fafProperties;

    private final PolicyClient policyClient;
    private final JWTParser jwtParser;

    private @Nullable LobbyConnection connection;
    private @Nullable UserAgent userAgent;
    private @Nullable Integer playerId;

    private final long sessionId = new SplittableRandom().nextLong(Long.MAX_VALUE);

    public SessionHandler(MessageBroker messageBroker, FAFProperties fafProperties,
                          @RestClient PolicyClient policyClient, JWTParser jwtParser) {
        this.messageBroker = messageBroker;
        this.fafProperties = fafProperties;
        this.policyClient = policyClient;
        this.jwtParser = jwtParser;
    }

    public void setConnection(LobbyConnection connection) {
        if (this.connection != null) {
            throw new IllegalStateException("Connection already set for session");
        }
        this.connection = connection;
    }

    private void setUserAgent(UserAgent userAgent) {
        if (this.userAgent != null) {
            LOG.warn("User agent already set for session");
            return;
        }
        LOG.debugf("Detected user agent %s", userAgent);
        this.userAgent = userAgent;
    }

    @Nullable UserAgent userAgent() {
        return userAgent;
    }

    int playerId() {
        if (playerId == null) {
            throw new IllegalStateException("Not authenticated");
        }
        return playerId;
    }

    long sessionId() {
        return sessionId;
    }

    @PreDestroy
    public void close() {
        try (NoThrowCloseable _ = populateMDC()) {
            if (playerId != null) {
                messageBroker.unregisterSession(this);
                playerId = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }
    }

    void sendMessage(ProtocolMessage.Outgoing message) {
        if (connection == null) {
            throw new IllegalStateException("No connection");
        }

        connection.sendAndAwait(message);
    }

    public void handleMessage(ProtocolMessage.Incoming message) {
        try (NoThrowCloseable _ = populateMDC(message)) {
            switch (message) {
                case ConnectionMessage.Incoming connectionMessage -> handleConnectionMessage(connectionMessage);
                case PassthroughMessage passthrough -> {
                    if (playerId == null) {
                        throw new IllegalStateException("Connection not yet authenticated");
                    }
                    messageBroker.handleInboundMessage(new InboundPassthroughMessage(sessionId, playerId, passthrough));
                }
            }
        }
    }

    private void handleConnectionMessage(ConnectionMessage.Incoming message) {
        switch (message) {
            case ConnectionMessage.Ping(String requestId) -> sendMessage(new ConnectionMessage.Pong(requestId));
            case ConnectionMessage.Pong _ -> {}
            case ConnectionMessage.SessionRequest(String requestId, String agent, String version) -> {
                setUserAgent(new UserAgent(agent, version));
                sendMessage(new ConnectionMessage.SessionResponse(requestId, sessionId));
            }
            case ConnectionMessage.AuthenticateRequest(String requestId, String token, String uniqueId) -> {
                if (this.playerId != null) {
                    throw new IllegalStateException("Player already set for the session");
                }

                JsonWebToken jwt;
                try {
                    jwt = jwtParser.parse(token);
                } catch (ParseException e) {
                    LOG.warn("Unable to parse jwt", e);
                    sendMessage(new ConnectionMessage.LoginFailureResponse(requestId, "Invalid access token provided"));
                    close();
                    return;
                }
                playerId = Integer.parseInt(jwt.getSubject());
                if (fafProperties.usePolicyServer()) {
                    policyClient.checkPolicy(new PolicyContents(sessionId, playerId, uniqueId));
                }
                messageBroker.registerSession(this);
            }
        }
    }

    private NoThrowCloseable populateMDC(ProtocolMessage.Incoming protocolMessage) {
        NoThrowCloseable sessionDetailsCloseable = populateMDC();
        MDC.put("requestId", protocolMessage.requestId());

        return () -> {
            sessionDetailsCloseable.close();
            MDC.remove("requestId");
        };
    }

    private NoThrowCloseable populateMDC() {
        MDC.put("sessionId", sessionId);
        if (playerId != null) {
            MDC.put("playerId", playerId);
        }

        return () -> {
            MDC.remove("sessionId");
            MDC.remove("playerId");
        };
    }
}
