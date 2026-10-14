package com.faforever.server.message;

import io.smallrye.reactive.messaging.MessageConverter;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.reactive.messaging.Message;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;

@RequiredArgsConstructor
@ApplicationScoped
public class JsonConverter implements MessageConverter {

    private final JsonMapper jsonMapper;

    @Override
    public boolean canConvert(Message<?> in, Type target) {
        return in.getPayload() instanceof JsonObject || in.getPayload() instanceof byte[];
    }

    @Override
    public Message<?> convert(Message<?> in, Type target) {
        if (!(target instanceof Class<?> clazz)) {
            throw new IllegalArgumentException("Unknown target type");
        }
        return in.withPayload(switch (in.getPayload()) {
            case JsonObject jsonObject -> jsonObject.mapTo(clazz);
            case byte[] byteArray -> jsonMapper.readValue(byteArray, clazz);
            default -> throw new IllegalArgumentException("Unknown payload type");
        });
    }
}
