package io.github.zeytx.bankmasker;

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
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!MaskingConfig.getInstance().isEnabled()) {
            return value;
        }
        return type.getStrategy().mask(value);
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
        int len = value.length();
        int totalVisible = visibleStart + visibleEnd;

        if (totalVisible >= len) {
            return value;
        }

        // If the caller uses the default annotation char '*', respect the global config
        char effectiveChar = (maskChar == '*')
                ? MaskingConfig.getInstance().getDefaultMaskChar()
                : maskChar;

        String prefix = value.substring(0, visibleStart);
        String suffix = visibleEnd > 0 ? value.substring(len - visibleEnd) : "";
        String masked = String.valueOf(effectiveChar).repeat(len - totalVisible);
        return prefix + masked + suffix;
    }
}

