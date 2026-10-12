package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

public sealed interface ProtocolMessage permits ProtocolMessage.Client, ProtocolMessage.Server {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = Passthrough.class, visible = true)
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = Connection.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = Connection.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = Connection.SessionResponse.class, name = "session"),
            }
    )
    sealed interface Server extends ProtocolMessage permits Connection.Server, Passthrough {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command", defaultImpl = Passthrough.class, visible = true)
    @JsonSubTypes(
            {
                    @JsonSubTypes.Type(value = Connection.Ping.class, name = "ping"),
                    @JsonSubTypes.Type(value = Connection.Pong.class, name = "pong"),
                    @JsonSubTypes.Type(value = Connection.SessionRequest.class, name = "ask_session"),
                    @JsonSubTypes.Type(value = Connection.AuthenticateRequest.class, name = "auth"),
            }
    )
    sealed interface Client extends ProtocolMessage permits Connection.Client, Passthrough {}

    sealed interface Passthrough extends Server, Client {

        ObjectNode message();

        record Lobby(
                String command,
                ObjectNode message
        ) implements Passthrough {}

        record GPG(
                String command,
                ObjectNode message
        ) implements Passthrough {
            public static final String GAME_PASSTHROUGH_SCHEMA = """
                                                                 {
                                                                   "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                                   "type": "object",
                                                                   "properties": {
                                                                     "command": {
                                                                       "type": "string"
                                                                     },
                                                                     "target": {
                                                                       "const": "game"
                                                                     },
                                                                     "args": {
                                                                       "type": "array",
                                                                       "items": {}
                                                                     }
                                                                   },
                                                                   "required": [
                                                                     "command",
                                                                     "target",
                                                                     "args"
                                                                   ],
                                                                   "additionalProperties": false
                                                                 }
                                                                 """;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Passthrough of(ObjectNode message) {
            String command = message.findValue("command").stringValue();
            if (message.findValue("target") instanceof StringNode stringNode &&
                stringNode.stringValue().equalsIgnoreCase("game")) {
                return new GPG(command, message);
            } else {
                return new Lobby(command, message);
            }
        }
    }

    sealed interface Connection {

        sealed interface Server extends Connection, ProtocolMessage.Server {}

        sealed interface Client extends Connection, ProtocolMessage.Client {}

        record Ping() implements Server, Client {}

        record Pong() implements Server, Client {}

        record SessionRequest(
                @Nullable String userAgent,
                @Nullable String version
        ) implements Client {}

        record AuthenticateRequest(
                String token,
                String uniqueId
        ) implements Client {}

        record SessionResponse(
                long session
        ) implements Server {}
    }
}
