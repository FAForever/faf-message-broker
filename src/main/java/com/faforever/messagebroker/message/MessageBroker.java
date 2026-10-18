package com.faforever.messagebroker.message;

import com.faforever.messagebroker.config.FAFProperties;
import com.faforever.messagebroker.domain.LobbyCommandSchemaRepository;
import com.faforever.messagebroker.endpoint.session.SessionHandler;
import com.faforever.messagebroker.interceptor.cdi.MDCPopulated;
import com.faforever.messagebroker.message.client.FailedMessage;
import com.faforever.messagebroker.message.client.PassthroughMessage;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.message.service.LobbySchemaDetails;
import com.faforever.messagebroker.message.service.OutboundPassthroughMessage;
import com.faforever.messagebroker.message.service.OutboundTarget;
import com.faforever.messagebroker.message.service.PlayerSessionState;
import com.faforever.messagebroker.micrometer.ProtocolMessageCommandResolver;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
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
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@ApplicationScoped
@RunOnVirtualThread
public class MessageBroker {

    private final FAFProperties fafProperties;

    private final Emitter<Map<String, String>> schemaRequestEmitter;
    private final Emitter<InboundPassthroughMessage> lobbyMessageEmitter;
    private final Emitter<PlayerSessionState> playerConnectionEmitter;

    private final LobbyCommandSchemaRepository lobbyCommandSchemaRepository;

    private final Map<LobbySchemaKey, LobbySchemaRoute> lobbySchemas;
    private final Map<Long, SessionHandler> sessionHandlerMap;
    private final Map<Integer, Set<SessionHandler>> playerSessionMap;
    private final Schema gameSchema;

    private final SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    public MessageBroker(FAFProperties fafProperties,
                         @Channel("lobby-schema-request") Emitter<Map<String, String>> schemaRequestEmitter,
                         @Channel("lobby-inbound") Emitter<InboundPassthroughMessage> lobbyMessageEmitter,
                         @Channel("player-connection") Emitter<PlayerSessionState> playerConnectionEmitter,
                         LobbyCommandSchemaRepository lobbyCommandSchemaRepository, MeterRegistry meterRegistry) {
        this.fafProperties = fafProperties;
        this.schemaRequestEmitter = schemaRequestEmitter;
        this.lobbyMessageEmitter = lobbyMessageEmitter;
        this.playerConnectionEmitter = playerConnectionEmitter;
        this.lobbyCommandSchemaRepository = lobbyCommandSchemaRepository;
        this.gameSchema = schemaRegistry.getSchema(SchemaLocation.of("gpg"),
                                                   PassthroughMessage.GPG.GAME_PASSTHROUGH_SCHEMA, InputFormat.JSON);
        sessionHandlerMap = meterRegistry.gaugeMapSize("faf.sessions", Set.of(), new ConcurrentHashMap<>());
        playerSessionMap = meterRegistry.gaugeMapSize("faf.players", Set.of(), new ConcurrentHashMap<>());
        lobbySchemas = meterRegistry.gaugeMapSize("faf.schemas", Set.of(), new ConcurrentHashMap<>());
    }

    @PostConstruct
    void loadSchemas() {
        lobbyCommandSchemaRepository.loadAll().forEach(this::registerSchema);
        schemaRequestEmitter.send(Message.of(Map.of()));
    }

    @Incoming("lobby-schema-registration")
    @Counted("faf.schema.registrations")
    void handleLobbySchemaRegistration(LobbySchemaDetails lobbySchemaDetails) {
        lobbyCommandSchemaRepository.save(lobbySchemaDetails);
        registerSchema(lobbySchemaDetails);
    }

    private void registerSchema(LobbySchemaDetails lobbySchemaDetails) {
        LobbySchemaKey lobbySchemaKey = new LobbySchemaKey(lobbySchemaDetails.command(),
                                                           lobbySchemaDetails.direction());
        ObjectNode schemaNode = lobbySchemaDetails.schema();
        schemaNode.withObjectProperty(SchemaKeyword.TAG_PROPERTIES.forVersion(SchemaVersion.DRAFT_2020_12))
                  .withObject("request_id")
                  .putIfAbsent(SchemaKeyword.TAG_TYPE.forVersion(SchemaVersion.DRAFT_2020_12),
                               new StringNode("string"));
        Schema schema = schemaRegistry.getSchema(SchemaLocation.of(lobbySchemaDetails.command()), schemaNode);
        lobbySchemas.put(lobbySchemaKey, new LobbySchemaRoute(lobbySchemaDetails.route(), schema));
    }


    @Timed(value = "faf.player.updates", histogram = true)
    @Scheduled(every = "${faf.player-session-status-refresh-period}",
               skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sendPlayerSessionUpdates() {
        playerSessionMap.entrySet()
                        .stream()
                        .flatMap(entry -> entry.getValue()
                                               .stream()
                                               .map(sessionHandler -> new PlayerSessionState(entry.getKey(),
                                                                                             sessionHandler.sessionId(),
                                                                                             sessionHandler.remoteIp(),
                                                                                             new PlayerSessionState.State.Existing(
                                                                                                     fafProperties.playerSessionStatusExpiry()))))
                        .forEach(playerConnectionEmitter::send);
    }

    @Incoming("player-active-request")
    @Counted("faf.player.session.requests")
    CompletionStage<Void> handlePlayerSessionRequest(Message<Map<String, String>> message) {
        Metadata metadata = message.getMetadata(IncomingRabbitMQMetadata.class)
                                   .map(IncomingRabbitMQMetadata::getReplyTo)
                                   .map(replyTo -> OutgoingRabbitMQMetadata.builder()
                                                                           .withRoutingKey(replyTo)
                                                                           .withExchange("")
                                                                           .build())
                                   .map(Metadata::of)
                                   .orElse(Metadata.of());
        playerSessionMap.entrySet()
                        .stream()
                        .flatMap(entry -> entry.getValue()
                                               .stream()
                                               .map(sessionHandler -> new PlayerSessionState(entry.getKey(),
                                                                                             sessionHandler.sessionId(),
                                                                                             sessionHandler.remoteIp(),
                                                                                             new PlayerSessionState.State.Existing(
                                                                                                     fafProperties.playerSessionStatusExpiry()))))
                        .map(playerSessionState -> Message.of(playerSessionState, metadata))
                        .forEach(playerConnectionEmitter::send);

        return message.ack();
    }

    public void handleInboundMessage(InboundPassthroughMessage inboundLobbyMessage) {
        switch (inboundLobbyMessage.message()) {
            case PassthroughMessage.Lobby lobbyPassthrough -> {
                String route = validateLobbyPassthrough(lobbyPassthrough);
                if (route == null) {
                    sendMessageToSession(inboundLobbyMessage.sessionId(),
                                         new FailedMessage(inboundLobbyMessage.message().requestId(),
                                                           "Invalid message received", "message.invalid"));
                    return;
                }

                String routingKey = "lobby." + route + "." + lobbyPassthrough.command();
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(routingKey)
                                                                                  .build();
                lobbyMessageEmitter.send(Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
            case PassthroughMessage.GPG gpgPassthrough -> {
                if (!validateGPGPassthrough(gpgPassthrough)) {
                    sendMessageToSession(inboundLobbyMessage.sessionId(),
                                         new FailedMessage(inboundLobbyMessage.message().requestId(),
                                                           "Invalid message received", "message.invalid"));
                    return;
                }

                String routingKey = "gpg." + gpgPassthrough.command();
                final OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                                                                                  .withRoutingKey(routingKey)
                                                                                  .build();
                lobbyMessageEmitter.send(Message.of(inboundLobbyMessage, Metadata.of(metadata)));
            }
        }
    }

    private @Nullable String validateLobbyPassthrough(PassthroughMessage.Lobby lobbyPassthrough) {
        String command = lobbyPassthrough.command();
        LobbySchemaRoute lobbySchemaRoute = lobbySchemas.get(
                new LobbySchemaKey(command, LobbySchemaDetails.Direction.INBOUND));
        if (lobbySchemaRoute == null) {
            LOG.warn("Unknown message type");
            return fafProperties.publishUnknownMessages() ? fafProperties.unknownMessageRoute() : null;
        }

        Schema schema = lobbySchemaRoute.schema();

        List<Error> errors = schema.validate(lobbyPassthrough.message());
        if (!errors.isEmpty()) {
            LOG.warn("Invalid message received: " + errors);
            return null;
        }

        return lobbySchemaRoute.route();
    }

    private boolean validateGPGPassthrough(PassthroughMessage.GPG gpgPassthrough) {
        List<Error> errors = gameSchema.validate(gpgPassthrough.message());
        if (!errors.isEmpty()) {
            LOG.warn("Invalid message received: " + errors);
            return false;
        }
        return true;
    }

    @MDCPopulated
    @Incoming("lobby-outbound")
    @Timed(value = "faf.messages.outbound", histogram = true)
    void handleOutboundMessage(@MeterTag(value = "command",
                                         resolver = ProtocolMessageCommandResolver.class) OutboundPassthroughMessage outboundPassthroughMessage) {
        PassthroughMessage message = outboundPassthroughMessage.message();
        switch (outboundPassthroughMessage.target()) {
            case OutboundTarget.Session(long sessionId) -> sendMessageToSession(sessionId, message);
            case OutboundTarget.Player(int playerId) -> sendMessageToPlayer(playerId, message);
            case OutboundTarget.All() -> sendMessageToAll(message);
        }
    }

    private void sendMessageToAll(PassthroughMessage message) {
        sessionHandlerMap.values().forEach(sessionHandler -> sendMessageAndCloseIfNecessary(sessionHandler, message));
    }

    private void sendMessageToPlayer(int playerId, PassthroughMessage message) {
        Set<SessionHandler> playerSessions = playerSessionMap.getOrDefault(playerId, Set.of());
        if (playerSessions.isEmpty()) {
            LOG.warn("No active sessions for player");
            return;
        }

        playerSessions.forEach(sessionHandler -> sendMessageAndCloseIfNecessary(sessionHandler, message));
    }

    private void sendMessageToSession(long sessionId, ProtocolMessage.Outgoing message) {
        SessionHandler sessionHandler = sessionHandlerMap.get(sessionId);
        if (sessionHandler == null) {
            LOG.warn("No active session for session id");
            return;
        }

        sendMessageAndCloseIfNecessary(sessionHandler, message);
    }

    private void sendMessageAndCloseIfNecessary(SessionHandler sessionHandler, ProtocolMessage.Outgoing message) {
        if (sessionHandler.isClosed()) {
            LOG.warn("Sending message too already closed session");
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

    public void registerSession(int playerId, SessionHandler sessionHandler) {
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
            sessions.add(sessionHandler);
            return sessions;
        });
        playerConnectionEmitter.send(new PlayerSessionState(playerId, sessionId, sessionHandler.remoteIp(),
                                                            new PlayerSessionState.State.New(
                                                                    fafProperties.playerSessionStatusExpiry())));
    }

    public void unregisterSession(int playerId, SessionHandler sessionHandler) {
        LOG.info("Session unregistered");
        long sessionId = sessionHandler.sessionId();

        if (!sessionHandlerMap.remove(sessionId, sessionHandler)) {
            LOG.warn("Session controller not associated with session");
            return;
        }

        playerConnectionEmitter.send(new PlayerSessionState(playerId, sessionId, sessionHandler.remoteIp(),
                                                            new PlayerSessionState.State.Gone()));
        playerSessionMap.compute(playerId, (_, sessions) -> {
            if (sessions == null || !sessions.remove(sessionHandler)) {
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
