package com.chatsocial.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import java.util.Locale;

public class NormalizedIdentifierDeserializer extends StdScalarDeserializer<String> {
    public NormalizedIdentifierDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        return context.readValue(parser, String.class).trim().toLowerCase(Locale.ROOT);
    }
}
