package com.faforever.server.endpoint.connection;


import com.faforever.server.message.client.Message;

public interface LobbyConnection {

    void sendAndAwait(Message.Server message);

    void close();

}
