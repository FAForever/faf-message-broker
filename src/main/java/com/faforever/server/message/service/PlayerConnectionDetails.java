package com.faforever.server.message.service;

public record PlayerConnectionDetails(
        int playerId,
        long sessionId,
        boolean connected
) {}
