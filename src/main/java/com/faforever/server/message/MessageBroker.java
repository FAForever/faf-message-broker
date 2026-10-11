package com.faforever.server.message;

import com.faforever.server.domain.LobbyCommandSchemaRepository;
import com.faforever.server.message.client.Message;
import com.faforever.server.message.service.InboundMessage;
import com.faforever.server.message.service.LobbySchemaDetails;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.OutboundTarget;
import com.faforever.server.utils.NoThrowCloseable;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.MDC;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@ApplicationScoped
public class MessageBroker {

    private final Emitter<InboundMessage> lobbyMessageEmitter;

    private final LobbyCommandSchemaRepository lobbyCommandSchemaRepository;

    private final Map<LobbySchemaKey, Schema> lobbySchemas = new ConcurrentHashMap<>();
    private final Schema gameSchema;

    private final Map<Long, SessionHandler> sessionHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Long>> playerSessionMap = new ConcurrentHashMap<>();

    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    public MessageBroker(@Channel("lobby-inbound") Emitter<InboundMessage> lobbyMessageEmitter, LobbyCommandSchemaRepository lobbyCommandSchemaRepository) {
        this.lobbyMessageEmitter = lobbyMessageEmitter;
        this.lobbyCommandSchemaRepository = lobbyCommandSchemaRepository;
        gameSchema = schemaRegistry.getSchema(SchemaLocation.of("gpg"), Message.Passthrough.GPG.GAME_PASSTHROUGH_SCHEMA, InputFormat.JSON);
    }

    @PostConstruct
    void loadSchemas() {
        lobbyCommandSchemaRepository.loadAll().forEach(this::registerSchema);
    }

    @Incoming("lobby-schema-registration")
    void handleLobbySchemaRegistration(LobbySchemaDetails lobbySchemaDetails) {
        lobbyCommandSchemaRepository.save(lobbySchemaDetails);
        registerSchema(lobbySchemaDetails);
    }

    private void registerSchema(LobbySchemaDetails lobbySchemaDetails) {
        LobbySchemaKey lobbySchemaKey = new LobbySchemaKey(lobbySchemaDetails.command(),
                                                           lobbySchemaDetails.direction());
        Schema schema = schemaRegistry.getSchema(SchemaLocation.of(lobbySchemaDetails.command()),
                                                 lobbySchemaDetails.schema());
        lobbySchemas.put(lobbySchemaKey, schema);
    }

    void handleInboundMessage(InboundMessage inboundLobbyMessage) {
        Message.Passthrough passthrough = inboundLobbyMessage.message();
        switch (passthrough) {
            case Message.Passthrough.Lobby lobbyPassthrough -> {
                validateLobbyPassthrough(lobbyPassthrough);
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(lobbyPassthrough.command())
                                                                                  .withDeliveryMode(2)
                                                                                  .build();
                lobbyMessageEmitter.send(
                        org.eclipse.microprofile.reactive.messaging.Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
            case Message.Passthrough.GPG gpgPassthrough -> {
                validateGPGPassthrough(gpgPassthrough);
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey("gpg")
                                                                                  .withDeliveryMode(2)
                                                                                  .build();
                lobbyMessageEmitter.send(
                        org.eclipse.microprofile.reactive.messaging.Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
        }
    }

    private void validateLobbyPassthrough(Message.Passthrough.Lobby lobbyPassthrough) {
        String command = lobbyPassthrough.command();
        Schema schema = lobbySchemas.get(new LobbySchemaKey(command, LobbySchemaDetails.Direction.INBOUND));

        if (schema == null) {
            throw new IllegalArgumentException("Unknown message type");
        }
        List<Error> errors = schema.validate(lobbyPassthrough.message());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid message");
        }
    }

    private void validateGPGPassthrough(Message.Passthrough.GPG gpgPassthrough) {
        List<Error> errors = gameSchema.validate(gpgPassthrough.message());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid message");
        }
    }

    @Incoming("lobby-outbound")
    void handleOutboundMessage(OutboundLobbyMessage outboundLobbyMessage) {
        try (NoThrowCloseable _ = populateMDC(outboundLobbyMessage)) {
            Message.Passthrough message = outboundLobbyMessage.message();
            switch (outboundLobbyMessage.target()) {
                case OutboundTarget.Session(long sessionId) -> sendMessageToSession(sessionId, message);
                case OutboundTarget.Player(int playerId) -> sendMessageToPlayer(playerId, message);
                case OutboundTarget.All() -> sendMessageToAll(message);
            }
        }
    }

    private void sendMessageToAll(Message.Passthrough message) {
        sessionHandlerMap.keySet().forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToPlayer(int playerId, Message.Passthrough message) {
        Set<Long> playerSessions = playerSessionMap.getOrDefault(playerId, Set.of());
        if (playerSessions.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessions.forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToSession(long sessionId, Message.Passthrough message) {
        SessionHandler sessionHandler = sessionHandlerMap.get(sessionId);
        if (sessionHandler == null) {
            LOG.warn("No active session for session id");
            return;
        }

        sessionHandler.sendMessage(message);
        if (message instanceof Message.Passthrough.Lobby(String command, ObjectNode payload) &&
            "notice".equals(command) &&
            payload.findValue("style") instanceof StringNode value &&
            value.stringValue().equalsIgnoreCase("kick")) {
            sessionHandler.close();
        }
    }

    void registerSession(SessionHandler sessionHandler) {
        int playerId = sessionHandler.playerId();
        long sessionId = sessionHandler.sessionId();
        SessionHandler mappedSessionHandler = sessionHandlerMap.putIfAbsent(sessionId, sessionHandler);
        if (mappedSessionHandler != null && mappedSessionHandler != sessionHandler) {
            throw new IllegalStateException("Existing session for id %d".formatted(sessionId));
        }

        playerSessionMap.computeIfAbsent(playerId, _ -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    void unregisterSession(SessionHandler sessionHandler) {
        if (!sessionHandlerMap.remove(sessionHandler.sessionId(), sessionHandler)) {
            LOG.warn("Session controller not associated with session");
        }

        playerSessionMap.compute(sessionHandler.playerId(), (_, sessions) -> {
            if (sessions == null || !sessions.remove(sessionHandler.sessionId())) {
                LOG.warn("Session controller not associated with player");
                return null;
            }

            return sessions.isEmpty() ? null : sessions;
        });

    }

    private NoThrowCloseable populateMDC(OutboundLobbyMessage outboundLobbyMessage) {
        switch (outboundLobbyMessage.target()) {
            case OutboundTarget.Player(int playerId) -> MDC.put("playerId", playerId);
            case OutboundTarget.Session(long sessionId) -> MDC.put("sessionId", sessionId);
            case OutboundTarget.All() -> {}
        }

        return () -> {
            MDC.remove("sessionId");
            MDC.remove("playerId");
        };
    }

    private record LobbySchemaKey(
            String command,
            LobbySchemaDetails.Direction direction
    ) {}
}
