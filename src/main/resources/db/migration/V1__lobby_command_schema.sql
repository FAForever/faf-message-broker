CREATE TABLE lobby_command_schema
(
    command   VARCHAR NOT NULL,
    direction VARCHAR NOT NULL CHECK (direction IN ('inbound', 'outbound')),
    schema    JSONB NOT NULL,
    route     VARCHAR NOT NULL,
    CONSTRAINT pk_command_schema PRIMARY KEY (command, direction)
);