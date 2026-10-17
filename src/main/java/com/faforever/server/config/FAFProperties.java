package com.faforever.server.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "faf")
public interface FAFProperties {

    @WithDefault("false")
    boolean usePolicyServer();

    @WithDefault("local")
    String instanceId();

    @WithDefault("1m")
    Duration playerSessionStatusRefreshPeriod();

    default Duration playerSessionStatusExpiry() {
        return playerSessionStatusRefreshPeriod().multipliedBy(2);
    }

}
