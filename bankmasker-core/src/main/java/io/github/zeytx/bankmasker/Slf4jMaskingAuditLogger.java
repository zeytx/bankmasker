package io.github.zeytx.bankmasker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * SLF4J-based implementation of {@link MaskingAuditLogger}.
 *
 * <p>Logs each masked field access at a configurable level (INFO by default).
 * Requires SLF4J 2.x on the classpath.
 *
 * <p>Usage:
 * <pre>{@code
 * MaskingConfig.getInstance()
 *     .setAuditLogger(new Slf4jMaskingAuditLogger());
 *
 * // Or at DEBUG level
 * MaskingConfig.getInstance()
 *     .setAuditLogger(new Slf4jMaskingAuditLogger(Level.DEBUG));
 * }</pre>
 *
 * @since 1.0.0
 */
public class Slf4jMaskingAuditLogger implements MaskingAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(Slf4jMaskingAuditLogger.class);

    private final Level level;

    /**
     * Creates a logger that audits at INFO level.
     */
    public Slf4jMaskingAuditLogger() {
        this(Level.INFO);
    }

    /**
     * Creates a logger that audits at the given level.
     *
     * @param level the SLF4J level to log at
     * @since 1.1.0
     */
    public Slf4jMaskingAuditLogger(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        this.level = level;
    }

    /**
     * Returns the level this logger audits at.
     *
     * @return the SLF4J level
     * @since 1.1.0
     */
    public Level getLevel() {
        return level;
    }

    @Override
    public void onFieldMasked(String fieldName, MaskType maskType) {
        log.atLevel(level).log("[BankMasker] Masked field '{}' using {}", fieldName, maskType);
    }
}
