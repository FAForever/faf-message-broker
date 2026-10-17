package com.faforever.server.endpoint.connection;


import com.faforever.server.message.client.ProtocolMessage;

public interface LobbyConnection {

    void sendAndAwait(ProtocolMessage.Outgoing message);

    void close();

}
