package com.faforever.server.message;

import com.faforever.server.domain.LobbyCommandSchemaRepository;
import com.faforever.server.message.client.ProtocolMessage;
import com.faforever.server.message.service.InboundPassthroughMessage;
import com.faforever.server.message.service.LobbySchemaDetails;
import com.faforever.server.message.service.OutboundPassthroughMessage;
import com.faforever.server.message.service.OutboundTarget;
import com.faforever.server.message.service.PlayerSessionDetails;
import com.faforever.server.utils.NoThrowCloseable;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.MDC;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@JBossLog
@ApplicationScoped
public class MessageBroker {

    private final Emitter<InboundPassthroughMessage> lobbyMessageEmitter;
    private final Emitter<PlayerSessionDetails> playerConnectionEmitter;

    private final LobbyCommandSchemaRepository lobbyCommandSchemaRepository;

    private final Map<LobbySchemaKey, LobbySchemaRoute> lobbySchemas = new ConcurrentHashMap<>();
    private final Schema gameSchema;

    private final Map<Long, SessionHandler> sessionHandlerMap = new ConcurrentHashMap<>();
    private final Map<Integer, Set<Long>> playerSessionMap = new ConcurrentHashMap<>();

    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    public MessageBroker(@Channel("lobby-inbound") Emitter<InboundPassthroughMessage> lobbyMessageEmitter, @Channel(
            "player-connection"
    ) Emitter<PlayerSessionDetails> playerConnectionEmitter, LobbyCommandSchemaRepository lobbyCommandSchemaRepository) {
        this.lobbyMessageEmitter = lobbyMessageEmitter;
        this.playerConnectionEmitter = playerConnectionEmitter;
        this.lobbyCommandSchemaRepository = lobbyCommandSchemaRepository;
        this.gameSchema = schemaRegistry.getSchema(SchemaLocation.of("gpg"),
                                              ProtocolMessage.Passthrough.GPG.GAME_PASSTHROUGH_SCHEMA,
                                              InputFormat.JSON);
    }

    public Collection<PlayerSessionDetails> loadActiveSessionDetails() {
        return sessionHandlerMap.values()
                .stream()
                .map(sessionHandler -> new PlayerSessionDetails(sessionHandler.playerId(), sessionHandler.sessionId(), true))
                .collect(Collectors.toSet());
    }

    @PostConstruct
    void loadSchemas() {
        lobbyCommandSchemaRepository.loadAll().forEach(this::registerSchema);
    }

    @Incoming("lobby-schema-registration")
    @RunOnVirtualThread
    void handleLobbySchemaRegistration(LobbySchemaDetails lobbySchemaDetails) {
        lobbyCommandSchemaRepository.save(lobbySchemaDetails);
        registerSchema(lobbySchemaDetails);
    }

    private void registerSchema(LobbySchemaDetails lobbySchemaDetails) {
        LobbySchemaKey lobbySchemaKey = new LobbySchemaKey(lobbySchemaDetails.command(),
                                                           lobbySchemaDetails.direction());
        Schema schema = schemaRegistry.getSchema(SchemaLocation.of(lobbySchemaDetails.command()),
                                                 lobbySchemaDetails.schema());
        lobbySchemas.put(lobbySchemaKey, new LobbySchemaRoute(lobbySchemaDetails.route(), schema));
    }

    void handleInboundMessage(InboundPassthroughMessage inboundLobbyMessage) {
        ProtocolMessage.Passthrough passthrough = inboundLobbyMessage.message();
        switch (passthrough) {
            case ProtocolMessage.Passthrough.Lobby lobbyPassthrough -> {
                String route = validateLobbyPassthrough(lobbyPassthrough);
                String routingKey = "lobby." + route + "." + lobbyPassthrough.command();
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(routingKey)
                                                                                  .build();
                lobbyMessageEmitter.send(Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
            case ProtocolMessage.Passthrough.GPG gpgPassthrough -> {
                validateGPGPassthrough(gpgPassthrough);
                String routingKey = "gpg." + gpgPassthrough.command();
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(routingKey)
                                                                                  .build();
                lobbyMessageEmitter.send(Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
        }
    }

    private String validateLobbyPassthrough(ProtocolMessage.Passthrough.Lobby lobbyPassthrough) {
        String command = lobbyPassthrough.command();
        LobbySchemaRoute lobbySchemaRoute = lobbySchemas.get(
                new LobbySchemaKey(command, LobbySchemaDetails.Direction.INBOUND));
        if (lobbySchemaRoute == null) {
            throw new IllegalArgumentException("Unknown message type");
        }

        Schema schema = lobbySchemaRoute.schema();

        List<Error> errors = schema.validate(lobbyPassthrough.message());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid message");
        }

        return lobbySchemaRoute.route();
    }

    private void validateGPGPassthrough(ProtocolMessage.Passthrough.GPG gpgPassthrough) {
        List<Error> errors = gameSchema.validate(gpgPassthrough.message());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid message");
        }
    }

    @Incoming("lobby-outbound")
    @RunOnVirtualThread
    void handleOutboundMessage(OutboundPassthroughMessage outboundPassthroughMessage) {
        try (NoThrowCloseable _ = populateMDC(outboundPassthroughMessage)) {
            ProtocolMessage.Passthrough message = outboundPassthroughMessage.message();
            switch (outboundPassthroughMessage.target()) {
                case OutboundTarget.Session(long sessionId) -> sendMessageToSession(sessionId, message);
                case OutboundTarget.Player(int playerId) -> sendMessageToPlayer(playerId, message);
                case OutboundTarget.All() -> sendMessageToAll(message);
            }
        }
    }

    private void sendMessageToAll(ProtocolMessage.Passthrough message) {
        sessionHandlerMap.keySet().forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToPlayer(int playerId, ProtocolMessage.Passthrough message) {
        Set<Long> playerSessions = playerSessionMap.getOrDefault(playerId, Set.of());
        if (playerSessions.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessions.forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToSession(long sessionId, ProtocolMessage.Passthrough message) {
        SessionHandler sessionHandler = sessionHandlerMap.get(sessionId);
        if (sessionHandler == null) {
            LOG.warn("No active session for session id");
            return;
        }

        sessionHandler.sendMessage(message);
        if (message instanceof ProtocolMessage.Passthrough.Lobby(String command, ObjectNode payload) &&
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
        playerConnectionEmitter.send(new PlayerSessionDetails(playerId, sessionId, true));
    }

    void unregisterSession(SessionHandler sessionHandler) {
        int playerId = sessionHandler.playerId();
        long sessionId = sessionHandler.sessionId();
        playerConnectionEmitter.send(new PlayerSessionDetails(playerId, sessionId, false));

        if (!sessionHandlerMap.remove(sessionId, sessionHandler)) {
            LOG.warn("Session controller not associated with session");
        }

        playerSessionMap.compute(playerId, (_, sessions) -> {
            if (sessions == null || !sessions.remove(sessionId)) {
                LOG.warn("Session controller not associated with player");
                return null;
            }

            return sessions.isEmpty() ? null : sessions;
        });

    }

    private NoThrowCloseable populateMDC(OutboundPassthroughMessage outboundPassthroughMessage) {
        switch (outboundPassthroughMessage.target()) {
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

    private record LobbySchemaRoute(
            String route,
            Schema schema
    ) {}
}
