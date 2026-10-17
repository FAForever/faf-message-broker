package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public sealed interface ProtocolMessage permits ProtocolMessage.Incoming, ProtocolMessage.Outgoing {

    String requestId();

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = PassthroughMessage.class, visible = true)
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = ConnectionMessage.SessionResponse.class, name = "session"),
                    @JsonSubTypes.Type(value = ConnectionMessage.LoginFailureResponse.class, name = "authentication_failure"),
            }
    )
    sealed interface Outgoing extends ProtocolMessage permits ConnectionMessage.Outgoing, PassthroughMessage {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = PassthroughMessage.class, visible = true)
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = ConnectionMessage.SessionRequest.class, name = "ask_session"),
                    @JsonSubTypes.Type(value = ConnectionMessage.AuthenticateRequest.class, name = "auth"),
            }
    )
    sealed interface Incoming extends ProtocolMessage permits ConnectionMessage.Incoming, PassthroughMessage {}

}
