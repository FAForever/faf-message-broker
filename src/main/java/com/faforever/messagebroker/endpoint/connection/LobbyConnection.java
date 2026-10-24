package com.faforever.messagebroker.endpoint.connection;


import com.faforever.messagebroker.message.client.ProtocolMessage;

public interface LobbyConnection {

    void send(ProtocolMessage.Outgoing message);

    void close();

    String remoteIp();
}
