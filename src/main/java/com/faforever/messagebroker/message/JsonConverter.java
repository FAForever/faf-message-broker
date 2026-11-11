package com.faforever.messagebroker.message;

import io.smallrye.reactive.messaging.MessageConverter;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Message;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@ApplicationScoped
@Slf4j
public class JsonConverter implements MessageConverter {

    private final JsonMapper jsonMapper;

    @Override
    public boolean canConvert(Message<?> in, Type target) {
        Object payload = in.getPayload();
        if (!(payload instanceof JsonObject || payload instanceof byte[] || payload instanceof String)) {
            return false;
        }

        return in.getMetadata(IncomingRabbitMQMetadata.class).filter(metadata -> {
            String encoding = metadata.getContentEncoding();
            return encoding == null || encoding.isEmpty();
        }).flatMap(IncomingRabbitMQMetadata::getEffectiveContentType).map("application/json"::equalsIgnoreCase).orElse(
                false);
    }

    @Override
    public Message<?> convert(Message<?> in, Type target) {
        if (!(target instanceof Class<?> clazz)) {
            throw new IllegalArgumentException("Unknown target type");
        }
        try {
            return in.withPayload(switch (in.getPayload()) {
                case JsonObject jsonObject -> jsonObject.mapTo(clazz);
                case byte[] byteArray -> jsonMapper.readValue(byteArray, clazz);
                case String string -> jsonMapper.readValue(string, clazz);
                default -> throw new IllegalArgumentException("Unknown payload type");
            });
        } catch (RuntimeException e) {
            LOG.atWarn().addArgument(() -> switch (in.getPayload()) {
                case byte[] byteArray -> new String(byteArray, StandardCharsets.UTF_8);
                case Object object -> object;
            }).log("Unable to convert payload {}", e);
            throw e;
        }
    }
}
