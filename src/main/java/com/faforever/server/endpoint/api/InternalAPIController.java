package com.faforever.server.endpoint.api;

import com.faforever.server.message.MessageBroker;
import com.faforever.server.message.service.PlayerSessionDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.Collection;

@Path("/internal/api")
@RequiredArgsConstructor
@ApplicationScoped
public class InternalAPIController {

    private final MessageBroker messageBroker;

    @GET
    @Path("/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<PlayerSessionDetails> listPlayerSessions() {
        return messageBroker.loadActiveSessionDetails();
    }

}
