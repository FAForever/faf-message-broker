package com.faforever.messagebroker.message.service;

import com.faforever.messagebroker.message.client.PassthroughMessage;

public record OutboundPassthroughMessage(
        OutboundTarget target,
        PassthroughMessage message
) {}
