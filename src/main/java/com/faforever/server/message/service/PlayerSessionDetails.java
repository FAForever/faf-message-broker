package com.faforever.server.message.service;

public record PlayerSessionDetails(
        int playerId,
        long sessionId,
        boolean connected
) {}
