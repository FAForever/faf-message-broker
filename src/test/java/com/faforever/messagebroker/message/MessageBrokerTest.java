package com.faforever.messagebroker.message;

import com.faforever.messagebroker.domain.LobbyCommandSchemaRepository;
import com.faforever.messagebroker.endpoint.connection.UserAgent;
import com.faforever.messagebroker.endpoint.session.SessionHandler;
import com.faforever.messagebroker.message.client.FailedMessage;
import com.faforever.messagebroker.message.client.PassthroughMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.message.service.LobbySchemaDetails;
import com.faforever.messagebroker.message.service.OutboundPassthroughMessage;
import com.faforever.messagebroker.message.service.OutboundTarget;
import com.faforever.messagebroker.message.service.PlayerSessionState;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class MessageBrokerTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID_1 = 2;
    private static final long SESSION_ID_2 = 3;
    private static final String REMOTE_IP = "127.0.0.1";

    private static final String COMMAND = "test";
    private static final String SCHEMA_STRING = """
                                                {
                                                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                  "type": "object",
                                                  "properties": {
                                                    "command": { "const": "test" }
                                                  },
                                                  "required": ["command"],
                                                  "additionalProperties": false
                                                }
                                                """;
    private static final ObjectNode TEST_SCHEMA = (ObjectNode) new JsonMapper().readTree(SCHEMA_STRING);

    @Inject
    MessageBroker messageBroker;

    @InjectMock
    private Emitter<InboundPassthroughMessage> lobbyMessageEmitter;
    @InjectMock
    private Emitter<PlayerSessionState> playerConnectionEmitter;
    @InjectMock
    private LobbyCommandSchemaRepository lobbyCommandSchemaRepository;

    @Nested
    class Registration {

        private final SessionHandler sessionHandler = mock();

        @BeforeEach
        void setup() {
            when(sessionHandler.playerId()).thenReturn(PLAYER_ID);
            when(sessionHandler.sessionId()).thenReturn(SESSION_ID_1);
            when(sessionHandler.remoteIp()).thenReturn(REMOTE_IP);
            when(sessionHandler.userAgent()).thenReturn(new UserAgent(null, null));
        }

        @Test
        @TestConfigProperty(key = "faf.player-session-status-refresh-period", value = "1m")
        void testRegistration() {
            messageBroker.registerSession(PLAYER_ID, sessionHandler);

            verify(playerConnectionEmitter).send(new PlayerSessionState(PLAYER_ID, SESSION_ID_1, REMOTE_IP,
                                                                        new PlayerSessionState.State.New(
                                                                                Duration.ofMinutes(2))));

            messageBroker.sendPlayerSessionUpdates();

            verify(playerConnectionEmitter).send(new PlayerSessionState(PLAYER_ID, SESSION_ID_1, REMOTE_IP,
                                                                        new PlayerSessionState.State.Existing(
                                                                                Duration.ofMinutes(2))));

            messageBroker.unregisterSession(PLAYER_ID, sessionHandler);

            verify(playerConnectionEmitter).send(new PlayerSessionState(PLAYER_ID, SESSION_ID_1, REMOTE_IP,
                                                                        new PlayerSessionState.State.Gone()));
        }

        @Test
        @TestConfigProperty(key = "faf.player-session-status-refresh-period", value = "1m")
        void testRequestedPlayerStatusPlayerOnline() {
            messageBroker.registerSession(PLAYER_ID, sessionHandler);

            messageBroker.handlePlayerSessionRequest(Message.of(Map.of(), Metadata.of(OutgoingRabbitMQMetadata.builder()
                                                                                                              .withReplyTo(
                                                                                                                      "test.queue")
                                                                                                              .build()
                                                                                                              .toIncomingMetadata(
                                                                                                                      "test",
                                                                                                                      false))));

            ArgumentCaptor<Message<PlayerSessionState>> metadataMessageCaptor = captor();

            verify(playerConnectionEmitter).send(metadataMessageCaptor.capture());

            Message<PlayerSessionState> activeMessage = metadataMessageCaptor.getValue();

            assertEquals("test.queue", activeMessage.getMetadata(OutgoingRabbitMQMetadata.class)
                                                    .map(OutgoingRabbitMQMetadata::getRoutingKey)
                                                    .orElseThrow());
            assertEquals(new PlayerSessionState(PLAYER_ID, SESSION_ID_1, REMOTE_IP,
                                                new PlayerSessionState.State.Existing(Duration.ofMinutes(2))),
                         activeMessage.getPayload());
        }


        @Test
        @TestConfigProperty(key = "faf.player-session-status-refresh-period", value = "1m")
        void testRequestedPlayerStatusNoPlayerOnline() {
            messageBroker.handlePlayerSessionRequest(Message.of(Map.of(), Metadata.of(OutgoingRabbitMQMetadata.builder()
                                                                                                              .withReplyTo(
                                                                                                                      "test.queue")
                                                                                                              .build()
                                                                                                              .toIncomingMetadata(
                                                                                                                      "test",
                                                                                                                      false))));

            verifyNoInteractions(playerConnectionEmitter);
        }

    }

    @Nested
    class Inbound {

        private final SessionHandler sessionHandler = mock();

        @BeforeEach
        void setup() {
            when(sessionHandler.playerId()).thenReturn(PLAYER_ID);
            when(sessionHandler.sessionId()).thenReturn(SESSION_ID_1);
            when(sessionHandler.userAgent()).thenReturn(new UserAgent(null, null));

            messageBroker.registerSession(PLAYER_ID, sessionHandler);
        }


        @Test
        void testUnknownLobbyMessage() {
            messageBroker.handleInboundMessage(createInboundMessage(
                    Map.of("command", new StringNode(COMMAND), "request_id", new StringNode("12345"))));
            verifyNoInteractions(lobbyMessageEmitter);
            verify(sessionHandler).sendMessage(
                    new FailedMessage("12345", "Invalid message received", "message.invalid"));
        }

        @Test
        void testInvalidLobbyMessage() {
            when(lobbyCommandSchemaRepository.loadAll()).thenReturn(
                    List.of(new LobbySchemaDetails(COMMAND, LobbySchemaDetails.Direction.INBOUND, TEST_SCHEMA,
                                                   "route")));

            messageBroker.loadSchemas();

            messageBroker.handleInboundMessage(
                    createInboundMessage(
                            Map.of("command", new StringNode(COMMAND), "key", NullNode.getInstance(), "request_id",
                                   new StringNode("12345"))));

            verifyNoInteractions(lobbyMessageEmitter);
            verify(sessionHandler).sendMessage(
                    new FailedMessage("12345", "Invalid message received", "message.invalid"));
        }

        @Test
        @TestConfigProperty(key = "faf.publish-unknown-messages", value = "true")
        void testInvalidLobbyMessageUnknownAllowed() {
            when(lobbyCommandSchemaRepository.loadAll()).thenReturn(
                    List.of(new LobbySchemaDetails(COMMAND, LobbySchemaDetails.Direction.INBOUND, TEST_SCHEMA,
                                                   "route")));

            messageBroker.loadSchemas();

            messageBroker.handleInboundMessage(
                    createInboundMessage(
                            Map.of("command", new StringNode(COMMAND), "key", NullNode.getInstance(), "request_id",
                                   new StringNode("12345"))));

            verifyNoInteractions(lobbyMessageEmitter);
            verify(sessionHandler).sendMessage(
                    new FailedMessage("12345", "Invalid message received", "message.invalid"));
        }

        @Test
        void testValidLobbyMessage() {
            when(lobbyCommandSchemaRepository.loadAll()).thenReturn(
                    List.of(new LobbySchemaDetails(COMMAND, LobbySchemaDetails.Direction.INBOUND, TEST_SCHEMA,
                                                   "route")));

            messageBroker.loadSchemas();

            InboundPassthroughMessage message = createInboundMessage(Map.of("command", new StringNode(COMMAND)));
            messageBroker.handleInboundMessage(message);

            ArgumentCaptor<Message<InboundPassthroughMessage>> messageCaptor = captor();

            verify(lobbyMessageEmitter).send(messageCaptor.capture());

            Message<InboundPassthroughMessage> sentMessage = messageCaptor.getValue();
            assertEquals(message, sentMessage.getPayload());

            Metadata metadata = sentMessage.getMetadata();
            OutgoingRabbitMQMetadata outgoingRabbitMQMetadata = metadata.get(OutgoingRabbitMQMetadata.class)
                                                                        .orElseThrow();
            assertEquals("lobby.route." + COMMAND, outgoingRabbitMQMetadata.getRoutingKey());
        }

        @Test
        @TestConfigProperty(key = "faf.publish-unknown-messages", value = "true")
        void testUnknownLobbyMessageUnknownAllowed() {
            InboundPassthroughMessage message = createInboundMessage(
                    Map.of("command", new StringNode(COMMAND), "request_id", new StringNode("12345")));

            messageBroker.handleInboundMessage(message);
            ArgumentCaptor<Message<InboundPassthroughMessage>> messageCaptor = captor();

            verify(lobbyMessageEmitter).send(messageCaptor.capture());

            Message<InboundPassthroughMessage> sentMessage = messageCaptor.getValue();
            assertEquals(message, sentMessage.getPayload());

            Metadata metadata = sentMessage.getMetadata();
            OutgoingRabbitMQMetadata outgoingRabbitMQMetadata = metadata.get(OutgoingRabbitMQMetadata.class)
                                                                        .orElseThrow();
            assertEquals("lobby.legacy." + COMMAND, outgoingRabbitMQMetadata.getRoutingKey());
        }

        @Test
        void testInvalidGameMessage() {
            messageBroker.handleInboundMessage(
                    createInboundMessage(Map.of("command", new StringNode("Test"), "target", new StringNode("game"))));

            verifyNoInteractions(lobbyMessageEmitter);
            verify(sessionHandler).sendMessage(new FailedMessage("", "Invalid message received", "message.invalid"));
        }

        @Test
        void testValidGameMessage() {
            InboundPassthroughMessage message = createInboundMessage(
                    Map.of("command", new StringNode("Test"), "target", new StringNode("game"), "args",
                           new ArrayNode(JsonNodeFactory.instance)));
            messageBroker.handleInboundMessage(message);

            ArgumentCaptor<Message<InboundPassthroughMessage>> messageCaptor = captor();

            verify(lobbyMessageEmitter).send(messageCaptor.capture());

            Message<InboundPassthroughMessage> sentMessage = messageCaptor.getValue();
            assertEquals(message, sentMessage.getPayload());

            Metadata metadata = sentMessage.getMetadata();
            OutgoingRabbitMQMetadata outgoingRabbitMQMetadata = metadata.get(OutgoingRabbitMQMetadata.class)
                                                                        .orElseThrow();
            assertEquals("gpg.Test", outgoingRabbitMQMetadata.getRoutingKey());
        }

        @Test
        void testSchemaRegistration() {
            LobbySchemaDetails lobbySchemaDetails = new LobbySchemaDetails(COMMAND,
                                                                           LobbySchemaDetails.Direction.INBOUND,
                                                                           TEST_SCHEMA, "route");
            messageBroker.handleLobbySchemaRegistration(lobbySchemaDetails);

            verify(lobbyCommandSchemaRepository).save(lobbySchemaDetails);

            InboundPassthroughMessage message = createInboundMessage(Map.of("command", new StringNode(COMMAND)));
            messageBroker.handleInboundMessage(message);

            ArgumentCaptor<Message<InboundPassthroughMessage>> messageCaptor = captor();

            verify(lobbyMessageEmitter).send(messageCaptor.capture());

            Message<InboundPassthroughMessage> sentMessage = messageCaptor.getValue();
            assertEquals(message, sentMessage.getPayload());

            Metadata metadata = sentMessage.getMetadata();
            OutgoingRabbitMQMetadata outgoingRabbitMQMetadata = metadata.get(OutgoingRabbitMQMetadata.class)
                                                                        .orElseThrow();
            assertEquals("lobby.route." + COMMAND, outgoingRabbitMQMetadata.getRoutingKey());
        }

        private InboundPassthroughMessage createInboundMessage(Map<String, ? extends JsonNode> properties) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.setAll(properties);
            PassthroughMessage message = PassthroughMessage.of(node);
            return new InboundPassthroughMessage(SESSION_ID_1, PLAYER_ID, message);
        }
    }

    @Nested
    class Outbound {

        private final SessionHandler sessionHandler1 = mock();
        private final SessionHandler sessionHandler2 = mock();

        @BeforeEach
        void setup() {
            when(sessionHandler1.playerId()).thenReturn(PLAYER_ID);
            when(sessionHandler1.sessionId()).thenReturn(SESSION_ID_1);
            when(sessionHandler1.userAgent()).thenReturn(new UserAgent(null, null));

            when(sessionHandler2.playerId()).thenReturn(PLAYER_ID);
            when(sessionHandler2.sessionId()).thenReturn(SESSION_ID_2);
            when(sessionHandler2.userAgent()).thenReturn(new UserAgent(null, null));

            messageBroker.registerSession(PLAYER_ID, sessionHandler1);
            messageBroker.registerSession(PLAYER_ID, sessionHandler2);
        }

        @Test
        void testAllMessage() {
            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.all(),
                                                                       Map.of("command", new StringNode(COMMAND)));

            messageBroker.handleOutboundMessage(message);

            ArgumentCaptor<PassthroughMessage> messageCaptor = captor();
            verify(sessionHandler1).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage1 = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage1);

            verify(sessionHandler2).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage2 = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage2);
        }

        @Test
        void testPlayerMessage() {
            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.player(PLAYER_ID),
                                                                       Map.of("command", new StringNode(COMMAND)));

            messageBroker.handleOutboundMessage(message);

            ArgumentCaptor<PassthroughMessage> messageCaptor = captor();
            verify(sessionHandler1).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage1 = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage1);

            verify(sessionHandler2).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage2 = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage2);
        }

        @Test
        void testSessionMessage() {
            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.session(SESSION_ID_1),
                                                                       Map.of("command", new StringNode(COMMAND)));

            messageBroker.handleOutboundMessage(message);

            ArgumentCaptor<PassthroughMessage> messageCaptor = captor();
            verify(sessionHandler1).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage);

            verify(sessionHandler2, never()).sendMessage(any());
        }

        @Test
        void testDifferentPlayerMessage() {
            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.player(0),
                                                                       Map.of("command", new StringNode(COMMAND)));

            messageBroker.handleOutboundMessage(message);

            verify(sessionHandler1, never()).sendMessage(any());
            verify(sessionHandler2, never()).sendMessage(any());
        }

        @Test
        void testDifferentSessionMessage() {
            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.session(0),
                                                                       Map.of("command", new StringNode(COMMAND)));

            messageBroker.handleOutboundMessage(message);

            verify(sessionHandler1, never()).sendMessage(any());
            verify(sessionHandler2, never()).sendMessage(any());
        }

        @Test
        void testKickMessage() {
            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.player(PLAYER_ID),
                                                                       Map.of("command", new StringNode("notice"),
                                                                              "style", new StringNode("kick")));

            messageBroker.handleOutboundMessage(message);

            ArgumentCaptor<PassthroughMessage> messageCaptor = captor();
            verify(sessionHandler1).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage1 = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage1);

            verify(sessionHandler1).close();

            verify(sessionHandler2).sendMessage(messageCaptor.capture());

            PassthroughMessage sentMessage2 = messageCaptor.getValue();
            assertEquals(message.message(), sentMessage2);

            verify(sessionHandler2).close();
        }

        @Test
        void testUnregisterSession() {
            messageBroker.unregisterSession(PLAYER_ID, sessionHandler1);

            OutboundPassthroughMessage message = createOutboundMessage(OutboundTarget.all(),
                                                                       Map.of("command", new StringNode(COMMAND)));

            messageBroker.handleOutboundMessage(message);

            verify(sessionHandler1, never()).sendMessage(any());
        }

        @Test
        void testDuplicateSessionRegistered() {
            messageBroker.unregisterSession(PLAYER_ID, sessionHandler2);

            when(sessionHandler2.sessionId()).thenReturn(SESSION_ID_1);

            assertThrows(IllegalStateException.class, () -> messageBroker.registerSession(PLAYER_ID, sessionHandler2));
        }

        private OutboundPassthroughMessage createOutboundMessage(OutboundTarget target,
                                                                 Map<String, ? extends JsonNode> properties) {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.setAll(properties);
            PassthroughMessage message = PassthroughMessage.of(node);
            return new OutboundPassthroughMessage(target, message);
        }

    }
}
