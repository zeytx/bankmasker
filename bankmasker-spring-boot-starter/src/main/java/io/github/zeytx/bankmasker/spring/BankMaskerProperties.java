package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.MaskType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for BankMasker.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * bankmasker:
 *   enabled: true
 *   default-mask-char: '*'
 *   pseudonym-key: ${BANKMASKER_PSEUDONYM_KEY}
 *   fields:
 *     password: TOTAL
 *     cardNumber: CREDIT_CARD
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
     * Secret key (at least 16 characters) for the PSEUDONYM mask type. Without
     * it, PSEUDONYM values are fully masked. Load it from an environment
     * variable or a secret store, never commit it.
     */
    private String pseudonymKey;

    /**
     * Masks JSON properties by name even without @MaskData (property name to
     * mask type), e.g. password: TOTAL. Names match ignoring case, '_' and '-'.
     */
    private Map<String, MaskType> fields = new LinkedHashMap<>();

    /**
     * Audit logging settings.
     */
    private Audit audit = new Audit();

    public String getPseudonymKey() {
        return pseudonymKey;
    }

    public void setPseudonymKey(String pseudonymKey) {
        this.pseudonymKey = pseudonymKey;
    }

    public Map<String, MaskType> getFields() {
        return fields;
    }

    public void setFields(Map<String, MaskType> fields) {
        this.fields = fields;
    }

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

