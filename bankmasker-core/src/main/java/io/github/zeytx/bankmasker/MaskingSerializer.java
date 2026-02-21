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

        String original = value.toString();
        if (original.isEmpty()) {
            gen.writeString(original);
            return;
        }

        MaskingConfig config = resolveConfig(provider);

        // If masking is globally disabled, write the original value
        if (!config.isEnabled()) {
            gen.writeString(original);
            return;
        }

        String masked = strategy.mask(original);
        gen.writeString(masked);

        // Audit logging
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
            return value -> MaskUtils.applyCustomMask(value, maskChar, visibleStart, visibleEnd);
        }

        return type.getStrategy();
    }
}