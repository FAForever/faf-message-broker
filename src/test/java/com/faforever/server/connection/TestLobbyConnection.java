package com.faforever.server.connection;

import com.faforever.server.endpoint.connection.LobbyConnection;
import com.faforever.server.message.client.Message;

import java.util.ArrayList;
import java.util.List;

public class TestLobbyConnection implements LobbyConnection {

    private final List<Message.Server> sentMessages = new ArrayList<>();

    private boolean closed = false;

    public List<Message.Server> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void sendAndAwait(Message.Server message) {
        if (closed) {
            throw new IllegalStateException("Test connection closed");
        }
        sentMessages.add(message);
    }

    @Override
    public void close() {
        closed = true;
    }
}
