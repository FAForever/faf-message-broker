package com.faforever.server.message;

import com.faforever.server.domain.LobbyCommandSchemaRepository;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.PassthroughMessage;
import com.faforever.server.message.service.InboundPassthroughMessage;
import com.faforever.server.message.service.LobbySchemaDetails;
import com.faforever.server.message.service.OutboundPassthroughMessage;
import com.faforever.server.message.service.OutboundTarget;
import com.faforever.server.message.service.PlayerSessionStatus;
import com.faforever.server.micrometer.ProtocolMessageCommandResolver;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
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
@RunOnVirtualThread
public class MessageBroker {

    private final Emitter<Map<String, String>> schemaRequestEmitter;
    private final Emitter<InboundPassthroughMessage> lobbyMessageEmitter;
    private final Emitter<PlayerSessionStatus> playerConnectionEmitter;

    private final LobbyCommandSchemaRepository lobbyCommandSchemaRepository;


    private final Map<LobbySchemaKey, LobbySchemaRoute> lobbySchemas;
    private final Map<Long, SessionHandler> sessionHandlerMap;
    private final Map<Integer, Set<Long>> playerSessionMap;
    private final Schema gameSchema;

    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    public MessageBroker(@Channel("lobby-schema-request") Emitter<Map<String, String>> schemaRequestEmitter,
                         @Channel("lobby-inbound") Emitter<InboundPassthroughMessage> lobbyMessageEmitter,
                         @Channel("player-connection") Emitter<PlayerSessionStatus> playerConnectionEmitter,
                         LobbyCommandSchemaRepository lobbyCommandSchemaRepository, MeterRegistry meterRegistry) {
        this.schemaRequestEmitter = schemaRequestEmitter;
        this.lobbyMessageEmitter = lobbyMessageEmitter;
        this.playerConnectionEmitter = playerConnectionEmitter;
        this.lobbyCommandSchemaRepository = lobbyCommandSchemaRepository;
        this.gameSchema = schemaRegistry.getSchema(SchemaLocation.of("gpg"),
                                                   PassthroughMessage.GPG.GAME_PASSTHROUGH_SCHEMA, InputFormat.JSON);
        sessionHandlerMap = meterRegistry.gaugeMapSize("sessions", Set.of(), new ConcurrentHashMap<>());
        playerSessionMap = meterRegistry.gaugeMapSize("players", Set.of(), new ConcurrentHashMap<>());
        lobbySchemas = meterRegistry.gaugeMapSize("schema", Set.of(), new ConcurrentHashMap<>());
    }

    @PostConstruct
    void loadSchemas() {
        lobbyCommandSchemaRepository.loadAll().forEach(this::registerSchema);
        schemaRequestEmitter.send(Message.of(Map.of()));
    }

    @Incoming("lobby-schema-registration")
    @Counted("schema.registrations")
    void handleLobbySchemaRegistration(LobbySchemaDetails lobbySchemaDetails) {
        lobbyCommandSchemaRepository.save(lobbySchemaDetails);
        registerSchema(lobbySchemaDetails);
    }

    private void registerSchema(LobbySchemaDetails lobbySchemaDetails) {
        LobbySchemaKey lobbySchemaKey = new LobbySchemaKey(lobbySchemaDetails.command(),
                                                           lobbySchemaDetails.direction());
        ObjectNode schemaNode = lobbySchemaDetails.schema();
        schemaNode.withObjectProperty(SchemaKeyword.TAG_PROPERTIES.forVersion(SchemaVersion.DRAFT_2020_12)).withObject(
                "request_id").putIfAbsent(SchemaKeyword.TAG_TYPE.forVersion(SchemaVersion.DRAFT_2020_12),
                                          new StringNode("string"));
        Schema schema = schemaRegistry.getSchema(SchemaLocation.of(lobbySchemaDetails.command()), schemaNode);
        lobbySchemas.put(lobbySchemaKey, new LobbySchemaRoute(lobbySchemaDetails.route(), schema));
    }

    @Incoming("player-active-request")
    @Outgoing("player-active-response")
    @Counted("player.session.requests")
    Message<Collection<PlayerSessionStatus>> handlePlayerSessionRequest(Message<Map<String, String>> message) {
        Set<PlayerSessionStatus> activeSessions = playerSessionMap.entrySet()
                                                                  .stream()
                                                                  .flatMap(entry -> entry.getValue()
                                                                                         .stream()
                                                                                         .map(sessionId -> new PlayerSessionStatus(
                                                                                                 entry.getKey(),
                                                                                                 sessionId,
                                                                                                 PlayerSessionStatus.State.EXISTING)))
                                                                  .collect(Collectors.toSet());

        String replyToQueue = message.getMetadata(IncomingRabbitMQMetadata.class).map(
                IncomingRabbitMQMetadata::getReplyTo).orElse("");
        return Message.of(activeSessions,
                          Metadata.of(OutgoingRabbitMQMetadata.builder().withRoutingKey(replyToQueue).build()));
    }

    void handleInboundMessage(InboundPassthroughMessage inboundLobbyMessage) {
        switch (inboundLobbyMessage.message()) {
            case PassthroughMessage.Lobby lobbyPassthrough -> {
                String route = validateLobbyPassthrough(lobbyPassthrough);
                String routingKey = "lobby." + route + "." + lobbyPassthrough.command();
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(routingKey)
                                                                                  .build();
                lobbyMessageEmitter.send(Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
            case PassthroughMessage.GPG gpgPassthrough -> {
                validateGPGPassthrough(gpgPassthrough);
                String routingKey = "gpg." + gpgPassthrough.command();
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(routingKey)
                                                                                  .build();
                lobbyMessageEmitter.send(Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
        }
    }

    private String validateLobbyPassthrough(PassthroughMessage.Lobby lobbyPassthrough) {
        String command = lobbyPassthrough.command();
        LobbySchemaRoute lobbySchemaRoute = lobbySchemas.get(
                new LobbySchemaKey(command, LobbySchemaDetails.Direction.INBOUND));
        if (lobbySchemaRoute == null) {
            throw new IllegalArgumentException("Unknown message type");
        }

        Schema schema = lobbySchemaRoute.schema();

        List<Error> errors = schema.validate(lobbyPassthrough.message());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid message" + errors);
        }

        return lobbySchemaRoute.route();
    }

    private void validateGPGPassthrough(PassthroughMessage.GPG gpgPassthrough) {
        List<Error> errors = gameSchema.validate(gpgPassthrough.message());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid message");
        }
    }

    @MDCPopulated
    @Incoming("lobby-outbound")
    @Counted("messages.outbound")
    void handleOutboundMessage(@MeterTag(
            value = "command", resolver = ProtocolMessageCommandResolver.class
    ) OutboundPassthroughMessage outboundPassthroughMessage) {
            PassthroughMessage message = outboundPassthroughMessage.message();
            switch (outboundPassthroughMessage.target()) {
                case OutboundTarget.Session(long sessionId) -> sendMessageToSession(sessionId, message);
                case OutboundTarget.Player(int playerId) -> sendMessageToPlayer(playerId, message);
                case OutboundTarget.All() -> sendMessageToAll(message);
            }
    }

    private void sendMessageToAll(PassthroughMessage message) {
        sessionHandlerMap.keySet().forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToPlayer(int playerId, PassthroughMessage message) {
        Set<Long> playerSessions = playerSessionMap.getOrDefault(playerId, Set.of());
        if (playerSessions.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessions.forEach(sessionId -> sendMessageToSession(sessionId, message));
    }

    private void sendMessageToSession(long sessionId, PassthroughMessage message) {
        SessionHandler sessionHandler = sessionHandlerMap.get(sessionId);
        if (sessionHandler == null) {
            LOG.warn("No active session for session id");
            return;
        }

        sessionHandler.sendMessage(message);
        if (message instanceof PassthroughMessage.Lobby(_, String command, ObjectNode payload) &&
            "notice".equals(command) &&
            payload.findValue("style") instanceof StringNode value &&
            value.stringValue().equalsIgnoreCase("kick")) {
            sessionHandler.close();
        }
    }

    void registerSession(int playerId, SessionHandler sessionHandler) {
        LOG.info("Session registered");
        long sessionId = sessionHandler.sessionId();
        SessionHandler mappedSessionHandler = sessionHandlerMap.putIfAbsent(sessionId, sessionHandler);
        if (mappedSessionHandler != null && mappedSessionHandler != sessionHandler) {
            throw new IllegalStateException("Existing session for id %d".formatted(sessionId));
        }

        playerSessionMap.compute(playerId, (_, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(sessionId);
            return sessions;
        });
        playerConnectionEmitter.send(new PlayerSessionStatus(playerId, sessionId, PlayerSessionStatus.State.NEW));
    }

    void unregisterSession(int playerId, SessionHandler sessionHandler) {
        LOG.info("Session unregistered");
        long sessionId = sessionHandler.sessionId();
        playerConnectionEmitter.send(new PlayerSessionStatus(playerId, sessionId, PlayerSessionStatus.State.GONE));

        if (!sessionHandlerMap.remove(sessionId, sessionHandler)) {
            LOG.warn("Session controller not associated with session");
            return;
        }

        playerSessionMap.compute(playerId, (_, sessions) -> {
            if (sessions == null || !sessions.remove(sessionId)) {
                LOG.warn("Session controller not associated with player");
                return null;
            }

            return sessions.isEmpty() ? null : sessions;
        });
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
