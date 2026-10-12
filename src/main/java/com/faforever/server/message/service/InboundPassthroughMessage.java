package com.faforever.server.message.service;

import com.faforever.server.message.client.ProtocolMessage;

public record InboundPassthroughMessage(
        long sessionId,
        int playerId,
        ProtocolMessage.Passthrough message
) {

}
