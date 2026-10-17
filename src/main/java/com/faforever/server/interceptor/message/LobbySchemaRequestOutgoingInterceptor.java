package com.faforever.server.interceptor.message;

import com.faforever.server.interceptor.message.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.PersistentRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.ReplyToRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Singleton
@Identifier("lobby-schema-request")
public class LobbySchemaRequestOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public LobbySchemaRequestOutgoingInterceptor(@ConfigProperty(name = "mp.messaging.incoming.lobby-schema-registration.queue.name") String queueName) {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor(), new ReplyToRabbitMQOutgoingInterceptor(queueName)));
    }

}
