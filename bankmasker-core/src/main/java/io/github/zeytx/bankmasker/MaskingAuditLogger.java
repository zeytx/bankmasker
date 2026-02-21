package io.github.zeytx.bankmasker;

/**
 * Functional interface for auditing masked field access.
 *
 * <p>Implement this to log or record which fields were masked and when.
 * Useful for compliance and security auditing in regulated environments.
 *
 * <p>Example:
 * <pre>{@code
 * MaskingConfig.getInstance().setAuditLogger((fieldName, maskType) ->
 *     log.info("Masked field '{}' using {}", fieldName, maskType));
 * }</pre>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface MaskingAuditLogger {

    /**
     * Called when a field is masked during serialization.
     *
     * @param fieldName the name of the masked field
     * @param maskType  the type of mask applied
     */
    void onFieldMasked(String fieldName, MaskType maskType);
}

