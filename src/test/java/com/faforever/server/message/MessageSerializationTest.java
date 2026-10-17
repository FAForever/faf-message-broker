package com.faforever.server.message;

import com.faforever.server.message.client.PassthroughMessage;
import com.faforever.server.message.service.InboundPassthroughMessage;
import com.faforever.server.message.service.LobbySchemaDetails;
import com.faforever.server.message.service.OutboundPassthroughMessage;
import com.faforever.server.message.service.OutboundTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageSerializationTest {

    private JsonMapper jsonMapper;

    @BeforeEach
    void setup() {
        jsonMapper = JsonMapper.builder()
                               .propertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy())
                               .build();
    }

    @Test
    void testInboundPassthroughSerialization() throws Exception {
        InboundPassthroughMessage inboundPassthroughMessage = new InboundPassthroughMessage(2, 1,
                                                                                            PassthroughMessage.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test").put("request_id", "12345")));

        JSONAssert.assertEquals("""
                                {
                                "player_id": 1,
                                "session_id": 2,
                                "message": {
                                    "request_id": "12345",
                                    "command": "test"
                                }
                                }
                                """, jsonMapper.writeValueAsString(inboundPassthroughMessage),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPassthroughSerialization() throws Exception {
        PassthroughMessage message = PassthroughMessage.of(
                jsonMapper.createObjectNode().put("command", "test").put("request_id", "12345"));

        JSONAssert.assertEquals("""
                                {
                                    "request_id": "12345",
                                    "command": "test"
                                }
                                """, jsonMapper.writeValueAsString(message),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testLobbySchemaDetailsSerialization() throws Exception {
        LobbySchemaDetails message = new LobbySchemaDetails("test", LobbySchemaDetails.Direction.INBOUND, jsonMapper.createObjectNode().put("type", "object"), "route");

        JSONAssert.assertEquals("""
                                {
                                    "command": "test",
                                    "direction": "inbound",
                                    "schema": {
                                      "type": "object"
                                    },
                                    "route": "route"
                                }
                                """, jsonMapper.writeValueAsString(message),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPassthroughDeserialization() {
        PassthroughMessage message = PassthroughMessage.of(
                jsonMapper.createObjectNode().put("command", "test").put("request_id", "12345"));

        assertEquals(message, jsonMapper.readValue("""
                                                   {
                                                       "request_id": "12345",
                                                       "command": "test"
                                                   }
                                                   """, PassthroughMessage.class));
    }

    @Test
    void testOutboundPassthroughDeserialization() {
        OutboundPassthroughMessage outboundPassthroughMessage = new OutboundPassthroughMessage(OutboundTarget.player(1),
                                                                                               PassthroughMessage.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test")
                                                                                                            .put("request_id", "12345")));

        assertEquals(outboundPassthroughMessage, jsonMapper.readValue("""
                                                   {
                                                       "target": {
                                                            "@type": "player",
                                                            "player_id": 1
                                                       },
                                                       "message": {
                                                           "request_id": "12345",
                                                           "command": "test"
                                                       }
                                                   }
                                                   """, OutboundPassthroughMessage.class));
    }

}
