package com.faforever.server.message;

import com.faforever.server.message.client.ProtocolMessage;
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
                                                                                            ProtocolMessage.Passthrough.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test")));

        JSONAssert.assertEquals("""
                                {
                                "player_id": 1,
                                "session_id": 2,
                                "message": {
                                    "command": "test"
                                }
                                }
                                """, jsonMapper.writeValueAsString(inboundPassthroughMessage),
                                JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPassthroughSerialization() throws Exception {
        ProtocolMessage.Passthrough message = ProtocolMessage.Passthrough.of(
                jsonMapper.createObjectNode().put("command", "test"));

        JSONAssert.assertEquals("""
                                {
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
    void testPassthroughDeserialization() throws Exception {
        ProtocolMessage.Passthrough message = ProtocolMessage.Passthrough.of(
                jsonMapper.createObjectNode().put("command", "test"));

        assertEquals(message, jsonMapper.readValue("""
                                                   {
                                                       "command": "test"
                                                   }
                                                   """, ProtocolMessage.Passthrough.class));
    }

    @Test
    void testOutboundPassthroughDeserialization() throws Exception {
        OutboundPassthroughMessage outboundPassthroughMessage = new OutboundPassthroughMessage(OutboundTarget.player(1),
                                                                                               ProtocolMessage.Passthrough.of(
                                                                                                    jsonMapper.createObjectNode()
                                                                                                              .put("command",
                                                                                                                   "test")));

        assertEquals(outboundPassthroughMessage, jsonMapper.readValue("""
                                                   {
                                                       "target": {
                                                            "@type": "player",
                                                            "player_id": 1
                                                       },
                                                       "message": {
                                                           "command": "test"
                                                       }
                                                   }
                                                   """, OutboundPassthroughMessage.class));
    }

}
