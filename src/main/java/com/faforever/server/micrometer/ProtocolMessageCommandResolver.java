package com.faforever.server.micrometer;

import com.faforever.server.message.client.ProtocolMessage;
import com.faforever.server.message.service.OutboundPassthroughMessage;
import io.micrometer.common.annotation.ValueResolver;
import org.jspecify.annotations.Nullable;

public class ProtocolMessageCommandResolver implements ValueResolver {
    @Override
    public String resolve(@Nullable Object parameter) {
        return switch (parameter) {
            case ProtocolMessage protocolMessage -> protocolMessage.command();
            case OutboundPassthroughMessage outboundPassthroughMessage ->
                    outboundPassthroughMessage.message().command();
            case null, default -> "";
        };
    }
}
