package com.faforever.server.message.service;

import com.faforever.server.message.client.Message;

public record OutboundLobbyMessage(
        OutboundTarget target,
        Message.Passthrough message
) {}
