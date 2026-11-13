package com.faforever.messagebroker.session;


import com.faforever.messagebroker.message.client.ProtocolMessage;

public interface LobbyConnection {

    void send(ProtocolMessage.Outgoing message);

    void close();

    String remoteIp();
}
