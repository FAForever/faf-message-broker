package com.faforever.messagebroker.message.service;

import com.faforever.messagebroker.message.client.ProtocolMessage;

public record OutboundPassthroughMessage(
        OutboundTarget target,
        ProtocolMessage.Outgoing message
) {}
