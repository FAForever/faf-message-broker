package com.faforever.server.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "faf")
public interface FAFProperties {

    @WithDefault("false")
    boolean usePolicyServer();

}
