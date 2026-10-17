package com.faforever.server.message.interceptor;

import com.faforever.server.message.interceptor.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.message.interceptor.util.PersistentRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
@Identifier("lobby-inbound")
public class LobbyInboundOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public LobbyInboundOutgoingInterceptor() {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor()));
    }
}
