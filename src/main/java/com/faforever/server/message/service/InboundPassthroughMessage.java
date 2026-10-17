package com.faforever.server.message.service;

import com.faforever.server.message.client.PassthroughMessage;

public record InboundPassthroughMessage(
        long sessionId,
        int playerId,
        PassthroughMessage message
) {}
