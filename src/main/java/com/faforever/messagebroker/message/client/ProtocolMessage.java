package com.faforever.messagebroker.message.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public sealed interface ProtocolMessage permits ProtocolMessage.Incoming, ProtocolMessage.Outgoing {

    String requestId();

    String command();

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = PassthroughMessage.class,
                  visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = ConnectionMessage.Ping.COMMAND),
                   @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = ConnectionMessage.Pong.COMMAND),
                   @JsonSubTypes.Type(value = ConnectionMessage.SessionResponse.class,
                                      name = ConnectionMessage.SessionResponse.COMMAND),
                   @JsonSubTypes.Type(value = ConnectionMessage.LoginFailureResponse.class,
                                      name = ConnectionMessage.LoginFailureResponse.COMMAND),
                   @JsonSubTypes.Type(value = FailedMessage.class, name = FailedMessage.COMMAND),})
    sealed interface Outgoing extends ProtocolMessage permits ConnectionMessage.Outgoing, FailedMessage, PassthroughMessage {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = PassthroughMessage.class,
                  visible = true)
    @JsonSubTypes({@JsonSubTypes.Type(value = ConnectionMessage.Ping.class, name = ConnectionMessage.Ping.COMMAND),
                   @JsonSubTypes.Type(value = ConnectionMessage.Pong.class, name = ConnectionMessage.Pong.COMMAND),
                   @JsonSubTypes.Type(value = ConnectionMessage.SessionRequest.class,
                                      name = ConnectionMessage.SessionRequest.COMMAND),
                   @JsonSubTypes.Type(value = ConnectionMessage.AuthenticateRequest.class,
                                      name = ConnectionMessage.AuthenticateRequest.COMMAND),})
    sealed interface Incoming extends ProtocolMessage permits ConnectionMessage.Incoming, PassthroughMessage {}

}
