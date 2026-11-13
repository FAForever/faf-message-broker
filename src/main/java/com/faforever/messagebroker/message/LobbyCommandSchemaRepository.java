package com.faforever.messagebroker.message;

import com.faforever.messagebroker.domain.LobbyCommandSchema;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import jakarta.transaction.Transactional;

import java.util.List;

@Repository
public interface LobbyCommandSchemaRepository {

    @Find
    @Transactional
    List<LobbyCommandSchema> loadAll();

    @Save
    @Transactional
    void save(LobbyCommandSchema lobbyCommandSchema);

}
