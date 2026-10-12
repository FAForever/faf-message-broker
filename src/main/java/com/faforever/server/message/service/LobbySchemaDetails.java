package com.faforever.server.message.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.node.ObjectNode;

public record LobbySchemaDetails(
        String command,
        Direction direction,
        ObjectNode schema,
        String route
) {

    public enum Direction {
        @JsonProperty("inbound") INBOUND, @JsonProperty("outbound") OUTBOUND
    }

}
