package com.faforever.server.endpoint.websocket;

import com.faforever.server.message.client.ProtocolMessage;
import io.quarkus.websockets.next.runtime.JsonTextMessageCodec;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

@Singleton
@Priority(100)
public class JsonNewlineTerminatedTextMessageCodec extends JsonTextMessageCodec {
    @Override
    public String encode(Object value) {
        String message = switch (value) {
            case ProtocolMessage.Passthrough passthrough -> super.encode(passthrough.message());
            case Object object -> super.encode(object);
        };
        return message + "\n";
    }
}
