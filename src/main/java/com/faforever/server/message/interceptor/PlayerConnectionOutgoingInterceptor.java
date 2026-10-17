package com.faforever.server.message.interceptor;

import com.faforever.server.message.interceptor.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.message.interceptor.util.PersistentRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Identifier("player-connection")
public class PlayerConnectionOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public PlayerConnectionOutgoingInterceptor() {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor()));
    }

}
