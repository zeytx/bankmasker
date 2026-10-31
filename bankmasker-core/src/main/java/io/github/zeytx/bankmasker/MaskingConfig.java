package io.github.zeytx.bankmasker;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

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
    private volatile byte[] pseudonymKey = null;
    private final Map<String, MaskType> fieldMasks = new ConcurrentHashMap<>();

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
     * <p>Control characters (e.g. {@code '\n'}, which would allow forging log
     * lines), lone UTF-16 surrogates (invalid output) and the delimiters
     * {@code '"'} and {@code '\\'} (masked text is spliced into JSON and quoted
     * values found in log messages) are rejected.
     *
     * @param defaultMaskChar the character to use for masking
     * @return this instance for chaining
     * @throws IllegalArgumentException if the character is a control character, a surrogate,
     *                                  {@code '"'} or {@code '\\'}
     */
    public MaskingConfig setDefaultMaskChar(char defaultMaskChar) {
        if (Character.isISOControl(defaultMaskChar) || Character.isSurrogate(defaultMaskChar)
                || defaultMaskChar == '"' || defaultMaskChar == '\\') {
            throw new IllegalArgumentException(
                    "Invalid mask character U+" + String.format("%04X", (int) defaultMaskChar));
        }
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
     * Sets the secret key used by {@link MaskType#PSEUDONYM} (HMAC-SHA256).
     * Without a key, {@code PSEUDONYM} values are fully masked. Keep the key
     * secret and stable: changing it changes every pseudonym.
     *
     * @param key the key, at least 16 bytes, or {@code null} to remove it
     * @return this instance for chaining
     * @throws IllegalArgumentException if the key is shorter than 16 bytes
     * @since 1.3.0
     */
    public MaskingConfig setPseudonymKey(byte[] key) {
        if (key != null && key.length < 16) {
            throw new IllegalArgumentException("Pseudonym key must be at least 16 bytes (128 bits)");
        }
        this.pseudonymKey = key == null ? null : key.clone();
        return this;
    }

    // Package-private on purpose: the key is never exposed through the public API
    byte[] pseudonymKey() {
        return pseudonymKey;
    }

    /**
     * Masks every JSON property with the given name, even without
     * {@link MaskData}: a safety net for DTOs you cannot annotate. Names are
     * matched ignoring case, {@code _} and {@code -} ({@code cardNumber}
     * also matches {@code card_number}); {@link MaskData} takes precedence.
     *
     * <p>Applied by {@link MaskingModule} (Jackson 2) and
     * {@link Jackson3MaskingModule} (Jackson 3), which must be registered on the
     * mapper (the Spring Boot starter does it). Configure the rules before the
     * first serialization: Jackson caches serializers per type.
     *
     * @param propertyName the JSON property name
     * @param type         the mask to apply
     * @return this instance for chaining
     * @since 1.3.0
     */
    public MaskingConfig maskField(String propertyName, MaskType type) {
        if (propertyName == null || type == null) {
            throw new IllegalArgumentException("propertyName and type must not be null");
        }
        fieldMasks.put(normalizeFieldName(propertyName), type);
        return this;
    }

    /**
     * Returns the mask configured for a property name with
     * {@link #maskField(String, MaskType)}, or {@code null}.
     *
     * @param propertyName the JSON property name
     * @return the mask type, or {@code null} if the property has no rule
     * @since 1.3.0
     */
    public MaskType getFieldMask(String propertyName) {
        if (propertyName == null || fieldMasks.isEmpty()) {
            return null;
        }
        return fieldMasks.get(normalizeFieldName(propertyName));
    }

    /**
     * Returns the property-name rules, keyed by normalized name.
     *
     * @return an immutable copy of the rules
     * @since 1.3.0
     */
    public Map<String, MaskType> getFieldMasks() {
        return Map.copyOf(new TreeMap<>(fieldMasks));
    }

    /**
     * Removes every property-name rule.
     *
     * @return this instance for chaining
     * @since 1.3.0
     */
    public MaskingConfig clearFieldMasks() {
        fieldMasks.clear();
        return this;
    }

    private static String normalizeFieldName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '_' && c != '-') {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
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
        this.pseudonymKey = null;
        this.fieldMasks.clear();
        return this;
    }
}

