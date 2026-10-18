package com.faforever.messagebroker.endpoint.session;

import com.faforever.messagebroker.endpoint.connection.LobbyConnection;
import com.faforever.messagebroker.endpoint.connection.UserAgent;
import com.faforever.messagebroker.message.MessageBroker;
import com.faforever.messagebroker.message.client.ConnectionMessage;
import com.faforever.messagebroker.message.client.PassthroughMessage;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.policy.PolicyClient;
import com.faforever.messagebroker.policy.PolicyContents;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class SessionHandlerTest {

    @Inject
    SessionHandler sessionHandler;

    @InjectMock
    private MessageBroker messageBroker;

    @InjectMock
    private PolicyClient policyClient;

    @InjectMock
    private JWTParser jwtParser;

    private final TestLobbyConnection connection = new TestLobbyConnection();

    @BeforeEach
    void setConnection() {
        sessionHandler.setConnection(connection);
    }

    @Test
    void testUnauthenticatedClientMessage() {
        PassthroughMessage message = createPassthroughMessage();

        assertThrows(IllegalStateException.class, () -> sessionHandler.handleMessage(message));
        verifyNoInteractions(messageBroker);
    }

    @Test
    void testUnauthenticatedPlayerId() {
        assertNull(sessionHandler.playerId());
    }

    @Test
    void testPing() {
        sessionHandler.handleMessage(new ConnectionMessage.Ping(""));
        assertThat(connection.getSentMessages(), contains(new ConnectionMessage.Pong("")));
    }

    @Test
    void testPong() {
        sessionHandler.handleMessage(new ConnectionMessage.Pong(""));
        assertThat(connection.getSentMessages(), hasSize(0));
    }

    @Test
    void testSessionRequest() {
        UserAgent userAgent = new UserAgent("userAgent", "1.0");
        sessionHandler.handleMessage(new ConnectionMessage.SessionRequest("", userAgent.agent(), userAgent.version()));
        assertThat(connection.getSentMessages(),
                   contains(new ConnectionMessage.SessionResponse("", sessionHandler.sessionId())));
        assertThat(sessionHandler.userAgent(), equalTo(userAgent));
    }

    @Test
    void testDuplicateSessionRequest() {
        UserAgent userAgent = new UserAgent("userAgent", "1.0");
        ConnectionMessage.SessionRequest message = new ConnectionMessage.SessionRequest("", userAgent.agent(),
                                                                                        userAgent.version());
        sessionHandler.handleMessage(message);
        assertDoesNotThrow(() -> sessionHandler.handleMessage(message));
    }

    @Test
    @TestConfigProperty(key = "faf.use-policy-server", value = "true")
    void testAuthenticateRequest() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new ConnectionMessage.AuthenticateRequest("", "", ""));

        assertDoesNotThrow(() -> sessionHandler.playerId());
        verify(policyClient).sendVerificationDetails(new PolicyContents(sessionHandler.sessionId(), playerId, ""));
    }

    @Test
    void testAuthenticateRequestNoPolicyServer() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new ConnectionMessage.AuthenticateRequest("", "", ""));

        assertEquals(playerId, sessionHandler.playerId());
        verifyNoInteractions(policyClient);
    }

    @Test
    void testDuplicateAuthenticateRequest() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        ConnectionMessage.AuthenticateRequest message = new ConnectionMessage.AuthenticateRequest("", "", "");
        sessionHandler.handleMessage(message);
        sessionHandler.handleMessage(message);
        assertThat(connection.getSentMessages(),
                   contains(new ConnectionMessage.LoginFailureResponse("", "Connection previously authenticated")));
        assertTrue(connection.isClosed());
    }

    @Test
    void testAuthenticateRequestBadToken() throws ParseException {
        when(jwtParser.parse("")).thenThrow(new ParseException(""));

        ConnectionMessage.AuthenticateRequest message = new ConnectionMessage.AuthenticateRequest("", "", "");
        sessionHandler.handleMessage(message);
        assertThat(connection.getSentMessages(),
                   contains(new ConnectionMessage.LoginFailureResponse("", "Invalid access token provided")));
        assertTrue(connection.isClosed());
    }

    @Test
    void testTrustedMessageAuthenticated() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new ConnectionMessage.AuthenticateRequest("", "", ""));

        PassthroughMessage message = createPassthroughMessage();
        sessionHandler.handleMessage(message);
        verify(messageBroker).handleInboundMessage(
                new InboundPassthroughMessage(sessionHandler.sessionId(), playerId, message));
    }

    @Test
    void testCloseBeforeAuth() {
        sessionHandler.close();

        assertTrue(connection.isClosed());
        verify(messageBroker, never()).unregisterSession(anyInt(), any(SessionHandler.class));
    }

    @Test
    void testCloseAfterAuth() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new ConnectionMessage.AuthenticateRequest("", "", ""));


        sessionHandler.close();

        assertTrue(connection.isClosed());
        verify(messageBroker).unregisterSession(playerId, sessionHandler);
    }

    @Test
    void testSendAfterClose() {
        sessionHandler.close();

        assertThrows(IllegalStateException.class, () -> sessionHandler.sendMessage(createPassthroughMessage()));
    }

    @Test
    void testDuplicateConnection() {
        assertThrows(IllegalStateException.class, () -> sessionHandler.setConnection(new TestLobbyConnection()));
    }

    private PassthroughMessage createPassthroughMessage() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("command", "test");
        return PassthroughMessage.of(node);
    }

    @NullMarked
    private static class TestLobbyConnection implements LobbyConnection {

        private final List<ProtocolMessage.Outgoing> sentMessages = new ArrayList<>();

        private boolean closed = false;

        public List<ProtocolMessage.Outgoing> getSentMessages() {
            return List.copyOf(sentMessages);
        }


        @Override
        public void sendAndAwait(ProtocolMessage.Outgoing message) {
            if (closed) {
                throw new IllegalStateException("Test connection closed");
            }
            sentMessages.add(message);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public String remoteIp() {
            return "127.0.0.1";
        }
    }
}
