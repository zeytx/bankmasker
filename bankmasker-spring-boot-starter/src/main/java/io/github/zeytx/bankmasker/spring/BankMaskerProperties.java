package io.github.zeytx.bankmasker.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for BankMasker.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * bankmasker:
 *   enabled: true
 *   default-mask-char: '*'
 *   audit:
 *     enabled: true
 *     level: INFO
 * </pre>
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "bankmasker")
public class BankMaskerProperties {

    /**
     * Whether masking is globally enabled.
     */
    private boolean enabled = true;

    /**
     * Default character used for masking.
     */
    private char defaultMaskChar = '*';

    /**
     * Audit logging settings.
     */
    private Audit audit = new Audit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public char getDefaultMaskChar() {
        return defaultMaskChar;
    }

    public void setDefaultMaskChar(char defaultMaskChar) {
        this.defaultMaskChar = defaultMaskChar;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    /**
     * Audit logging sub-properties.
     */
    public static class Audit {

        /**
         * Whether audit logging of masked fields is enabled.
         */
        private boolean enabled = false;

        /**
         * SLF4J level for audit log entries (TRACE, DEBUG, INFO, WARN, ERROR).
         */
        private String level = "INFO";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }
}

