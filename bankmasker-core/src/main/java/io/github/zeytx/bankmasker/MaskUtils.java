package io.github.zeytx.bankmasker;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Utility class for masking sensitive data programmatically.
 *
 * <p>Use this in {@code toString()} methods, log statements, or anywhere
 * outside Jackson serialization where you need to mask values.
 *
 * <p>Example:
 * <pre>{@code
 * @Override
 * public String toString() {
 *     return "User{card=" + MaskUtils.mask(cardNumber, MaskType.CREDIT_CARD) + "}";
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public final class MaskUtils {

    private MaskUtils() {
        // utility class
    }

    /**
     * Masks the given value using a built-in mask type.
     * Respects {@link MaskingConfig#isEnabled()} — returns the original value if masking is disabled.
     *
     * @param value the sensitive value to mask
     * @param type  the mask type to apply
     * @return the masked value, or the original if masking is disabled or value is null/empty
     */
    public static String mask(String value, MaskType type) {
        return mask(value, type, MaskingConfig.getInstance());
    }

    /**
     * Masks the given value using a built-in mask type and an explicit
     * configuration (mask character, enabled flag, pseudonym key) instead of
     * the global one.
     *
     * @param value  the sensitive value to mask
     * @param type   the mask type to apply
     * @param config the configuration to use
     * @return the masked value, or the original if masking is disabled or value is null/empty
     * @since 1.3.0
     */
    public static String mask(String value, MaskType type, MaskingConfig config) {
        if (value == null || value.isEmpty() || !config.isEnabled()) {
            return value;
        }
        return type.getStrategy().mask(value, config);
    }

    /**
     * Builds a {@code toString()} representation of an object in which the
     * fields annotated with {@link MaskData} are masked, exactly as in JSON.
     * Records' generated {@code toString()} and Lombok's {@code @ToString}
     * print those fields in clear text, which then ends up in logs.
     *
     * <pre>{@code
     * public record Payment(@MaskData(MaskType.CREDIT_CARD) String card, BigDecimal amount) {
     *     @Override
     *     public String toString() {
     *         return MaskUtils.toMaskedString(this);   // Payment[card=****-****-****-1111, amount=10.00]
     *     }
     * }
     * }</pre>
     *
     * <p>Fields are read reflectively (superclass fields first). A field that
     * cannot be read, e.g. in a module that does not open its package, is
     * printed as {@code ?}, or fully masked if annotated.
     *
     * @param object the object to describe, may be {@code null}
     * @return the masked representation
     * @since 1.3.0
     */
    public static String toMaskedString(Object object) {
        return MaskedToString.of(object, MaskingConfig.getInstance());
    }

    /**
     * Masks the given value using custom parameters.
     *
     * @param value        the sensitive value to mask
     * @param maskChar     the character to use for masking
     * @param visibleStart number of characters visible at the beginning
     * @param visibleEnd   number of characters visible at the end
     * @return the masked value
     */
    public static String mask(String value, char maskChar, int visibleStart, int visibleEnd) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!MaskingConfig.getInstance().isEnabled()) {
            return value;
        }
        return applyCustomMask(value, maskChar, Math.max(0, visibleStart), Math.max(0, visibleEnd));
    }

    /**
     * Applies a custom mask to a value. This method is also used internally
     * by {@link MaskingSerializer} to avoid code duplication.
     *
     * @param value        the value to mask
     * @param maskChar     the masking character
     * @param visibleStart visible characters from the start
     * @param visibleEnd   visible characters from the end
     * @return the masked string
     */
    static String applyCustomMask(String value, char maskChar, int visibleStart, int visibleEnd) {
        return applyCustomMask(value, maskChar, visibleStart, visibleEnd, MaskingConfig.getInstance());
    }

    /**
     * Applies a custom mask resolving the default mask character from the given
     * configuration (per-mapper or global).
     *
     * @param value        the value to mask
     * @param maskChar     the masking character
     * @param visibleStart visible characters from the start
     * @param visibleEnd   visible characters from the end
     * @param config       the resolved masking configuration
     * @return the masked string
     * @since 1.1.0
     */
    static String applyCustomMask(String value, char maskChar, int visibleStart, int visibleEnd,
                                  MaskingConfig config) {
        int len = value.length();
        // long: visibleStart + visibleEnd may overflow int
        long totalVisible = (long) visibleStart + visibleEnd;

        // If the caller uses the default annotation char '*', respect the config
        char effectiveChar = (maskChar == '*')
                ? config.getDefaultMaskChar()
                : maskChar;

        // Fail-closed: if the visible window covers the whole value, mask everything
        // instead of leaking the original (PCI-DSS: never expose the full value).
        if (totalVisible >= len) {
            return String.valueOf(effectiveChar).repeat(len);
        }

        String prefix = value.substring(0, visibleStart);
        String suffix = visibleEnd > 0 ? value.substring(len - visibleEnd) : "";
        String masked = String.valueOf(effectiveChar).repeat(len - (int) totalVisible);
        return prefix + masked + suffix;
    }

    /** Reflection-based masked {@code toString()}; fields are resolved once per class. */
    private static final class MaskedToString {

        private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
            @Override
            protected List<Field> computeValue(Class<?> type) {
                List<Field> fields = new ArrayList<>();
                for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                    List<Field> own = new ArrayList<>();
                    for (Field field : c.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                            field.trySetAccessible();
                            own.add(field);
                        }
                    }
                    fields.addAll(0, own);
                }
                return List.copyOf(fields);
            }
        };

        static String of(Object object, MaskingConfig config) {
            if (object == null) {
                return "null";
            }
            StringBuilder sb = new StringBuilder(object.getClass().getSimpleName()).append('[');
            List<Field> fields = FIELDS.get(object.getClass());
            for (int i = 0; i < fields.size(); i++) {
                Field field = fields.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(field.getName()).append('=');
                MaskData annotation = field.getAnnotation(MaskData.class);
                Object value;
                try {
                    value = field.get(object);
                } catch (IllegalAccessException e) {
                    sb.append(annotation != null ? MaskType.repeat(config.getDefaultMaskChar(), 8) : "?");
                    continue;
                }
                if (annotation == null) {
                    sb.append(value);
                } else {
                    MaskingSupport.of(annotation, field.getName())
                            .serialize(value, new JavaStyleTokens(sb), config, sb::append);
                }
            }
            return sb.append(']').toString();
        }
    }

    /** Renders masked values the way Java collections print themselves: {@code [a, b]}, {@code {k=v}}. */
    private static final class JavaStyleTokens implements MaskingSupport.Tokens<RuntimeException> {

        private final StringBuilder sb;
        // One entry per open container: true until its first element is written
        private final Deque<Boolean> first = new ArrayDeque<>();
        private boolean afterName;

        JavaStyleTokens(StringBuilder sb) {
            this.sb = sb;
        }

        private void separate() {
            if (afterName) {
                afterName = false;
            } else if (!first.isEmpty()) {
                if (!first.pop()) {
                    sb.append(", ");
                }
                first.push(false);
            }
        }

        @Override
        public void startObject() {
            separate();
            sb.append('{');
            first.push(true);
        }

        @Override
        public void endObject() {
            first.pop();
            sb.append('}');
        }

        @Override
        public void startArray() {
            separate();
            sb.append('[');
            first.push(true);
        }

        @Override
        public void endArray() {
            first.pop();
            sb.append(']');
        }

        @Override
        public void name(String name) {
            separate();
            sb.append(name).append('=');
            afterName = true;
        }

        @Override
        public void string(String value) {
            separate();
            sb.append(value);
        }

        @Override
        public void nullValue() {
            separate();
            sb.append("null");
        }
    }
}

