package com.faforever.server.interceptor.message.util;

import io.smallrye.reactive.messaging.OutgoingInterceptor;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;

//Used to ensure all rabbitmq messages are persistent
public class PersistentRabbitMQOutgoingInterceptor implements OutgoingInterceptor {

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
