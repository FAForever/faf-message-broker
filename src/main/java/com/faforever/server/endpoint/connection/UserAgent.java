package com.faforever.server.endpoint.connection;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public record UserAgent(
        @Nullable String agent,
        @Nullable String version
) implements Serializable {}
