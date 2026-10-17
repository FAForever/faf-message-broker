package com.faforever.server.message.service;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerSessionStatus(
        int playerId,
        long sessionId,
        State state
) {
    public enum State {
        @JsonProperty("new") NEW, @JsonProperty("existing") EXISTING, @JsonProperty("gone") GONE
    }
}
