package com.faforever.messagebroker.message.service;

import com.faforever.messagebroker.message.client.PassthroughMessage;

public record InboundPassthroughMessage(
        long sessionId,
        int playerId,
        PassthroughMessage message
) {}
