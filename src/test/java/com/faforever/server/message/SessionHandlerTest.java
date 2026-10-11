package com.faforever.server.message;

import com.faforever.server.connection.TestLobbyConnection;
import com.faforever.server.endpoint.connection.UserAgent;
import com.faforever.server.message.client.Message;
import com.faforever.server.message.service.InboundMessage;
import com.faforever.server.policy.PolicyClient;
import com.faforever.server.policy.PolicyContents;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        Message.Passthrough message = createPassthroughMessage();

        assertThrows(IllegalStateException.class, () -> sessionHandler.handleMessage(message));
        verifyNoInteractions(messageBroker);
    }

    @Test
    void testUnauthenticatedPlayerId() {
        assertThrows(IllegalStateException.class, () -> sessionHandler.playerId());
    }

    @Test
    void testPing() {
        sessionHandler.handleMessage(new Message.Connection.Ping());
        assertThat(connection.getSentMessages(), contains(new Message.Connection.Pong()));
    }

    @Test
    void testPong() {
        sessionHandler.handleMessage(new Message.Connection.Pong());
        assertThat(connection.getSentMessages(), hasSize(0));
    }

    @Test
    void testSessionRequest() {
        UserAgent userAgent = new UserAgent("userAgent", "1.0");
        sessionHandler.handleMessage(new Message.Connection.SessionRequest(userAgent.agent(), userAgent.version()));
        assertThat(connection.getSentMessages(),
                   contains(new Message.Connection.SessionResponse(sessionHandler.sessionId())));
        assertThat(sessionHandler.userAgent(), equalTo(userAgent));
    }

    @Test
    void testDuplicateSessionRequest() {
        UserAgent userAgent = new UserAgent("userAgent", "1.0");
        Message.Connection.SessionRequest message = new Message.Connection.SessionRequest(userAgent.agent(),
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
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new Message.Connection.AuthenticateRequest("", ""));

        assertDoesNotThrow(() -> sessionHandler.playerId());
        verify(policyClient).checkPolicy(new PolicyContents(sessionHandler.sessionId(), playerId, ""));
    }

    @Test
    void testAuthenticateRequestNoPolicyServer() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new Message.Connection.AuthenticateRequest("", ""));

        assertEquals(playerId, sessionHandler.playerId());
        verifyNoInteractions(policyClient);
    }

    @Test
    void testDuplicateAuthenticateRequest() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        Message.Connection.AuthenticateRequest message = new Message.Connection.AuthenticateRequest("", "");
        sessionHandler.handleMessage(message);
        assertThrows(IllegalStateException.class, () -> sessionHandler.handleMessage(message));
    }

    @Test
    void testTrustedMessageAuthenticated() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new Message.Connection.AuthenticateRequest("", ""));

        Message.Passthrough message = createPassthroughMessage();
        sessionHandler.handleMessage(message);
        verify(messageBroker).handleInboundMessage(
                new InboundMessage(sessionHandler.sessionId(), playerId, message));
    }

    @Test
    void testCloseBeforeAuth() {
        sessionHandler.close();

        assertTrue(connection.closed());
        verify(messageBroker, never()).unregisterSession(sessionHandler);
    }

    @Test
    void testCloseAfterAuth() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.handleMessage(new Message.Connection.AuthenticateRequest("", ""));


        sessionHandler.close();

        assertTrue(connection.closed());
        verify(messageBroker).unregisterSession(sessionHandler);
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

    private Message.Passthrough createPassthroughMessage() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("command", "test");
        return Message.Passthrough.of(node);
    }
}
