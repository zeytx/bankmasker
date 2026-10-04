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
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final MaskingStrategy keyStrategy;
    private final MaskType maskType;
    private final String fieldName;

    /**
     * Default no-arg constructor required by Jackson.
     */
    public MaskingSerializer() {
        super(Object.class);
        this.strategy = MaskType.TOTAL.getStrategy();
        this.keyStrategy = null;
        this.maskType = MaskType.TOTAL;
        this.fieldName = "unknown";
    }

    /**
     * Creates a serializer with the given masking strategy and metadata for auditing.
     *
     * @param strategy    the masking strategy to apply
     * @param keyStrategy the strategy for map keys, or {@code null} to keep keys visible
     * @param maskType    the mask type (for audit logging)
     * @param fieldName   the field name (for audit logging)
     */
    MaskingSerializer(MaskingStrategy strategy, MaskingStrategy keyStrategy, MaskType maskType, String fieldName) {
        super(Object.class);
        this.strategy = strategy;
        this.keyStrategy = keyStrategy;
        this.maskType = maskType;
        this.fieldName = fieldName;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        value = unwrap(value);
        if (value == null) {
            gen.writeNull();
            return;
        }

        MaskingConfig config = resolveConfig(provider);

        if (isContainer(value)) {
            if (!config.isEnabled()) {
                // Delegate to Jackson so the original structure is preserved
                provider.defaultSerializeValue(value, gen);
                return;
            }
            writeMaskedContainer(value, gen, config);
            audit(config);
            return;
        }

        // If masking is globally disabled, write the original value with its type
        if (!config.isEnabled()) {
            if (value instanceof char[] chars) {
                gen.writeString(chars, 0, chars.length);
            } else {
                provider.defaultSerializeValue(value, gen);
            }
            return;
        }

        String masked = maskValue(value, strategy, config);
        gen.writeString(masked);
        if (!masked.isEmpty()) {
            audit(config);
        }
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Object value) {
        value = unwrap(value);
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence chars) {
            return chars.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

    private static Object unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    // char[] is treated as a single secret value, not as a container
    private static boolean isContainer(Object value) {
        return value instanceof Collection || value instanceof Map
                || (value.getClass().isArray() && !(value instanceof char[]));
    }

    private static boolean isScalar(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Character
                || value instanceof Boolean || value instanceof UUID || value instanceof Enum<?>
                || value instanceof TemporalAccessor || value instanceof char[];
    }

    /**
     * Masks a non-container value. Scalars (String, Number, UUID, dates, …) are
     * masked through their string form; empty strings are returned unchanged.
     * Any other object is fully masked: type-specific strategies keep parts of
     * their input (EMAIL everything after '@', DNI the tail), so applying them
     * to a POJO's {@code toString()} could expose its other fields.
     */
    private static String maskValue(Object value, MaskingStrategy strategy, MaskingConfig config) {
        if (!isScalar(value)) {
            return MaskType.repeat(config.getDefaultMaskChar(), 8);
        }
        String original = value instanceof char[] chars ? new String(chars) : value.toString();
        return original.isEmpty() ? original : strategy.mask(original, config);
    }

    /**
     * Masks a collection, map or array element by element, preserving the JSON
     * shape (array/object) instead of masking the container's {@code toString()},
     * which would change the output type and could leak fragments of elements.
     */
    private void writeMaskedContainer(Object value, JsonGenerator gen, MaskingConfig config) throws IOException {
        if (value instanceof Map<?, ?> map) {
            gen.writeStartObject();
            Set<String> writtenKeys = keyStrategy != null ? new HashSet<>() : null;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                gen.writeFieldName(writtenKeys != null
                        ? maskKey(entry.getKey(), config, writtenKeys)
                        : String.valueOf(entry.getKey()));
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
        element = unwrap(element);
        if (element == null) {
            gen.writeNull();
            return;
        }
        if (isContainer(element)) {
            writeMaskedContainer(element, gen, config);
            return;
        }
        gen.writeString(maskValue(element, strategy, config));
    }

    /**
     * Masks a map key. Distinct keys can mask to the same value (two cards
     * ending in 1111), so repeated masked keys get a {@code ~2}, {@code ~3}, …
     * suffix instead of producing duplicate JSON fields that drop entries.
     */
    private String maskKey(Object key, MaskingConfig config, Set<String> writtenKeys) {
        String masked = key == null ? "null" : maskValue(key, keyStrategy, config);
        String unique = masked;
        for (int n = 2; !writtenKeys.add(unique); n++) {
            unique = masked + "~" + n;
        }
        return unique;
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

        MaskType[] keyMask = annotation.keyMask();
        if (keyMask.length > 1) {
            throw JsonMappingException.from(prov,
                    "@MaskData.keyMask accepts a single MaskType, got " + keyMask.length
                            + " on property '" + property.getName() + "'");
        }
        MaskType type = annotation.value();
        MaskingStrategy keyStrategy = keyMask.length == 1 ? resolveStrategy(keyMask[0], annotation) : null;
        return new MaskingSerializer(resolveStrategy(type, annotation), keyStrategy, type, property.getName());
    }

    /**
     * Resolves the masking strategy for a type. For CUSTOM, it builds a strategy
     * using the annotation's maskChar, visibleStart and visibleEnd.
     */
    private static MaskingStrategy resolveStrategy(MaskType type, MaskData annotation) {
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