package com.faforever.messagebroker.micrometer;

import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.OutboundPassthroughMessage;
import io.micrometer.common.annotation.ValueResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
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
