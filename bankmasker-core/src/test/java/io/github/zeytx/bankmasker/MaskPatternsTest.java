package io.github.zeytx.bankmasker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskPatterns — sensitive data detection")
class MaskPatternsTest {

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
}
