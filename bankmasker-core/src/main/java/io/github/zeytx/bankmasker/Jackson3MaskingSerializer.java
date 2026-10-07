package io.github.zeytx.bankmasker;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3 ({@code tools.jackson}) serializer that applies masking to fields
 * annotated with {@link MaskData}.
 *
 * <p>Registered automatically through {@link MaskData}, exactly like the
 * Jackson 2 {@link MaskingSerializer}, so no module is required. Use
 * {@link Jackson3MaskingModule} only for per-mapper configuration.
 *
 * @since 1.2.0
 * @see MaskingSerializer
 */
public class Jackson3MaskingSerializer extends StdSerializer<Object> {

    private final MaskingSupport support;

    /**
     * Default no-arg constructor required by Jackson.
     */
    public Jackson3MaskingSerializer() {
        this(MaskingSupport.TOTAL);
    }

    private Jackson3MaskingSerializer(MaskingSupport support) {
        super(Object.class);
        this.support = support;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        support.serialize(value, tokens(gen), Jackson3MaskingModule.resolveConfig(ctxt),
                original -> ctxt.writeValue(gen, original));
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Object value) {
        return MaskingSupport.isEmpty(value);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }

        MaskData annotation = property.getAnnotation(MaskData.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(MaskData.class);
        }
        if (annotation == null) {
            return ctxt.findPrimaryPropertySerializer(property.getType(), property);
        }

        try {
            return new Jackson3MaskingSerializer(MaskingSupport.of(annotation, property.getName()));
        } catch (IllegalArgumentException e) {
            return ctxt.reportBadDefinition(property.getType(), e.getMessage());
        }
    }

    private static MaskingSupport.Tokens<RuntimeException> tokens(JsonGenerator gen) {
        return new MaskingSupport.Tokens<>() {
            @Override
            public void startObject() {
                gen.writeStartObject();
            }

            @Override
            public void endObject() {
                gen.writeEndObject();
            }

            @Override
            public void startArray() {
                gen.writeStartArray();
            }

            @Override
            public void endArray() {
                gen.writeEndArray();
            }

            @Override
            public void name(String name) {
                gen.writeName(name);
            }

            @Override
            public void string(String value) {
                gen.writeString(value);
            }

            @Override
            public void nullValue() {
                gen.writeNull();
            }
        };
    }
}
