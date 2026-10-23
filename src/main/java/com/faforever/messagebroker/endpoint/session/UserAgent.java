package com.faforever.messagebroker.endpoint.session;

import org.jspecify.annotations.Nullable;

public record UserAgent(
        @Nullable String agent,
        @Nullable String version
) {}
