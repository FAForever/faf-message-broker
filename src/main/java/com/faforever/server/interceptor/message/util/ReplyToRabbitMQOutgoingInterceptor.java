package com.faforever.server.interceptor.message.util;

import io.smallrye.reactive.messaging.OutgoingInterceptor;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;

//Used to ensure all rabbitmq messages have reply-to
public class ReplyToRabbitMQOutgoingInterceptor implements OutgoingInterceptor {

    private final String queueName;

    public ReplyToRabbitMQOutgoingInterceptor(String queueName) {
        this.queueName = queueName;
    }

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
                                                      .withReplyTo(queueName)
                                                      .build();
        return message.addMetadata(newMetadata);
    }
}
