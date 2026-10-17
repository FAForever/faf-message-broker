package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumeratedValue;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.node.ObjectNode;

@Entity
@Table(name = "lobby_command_schema")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@Setter
class LobbyCommandSchema {

    @EmbeddedId
    private Id id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema")
    private ObjectNode schema;

    @Column(name = "route")
    private String route;

    @Embeddable
    record Id(
            @Column(name = "command")
            String command,
            @Column(name = "direction")
            @Enumerated(EnumType.STRING)
            Direction direction
    ) {}

    @RequiredArgsConstructor
    @Getter
    enum Direction {
        INBOUND("inbound"), OUTBOUND("outbound");

        @EnumeratedValue
        private final String value;
    }
}
