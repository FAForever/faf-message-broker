package com.faforever.server.domain;

import com.faforever.server.message.service.LobbySchemaDetails;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.stream.Collectors;

@ApplicationScoped
@NullMarked
public class LobbyCommandSchemaRepository implements PanacheRepositoryBase<LobbyCommandSchema, LobbyCommandSchema.Id> {

    @Transactional
    public Collection<LobbySchemaDetails> loadAll() {
        return streamAll().map(lobbyCommandSchema -> {
            LobbySchemaDetails.Direction direction = switch (lobbyCommandSchema.getId().direction()) {
                case OUTBOUND -> LobbySchemaDetails.Direction.OUTBOUND;
                case INBOUND -> LobbySchemaDetails.Direction.INBOUND;
            };
            String command = lobbyCommandSchema.getId().command();
            ObjectNode schema = lobbyCommandSchema.getSchema();
            return new LobbySchemaDetails(command, direction, schema, lobbyCommandSchema.getRoute());
        }).collect(Collectors.toSet());
    }

    @Transactional
    public void save(LobbySchemaDetails lobbySchemaDetails) {
        ObjectNode jsonSchema = lobbySchemaDetails.schema();
        String command = lobbySchemaDetails.command();

        LobbyCommandSchema.Direction direction = switch (lobbySchemaDetails.direction()) {
            case INBOUND -> LobbyCommandSchema.Direction.INBOUND;
            case OUTBOUND -> LobbyCommandSchema.Direction.OUTBOUND;
        };
        LobbyCommandSchema.Id id = new LobbyCommandSchema.Id(command, direction);

        getEntityManager().merge(new LobbyCommandSchema(id, jsonSchema, lobbySchemaDetails.route()));
    }

}
