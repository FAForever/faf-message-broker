package com.faforever.messagebroker.message.service;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
        {
                @JsonSubTypes.Type(value = OutboundTarget.Session.class, name = "session"),
                @JsonSubTypes.Type(value = OutboundTarget.Player.class, name = "player"),
                @JsonSubTypes.Type(value = OutboundTarget.All.class, name = "all"),
        }
)
public sealed interface OutboundTarget {

    record Session(
            long sessionId
    ) implements OutboundTarget {}

    record Player(
            int playerId
    ) implements OutboundTarget {}

    record All() implements OutboundTarget {}

    static OutboundTarget.All all() {
        return new All();
    }

    static OutboundTarget.Player player(int playerId) {
        return new Player(playerId);
    }

    static OutboundTarget.Session session(long sessionId) {
        return new Session(sessionId);
    }
}
