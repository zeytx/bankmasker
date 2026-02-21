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
}

