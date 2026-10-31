package com.faforever.messagebroker.endpoint.session;

import com.faforever.messagebroker.endpoint.connection.LobbyConnection;
import com.faforever.messagebroker.message.MessageBroker;
import com.faforever.messagebroker.message.client.ConnectionMessage;
import com.faforever.messagebroker.message.client.PassthroughMessage;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.policy.PolicyClient;
import com.faforever.messagebroker.policy.PolicyContents;
import com.faforever.messagebroker.session.SessionHandler;
import com.faforever.messagebroker.session.UserAgent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Timeout(1)
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
    void testUnauthenticatedClientMessage() throws Exception {
        PassthroughMessage message = createPassthroughMessage();

        sessionHandler.accept(message);
        verifyNoInteractions(messageBroker);

        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));
        verify(messageBroker, timeout(1000)).handleInboundMessage(
                new InboundPassthroughMessage(sessionHandler.sessionId(), playerId, message));
    }

    @Test
    void testUnauthenticatedPlayerId() {
        assertNull(sessionHandler.playerId());
    }

    @Test
    void testPing() throws Exception {
        sessionHandler.accept(new ConnectionMessage.Ping(""));
        assertEquals(new ConnectionMessage.Pong(""), connection.waitForNextMessage());
    }

    @Test
    void testPong() throws Exception {
        sessionHandler.accept(new ConnectionMessage.Pong(""));
        sessionHandler.close();
        connection.waitUntilClosed();
        assertEquals(0, connection.getNumMessagesSent());
    }

    @Test
    void testSessionRequest() throws Exception {
        UserAgent userAgent = new UserAgent("userAgent", "1.0");
        sessionHandler.accept(new ConnectionMessage.SessionRequest("", userAgent.agent(), userAgent.version()));
        assertEquals(new ConnectionMessage.SessionResponse("", sessionHandler.sessionId()),
                     connection.waitForNextMessage());
        assertThat(sessionHandler.userAgent(), equalTo(userAgent));
    }

    @Test
    void testDuplicateSessionRequest() {
        UserAgent userAgent = new UserAgent("userAgent", "1.0");
        ConnectionMessage.SessionRequest message = new ConnectionMessage.SessionRequest("", userAgent.agent(),
                                                                                        userAgent.version());
        sessionHandler.accept(message);
        assertDoesNotThrow(() -> sessionHandler.accept(message));
    }

    @Test
    @TestConfigProperty(key = "faf.use-policy-server", value = "true")
    void testAuthenticateRequest() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));

        assertEquals(playerId, sessionHandler.playerId());
        verify(policyClient).sendVerificationDetails(new PolicyContents(sessionHandler.sessionId(), playerId, ""));
        verify(messageBroker).registerSession(playerId, sessionHandler);
    }

    @Test
    void testAuthenticateRequestNoPolicyServer() throws ParseException {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));

        assertEquals(playerId, sessionHandler.playerId());
        verifyNoInteractions(policyClient);
        verify(messageBroker).registerSession(playerId, sessionHandler);
    }

    @Test
    void testAuthenticationSendsQueuedMessages() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        PassthroughMessage passthroughMessage = createPassthroughMessage();
        sessionHandler.send(passthroughMessage);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));

        assertEquals(playerId, sessionHandler.playerId());
        verifyNoInteractions(policyClient);
        verify(messageBroker).registerSession(playerId, sessionHandler);
        assertEquals(passthroughMessage, connection.waitForNextMessage());
    }

    @Test
    void testDuplicateAuthenticateRequest() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        ConnectionMessage.AuthenticateRequest message = new ConnectionMessage.AuthenticateRequest("", "", "");
        sessionHandler.accept(message);
        sessionHandler.accept(message);
        assertEquals(new ConnectionMessage.LoginFailureResponse("", "Connection previously authenticated"),
                     connection.waitForNextMessage());
        connection.waitUntilClosed();
    }

    @Test
    void testAuthenticateRequestBadToken() throws Exception {
        when(jwtParser.parse("")).thenThrow(new ParseException(""));

        ConnectionMessage.AuthenticateRequest message = new ConnectionMessage.AuthenticateRequest("", "", "");
        sessionHandler.accept(message);
        assertEquals(new ConnectionMessage.LoginFailureResponse("", "Invalid access token provided"),
                     connection.waitForNextMessage());
        connection.waitUntilClosed();
    }

    @Test
    void testAuthenticateRequestNoLobbyScope() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        ConnectionMessage.AuthenticateRequest message = new ConnectionMessage.AuthenticateRequest("", "", "");
        sessionHandler.accept(message);
        assertEquals(new ConnectionMessage.LoginFailureResponse("",
                                                                "Token does not have permission to login to the lobby server"),
                     connection.waitForNextMessage());
        connection.waitUntilClosed();
    }

    @Test
    void testTrustedMessageAuthenticated() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));

        PassthroughMessage message = createPassthroughMessage();
        sessionHandler.accept(message);
        verify(messageBroker, timeout(1000)).handleInboundMessage(
                new InboundPassthroughMessage(sessionHandler.sessionId(), playerId, message));
    }

    @Test
    void testKickMessageCloses() throws Exception {
        sessionHandler.send(createPassthroughMessage(Map.of("command", new StringNode("notice"),
                                                            "style", new StringNode("kick"))));

        connection.waitUntilClosed();
    }

    @Test
    void testCloseBeforeAuth() throws Exception {
        sessionHandler.close();

        connection.waitUntilClosed();
        verify(messageBroker, never()).unregisterSession(anyInt(), any(SessionHandler.class));
    }

    @Test
    void testCloseAfterAuth() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));


        sessionHandler.close();

        connection.waitUntilClosed();
        verify(messageBroker).unregisterSession(playerId, sessionHandler);
    }

    @Test
    void testNoSendAfterClose() throws Exception {
        sessionHandler.close();

        sessionHandler.send(createPassthroughMessage());

        connection.waitUntilClosed();
        assertEquals(0, connection.getNumMessagesSent());
        assertEquals(0, sessionHandler.numOutboundQueuedMessages());
    }

    @Test
    void testNoAcceptAfterClose() throws Exception {
        sessionHandler.close();

        sessionHandler.accept(createPassthroughMessage());

        connection.waitUntilClosed();
        verifyNoInteractions(messageBroker);
        assertEquals(0, sessionHandler.numInboundQueuedMessages());
    }

    @Test
    void testCloseSendsRemainingOutboundMessages() throws Exception {

        connection.pauseSends();

        ConnectionMessage.Pong message = new ConnectionMessage.Pong("");
        sessionHandler.send(message);
        sessionHandler.send(message);

        Thread.startVirtualThread(sessionHandler::close);

        connection.resumeSends();

        assertEquals(message, connection.waitForNextMessage());
        assertEquals(message, connection.waitForNextMessage());

        connection.waitUntilClosed();
    }

    @Test
    void testCloseAfterAuthSendsRemainingInboundMessages() throws Exception {
        int playerId = 1;
        JsonWebToken jsonWebToken = mock();
        when(jsonWebToken.getSubject()).thenReturn(Integer.toString(playerId));
        when(jsonWebToken.claim("scp")).thenReturn(Optional.of("lobby"));
        when(jwtParser.parse("")).thenReturn(jsonWebToken);

        sessionHandler.accept(new ConnectionMessage.AuthenticateRequest("", "", ""));

        CountDownLatch acceptLatch = new CountDownLatch(1);
        CountDownLatch incomingLatch = new CountDownLatch(2);
        doAnswer(_ -> {
            try {
                acceptLatch.await();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            incomingLatch.countDown();
            return null;
        }).when(messageBroker).handleInboundMessage(any(InboundPassthroughMessage.class));

        PassthroughMessage message = createPassthroughMessage();
        sessionHandler.accept(message);
        sessionHandler.accept(message);

        verify(messageBroker, atMostOnce()).handleInboundMessage(new InboundPassthroughMessage(sessionHandler.sessionId(), playerId, message));

        Thread.startVirtualThread(sessionHandler::close);

        acceptLatch.countDown();
        incomingLatch.await();

        verify(messageBroker, times(2)).handleInboundMessage(new InboundPassthroughMessage(sessionHandler.sessionId(), playerId, message));
        connection.waitUntilClosed();
    }

    @Test
    void testCloseBeforeAuthDoesNotSendRemainingInboundMessages() throws Exception {
        PassthroughMessage message = createPassthroughMessage();
        sessionHandler.accept(message);
        sessionHandler.accept(message);

        assertEquals(2, sessionHandler.numInboundQueuedMessages());

        sessionHandler.close();

        assertEquals(2, sessionHandler.numInboundQueuedMessages());

        verifyNoInteractions(messageBroker);
        connection.waitUntilClosed();
    }

    @Test
    @TestConfigProperty(key = "faf.max-message-queue-size", value = "1")
    void testMessagesDroppedAfterQueueLimit() {
        connection.pauseSends();

        ConnectionMessage.Pong outboundMessage = new ConnectionMessage.Pong("");
        sessionHandler.send(outboundMessage);
        sessionHandler.send(outboundMessage);
        sessionHandler.send(outboundMessage);

        assertEquals(1, sessionHandler.numOutboundQueuedMessages());

        PassthroughMessage inboundMessage = createPassthroughMessage();
        sessionHandler.accept(inboundMessage);
        sessionHandler.accept(inboundMessage);

        assertEquals(1, sessionHandler.numInboundQueuedMessages());

        connection.resumeSends();
    }

    @Test
    void testDuplicateConnection() {
        assertThrows(IllegalStateException.class, () -> sessionHandler.setConnection(connection));
    }

    private PassthroughMessage createPassthroughMessage() {
        return createPassthroughMessage(Map.of("command", new StringNode("test")));
    }

    private PassthroughMessage createPassthroughMessage(Map<String, ? extends JsonNode> properties) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.setAll(properties);
        return PassthroughMessage.of(node);
    }

    @ApplicationScoped
    static class TestMeterRegistry extends SimpleMeterRegistry {}

    private static class TestLobbyConnection implements LobbyConnection {
        private final BlockingQueue<ProtocolMessage.Outgoing> sentMessages = new ArrayBlockingQueue<>(10);
        private final LongAdder sentMessageCount = new LongAdder();

        private final CompletableFuture<@Nullable Void> closed = new CompletableFuture<>();

        private CountDownLatch sendLatch = new CountDownLatch(0);

        private void pauseSends() {
            if (sendLatch.getCount() == 0) {
                sendLatch = new CountDownLatch(1);
            }
        }

        private void resumeSends() {
            sendLatch.countDown();
        }

        private ProtocolMessage.Outgoing waitForNextMessage() throws InterruptedException {
            return sentMessages.take();
        }

        private int getNumMessagesSent() {
            return sentMessageCount.intValue();
        }

        private void waitUntilClosed() throws ExecutionException, InterruptedException {
            closed.get();
        }

        @Override
        public void send(ProtocolMessage.Outgoing message) {
            try {
                sendLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sentMessages.add(message);
            sentMessageCount.increment();
        }

        @Override
        public void close() {
            closed.complete(null);
        }

        @Override
        public String remoteIp() {
            return "127.0.0.1";
        }
    }
}
