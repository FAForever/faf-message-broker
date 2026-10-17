package com.faforever.server.interceptor.message.util;

import io.smallrye.reactive.messaging.OutgoingInterceptor;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.Collection;
import java.util.List;

//Used to apply multiple outgoing interceptors
public abstract class CompundRabbitMQOutgoingInterceptor implements OutgoingInterceptor {

    private final List<OutgoingInterceptor> outgoingInterceptors;

    public CompundRabbitMQOutgoingInterceptor(Collection<OutgoingInterceptor> outgoingInterceptors) {
        this.outgoingInterceptors = List.copyOf(outgoingInterceptors);
    }

    @Override
    public final void onMessageAck(Message<?> message) {
        outgoingInterceptors.forEach(outgoingInterceptor -> outgoingInterceptor.onMessageAck(message));
    }

    @Override
    public final void onMessageNack(Message<?> message, Throwable failure) {
        outgoingInterceptors.forEach(outgoingInterceptor -> outgoingInterceptor.onMessageNack(message, failure));
    }

    @Override
    public final Message<?> beforeMessageSend(Message<?> message) {
        for (OutgoingInterceptor outgoingInterceptor : outgoingInterceptors) {
            message = outgoingInterceptor.beforeMessageSend(message);
        }

        return message;
    }
}
