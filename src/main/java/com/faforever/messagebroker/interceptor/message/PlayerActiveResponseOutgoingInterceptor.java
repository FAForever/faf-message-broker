package com.faforever.messagebroker.interceptor.message;

import com.faforever.messagebroker.interceptor.message.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.messagebroker.interceptor.message.util.PersistentRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Identifier("player-active-response")
public class PlayerActiveResponseOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public PlayerActiveResponseOutgoingInterceptor() {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor()));
    }

}
