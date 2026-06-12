package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * Jackson serializer that applies masking to sensitive fields annotated with {@link MaskData}.
 *
 * <p>This serializer delegates masking to the strategy defined in {@link MaskType} or
 * applies a custom mask when {@link MaskType#CUSTOM} is used.
 *
 * <p>Respects {@link MaskingConfig} for global enable/disable and audit logging.
 *
 * @since 1.0.0
 * @see MaskData
 * @see MaskType
 * @see MaskingStrategy
 * @see MaskingConfig
 */
public class MaskingSerializer extends StdSerializer<Object> implements ContextualSerializer {

    private final MaskingStrategy strategy;
    private final MaskType maskType;
    private final String fieldName;

    /**
     * Default no-arg constructor required by Jackson.
     */
    public MaskingSerializer() {
        super(Object.class);
        this.strategy = MaskType.TOTAL.getStrategy();
        this.maskType = MaskType.TOTAL;
        this.fieldName = "unknown";
    }

    /**
     * Creates a serializer with the given masking strategy and metadata for auditing.
     *
     * @param strategy  the masking strategy to apply
     * @param maskType  the mask type (for audit logging)
     * @param fieldName the field name (for audit logging)
     */
    MaskingSerializer(MaskingStrategy strategy, MaskType maskType, String fieldName) {
        super(Object.class);
        this.strategy = strategy;
        this.maskType = maskType;
        this.fieldName = fieldName;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        MaskingConfig config = resolveConfig(provider);

        // char[] is treated as a single secret value, not as a container
        boolean charArray = value instanceof char[];

        if (!charArray && isContainer(value)) {
            if (!config.isEnabled()) {
                // Delegate to Jackson so the original structure is preserved
                provider.defaultSerializeValue(value, gen);
                return;
            }
            writeMaskedContainer(value, gen, config);
            audit(config);
            return;
        }

        // Non-String scalars (Number, UUID, …) are masked through their toString()
        // and always written as a JSON string.
        String original = charArray ? new String((char[]) value) : value.toString();
        if (original.isEmpty()) {
            gen.writeString(original);
            return;
        }

        // If masking is globally disabled, write the original value with its type
        if (!config.isEnabled()) {
            if (value instanceof String || charArray) {
                gen.writeString(original);
            } else {
                provider.defaultSerializeValue(value, gen);
            }
            return;
        }

        gen.writeString(strategy.mask(original, config));
        audit(config);
    }

    private static boolean isContainer(Object value) {
        return value instanceof Collection || value instanceof Map || value.getClass().isArray();
    }

    /**
     * Masks a collection, map or array element by element, preserving the JSON
     * shape (array/object) instead of masking the container's {@code toString()},
     * which would change the output type and could leak fragments of elements.
     */
    private void writeMaskedContainer(Object value, JsonGenerator gen, MaskingConfig config) throws IOException {
        if (value instanceof Map<?, ?> map) {
            gen.writeStartObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                gen.writeFieldName(String.valueOf(entry.getKey()));
                writeMaskedElement(entry.getValue(), gen, config);
            }
            gen.writeEndObject();
            return;
        }

        gen.writeStartArray();
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                writeMaskedElement(element, gen, config);
            }
        } else {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                writeMaskedElement(Array.get(value, i), gen, config);
            }
        }
        gen.writeEndArray();
    }

    private void writeMaskedElement(Object element, JsonGenerator gen, MaskingConfig config) throws IOException {
        if (element == null) {
            gen.writeNull();
            return;
        }
        if (isContainer(element)) {
            writeMaskedContainer(element, gen, config);
            return;
        }
        String original = element.toString();
        gen.writeString(original.isEmpty() ? original : strategy.mask(original, config));
    }

    private void audit(MaskingConfig config) {
        MaskingAuditLogger logger = config.getAuditLogger();
        if (logger != null) {
            logger.onFieldMasked(fieldName, maskType);
        }
    }

    /**
     * Resolves the {@link MaskingConfig} from the per-ObjectMapper context
     * (via {@link MaskingModule}), falling back to the global singleton.
     *
     * @param provider the serializer provider
     * @return the resolved config
     */
    private static MaskingConfig resolveConfig(SerializerProvider provider) {
        return MaskingModule.resolveConfig(provider);
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

        String name = property.getName();
        MaskType type = annotation.value();
        MaskingStrategy resolved = resolveStrategy(annotation);
        return new MaskingSerializer(resolved, type, name);
    }

    /**
     * Resolves the masking strategy from the annotation parameters.
     * When the type is CUSTOM, it builds a strategy using maskChar, visibleStart and visibleEnd.
     */
    private static MaskingStrategy resolveStrategy(MaskData annotation) {
        MaskType type = annotation.value();

        if (type == MaskType.CUSTOM) {
            char maskChar = annotation.maskChar();
            int visibleStart = Math.max(0, annotation.visibleStart());
            int visibleEnd = Math.max(0, annotation.visibleEnd());
            return ConfigAwareStrategy.adapt((value, config) ->
                    MaskUtils.applyCustomMask(value, maskChar, visibleStart, visibleEnd, config));
        }

        return type.getStrategy();
    }
}