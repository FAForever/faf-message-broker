CREATE TABLE lobby_command_schema
(
    command   VARCHAR NOT NULL,
    direction varchar NOT NULL CHECK (direction IN ('inbound', 'outbound')),
    schema    JSONB NOT NULL,
    CONSTRAINT pk_command_schema PRIMARY KEY (command, direction)
);