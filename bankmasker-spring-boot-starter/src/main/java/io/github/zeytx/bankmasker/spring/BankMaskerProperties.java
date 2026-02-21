package io.github.zeytx.bankmasker.spring;

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
 * </pre>
 *
 * @since 1.0.0
 */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

