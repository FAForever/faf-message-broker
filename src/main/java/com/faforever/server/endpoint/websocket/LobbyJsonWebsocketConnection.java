package com.faforever.server.endpoint.websocket;

import com.faforever.server.endpoint.connection.LobbyConnection;
import com.faforever.server.message.client.Message;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LobbyJsonWebsocketConnection implements LobbyConnection {

    private final WebSocketConnection websocket;

    @Override
    public void sendAndAwait(Message.Server message) {
        websocket.sendTextAndAwait(message);
    }

    @Override
    public void close() {
        websocket.closeAndAwait();
    }
}
