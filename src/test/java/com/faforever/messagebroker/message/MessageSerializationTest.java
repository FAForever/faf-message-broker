package com.faforever.messagebroker.message;

import com.faforever.messagebroker.message.client.ConnectionMessage;
import com.faforever.messagebroker.message.client.FailedMessage;
import com.faforever.messagebroker.message.client.PassthroughMessage;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.message.service.LobbySchemaDetails;
import com.faforever.messagebroker.message.service.OutboundPassthroughMessage;
import com.faforever.messagebroker.message.service.OutboundTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void testPingWithoutRequestIdSerialization() throws Exception {
        ConnectionMessage.Ping ping = new ConnectionMessage.Ping(null);

        JSONAssert.assertEquals("""
                                {
                                    "command": "ping",
                                    "request_id": "%s"
                                }
                                """.formatted(ping.requestId()),
                                jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) ping),
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
    void testPingWithoutRequestIdIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "ping"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(ConnectionMessage.Ping.class, message);
        assertEquals("ping", message.command());
        assertNotNull(message.requestId());
        assertFalse(message.requestId().isBlank());
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
    void testPingWithoutRequestIdOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "ping"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(ConnectionMessage.Ping.class, message);
        assertEquals("ping", message.command());
        assertNotNull(message.requestId());
        assertFalse(message.requestId().isBlank());
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
    void testPongWithoutRequestIdSerialization() throws Exception {
        ConnectionMessage.Pong pong = new ConnectionMessage.Pong(null);

        JSONAssert.assertEquals("""
                                {
                                    "command": "pong",
                                    "request_id": "%s"
                                }
                                """.formatted(pong.requestId()),
                                jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) pong),
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
    void testPongWithoutRequestIdIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "pong"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(ConnectionMessage.Pong.class, message);
        assertEquals("pong", message.command());
        assertNotNull(message.requestId());
        assertFalse(message.requestId().isBlank());
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
    void testPongWithoutRequestIdOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "pong"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(ConnectionMessage.Pong.class, message);
        assertEquals("pong", message.command());
        assertNotNull(message.requestId());
        assertFalse(message.requestId().isBlank());
    }

    @Test
    void testSessionRequestSerialization() throws Exception {
        ConnectionMessage.SessionRequest sessionRequest = new ConnectionMessage.SessionRequest("12345",
                                                                                               "downlords-faf-client",
                                                                                               "1.0.0");

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
    void testSessionRequestWithoutRequestIdSerialization() throws Exception {
        ConnectionMessage.SessionRequest sessionRequest = new ConnectionMessage.SessionRequest(null,
                                                                                               "downlords-faf-client",
                                                                                               "1.0.0");

        JSONAssert.assertEquals("""
                                {
                                    "command": "ask_session",
                                    "request_id": "%s",
                                    "user_agent": "downlords-faf-client",
                                    "version": "1.0.0"
                                }
                                """.formatted(sessionRequest.requestId()),
                                jsonMapper.writeValueAsString((ProtocolMessage.Incoming) sessionRequest),
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
    void testSessionRequestWithoutRequestIdDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "ask_session",
                                                                    "user_agent": "downlords-faf-client",
                                                                    "version": "1.0.0"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(ConnectionMessage.SessionRequest.class, message);
        ConnectionMessage.SessionRequest sessionRequest = (ConnectionMessage.SessionRequest) message;
        assertEquals("ask_session", sessionRequest.command());
        assertNotNull(sessionRequest.requestId());
        assertFalse(sessionRequest.requestId().isBlank());
        assertEquals("downlords-faf-client", sessionRequest.userAgent());
        assertEquals("1.0.0", sessionRequest.version());
    }

    @Test
    void testAuthenticateRequestSerialization() throws Exception {
        ConnectionMessage.AuthenticateRequest authRequest = new ConnectionMessage.AuthenticateRequest("12345",
                                                                                                      "jwt-token-xyz",
                                                                                                      "unique-id-999");

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
    void testAuthenticateRequestWithoutRequestIdSerialization() throws Exception {
        ConnectionMessage.AuthenticateRequest authRequest = new ConnectionMessage.AuthenticateRequest(null,
                                                                                                      "jwt-token-xyz",
                                                                                                      "unique-id-999");

        JSONAssert.assertEquals("""
                                {
                                    "command": "auth",
                                    "request_id": "%s",
                                    "token": "jwt-token-xyz",
                                    "unique_id": "unique-id-999"
                                }
                                """.formatted(authRequest.requestId()),
                                jsonMapper.writeValueAsString((ProtocolMessage.Incoming) authRequest),
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
    void testAuthenticateRequestWithoutRequestIdDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "auth",
                                                                    "token": "jwt-token-xyz",
                                                                    "unique_id": "unique-id-999"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(ConnectionMessage.AuthenticateRequest.class, message);
        ConnectionMessage.AuthenticateRequest authRequest = (ConnectionMessage.AuthenticateRequest) message;
        assertEquals("auth", authRequest.command());
        assertNotNull(authRequest.requestId());
        assertFalse(authRequest.requestId().isBlank());
        assertEquals("jwt-token-xyz", authRequest.token());
        assertEquals("unique-id-999", authRequest.uniqueId());
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
    void testSessionResponseWithoutRequestIdSerialization() throws Exception {
        ConnectionMessage.SessionResponse sessionResponse = new ConnectionMessage.SessionResponse(null, 987654L);

        JSONAssert.assertEquals("""
                                {
                                    "command": "session",
                                    "request_id": "%s",
                                    "session": 987654
                                }
                                """.formatted(sessionResponse.requestId()),
                                jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) sessionResponse),
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
    void testSessionResponseWithoutRequestIdDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "session",
                                                                    "session": 987654
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(ConnectionMessage.SessionResponse.class, message);
        ConnectionMessage.SessionResponse sessionResponse = (ConnectionMessage.SessionResponse) message;
        assertEquals("session", sessionResponse.command());
        assertNotNull(sessionResponse.requestId());
        assertFalse(sessionResponse.requestId().isBlank());
        assertEquals(987654L, sessionResponse.session());
    }

    @Test
    void testLoginFailureResponseSerialization() throws Exception {
        ConnectionMessage.LoginFailureResponse failureResponse = new ConnectionMessage.LoginFailureResponse("12345",
                                                                                                            "Invalid credentials");

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
    void testLoginFailureResponseWithoutRequestIdSerialization() throws Exception {
        ConnectionMessage.LoginFailureResponse failureResponse = new ConnectionMessage.LoginFailureResponse(null,
                                                                                                            "Invalid credentials");

        JSONAssert.assertEquals("""
                                {
                                    "command": "authentication_failure",
                                    "request_id": "%s",
                                    "text": "Invalid credentials"
                                }
                                """.formatted(failureResponse.requestId()),
                                jsonMapper.writeValueAsString((ProtocolMessage.Outgoing) failureResponse),
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
    void testLoginFailureResponseWithoutRequestIdDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "authentication_failure",
                                                                    "text": "Invalid credentials"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(ConnectionMessage.LoginFailureResponse.class, message);
        ConnectionMessage.LoginFailureResponse failureResponse = (ConnectionMessage.LoginFailureResponse) message;
        assertEquals("authentication_failure", failureResponse.command());
        assertNotNull(failureResponse.requestId());
        assertFalse(failureResponse.requestId().isBlank());
        assertEquals("Invalid credentials", failureResponse.text());
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
                                                                             jsonMapper.createObjectNode()
                                                                                       .put("command", "test_command")
                                                                                       .put("request_id", "12345")
                                                                                       .put("param", "value"));

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
    void testLobbyPassthroughWithoutRequestIdIncomingDeserialization() {
        ProtocolMessage.Incoming message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "test_command",
                                                                    "param": "value"
                                                                }
                                                                """, ProtocolMessage.Incoming.class);

        assertInstanceOf(PassthroughMessage.Lobby.class, message);
        PassthroughMessage.Lobby lobby = (PassthroughMessage.Lobby) message;
        assertEquals("test_command", lobby.command());
        assertNotNull(lobby.requestId());
        assertFalse(lobby.requestId().isBlank());
        assertEquals(lobby.requestId(), lobby.message().get("request_id").stringValue());
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
    void testLobbyPassthroughWithoutRequestIdOutgoingDeserialization() {
        ProtocolMessage.Outgoing message = jsonMapper.readValue("""
                                                                {
                                                                    "command": "test_command",
                                                                    "param": "value"
                                                                }
                                                                """, ProtocolMessage.Outgoing.class);

        assertInstanceOf(PassthroughMessage.Lobby.class, message);
        PassthroughMessage.Lobby lobby = (PassthroughMessage.Lobby) message;
        assertEquals("test_command", lobby.command());
        assertNotNull(lobby.requestId());
        assertFalse(lobby.requestId().isBlank());
        assertEquals(lobby.requestId(), lobby.message().get("request_id").stringValue());
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
                                                                                                                   "test")
                                                                                                              .put("request_id",
                                                                                                                   "12345")));

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
    void testInboundPassthroughWithoutRequestIdSerialization() throws Exception {
        InboundPassthroughMessage inboundPassthroughMessage = new InboundPassthroughMessage(2, 1,
                                                                                            PassthroughMessage.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test")));

        JSONAssert.assertEquals("""
                                {
                                "player_id": 1,
                                "session_id": 2,
                                "message": {
                                    "request_id": "%s",
                                    "command": "test"
                                }
                                }
                                """.formatted(inboundPassthroughMessage.message().requestId()),
                                jsonMapper.writeValueAsString(inboundPassthroughMessage),
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
    void testPassthroughWithoutRequestIdSerialization() throws Exception {
        PassthroughMessage message = PassthroughMessage.of(
                jsonMapper.createObjectNode().put("command", "test"));

        JSONAssert.assertEquals("""
                                {
                                    "request_id": "%s",
                                    "command": "test"
                                }
                                """.formatted(message.requestId()), jsonMapper.writeValueAsString(message),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testLobbySchemaDetailsSerialization() throws Exception {
        LobbySchemaDetails message = new LobbySchemaDetails("test", LobbySchemaDetails.Direction.INBOUND,
                                                            jsonMapper.createObjectNode().put("type", "object"),
                                                            "route");

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
    void testPassthroughWithoutRequestIdDeserialization() {
        PassthroughMessage message = jsonMapper.readValue("""
                                                          {
                                                              "command": "test"
                                                          }
                                                          """, PassthroughMessage.class);

        assertInstanceOf(PassthroughMessage.Lobby.class, message);
        PassthroughMessage.Lobby lobby = (PassthroughMessage.Lobby) message;
        assertEquals("test", lobby.command());
        assertNotNull(lobby.requestId());
        assertFalse(lobby.requestId().isBlank());
        assertEquals(lobby.requestId(), lobby.message().get("request_id").stringValue());
    }

    @Test
    void testOutboundPassthroughDeserialization() {
        OutboundPassthroughMessage outboundPassthroughMessage = new OutboundPassthroughMessage(OutboundTarget.player(1),
                                                                                               PassthroughMessage.of(
                                                                                                       jsonMapper.createObjectNode()
                                                                                                                 .put("command",
                                                                                                                      "test")
                                                                                                                 .put("request_id",
                                                                                                                      "12345")));

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

    @Test
    void testOutboundPassthroughWithoutRequestIdDeserialization() {
        OutboundPassthroughMessage message = jsonMapper.readValue("""
                                                                  {
                                                                      "target": {
                                                                           "@type": "player",
                                                                           "player_id": 1
                                                                      },
                                                                      "message": {
                                                                          "command": "test"
                                                                      }
                                                                  }
                                                                  """, OutboundPassthroughMessage.class);

        assertEquals(OutboundTarget.player(1), message.target());
        assertInstanceOf(PassthroughMessage.Lobby.class, message.message());
        assertNotNull(message.message().requestId());
        assertFalse(message.message().requestId().isBlank());
        assertEquals("test", message.message().command());
    }

}
