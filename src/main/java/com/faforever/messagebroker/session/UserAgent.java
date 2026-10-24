package com.faforever.messagebroker.session;

import org.jspecify.annotations.Nullable;

public record UserAgent(
        @Nullable String agent,
        @Nullable String version
) {}
