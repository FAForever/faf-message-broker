package com.faforever.messagebroker.message.client;

public record FailedMessage(
        String requestId,
        String message,
        String messageKey
) implements ProtocolMessage.Outgoing {

    public static final String COMMAND = "failed_message";


    @Override
    public String command() {
        return COMMAND;
    }
}
