package com.faforever.server.message.client;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;

public class PassthroughSerializer extends ValueSerializer<PassthroughMessage> {

    @Override
    public void serialize(PassthroughMessage value, JsonGenerator gen,
                          SerializationContext ctxt) throws JacksonException {
        gen.writeTree(value.message());
    }

    @Override
    public void serializeWithType(PassthroughMessage value, JsonGenerator gen, SerializationContext ctxt,
                                  TypeSerializer typeSer) throws JacksonException {
        serialize(value, gen, ctxt);
    }
}
