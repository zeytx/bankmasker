package io.github.zeytx.bankmasker;

/**
 * Strategy interface for masking sensitive data.
 * Implement this interface to provide custom masking logic.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface MaskingStrategy {

    /**
     * Applies a masking transformation to the given value.
     *
     * @param value the original sensitive value
     * @return the masked value
     */
    String mask(String value);

    /**
     * Applies a masking transformation using an explicit configuration.
     * Built-in strategies use {@code config} (e.g. for the mask character);
     * the default implementation ignores it and delegates to {@link #mask(String)},
     * so existing custom strategies keep working unchanged.
     *
     * @param value  the original sensitive value
     * @param config the resolved masking configuration
     * @return the masked value
     * @since 1.1.0
     */
    default String mask(String value, MaskingConfig config) {
        return mask(value);
    }
}

