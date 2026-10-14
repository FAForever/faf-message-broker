package com.faforever.server.message.interceptor;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.OutgoingInterceptor;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
@Identifier("lobby-inbound")
public class LobbyInboundOutgoingInterceptor implements OutgoingInterceptor {

    @Override
    public void onMessageAck(Message<?> message) {

    }

    @Override
    public void onMessageNack(Message<?> message, Throwable failure) {

    }

    @Override
    public Message<?> beforeMessageSend(Message<?> message) {
        OutgoingRabbitMQMetadata newMetadata = message.getMetadata(OutgoingRabbitMQMetadata.class)
                .map(OutgoingRabbitMQMetadata::from)
                .orElseGet(OutgoingRabbitMQMetadata::builder)
                .withDeliveryMode(2)
                .build();
        return message.addMetadata(newMetadata);
    }
}
