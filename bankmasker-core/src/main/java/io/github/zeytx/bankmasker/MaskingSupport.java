package io.github.zeytx.bankmasker;

import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Masking logic shared by the Jackson 2 ({@link MaskingSerializer}) and
 * Jackson 3 ({@link Jackson3MaskingSerializer}) serializers.
 *
 * <p>It must not reference any Jackson type: either Jackson version may be
 * absent at runtime. Each serializer adapts its generator to {@link Tokens}.
 *
 * @since 1.2.0
 */
final class MaskingSupport {

    /** Context attribute holding a per-mapper {@link MaskingConfig} (both Jackson versions). */
    static final Object CONFIG_KEY = MaskingSupport.class.getName() + ".config";

    /** Used when no {@link MaskData} is available: masks everything. */
    static final MaskingSupport TOTAL =
            new MaskingSupport(MaskType.TOTAL.getStrategy(), null, MaskType.TOTAL, "unknown");

    /** Minimal JSON writer over a Jackson generator. */
    interface Tokens<E extends Exception> {
        void startObject() throws E;

        void endObject() throws E;

        void startArray() throws E;

        void endArray() throws E;

        void name(String name) throws E;

        void string(String value) throws E;

        void nullValue() throws E;
    }

    /** Writes a value unmasked, with the mapper's own serializers. */
    @FunctionalInterface
    interface Passthrough<E extends Exception> {
        void write(Object value) throws E;
    }

    private final MaskingStrategy strategy;
    private final MaskingStrategy keyStrategy;
    private final MaskType maskType;
    private final String fieldName;

    private MaskingSupport(MaskingStrategy strategy, MaskingStrategy keyStrategy, MaskType maskType, String fieldName) {
        this.strategy = strategy;
        this.keyStrategy = keyStrategy;
        this.maskType = maskType;
        this.fieldName = fieldName;
    }

    /**
     * Builds the masking setup for an annotated property.
     *
     * @throws IllegalArgumentException if {@link MaskData#keyMask()} has more than one type
     */
    static MaskingSupport of(MaskData annotation, String fieldName) {
        MaskType[] keyMask = annotation.keyMask();
        if (keyMask.length > 1) {
            throw new IllegalArgumentException("@MaskData.keyMask accepts a single MaskType, got "
                    + keyMask.length + " on property '" + fieldName + "'");
        }
        MaskType type = annotation.value();
        MaskingStrategy keyStrategy = keyMask.length == 1 ? resolveStrategy(keyMask[0], annotation) : null;
        return new MaskingSupport(resolveStrategy(type, annotation), keyStrategy, type, fieldName);
    }

    /** Returns the per-mapper config stored as {@code attribute}, or the global one. */
    static MaskingConfig configOrGlobal(Object attribute) {
        return attribute instanceof MaskingConfig perMapper ? perMapper : MaskingConfig.getInstance();
    }

    <E extends Exception> void serialize(Object value, Tokens<E> out, MaskingConfig config,
                                         Passthrough<E> original) throws E {
        value = unwrap(value);
        if (value == null) {
            out.nullValue();
            return;
        }
        if (!config.isEnabled()) {
            // Write the original value with its JSON type and structure
            if (value instanceof char[] chars) {
                out.string(new String(chars));
            } else {
                original.write(value);
            }
            return;
        }
        if (isContainer(value)) {
            writeContainer(value, out, config);
            audit(config);
            return;
        }
        String masked = mask(value, strategy, config);
        out.string(masked);
        if (!masked.isEmpty()) {
            audit(config);
        }
    }

    static boolean isEmpty(Object value) {
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
    private static String mask(Object value, MaskingStrategy strategy, MaskingConfig config) {
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
    private <E extends Exception> void writeContainer(Object value, Tokens<E> out, MaskingConfig config) throws E {
        if (value instanceof Map<?, ?> map) {
            out.startObject();
            Set<String> writtenKeys = keyStrategy != null ? new HashSet<>() : null;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.name(writtenKeys != null
                        ? maskKey(entry.getKey(), config, writtenKeys)
                        : String.valueOf(entry.getKey()));
                writeElement(entry.getValue(), out, config);
            }
            out.endObject();
            return;
        }

        out.startArray();
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                writeElement(element, out, config);
            }
        } else {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                writeElement(Array.get(value, i), out, config);
            }
        }
        out.endArray();
    }

    private <E extends Exception> void writeElement(Object element, Tokens<E> out, MaskingConfig config) throws E {
        element = unwrap(element);
        if (element == null) {
            out.nullValue();
            return;
        }
        if (isContainer(element)) {
            writeContainer(element, out, config);
            return;
        }
        out.string(mask(element, strategy, config));
    }

    /**
     * Masks a map key. Distinct keys can mask to the same value (two cards
     * ending in 1111), so repeated masked keys get a {@code ~2}, {@code ~3}, …
     * suffix instead of producing duplicate JSON fields that drop entries.
     */
    private String maskKey(Object key, MaskingConfig config, Set<String> writtenKeys) {
        String masked = key == null ? "null" : mask(key, keyStrategy, config);
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
