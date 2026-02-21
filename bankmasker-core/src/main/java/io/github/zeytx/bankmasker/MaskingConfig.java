package io.github.zeytx.bankmasker;

/**
 * Global configuration for the BankMasker library.
 *
 * <p>Use this class to enable/disable masking globally, change the default
 * mask character, or attach an audit logger.
 *
 * <p>Example (global singleton):
 * <pre>{@code
 * MaskingConfig.getInstance()
 *     .setEnabled(true)
 *     .setDefaultMaskChar('#')
 *     .setAuditLogger((field, type) ->
 *         System.out.println("Masked " + field + " with " + type));
 * }</pre>
 *
 * <p>Example (per-ObjectMapper):
 * <pre>{@code
 * MaskingConfig perMapper = MaskingConfig.create()
 *     .setEnabled(true)
 *     .setDefaultMaskChar('#');
 *
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new MaskingModule(perMapper));
 * }</pre>
 *
 * <p>This class is thread-safe.
 *
 * @since 1.0.0
 * @see MaskingModule
 */
public final class MaskingConfig {

    private static final MaskingConfig INSTANCE = new MaskingConfig();

    private volatile boolean enabled = true;
    private volatile char defaultMaskChar = '*';
    private volatile MaskingAuditLogger auditLogger = null;

    private MaskingConfig() {
    }

    /**
     * Returns the global singleton instance.
     *
     * @return the global configuration
     */
    public static MaskingConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new independent instance for per-ObjectMapper configuration.
     * Use with {@link MaskingModule} to register on a specific ObjectMapper.
     *
     * @return a new configuration instance
     * @since 1.1.0
     * @see MaskingModule
     */
    public static MaskingConfig create() {
        return new MaskingConfig();
    }

    /**
     * Whether masking is globally enabled.
     * When disabled, all fields are serialized with their original values.
     *
     * @return {@code true} if masking is enabled (default)
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables masking globally.
     *
     * @param enabled {@code true} to enable, {@code false} to bypass masking
     * @return this instance for chaining
     */
    public MaskingConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Returns the default mask character used when no specific character is configured.
     *
     * @return the default mask character (default {@code '*'})
     */
    public char getDefaultMaskChar() {
        return defaultMaskChar;
    }

    /**
     * Sets the default mask character.
     *
     * @param defaultMaskChar the character to use for masking
     * @return this instance for chaining
     */
    public MaskingConfig setDefaultMaskChar(char defaultMaskChar) {
        this.defaultMaskChar = defaultMaskChar;
        return this;
    }

    /**
     * Returns the configured audit logger, or {@code null} if none is set.
     *
     * @return the audit logger, or {@code null}
     */
    public MaskingAuditLogger getAuditLogger() {
        return auditLogger;
    }

    /**
     * Sets an audit logger that will be called every time a field is masked.
     *
     * @param auditLogger the logger, or {@code null} to disable auditing
     * @return this instance for chaining
     */
    public MaskingConfig setAuditLogger(MaskingAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
        return this;
    }

    /**
     * Resets all configuration to defaults. Useful in tests.
     *
     * @return this instance for chaining
     */
    public MaskingConfig reset() {
        this.enabled = true;
        this.defaultMaskChar = '*';
        this.auditLogger = null;
        return this;
    }
}

