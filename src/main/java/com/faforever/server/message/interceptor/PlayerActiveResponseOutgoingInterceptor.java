package com.faforever.server.message.interceptor;

import com.faforever.server.message.interceptor.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.message.interceptor.util.PersistentRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
@Identifier("player-active-response")
public class PlayerActiveResponseOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public PlayerActiveResponseOutgoingInterceptor() {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor()));
    }

}
