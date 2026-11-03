package com.chatsocial.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

public class TrimmedStringDeserializer extends StdScalarDeserializer<String> {
    public TrimmedStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        return context.readValue(parser, String.class).trim();
    }
}
