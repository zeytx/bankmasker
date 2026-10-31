package io.github.zeytx.bankmasker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskPatterns — sensitive data detection")
class MaskPatternsTest {

    @ParameterizedTest
    @DisplayName("char filters match the regex they replace")
    @ValueSource(strings = {
            "4111-1111 1111\t1111",
            "+52 (55) 1234-5678",
            "ES66 2100\n0418\u000B4012\f3456\r7891",
            "١٢٣abc x",   // non-ASCII digits and NBSP are not \d / \s
            ""
    })
    void filtersMatchRegex(String value) {
        assertThat(MaskPatterns.digitsOnly(value)).isEqualTo(value.replaceAll("\\D", ""));
        assertThat(MaskPatterns.withoutWhitespace(value)).isEqualTo(value.replaceAll("\\s", ""));
    }

    @ParameterizedTest
    @DisplayName("detects valid card numbers (13-19 digits, Luhn)")
    @ValueSource(strings = {
            "4111111111111111",      // Visa 16
            "378282246310005",       // Amex 15
            "30569309025904",        // Diners 14
            "4222222222222",         // Visa 13
            "4111 1111 1111 1111",   // with spaces
            "4111-1111-1111-1111"    // with dashes
    })
    void detectsValidCards(String value) {
        assertThat(MaskPatterns.isCreditCard(value)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejects non-card values")
    @ValueSource(strings = {
            "1234567890123456",      // 16 digits, Luhn fails
            "411111111111",          // 12 digits, too short
            "41111111111111111111",  // 20 digits, too long
            "not-a-card",
            "4111x1111x1111x1111"
    })
    void rejectsInvalidCards(String value) {
        assertThat(MaskPatterns.isCreditCard(value)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("detects emails")
    @ValueSource(strings = {"john@mail.com", "a.b+c@sub.domain.org"})
    void detectsEmails(String value) {
        assertThat(MaskPatterns.isEmail(value)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejects non-emails")
    @ValueSource(strings = {"notanemail", "a@b", "@mail.com", "a@@mail.com"})
    void rejectsNonEmails(String value) {
        assertThat(MaskPatterns.isEmail(value)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("detects IBANs (with and without spaces)")
    @ValueSource(strings = {
            "ES6621000418401234567891",
            "ES66 2100 0418 4012 3456 7891",
            "DE89370400440532013000",
            "GB29NWBK60161331926819"
    })
    void detectsIbans(String value) {
        assertThat(MaskPatterns.isIban(value)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejects non-IBANs")
    @ValueSource(strings = {"ES66", "1234567890123456", "es6621000418401234567891"})
    void rejectsNonIbans(String value) {
        assertThat(MaskPatterns.isIban(value)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("Luhn validates known good numbers")
    @ValueSource(strings = {"4111111111111111", "378282246310005", "79927398713"})
    void luhnValid(String digits) {
        assertThat(MaskPatterns.passesLuhn(digits)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("Luhn rejects bad input")
    @ValueSource(strings = {"4111111111111112", "79927398710", "abc", ""})
    void luhnInvalid(String digits) {
        assertThat(MaskPatterns.passesLuhn(digits)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("detects assignable SSNs in the dashed format")
    @ValueSource(strings = {"123-45-6789", "001-01-0001", "899-99-9999"})
    void ssnValid(String value) {
        assertThat(MaskPatterns.isSsn(value)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejects unassignable or undashed SSNs")
    @ValueSource(strings = {"000-12-3456", "666-12-3456", "900-12-3456", "123-00-6789", "123-45-0000",
            "123456789", "123-456-789"})
    void ssnInvalid(String value) {
        assertThat(MaskPatterns.isSsn(value)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("detects international and separated local phone numbers")
    @ValueSource(strings = {"+525512345678", "+52 55 1234 5678", "+1 (555) 123-4567", "(555) 123-4567",
            "555-123-4567", "+34 912 345 678"})
    void phoneValid(String value) {
        assertThat(MaskPatterns.isPhone(value)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("bare digit runs, dates, SSNs and IPs are not phone numbers")
    @ValueSource(strings = {"5551234567", "2024-10-30", "123-45-6789", "192.168.1.100", "+123", "+1234567890123456"})
    void phoneInvalid(String value) {
        assertThat(MaskPatterns.isPhone(value)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("detects national IDs with a valid check digit")
    @ValueSource(strings = {
            "12345678Z", "12345678-Z",            // Spain DNI
            "X1234567L", "X-1234567-L",           // Spain NIE
            "529.982.247-25",                     // Brazil CPF
            "12.345.678-5",                       // Chile RUT
            "20-12345678-6",                      // Argentina CUIT/CUIL
            "GOMA850512HDFRRN07"                  // Mexico CURP
    })
    void nationalIdValid(String value) {
        assertThat(MaskPatterns.isNationalId(value)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("rejects national IDs with a wrong check digit or format")
    @ValueSource(strings = {"12345678A", "X1234567A", "529.982.247-26", "111.111.111-11", "12.345.678-K",
            "20-12345678-5", "GOMA850512HDFRRN08", "GOMA851312HDFRRN07", "12345678", "529982247-25"})
    void nationalIdInvalid(String value) {
        assertThat(MaskPatterns.isNationalId(value)).isFalse();
    }
}
