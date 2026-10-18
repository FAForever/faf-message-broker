package com.faforever.messagebroker.endpoint.connection;


import com.faforever.messagebroker.message.client.ProtocolMessage;

public interface LobbyConnection {

    void sendAndAwait(ProtocolMessage.Outgoing message);

    void close();

    boolean isClosed();

    String remoteIp();

}
