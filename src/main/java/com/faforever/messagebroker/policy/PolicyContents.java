package com.faforever.messagebroker.policy;

public record PolicyContents(
        long session,
        int playerId,
        String uidHash
) {}
