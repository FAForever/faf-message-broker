package com.faforever.server.message.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.UUID;

// PassthroughMessage needs a custom serializer so that the internal message is used as the value to serialize.
// This is necessary because @JsonValue doesn't work on a class when it is part of a polymorphic hierarchy.
// When it is part of a polymorphic hierarchy Jackson always outputs the value as an array [{FQCN}, {JSONVVALUE}]
@JsonSerialize(using = PassthroughSerializer.class)
sealed public interface PassthroughMessage extends ProtocolMessage.Outgoing, ProtocolMessage.Incoming {

    ObjectNode message();

    record Lobby(
            String requestId,
            String command,
            ObjectNode message
    ) implements PassthroughMessage {}

    record GPG(
            String command,
            ObjectNode message
    ) implements PassthroughMessage {
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

        @Override
        public String requestId() {
            return "";
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static PassthroughMessage of(ObjectNode message) {
        String command = message.findValue("command").stringValue();
        if (message.findValue("target") instanceof StringNode stringNode &&
            stringNode.stringValue().equalsIgnoreCase("game")) {
            return new GPG(command, message);
        } else {
            if (!message.hasNonNull("request_id")) {
                message.put("request_id", UUID.randomUUID().toString());
            }
            String requestId = message.findValue("request_id").stringValue();
            return new Lobby(requestId, command, message);
        }
    }
}
