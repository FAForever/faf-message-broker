package com.faforever.server.domain;

import lombok.AccessLevel;
import tools.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumeratedValue;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "lobby_command_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
class LobbyCommandSchema {

    @EmbeddedId
    private Id id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema")
    private ObjectNode schema;

    @Embeddable
    public record Id(
            @Column(name = "command")
            String command,
            @Column(name = "direction")
            @Enumerated(EnumType.STRING)
            Direction direction
    ) {}

    public LobbyCommandSchema(Id id, ObjectNode schema) {
        this.id = id;
        this.schema = schema;
    }

    @RequiredArgsConstructor
    @Getter
    public enum Direction {
       INBOUND("inbound"), OUTBOUND("outbound");

       @EnumeratedValue
       private final String value;
    }
}
