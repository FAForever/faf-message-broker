package com.faforever.server.message.service;

import com.faforever.server.message.client.PassthroughMessage;

public record OutboundPassthroughMessage(
        OutboundTarget target,
        PassthroughMessage message
) {}
