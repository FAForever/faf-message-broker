package com.faforever.messagebroker.endpoint.connection;

import org.jspecify.annotations.Nullable;

public record UserAgent(
        @Nullable String agent,
        @Nullable String version
) {}
