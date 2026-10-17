package com.faforever.server.message;

import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.FailedMessage;
import com.faforever.server.message.client.PassthroughMessage;
import com.faforever.server.message.client.ProtocolMessage;
import com.faforever.server.message.service.InboundPassthroughMessage;
import com.faforever.server.message.service.LobbySchemaDetails;
import com.faforever.server.message.service.OutboundPassthroughMessage;
import com.faforever.server.message.service.OutboundTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MessageSerializationTest {

    private JsonMapper jsonMapper;

    @BeforeEach
    void setup() {
        jsonMapper = JsonMapper.builder()
                               .propertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy())
                               .build();
    }

    @Test
    void testPingSerialization() throws Exception {
        ConnectionMessage.Ping ping = new ConnectionMessage.Ping("12345");

        JSONAssert.assertEquals("""
                                {
                                    "command": "ping",
                                    "request_id": "12345"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) ping),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPingIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "ping",
                                                                    "request_id": "12345"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertEquals(new ConnectionMessage.Ping("12345"), message);
    }

    @Test
    void testPingOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "ping",
                                                                    "request_id": "12345"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertEquals(new ConnectionMessage.Ping("12345"), message);
    }

    @Test
    void testPongSerialization() throws Exception {
        ConnectionMessage.Pong pong = new ConnectionMessage.Pong("12345");

        JSONAssert.assertEquals("""
                                {
                                    "command": "pong",
                                    "request_id": "12345"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) pong),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPongIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "pong",
                                                                    "request_id": "12345"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertEquals(new ConnectionMessage.Pong("12345"), message);
    }

    @Test
    void testPongOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "pong",
                                                                    "request_id": "12345"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertEquals(new ConnectionMessage.Pong("12345"), message);
    }

    @Test
    void testSessionRequestSerialization() throws Exception {
        ConnectionMessage.SessionRequest sessionRequest = new ConnectionMessage.SessionRequest("12345", "downlords-faf-client", "1.0.0");

        JSONAssert.assertEquals("""
                                {
                                    "command": "ask_session",
                                    "request_id": "12345",
                                    "user_agent": "downlords-faf-client",
                                    "version": "1.0.0"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Incoming) sessionRequest),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testSessionRequestDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "ask_session",
                                                                    "request_id": "12345",
                                                                    "user_agent": "downlords-faf-client",
                                                                    "version": "1.0.0"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertEquals(new ConnectionMessage.SessionRequest("12345", "downlords-faf-client", "1.0.0"), message);
    }

    @Test
    void testAuthenticateRequestSerialization() throws Exception {
        ConnectionMessage.AuthenticateRequest authRequest = new ConnectionMessage.AuthenticateRequest("12345", "jwt-token-xyz", "unique-id-999");

        JSONAssert.assertEquals("""
                                {
                                    "command": "auth",
                                    "request_id": "12345",
                                    "token": "jwt-token-xyz",
                                    "unique_id": "unique-id-999"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Incoming) authRequest),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testAuthenticateRequestDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "auth",
                                                                    "request_id": "12345",
                                                                    "token": "jwt-token-xyz",
                                                                    "unique_id": "unique-id-999"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertEquals(new ConnectionMessage.AuthenticateRequest("12345", "jwt-token-xyz", "unique-id-999"), message);
    }

    @Test
    void testSessionResponseSerialization() throws Exception {
        ConnectionMessage.SessionResponse sessionResponse = new ConnectionMessage.SessionResponse("12345", 987654L);

        JSONAssert.assertEquals("""
                                {
                                    "command": "session",
                                    "request_id": "12345",
                                    "session": 987654
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) sessionResponse),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testSessionResponseDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "session",
                                                                    "request_id": "12345",
                                                                    "session": 987654
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertEquals(new ConnectionMessage.SessionResponse("12345", 987654L), message);
    }

    @Test
    void testLoginFailureResponseSerialization() throws Exception {
        ConnectionMessage.LoginFailureResponse failureResponse = new ConnectionMessage.LoginFailureResponse("12345", "Invalid credentials");

        JSONAssert.assertEquals("""
                                {
                                    "command": "authentication_failure",
                                    "request_id": "12345",
                                    "text": "Invalid credentials"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) failureResponse),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testLoginFailureResponseDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "authentication_failure",
                                                                    "request_id": "12345",
                                                                    "text": "Invalid credentials"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertEquals(new ConnectionMessage.LoginFailureResponse("12345", "Invalid credentials"), message);
    }

    @Test
    void testFailedMessageSerialization() throws Exception {
        FailedMessage failedMessage = new FailedMessage("12345", "Invalid message received", "message.invalid");

        JSONAssert.assertEquals("""
                                {
                                    "command": "failed_message",
                                    "request_id": "12345",
                                    "message": "Invalid message received",
                                    "message_key": "message.invalid"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) failedMessage),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testFailedMessageDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "failed_message",
                                                                    "request_id": "12345",
                                                                    "message": "Invalid message received",
                                                                    "message_key": "message.invalid"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertEquals(new FailedMessage("12345", "Invalid message received", "message.invalid"), message);
    }

    @Test
    void testLobbyPassthroughSerialization() throws Exception {
        PassthroughMessage.Lobby lobbyMessage = new PassthroughMessage.Lobby("12345", "test_command",
                jsonMapper.createObjectNode().put("command", "test_command").put("request_id", "12345").put("param", "value"));

        JSONAssert.assertEquals("""
                                {
                                    "command": "test_command",
                                    "request_id": "12345",
                                    "param": "value"
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) lobbyMessage),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testLobbyPassthroughIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "test_command",
                                                                    "request_id": "12345",
                                                                    "param": "value"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(PassthroughMessage.Lobby.class, message);
        PassthroughMessage.Lobby lobby = (PassthroughMessage.Lobby) message;
        assertEquals("test_command", lobby.command());
        assertEquals("12345", lobby.requestId());
        assertEquals("value", lobby.message().get("param").stringValue());
    }

    @Test
    void testLobbyPassthroughOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "test_command",
                                                                    "request_id": "12345",
                                                                    "param": "value"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(PassthroughMessage.Lobby.class, message);
        PassthroughMessage.Lobby lobby = (PassthroughMessage.Lobby) message;
        assertEquals("test_command", lobby.command());
        assertEquals("12345", lobby.requestId());
        assertEquals("value", lobby.message().get("param").stringValue());
    }

    @Test
    void testGpgPassthroughSerialization() throws Exception {
        ObjectNode node = jsonMapper.createObjectNode()
                                    .put("command", "test_gpg")
                                    .put("target", "game");
        node.putArray("args").add("arg1").add(42);
        PassthroughMessage.GPG gpgMessage = new PassthroughMessage.GPG("test_gpg", node);

        JSONAssert.assertEquals("""
                                {
                                    "command": "test_gpg",
                                    "target": "game",
                                    "args": ["arg1", 42]
                                }
                                """, jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) gpgMessage),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testGpgPassthroughIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "test_gpg",
                                                                    "target": "game",
                                                                    "args": ["arg1", 42]
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(PassthroughMessage.GPG.class, message);
        PassthroughMessage.GPG gpg = (PassthroughMessage.GPG) message;
        assertEquals("test_gpg", gpg.command());
        assertEquals("", gpg.requestId());
        assertEquals("game", gpg.message().get("target").stringValue());
        assertEquals("arg1", gpg.message().get("args").get(0).stringValue());
        assertEquals(42, gpg.message().get("args").get(1).intValue());
    }

    @Test
    void testGpgPassthroughOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "test_gpg",
                                                                    "target": "game",
                                                                    "args": ["arg1", 42]
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(PassthroughMessage.GPG.class, message);
        PassthroughMessage.GPG gpg = (PassthroughMessage.GPG) message;
        assertEquals("test_gpg", gpg.command());
        assertEquals("", gpg.requestId());
        assertEquals("game", gpg.message().get("target").stringValue());
        assertEquals("arg1", gpg.message().get("args").get(0).stringValue());
        assertEquals(42, gpg.message().get("args").get(1).intValue());
    }

    @Test
    void testInboundPassthroughSerialization() throws Exception {
        InboundPassthroughMessage inboundPassthroughMessage = new InboundPassthroughMessage(2, 1,
                                                                                            PassthroughMessage.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test").put("request_id", "12345")));

        JSONAssert.assertEquals("""
                                {
                                "player_id": 1,
                                "session_id": 2,
                                "message": {
                                    "request_id": "12345",
                                    "command": "test"
                                }
                                }
                                """, jsonMapper.writeValueAsString(inboundPassthroughMessage),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPassthroughSerialization() throws Exception {
        PassthroughMessage message = PassthroughMessage.of(
                jsonMapper.createObjectNode().put("command", "test").put("request_id", "12345"));

        JSONAssert.assertEquals("""
                                {
                                    "request_id": "12345",
                                    "command": "test"
                                }
                                """, jsonMapper.writeValueAsString(message),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testLobbySchemaDetailsSerialization() throws Exception {
        LobbySchemaDetails message = new LobbySchemaDetails("test", LobbySchemaDetails.Direction.INBOUND, jsonMapper.createObjectNode().put("type", "object"), "route");

        JSONAssert.assertEquals("""
                                {
                                    "command": "test",
                                    "direction": "inbound",
                                    "schema": {
                                      "type": "object"
                                    },
                                    "route": "route"
                                }
                                """, jsonMapper.writeValueAsString(message),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPassthroughDeserialization() {
        PassthroughMessage message = PassthroughMessage.of(
                jsonMapper.createObjectNode().put("command", "test").put("request_id", "12345"));

        assertEquals(message, jsonMapper.readValue("""
                                                   {
                                                       "request_id": "12345",
                                                       "command": "test"
                                                   }
                                                   """, PassthroughMessage.class));
    }

    @Test
    void testOutboundPassthroughDeserialization() {
        OutboundPassthroughMessage outboundPassthroughMessage = new OutboundPassthroughMessage(OutboundTarget.player(1),
                                                                                               PassthroughMessage.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test")
                                                                                                              .put("request_id", "12345")));

        assertEquals(outboundPassthroughMessage, jsonMapper.readValue("""
                                                   {
                                                       "target": {
                                                            "@type": "player",
                                                            "player_id": 1
                                                       },
                                                       "message": {
                                                           "request_id": "12345",
                                                           "command": "test"
                                                       }
                                                   }
                                                   """, OutboundPassthroughMessage.class));
    }

}
