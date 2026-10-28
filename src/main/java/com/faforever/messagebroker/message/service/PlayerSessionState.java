package com.faforever.messagebroker.message.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * A session as the lobby server is told about it.
 *
 * @param userAgent the client's own name for itself, from its {@code ask_session}; the lobby server records it as
 *                  the player's {@code login.user_agent}, which it used to read from that message itself
 * @param version   the client's version, from the same message
 */
public record PlayerSessionState(
        int playerId,
        long sessionId,
        @Nullable String ipAddress,
        @Nullable String userAgent,
        @Nullable String version,
        State state
) {
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({@JsonSubTypes.Type(value = State.New.class, name = "new"),
                   @JsonSubTypes.Type(value = State.Existing.class, name = "existing"),
                   @JsonSubTypes.Type(value = State.Gone.class, name = "gone"),})
    public sealed interface State {

        record New(@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT) Duration secondsToExpiry) implements State {}

        record Existing(@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT) Duration secondsToExpiry) implements State {}

        record Gone() implements State {}

    }
}
