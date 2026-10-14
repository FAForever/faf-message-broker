package com.faforever.server.endpoint.websocket;

import io.quarkus.websockets.next.runtime.JsonTextMessageCodec;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

@Singleton
@Priority(100)
public class JsonNewlineTerminatedTextMessageCodec extends JsonTextMessageCodec {
    @Override
    public String encode(Object value) {
        return super.encode(value) + "\n";
    }
}
