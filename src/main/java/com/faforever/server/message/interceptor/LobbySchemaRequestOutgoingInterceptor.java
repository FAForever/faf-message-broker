package com.faforever.server.message.interceptor;

import com.faforever.server.message.interceptor.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.message.interceptor.util.PersistentRabbitMQOutgoingInterceptor;
import com.faforever.server.message.interceptor.util.ReplyToRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Singleton
@Identifier("player-active-response")
public class LobbySchemaRequestOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public LobbySchemaRequestOutgoingInterceptor(@ConfigProperty(name = "mp.messaging.incoming.lobby-schema-registration.queue.name") String queueName) {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor(), new ReplyToRabbitMQOutgoingInterceptor(queueName)));
    }

}
