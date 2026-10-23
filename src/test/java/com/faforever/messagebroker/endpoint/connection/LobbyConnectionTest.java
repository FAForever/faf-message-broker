package com.faforever.messagebroker.endpoint.connection;

import com.faforever.messagebroker.message.client.ConnectionMessage;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

public class LobbyConnectionTest {

    private final TestLobbyConnection connection = new TestLobbyConnection();

    @BeforeEach
    void setup() {
        connection.startDrain();
    }

    @Test
    void testQueuedMessagesSent() {
        connection.allowSend();

        ConnectionMessage.Pong message = new ConnectionMessage.Pong("");
        connection.queue(message);

        connection.close();

        assertThat(connection.getSentMessages(), contains(message));
    }

    @Test
    void testCloseSendsRemainingMessages() throws Exception {

        ConnectionMessage.Pong message = new ConnectionMessage.Pong("");
        connection.queue(message);
        connection.queue(message);

        assertThat(connection.getSentMessages(), hasSize(0));

        CompletableFuture.runAsync(connection::close);

        connection.allowSend();

        connection.waitForClose();

        assertThat(connection.getSentMessages(), contains(message, message));
    }

    @Test
    void testNoSendAfterClose() throws Exception {

        ConnectionMessage.Pong message = new ConnectionMessage.Pong("");
        connection.close();
        connection.queue(message);

        assertThat(connection.getSentMessages(), hasSize(0));
    }

    @NullMarked
    private static class TestLobbyConnection extends LobbyConnection {

        private final List<ProtocolMessage.Outgoing> sentMessages = new ArrayList<>();

        private final CompletableFuture<@Nullable Void> closed = new CompletableFuture<>();

        private final CompletableFuture<@Nullable Void> ready = new CompletableFuture<>();

        private List<ProtocolMessage.Outgoing> getSentMessages() {
            return List.copyOf(sentMessages);
        }

        private  void allowSend() {
            ready.complete(null);
        }

        private  void waitForClose() throws ExecutionException, InterruptedException, TimeoutException {
            closed.get(10, TimeUnit.SECONDS);
        }

        @Override
        public void send(ProtocolMessage.Outgoing message) {
            ready.join();
            sentMessages.add(message);
        }

        @Override
        protected void doClose() {
            closed.complete(null);
        }

        @Override
        public boolean isClosed() {
            return closed.isDone();
        }

        @Override
        public String remoteIp() {
            return "127.0.0.1";
        }
    }
}
