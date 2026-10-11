package com.faforever.server.message.service;

import com.faforever.server.message.client.Message;

public record InboundMessage(
        long sessionId,
        int playerId,
        Message.Passthrough message
) {

}
