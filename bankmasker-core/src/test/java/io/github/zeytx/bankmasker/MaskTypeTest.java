package io.github.zeytx.bankmasker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaskType strategies")
class MaskTypeTest {

    @BeforeEach
    void setUp() {
        MaskingConfig.getInstance().reset();
    }

    @AfterEach
    void tearDown() {
        MaskingConfig.getInstance().reset();
    }

    @ParameterizedTest
    @DisplayName("CREDIT_CARD masks correctly")
    @CsvSource({
            "4111111111111111, ****-****-****-1111",
            "4111-1111-1111-5678, ****-****-****-5678",
            "12345678, ****-****-****-5678",
            "1234567, ****",
            "12345, ****",
            "12, ****"
    })
    void creditCard(String input, String expected) {
        assertEquals(expected, MaskType.CREDIT_CARD.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("EMAIL masks correctly")
    @CsvSource({
            "john@mail.com, jo****@mail.com",
            "a@mail.com, ****@mail.com",
            "ab@mail.com, ****@mail.com",
            "abc@mail.com, a****@mail.com",
            "abcd@mail.com, ab****@mail.com",
            "ab@evil@real.com, ab****@real.com",
            "@mail.com, ********",
            "notanemail, ********"
    })
    void email(String input, String expected) {
        assertEquals(expected, MaskType.EMAIL.getStrategy().mask(input));
    }

    @Test
    @DisplayName("PHONE masks correctly")
    void phone() {
        assertEquals("********5678", MaskType.PHONE.getStrategy().mask("+525512345678"));
    }

    @Test
    @DisplayName("DNI masks correctly")
    void dni() {
        assertEquals("******3456", MaskType.DNI.getStrategy().mask("ABCD123456"));
    }

    @ParameterizedTest
    @DisplayName("last-4 types fully mask values where visible part >= hidden part")
    @CsvSource({
            "PHONE, 5551234, ****",
            "PHONE, 55512345, ****2345",
            "DNI, ABC1234, ****",
            "DNI, ABCD1234, ****1234",
            "BANK_ACCOUNT, 1234567, ****",
            "SSN, 1234567, ***-**-****",
            "IBAN, ES66210004, ****",
            "IBAN, ES6621000418, ES******0418"
    })
    void shortValuesFailClosed(MaskType type, String input, String expected) {
        assertEquals(expected, type.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("IBAN masks correctly")
    @CsvSource({
            "ES6621000418401234567891, ES******************7891",
            "DE89370400440532013000, DE****************3000"
    })
    void iban(String input, String expected) {
        assertEquals(expected, MaskType.IBAN.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("SSN masks correctly")
    @CsvSource({
            "123-45-6789, ***-**-6789",
            "123456789, ***-**-6789"
    })
    void ssn(String input, String expected) {
        assertEquals(expected, MaskType.SSN.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("NAME masks correctly")
    @CsvSource({
            "John Doe, J*** D**",
            "Alice, A****",
            "Maria del Carmen, M**** d** C*****"
    })
    void name(String input, String expected) {
        assertEquals(expected, MaskType.NAME.getStrategy().mask(input));
    }

    @Test
    @DisplayName("TOTAL replaces everything")
    void total() {
        assertEquals("********", MaskType.TOTAL.getStrategy().mask("anything"));
    }

    // --- New mask types ---

    @ParameterizedTest
    @DisplayName("PASSPORT masks correctly")
    @CsvSource({
            "AB1234567, AB****567",
            "X12345678901, X1*******901",
            "ABCDE, ****",
            "ABCDEF, ****"
    })
    void passport(String input, String expected) {
        assertEquals(expected, MaskType.PASSPORT.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("BANK_ACCOUNT masks correctly")
    @CsvSource({
            "12345678901234, **********1234",
            "1234-5678-9012-3456, ************3456",
            "1234, ****"
    })
    void bankAccount(String input, String expected) {
        assertEquals(expected, MaskType.BANK_ACCOUNT.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("IP_ADDRESS masks correctly")
    @CsvSource({
            "192.168.1.100, ***.***.***.100",
            "10.0.0.1, ***.***.***.1",
            "255.255.255.0, ***.***.***.0"
    })
    void ipAddress(String input, String expected) {
        assertEquals(expected, MaskType.IP_ADDRESS.getStrategy().mask(input));
    }

    @Test
    @DisplayName("IP_ADDRESS without dots falls back to total mask")
    void ipAddressNoDots() {
        assertEquals("********", MaskType.IP_ADDRESS.getStrategy().mask("noperiod"));
    }

    // --- AUTO ---

    @ParameterizedTest
    @DisplayName("AUTO detects the format and applies the matching strategy")
    @CsvSource({
            "4111111111111111, ****-****-****-1111",
            "4111-1111-1111-1111, ****-****-****-1111",
            "'4111 1111 1111 1111', ****-****-****-1111",
            "john.doe@mail.com, jo****@mail.com",
            "ES6621000418401234567891, ES******************7891",
            "just some text, ********",
            "1234567890123, ********"
    })
    void auto(String input, String expected) {
        assertEquals(expected, MaskType.AUTO.getStrategy().mask(input));
    }

    @Test
    @DisplayName("AUTO respects defaultMaskChar")
    void autoCustomChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("####-####-####-1111", MaskType.AUTO.getStrategy().mask("4111111111111111"));
        assertEquals("########", MaskType.AUTO.getStrategy().mask("unknown format"));
    }

    // --- NAME edge cases ---

    @Test
    @DisplayName("NAME with leading/trailing whitespace has no stray spaces")
    void nameLeadingWhitespace() {
        assertEquals("J*** D**", MaskType.NAME.getStrategy().mask("  John Doe  "));
    }

    // --- defaultMaskChar ---

    @Test
    @DisplayName("CREDIT_CARD respects defaultMaskChar")
    void creditCardCustomChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("####-####-####-1111", MaskType.CREDIT_CARD.getStrategy().mask("4111111111111111"));
    }

    @Test
    @DisplayName("EMAIL respects defaultMaskChar")
    void emailCustomChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("jo####@mail.com", MaskType.EMAIL.getStrategy().mask("john@mail.com"));
    }

    @Test
    @DisplayName("TOTAL respects defaultMaskChar")
    void totalCustomChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("########", MaskType.TOTAL.getStrategy().mask("anything"));
    }

    @Test
    @DisplayName("NAME respects defaultMaskChar")
    void nameCustomChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("J### D##", MaskType.NAME.getStrategy().mask("John Doe"));
    }

    @Test
    @DisplayName("SSN respects defaultMaskChar")
    void ssnCustomChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertEquals("###-##-6789", MaskType.SSN.getStrategy().mask("123-45-6789"));
    }

    @ParameterizedTest
    @DisplayName("AUTO also detects SSNs, national IDs and phone numbers")
    @CsvSource({
            "123-45-6789, ***-**-6789",
            "12345678Z, *****678Z",
            "529.982.247-25, **********7-25",
            "'+52 55 1234 5678', ********5678",
            "'(555) 123-4567', ******4567",
            "5551234567, ********",
            "000-12-3456, ********",
            "12345678A, ********"
    })
    void autoExtendedFormats(String input, String expected) {
        assertEquals(expected, MaskType.AUTO.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("IP_ADDRESS masks IPv6 groups but the last one")
    @CsvSource({
            "2001:db8::8a2e:7334, ****:****::****:7334",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334, ****:****:****:****:****:****:****:7334",
            "::ffff:192.168.1.100, ***.***.***.100",
            "fe80::1, ****::1"
    })
    void ipv6(String input, String expected) {
        assertEquals(expected, MaskType.IP_ADDRESS.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("CREDIT_CARD_BIN keeps the BIN and last 4 digits")
    @CsvSource({
            "4111111111111111, 411111******1111",
            "'4111 1111 1111 1111', 411111******1111",
            "378282246310005, 378282*****0005",
            "4111111111111, 411111***1111",
            "411111111111, ****"
    })
    void creditCardBin(String input, String expected) {
        assertEquals(expected, MaskType.CREDIT_CARD_BIN.getStrategy().mask(input));
    }

    @ParameterizedTest
    @DisplayName("BIRTH_DATE keeps only the year")
    @CsvSource({
            "1990-05-12, 1990-**-**",
            "12/05/1990, **/**/1990",
            "'May 12, 1990', '*** **, 1990'",
            "1990-05-12T10:15:30, 1990-**-**T**:**:**",
            "19900512, ********",
            "unknown, ********"
    })
    void birthDate(String input, String expected) {
        assertEquals(expected, MaskType.BIRTH_DATE.getStrategy().mask(input));
    }

    @Test
    @DisplayName("PSEUDONYM is fully masked without a key")
    void pseudonymWithoutKey() {
        assertEquals("********", MaskType.PSEUDONYM.getStrategy().mask("CUS-123456"));
    }

    @Test
    @DisplayName("PSEUDONYM is a stable, key-dependent HMAC")
    void pseudonymWithKey() {
        MaskingConfig a = MaskingConfig.create().setPseudonymKey("0123456789abcdef".getBytes());
        MaskingConfig b = MaskingConfig.create().setPseudonymKey("fedcba9876543210".getBytes());
        String first = MaskType.PSEUDONYM.getStrategy().mask("CUS-123456", a);

        assertTrue(first.matches("[0-9a-f]{16}"), first);
        assertEquals(first, MaskType.PSEUDONYM.getStrategy().mask("CUS-123456", a));
        assertNotEquals(first, MaskType.PSEUDONYM.getStrategy().mask("CUS-123457", a));
        assertNotEquals(first, MaskType.PSEUDONYM.getStrategy().mask("CUS-123456", b));
    }

    @Test
    @DisplayName("pseudonym key must have at least 128 bits and is copied")
    void pseudonymKeyValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> MaskingConfig.create().setPseudonymKey("too-short".getBytes()));

        byte[] key = "0123456789abcdef".getBytes();
        MaskingConfig config = MaskingConfig.create().setPseudonymKey(key);
        String before = MaskType.PSEUDONYM.getStrategy().mask("v", config);
        key[0] = 'X';
        assertEquals(before, MaskType.PSEUDONYM.getStrategy().mask("v", config));
    }

    @ParameterizedTest
    @DisplayName("defaultMaskChar rejects quotes and backslashes")
    @CsvSource({"34", "92"})
    void rejectsDelimiterMaskChar(int codeUnit) {
        assertThrows(IllegalArgumentException.class, () -> MaskingConfig.create().setDefaultMaskChar((char) codeUnit));
    }

    @ParameterizedTest
    @DisplayName("defaultMaskChar rejects control characters and surrogates")
    @CsvSource({"10", "13", "0", "55357"})
    void rejectsUnsafeMaskChar(int codeUnit) {
        MaskingConfig config = MaskingConfig.create();
        assertThrows(IllegalArgumentException.class, () -> config.setDefaultMaskChar((char) codeUnit));
        assertEquals('*', config.getDefaultMaskChar());
    }
}
