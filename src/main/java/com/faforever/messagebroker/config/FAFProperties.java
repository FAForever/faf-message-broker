package com.faforever.messagebroker.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "faf")
public interface FAFProperties {

    /// Whether the unique id should be sent to the policy server
    @WithDefault("false")
    boolean usePolicyServer();

    /// Name of the instance to create unique rabbitmq queues
    @WithDefault("local")
    String instanceId();

    /// How often an Existing state message should be sent out for each session
    @WithDefault("1m")
    Duration playerSessionStatusRefreshPeriod();

    /// How often ping is sent out to registered sessions
    @WithDefault("1m")
    Duration pingInterval();

    /// How long consumers of the player session status message should consider it valid
    default Duration playerSessionStatusExpiry() {
        return playerSessionStatusRefreshPeriod().multipliedBy(5);
    }

    /// Header used to get the real ip of connecting clients from the websocket connection
    @WithDefault("X-Real-IP")
    String realIpHeader();

    @WithDefault("false")
    boolean publishUnknownMessages();

    @WithDefault("legacy")
    String unknownMessageRoute();

    @WithDefault("100")
    int maxMessageQueueSize();

}
