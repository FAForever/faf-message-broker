package com.faforever.messagebroker.endpoint.websocket;

import com.faforever.messagebroker.endpoint.connection.LobbyConnection;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import io.quarkus.websockets.next.HandshakeRequest;
import io.quarkus.websockets.next.WebSocketConnection;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class LobbyJsonWebsocketConnection extends LobbyConnection {

    private final WebSocketConnection websocket;
    private final String ipHeader;

    @Override
    public void send(ProtocolMessage.Outgoing message) {
        websocket.sendTextAndAwait(message);
    }

    @Override
    protected void doClose() {
        websocket.closeAndAwait();
    }

    @Override
    public boolean isClosed() {
        return websocket.isClosed();
    }

    @Override
    public String remoteIp() {
        HandshakeRequest handshakeRequest = websocket.handshakeRequest();
        List<String> ipHeaders = handshakeRequest.headers(ipHeader);
        if (!ipHeaders.isEmpty()) {
            return ipHeaders.getFirst();
        } else {
            return handshakeRequest.remoteAddress().split(":")[0];
        }
    }
}
