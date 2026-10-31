package io.github.zeytx.bankmasker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaskUtils — Programmatic masking")
class MaskUtilsTest {

    @BeforeEach
    void setUp() {
        MaskingConfig.getInstance().reset();
    }

    @AfterEach
    void tearDown() {
        MaskingConfig.getInstance().reset();
    }

    @Test
    @DisplayName("masks credit card via MaskUtils")
    void maskCreditCard() {
        assertEquals("****-****-****-1111", MaskUtils.mask("4111111111111111", MaskType.CREDIT_CARD));
    }

    @Test
    @DisplayName("masks email via MaskUtils")
    void maskEmail() {
        assertEquals("jo****@example.com", MaskUtils.mask("john@example.com", MaskType.EMAIL));
    }

    @Test
    @DisplayName("masks with custom params")
    void maskCustom() {
        assertEquals("AB######IJK", MaskUtils.mask("ABCDEFGHIJK", '#', 2, 3));
    }

    @Test
    @DisplayName("returns null for null input")
    void nullInput() {
        assertNull(MaskUtils.mask(null, MaskType.TOTAL));
    }

    @Test
    @DisplayName("returns empty for empty input")
    void emptyInput() {
        assertEquals("", MaskUtils.mask("", MaskType.TOTAL));
    }

    @Test
    @DisplayName("bypasses masking when disabled")
    void disabledBypass() {
        MaskingConfig.getInstance().setEnabled(false);
        assertEquals("4111111111111111", MaskUtils.mask("4111111111111111", MaskType.CREDIT_CARD));
    }

    @Test
    @DisplayName("custom mask with huge visible window does not overflow")
    void customMaskNoOverflow() {
        assertEquals("######", MaskUtils.mask("ABCDEF", '#', Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("custom mask bypasses when disabled")
    void disabledCustomBypass() {
        MaskingConfig.getInstance().setEnabled(false);
        assertEquals("ABCDEFGHIJK", MaskUtils.mask("ABCDEFGHIJK", '#', 2, 3));
    }

    @Test
    @DisplayName("masks IBAN via MaskUtils")
    void maskIban() {
        assertEquals("ES******************7891", MaskUtils.mask("ES6621000418401234567891", MaskType.IBAN));
    }

    @Test
    @DisplayName("masks SSN via MaskUtils")
    void maskSsn() {
        assertEquals("***-**-6789", MaskUtils.mask("123-45-6789", MaskType.SSN));
    }

    @Test
    @DisplayName("masks NAME via MaskUtils")
    void maskName() {
        assertEquals("J*** D**", MaskUtils.mask("John Doe", MaskType.NAME));
    }

    @Test
    @DisplayName("masks PHONE via MaskUtils")
    void maskPhone() {
        assertEquals("********5678", MaskUtils.mask("+525512345678", MaskType.PHONE));
    }

    @Test
    @DisplayName("useful in toString()")
    void usefulInToString() {
        String card = "4111111111111111";
        String result = "Payment{card=" + MaskUtils.mask(card, MaskType.CREDIT_CARD) + "}";
        assertEquals("Payment{card=****-****-****-1111}", result);
    }

    // --- New mask types ---

    @Test
    @DisplayName("masks PASSPORT via MaskUtils")
    void maskPassport() {
        assertEquals("AB****567", MaskUtils.mask("AB1234567", MaskType.PASSPORT));
    }

    @Test
    @DisplayName("masks BANK_ACCOUNT via MaskUtils")
    void maskBankAccount() {
        assertEquals("**********1234", MaskUtils.mask("12345678901234", MaskType.BANK_ACCOUNT));
    }

    @Test
    @DisplayName("masks IP_ADDRESS via MaskUtils")
    void maskIpAddress() {
        assertEquals("***.***.***.100", MaskUtils.mask("192.168.1.100", MaskType.IP_ADDRESS));
    }

    // --- defaultMaskChar ---

    @Test
    @DisplayName("mask with type respects defaultMaskChar")
    void defaultMaskCharWithType() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("####-####-####-1111", MaskUtils.mask("4111111111111111", MaskType.CREDIT_CARD));
    }

    @Test
    @DisplayName("custom mask with default '*' respects global defaultMaskChar")
    void customMaskRespectsGlobalChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("AB######IJK", MaskUtils.mask("ABCDEFGHIJK", '*', 2, 3));
    }

    @Test
    @DisplayName("custom mask with explicit non-default char ignores global")
    void customMaskExplicitCharIgnoresGlobal() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("AB@@@@@@IJK", MaskUtils.mask("ABCDEFGHIJK", '@', 2, 3));
    }

    // --- Fail-closed: visible window >= length (C1) ---

    @Test
    @DisplayName("value shorter than visible window is fully masked, never leaked")
    void shorterThanWindowIsFullyMasked() {
        assertThat(MaskUtils.mask("ABCD", '#', 2, 3)).isEqualTo("####");
    }

    @Test
    @DisplayName("value with length equal to visible window is fully masked")
    void equalToWindowIsFullyMasked() {
        assertThat(MaskUtils.mask("ABCD", '#', 2, 2)).isEqualTo("####");
    }

    @Test
    @DisplayName("single char with visible window is fully masked")
    void singleCharIsFullyMasked() {
        assertThat(MaskUtils.mask("A", '#', 1, 0)).isEqualTo("#");
    }

    @Test
    @DisplayName("fail-closed mask with default '*' respects global defaultMaskChar")
    void failClosedRespectsGlobalChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertThat(MaskUtils.mask("ABCD", '*', 2, 3)).isEqualTo("####");
    }

    @Test
    @DisplayName("mask with an explicit config ignores the global one")
    void maskWithExplicitConfig() {
        MaskingConfig custom = MaskingConfig.create().setDefaultMaskChar('#');
        assertEquals("####-####-####-1111", MaskUtils.mask("4111111111111111", MaskType.CREDIT_CARD, custom));
        assertEquals("4111111111111111", MaskUtils.mask("4111111111111111", MaskType.CREDIT_CARD,
                MaskingConfig.create().setEnabled(false)));
    }

    record Payment(@MaskData(MaskType.CREDIT_CARD) String card,
                   @MaskData(MaskType.EMAIL) java.util.List<String> emails,
                   @MaskData(MaskType.TOTAL) java.util.Map<String, String> secrets,
                   java.math.BigDecimal amount) {
        @Override
        public String toString() {
            return MaskUtils.toMaskedString(this);
        }
    }

    static class Account {
        @MaskData(MaskType.IBAN)
        private final String iban = "ES6621000418401234567891";
        private final String alias = "main";
        private static final String IGNORED = "static fields are skipped";
    }

    static class SavingsAccount extends Account {
        @MaskData(MaskType.PSEUDONYM)
        private final String customerId = "CUS-1";
    }

    @Test
    @DisplayName("toMaskedString masks annotated record components, containers included")
    void toMaskedStringRecord() {
        Payment payment = new Payment("4111111111111111", java.util.List.of("john.doe@mail.com"),
                new java.util.LinkedHashMap<>(java.util.Map.of("apiKey", "s3cr3t")), new java.math.BigDecimal("10.00"));

        assertEquals("Payment[card=****-****-****-1111, emails=[jo****@mail.com], secrets={apiKey=********}, amount=10.00]",
                payment.toString());
    }

    @Test
    @DisplayName("toMaskedString walks superclass fields first and skips statics")
    void toMaskedStringClassHierarchy() {
        assertEquals("SavingsAccount[iban=ES******************7891, alias=main, customerId=********]",
                MaskUtils.toMaskedString(new SavingsAccount()));
    }

    @Test
    @DisplayName("toMaskedString prints original values when masking is disabled, and null")
    void toMaskedStringDisabledAndNull() {
        MaskingConfig.getInstance().setEnabled(false);
        assertEquals("Payment[card=4111111111111111, emails=[a@b.co], secrets=null, amount=null]",
                MaskUtils.toMaskedString(new Payment("4111111111111111", java.util.List.of("a@b.co"), null, null)));
        assertEquals("null", MaskUtils.toMaskedString(null));
    }
}

