package com.faforever.server.message.interceptor;

import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.OutgoingInterceptor;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
@Identifier("player-connection")
public class PlayerConnectionOutgoingInterceptor implements OutgoingInterceptor {

    @Override
    public void onMessageAck(Message<?> message) {

    }

    @Override
    public void onMessageNack(Message<?> message, Throwable failure) {

    }

    @Override
    public Message<?> beforeMessageSend(Message<?> message) {
        return message.addMetadata(OutgoingRabbitMQMetadata.builder().withDeliveryMode(2).build());
    }
}
