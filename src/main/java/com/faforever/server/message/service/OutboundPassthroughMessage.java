package com.faforever.server.message.service;

import com.faforever.server.message.client.ProtocolMessage;

public record OutboundPassthroughMessage(
        OutboundTarget target,
        ProtocolMessage.Passthrough message
) {}
