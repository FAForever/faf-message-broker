package com.faforever.server.policy;

public record PolicyContents(
        long session,
        int playerId,
        String uidHash
) {}
