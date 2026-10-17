package com.faforever.server.endpoint.websocket;

import com.faforever.server.message.SessionHandler;
import com.faforever.server.message.client.ProtocolMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.TextDecodeException;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.SessionScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.io.Serializable;

@JBossLog
@RequiredArgsConstructor
@WebSocket(path = "/")
@SessionScoped
@RunOnVirtualThread
public class LobbyWebSocket implements Serializable {

    private final SessionHandler sessionHandler;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        sessionHandler.setConnection(new LobbyJsonWebsocketConnection(connection));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        sessionHandler.close();
    }

    @OnTextMessage
    public void onTextMessage(ProtocolMessage.Incoming message) {
        sessionHandler.handleMessage(message);
    }

    @OnError
    public void onError(TextDecodeException decodeException) {
        LOG.warn("Invalid message received", decodeException);
    }

}
