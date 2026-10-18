package com.faforever.messagebroker.endpoint.session;

import com.faforever.messagebroker.config.FAFProperties;
import com.faforever.messagebroker.endpoint.connection.LobbyConnection;
import com.faforever.messagebroker.endpoint.connection.UserAgent;
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

    @MDCPopulated
    public void setConnection(LobbyConnection connection) {
        LOG.trace("Establishing connection");
        if (this.connection != null) {
            throw new IllegalStateException("Connection already set for session");
        }
        this.connection = connection;
        LOG.debug("Connection established");
    }

    private void setUserAgent(UserAgent userAgent) {
        if (this.userAgent != null) {
            LOG.warn("User agent already set for session");
            return;
        }
        LOG.infof("Detected user agent %s", userAgent);
        this.userAgent = userAgent;
    }

    @Nullable UserAgent userAgent() {
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

    public void sendMessage(ProtocolMessage.Outgoing message) {
        if (connection == null) {
            throw new IllegalStateException("No established connection");
        }

        connection.sendAndAwait(message);
    }

    @MDCPopulated
    @Timed(value = "faf.messages.inbound", histogram = true)
    public void handleMessage(@MeterTag(
            value = "command", resolver = ProtocolMessageCommandResolver.class
    ) ProtocolMessage.Incoming message) {
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
                    LOG.warn("Player already set for session");
                    sendMessage(new ConnectionMessage.LoginFailureResponse(requestId, "Connection previously authenticated"));
                    close();
                    return;
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
                    policyClient.sendVerificationDetails(new PolicyContents(sessionId, playerId, uniqueId));
                }
                messageBroker.registerSession(playerId, this);
            }
        }
    }
}
