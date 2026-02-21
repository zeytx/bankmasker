package io.github.zeytx.bankmasker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SLF4J-based implementation of {@link MaskingAuditLogger}.
 *
 * <p>Logs each masked field access at INFO level. Requires SLF4J on the classpath.
 *
 * <p>Usage:
 * <pre>{@code
 * MaskingConfig.getInstance()
 *     .setAuditLogger(new Slf4jMaskingAuditLogger());
 * }</pre>
 *
 * @since 1.0.0
 */
public class Slf4jMaskingAuditLogger implements MaskingAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(Slf4jMaskingAuditLogger.class);

    @Override
    public void onFieldMasked(String fieldName, MaskType maskType) {
        log.info("[BankMasker] Masked field '{}' using {}", fieldName, maskType);
    }
}

