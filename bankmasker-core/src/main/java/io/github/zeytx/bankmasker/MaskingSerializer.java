package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Jackson 2 serializer that applies masking to sensitive fields annotated with {@link MaskData}.
 *
 * <p>This serializer delegates masking to the strategy defined in {@link MaskType} or
 * applies a custom mask when {@link MaskType#CUSTOM} is used.
 *
 * <p>Respects {@link MaskingConfig} for global enable/disable and audit logging.
 * The Jackson 3 counterpart is {@link Jackson3MaskingSerializer}.
 *
 * @since 1.0.0
 * @see MaskData
 * @see MaskType
 * @see MaskingStrategy
 * @see MaskingConfig
 */
public class MaskingSerializer extends StdSerializer<Object> implements ContextualSerializer {

    private final MaskingSupport support;

    /**
     * Default no-arg constructor required by Jackson.
     */
    public MaskingSerializer() {
        this(MaskingSupport.TOTAL);
    }

    private MaskingSerializer(MaskingSupport support) {
        super(Object.class);
        this.support = support;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        support.serialize(value, tokens(gen), MaskingModule.resolveConfig(provider),
                original -> provider.defaultSerializeValue(original, gen));
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Object value) {
        return MaskingSupport.isEmpty(value);
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }

        MaskData annotation = property.getAnnotation(MaskData.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(MaskData.class);
        }
        if (annotation == null) {
            return prov.findValueSerializer(property.getType(), property);
        }

        try {
            return new MaskingSerializer(MaskingSupport.of(annotation, property.getName()));
        } catch (IllegalArgumentException e) {
            throw JsonMappingException.from(prov, e.getMessage());
        }
    }

    private static MaskingSupport.Tokens<IOException> tokens(JsonGenerator gen) {
        return new MaskingSupport.Tokens<>() {
            @Override
            public void startObject() throws IOException {
                gen.writeStartObject();
            }

            @Override
            public void endObject() throws IOException {
                gen.writeEndObject();
            }

            @Override
            public void startArray() throws IOException {
                gen.writeStartArray();
            }

            @Override
            public void endArray() throws IOException {
                gen.writeEndArray();
            }

            @Override
            public void name(String name) throws IOException {
                gen.writeFieldName(name);
            }

            @Override
            public void string(String value) throws IOException {
                gen.writeString(value);
            }

            @Override
            public void nullValue() throws IOException {
                gen.writeNull();
            }
        };
    }
}
