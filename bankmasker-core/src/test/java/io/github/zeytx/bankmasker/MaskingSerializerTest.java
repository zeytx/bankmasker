package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaskingSerializerTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        MaskingConfig.getInstance().reset();
    }

    @AfterEach
    void tearDown() {
        MaskingConfig.getInstance().reset();
    }

    // --- Test DTOs ---

    static class CreditCardDTO {
        @MaskData(MaskType.CREDIT_CARD)
        public String cardNumber;
        public CreditCardDTO(String cardNumber) { this.cardNumber = cardNumber; }
    }

    static class EmailDTO {
        @MaskData(MaskType.EMAIL)
        public String email;
        public EmailDTO(String email) { this.email = email; }
    }

    static class PhoneDTO {
        @MaskData(MaskType.PHONE)
        public String phone;
        public PhoneDTO(String phone) { this.phone = phone; }
    }

    static class DniDTO {
        @MaskData(MaskType.DNI)
        public String dni;
        public DniDTO(String dni) { this.dni = dni; }
    }

    static class IbanDTO {
        @MaskData(MaskType.IBAN)
        public String iban;
        public IbanDTO(String iban) { this.iban = iban; }
    }

    static class SsnDTO {
        @MaskData(MaskType.SSN)
        public String ssn;
        public SsnDTO(String ssn) { this.ssn = ssn; }
    }

    static class NameDTO {
        @MaskData(MaskType.NAME)
        public String name;
        public NameDTO(String name) { this.name = name; }
    }

    static class TotalDTO {
        @MaskData
        public String secret;
        public TotalDTO(String secret) { this.secret = secret; }
    }

    static class CustomDTO {
        @MaskData(value = MaskType.CUSTOM, maskChar = '#', visibleStart = 2, visibleEnd = 3)
        public String accountId;
        public CustomDTO(String accountId) { this.accountId = accountId; }
    }

    static class CustomDefaultCharDTO {
        @MaskData(value = MaskType.CUSTOM, visibleStart = 2, visibleEnd = 3)
        public String accountId;
        public CustomDefaultCharDTO(String accountId) { this.accountId = accountId; }
    }

    static class NullFieldDTO {
        @MaskData(MaskType.CREDIT_CARD)
        public String cardNumber;
        public NullFieldDTO() { this.cardNumber = null; }
    }

    static class PassportDTO {
        @MaskData(MaskType.PASSPORT)
        public String passport;
        public PassportDTO(String passport) { this.passport = passport; }
    }

    static class BankAccountDTO {
        @MaskData(MaskType.BANK_ACCOUNT)
        public String account;
        public BankAccountDTO(String account) { this.account = account; }
    }

    static class IpAddressDTO {
        @MaskData(MaskType.IP_ADDRESS)
        public String ip;
        public IpAddressDTO(String ip) { this.ip = ip; }
    }

    static class LongCardDTO {
        @MaskData(MaskType.CREDIT_CARD)
        public Long cardNumber;
        public LongCardDTO(Long cardNumber) { this.cardNumber = cardNumber; }
    }

    static class BigDecimalDTO {
        @MaskData
        public java.math.BigDecimal balance;
        public BigDecimalDTO(java.math.BigDecimal balance) { this.balance = balance; }
    }

    static class CharArrayDTO {
        @MaskData(MaskType.CREDIT_CARD)
        public char[] cardNumber;
        public CharArrayDTO(char[] cardNumber) { this.cardNumber = cardNumber; }
    }

    static class EmailListDTO {
        @MaskData(MaskType.EMAIL)
        public List<String> emails;
        public EmailListDTO(List<String> emails) { this.emails = emails; }
    }

    static class CustomListDTO {
        @MaskData(value = MaskType.CUSTOM, maskChar = '#', visibleStart = 2, visibleEnd = 3)
        public List<String> accounts;
        public CustomListDTO(List<String> accounts) { this.accounts = accounts; }
    }

    static class MapDTO {
        @MaskData(MaskType.TOTAL)
        public java.util.Map<String, String> secrets;
        public MapDTO(java.util.Map<String, String> secrets) { this.secrets = secrets; }
    }

    static class ArrayDTO {
        @MaskData(MaskType.CREDIT_CARD)
        public String[] cards;
        public ArrayDTO(String[] cards) { this.cards = cards; }
    }

    // --- Tests ---

    @Nested
    @DisplayName("Credit Card Masking")
    class CreditCardTests {

        @Test
        @DisplayName("masks standard 16-digit card number")
        void masksStandardCard() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(json.contains("****-****-****-1111"));
        }

        @Test
        @DisplayName("masks card number with dashes")
        void masksCardWithDashes() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CreditCardDTO("4111-1111-1111-1234"));
            assertTrue(json.contains("****-****-****-1234"));
        }

        @Test
        @DisplayName("handles short card gracefully")
        void handlesShortCard() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CreditCardDTO("12"));
            assertTrue(json.contains("****"));
        }
    }

    @Nested
    @DisplayName("Email Masking")
    class EmailTests {

        @Test
        @DisplayName("masks standard email")
        void masksStandardEmail() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new EmailDTO("john.doe@example.com"));
            assertTrue(json.contains("jo****@example.com"));
        }

        @Test
        @DisplayName("fully masks local part of 1-2 chars (no leak)")
        void masksShortLocalPart() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new EmailDTO("a@example.com"));
            assertFalse(json.contains("a****@example.com"), "must not expose the full local part");
            assertTrue(json.contains("****@example.com"));
        }

        @Test
        @DisplayName("falls back for invalid email")
        void fallsBackForInvalid() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new EmailDTO("notanemail"));
            assertTrue(json.contains("********"));
        }
    }

    @Nested
    @DisplayName("Phone Masking")
    class PhoneTests {

        @Test
        @DisplayName("masks phone number keeping last 4")
        void masksPhone() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new PhoneDTO("+525512345678"));
            assertTrue(json.contains("********5678"));
        }
    }

    @Nested
    @DisplayName("DNI Masking")
    class DniTests {

        @Test
        @DisplayName("masks DNI keeping last 4")
        void masksDni() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new DniDTO("ABCD123456"));
            assertTrue(json.contains("******3456"));
        }

        @Test
        @DisplayName("handles short DNI")
        void handlesShortDni() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new DniDTO("AB"));
            assertTrue(json.contains("****"));
        }
    }

    @Nested
    @DisplayName("IBAN Masking")
    class IbanTests {

        @Test
        @DisplayName("masks standard IBAN keeping country + last 4")
        void masksStandardIban() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new IbanDTO("ES6621000418401234567891"));
            assertTrue(json.contains("ES******************7891"));
        }

        @Test
        @DisplayName("masks IBAN with spaces")
        void masksIbanWithSpaces() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new IbanDTO("ES66 2100 0418 4012 3456 7891"));
            assertTrue(json.contains("ES******************7891"));
        }

        @Test
        @DisplayName("handles short IBAN")
        void handlesShortIban() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new IbanDTO("ES66"));
            assertTrue(json.contains("****"));
        }
    }

    @Nested
    @DisplayName("SSN Masking")
    class SsnTests {

        @Test
        @DisplayName("masks SSN keeping last 4")
        void masksStandardSsn() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new SsnDTO("123-45-6789"));
            assertTrue(json.contains("***-**-6789"));
        }

        @Test
        @DisplayName("masks SSN without dashes")
        void masksSsnWithoutDashes() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new SsnDTO("123456789"));
            assertTrue(json.contains("***-**-6789"));
        }
    }

    @Nested
    @DisplayName("Name Masking")
    class NameTests {

        @Test
        @DisplayName("masks full name keeping first letter of each word")
        void masksFullName() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new NameDTO("John Doe"));
            assertTrue(json.contains("J*** D**"));
        }

        @Test
        @DisplayName("masks single name")
        void masksSingleName() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new NameDTO("Alice"));
            assertTrue(json.contains("A****"));
        }

        @Test
        @DisplayName("masks three-part name")
        void masksThreePartName() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new NameDTO("Maria del Carmen"));
            assertTrue(json.contains("M**** d** C*****"));
        }
    }

    @Nested
    @DisplayName("Total Masking")
    class TotalTests {

        @Test
        @DisplayName("replaces entire value")
        void masksTotally() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new TotalDTO("super-secret"));
            assertTrue(json.contains("********"));
        }
    }

    @Nested
    @DisplayName("Custom Masking")
    class CustomTests {

        @Test
        @DisplayName("applies custom mask with visible start and end")
        void appliesCustomMask() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CustomDTO("ABCDEFGHIJK"));
            assertTrue(json.contains("AB######IJK"));
        }

        @Test
        @DisplayName("fully masks value when visible window >= length (fail-closed)")
        void masksFullyIfWindowCoversValue() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CustomDTO("AB"));
            assertFalse(json.contains("AB"), "must not leak the original value");
            assertTrue(json.contains("##"));
        }
    }

    @Nested
    @DisplayName("Non-String field types")
    class NonStringTests {

        @Test
        @DisplayName("masks a Long card number as masked string")
        void masksLong() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new LongCardDTO(4111111111111111L));
            assertEquals("{\"cardNumber\":\"****-****-****-1111\"}", json);
        }

        @Test
        @DisplayName("masks a BigDecimal with TOTAL")
        void masksBigDecimal() throws JsonProcessingException {
            String json = mapper.writeValueAsString(
                    new BigDecimalDTO(new java.math.BigDecimal("12345.67")));
            assertEquals("{\"balance\":\"********\"}", json);
        }

        @Test
        @DisplayName("masks char[] content, not its Object toString")
        void masksCharArray() throws JsonProcessingException {
            String json = mapper.writeValueAsString(
                    new CharArrayDTO("4111111111111111".toCharArray()));
            assertEquals("{\"cardNumber\":\"****-****-****-1111\"}", json);
        }

        @Test
        @DisplayName("when disabled, a Number keeps its JSON numeric type")
        void disabledKeepsNumericType() throws JsonProcessingException {
            MaskingConfig.getInstance().setEnabled(false);
            String json = mapper.writeValueAsString(new LongCardDTO(4111111111111111L));
            assertEquals("{\"cardNumber\":4111111111111111}", json);
        }

        @Test
        @DisplayName("when disabled, char[] is written as its string content")
        void disabledCharArrayAsString() throws JsonProcessingException {
            MaskingConfig.getInstance().setEnabled(false);
            String json = mapper.writeValueAsString(new CharArrayDTO("1234".toCharArray()));
            assertEquals("{\"cardNumber\":\"1234\"}", json);
        }
    }

    @Nested
    @DisplayName("Collections, Maps & Arrays")
    class ContainerTests {

        @Test
        @DisplayName("masks each element of a List, keeping the JSON array shape")
        void masksListElements() throws JsonProcessingException {
            String json = mapper.writeValueAsString(
                    new EmailListDTO(List.of("john.doe@mail.com", "jane.roe@mail.com")));
            assertEquals("{\"emails\":[\"jo****@mail.com\",\"ja****@mail.com\"]}", json);
        }

        @Test
        @DisplayName("CUSTOM over a List does not leak the collection toString")
        void customListDoesNotLeakToString() throws JsonProcessingException {
            String json = mapper.writeValueAsString(
                    new CustomListDTO(List.of("ABCDEFGHIJK")));
            assertFalse(json.contains("[A"), "must not expose '[' + first chars of toString");
            assertEquals("{\"accounts\":[\"AB######IJK\"]}", json);
        }

        @Test
        @DisplayName("masks Map values, keeps keys visible and object shape")
        void masksMapValues() throws JsonProcessingException {
            String json = mapper.writeValueAsString(
                    new MapDTO(new java.util.LinkedHashMap<>(java.util.Map.of("apiKey", "super-secret"))));
            assertEquals("{\"secrets\":{\"apiKey\":\"********\"}}", json);
        }

        @Test
        @DisplayName("masks array elements")
        void masksArrayElements() throws JsonProcessingException {
            String json = mapper.writeValueAsString(
                    new ArrayDTO(new String[]{"4111111111111111", "5500000000000004"}));
            assertEquals("{\"cards\":[\"****-****-****-1111\",\"****-****-****-0004\"]}", json);
        }

        @Test
        @DisplayName("null elements are written as null")
        void nullElements() throws JsonProcessingException {
            List<String> withNull = new ArrayList<>();
            withNull.add("john.doe@mail.com");
            withNull.add(null);
            String json = mapper.writeValueAsString(new EmailListDTO(withNull));
            assertEquals("{\"emails\":[\"jo****@mail.com\",null]}", json);
        }

        @Test
        @DisplayName("when disabled, serializes the original collection unmasked")
        void disabledKeepsOriginalCollection() throws JsonProcessingException {
            MaskingConfig.getInstance().setEnabled(false);
            String json = mapper.writeValueAsString(
                    new EmailListDTO(List.of("john.doe@mail.com")));
            assertEquals("{\"emails\":[\"john.doe@mail.com\"]}", json);
        }

        @Test
        @DisplayName("audit logger fires once per masked container field")
        void auditOncePerContainerField() throws JsonProcessingException {
            List<String> auditLog = new ArrayList<>();
            MaskingConfig.getInstance().setAuditLogger((field, type) ->
                    auditLog.add(field + ":" + type.name()));
            mapper.writeValueAsString(new EmailListDTO(List.of("a@b.com", "c@d.com")));
            assertEquals(List.of("emails:EMAIL"), auditLog);
        }
    }

    @Nested
    @DisplayName("Null & Empty handling")
    class NullEmptyTests {

        @Test
        @DisplayName("writes null for null value")
        void handlesNull() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new NullFieldDTO());
            assertTrue(json.contains("null"));
        }

        @Test
        @DisplayName("writes empty string as-is")
        void handlesEmpty() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CreditCardDTO(""));
            assertTrue(json.contains("\"\""));
        }
    }

    @Nested
    @DisplayName("MaskingConfig — Global Configuration")
    class ConfigTests {

        @Test
        @DisplayName("when disabled, serializes original value")
        void disabledBypassesMasking() throws JsonProcessingException {
            MaskingConfig.getInstance().setEnabled(false);
            String json = mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(json.contains("4111111111111111"));
        }

        @Test
        @DisplayName("when re-enabled, masks again")
        void reEnabledMasksAgain() throws JsonProcessingException {
            MaskingConfig.getInstance().setEnabled(false);
            MaskingConfig.getInstance().setEnabled(true);
            String json = mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(json.contains("****-****-****-1111"));
        }
    }

    @Nested
    @DisplayName("MaskingAuditLogger")
    class AuditTests {

        @Test
        @DisplayName("audit logger is called when field is masked")
        void auditLoggerIsCalled() throws JsonProcessingException {
            List<String> auditLog = new ArrayList<>();
            MaskingConfig.getInstance().setAuditLogger((field, type) ->
                    auditLog.add(field + ":" + type.name()));

            mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));

            assertEquals(1, auditLog.size());
            assertEquals("cardNumber:CREDIT_CARD", auditLog.get(0));
        }

        @Test
        @DisplayName("audit logger is NOT called when masking is disabled")
        void auditNotCalledWhenDisabled() throws JsonProcessingException {
            List<String> auditLog = new ArrayList<>();
            MaskingConfig.getInstance()
                    .setEnabled(false)
                    .setAuditLogger((field, type) -> auditLog.add(field));

            mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));

            assertTrue(auditLog.isEmpty());
        }

        @Test
        @DisplayName("audit logger captures multiple fields")
        void auditCapturesMultipleFields() throws JsonProcessingException {
            List<String> auditLog = new ArrayList<>();
            MaskingConfig.getInstance().setAuditLogger((field, type) ->
                    auditLog.add(field + ":" + type.name()));

            // Serialize two different DTOs
            mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            mapper.writeValueAsString(new EmailDTO("john@mail.com"));

            assertEquals(2, auditLog.size());
            assertEquals("cardNumber:CREDIT_CARD", auditLog.get(0));
            assertEquals("email:EMAIL", auditLog.get(1));
        }
    }

    @Nested
    @DisplayName("Passport Masking")
    class PassportTests {

        @Test
        @DisplayName("masks standard passport number")
        void masksStandardPassport() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new PassportDTO("AB1234567"));
            assertTrue(json.contains("AB****567"));
        }

        @Test
        @DisplayName("handles short passport gracefully")
        void handlesShortPassport() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new PassportDTO("ABCDE"));
            assertTrue(json.contains("****"));
        }
    }

    @Nested
    @DisplayName("Bank Account Masking")
    class BankAccountTests {

        @Test
        @DisplayName("masks standard bank account")
        void masksStandardBankAccount() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new BankAccountDTO("12345678901234"));
            assertTrue(json.contains("**********1234"));
        }

        @Test
        @DisplayName("handles short bank account")
        void handlesShortBankAccount() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new BankAccountDTO("1234"));
            assertTrue(json.contains("****"));
        }
    }

    @Nested
    @DisplayName("IP Address Masking")
    class IpAddressTests {

        @Test
        @DisplayName("masks standard IPv4 address")
        void masksStandardIp() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new IpAddressDTO("192.168.1.100"));
            assertTrue(json.contains("***.***.***.100"));
        }

        @Test
        @DisplayName("handles IP without dots")
        void handlesIpWithoutDots() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new IpAddressDTO("localhost"));
            assertTrue(json.contains("********"));
        }
    }

    @Nested
    @DisplayName("defaultMaskChar integration")
    class DefaultMaskCharTests {

        @Test
        @DisplayName("serializer respects global defaultMaskChar")
        void serializerRespectsDefaultMaskChar() throws JsonProcessingException {
            MaskingConfig.getInstance().setDefaultMaskChar('#');
            String json = mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(json.contains("####-####-####-1111"));
        }

        @Test
        @DisplayName("email masking respects defaultMaskChar")
        void emailRespectsDefaultMaskChar() throws JsonProcessingException {
            MaskingConfig.getInstance().setDefaultMaskChar('#');
            String json = mapper.writeValueAsString(new EmailDTO("john@mail.com"));
            assertTrue(json.contains("jo####@mail.com"));
        }

        @Test
        @DisplayName("total masking respects defaultMaskChar")
        void totalRespectsDefaultMaskChar() throws JsonProcessingException {
            MaskingConfig.getInstance().setDefaultMaskChar('#');
            String json = mapper.writeValueAsString(new TotalDTO("secret"));
            assertTrue(json.contains("########"));
        }
    }

    @Nested
    @DisplayName("MaskingModule — per-ObjectMapper config")
    class MaskingModuleTests {

        @Test
        @DisplayName("per-mapper config overrides global singleton")
        void perMapperOverridesGlobal() throws JsonProcessingException {
            MaskingConfig perMapper = MaskingConfig.create()
                    .setEnabled(false);

            ObjectMapper customMapper = new ObjectMapper();
            customMapper.registerModule(new MaskingModule(perMapper));

            // Global is enabled, per-mapper is disabled
            MaskingConfig.getInstance().setEnabled(true);
            String json = customMapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(json.contains("4111111111111111"), "per-mapper disabled should bypass masking");

            // Global mapper still masks
            String globalJson = mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(globalJson.contains("****-****-****-1111"), "global should still mask");
        }

        @Test
        @DisplayName("MaskingModule rejects null config")
        void rejectsNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> new MaskingModule(null));
        }

        @Test
        @DisplayName("per-mapper defaultMaskChar applies to built-in types")
        void perMapperMaskCharBuiltIn() throws JsonProcessingException {
            ObjectMapper customMapper = new ObjectMapper();
            customMapper.registerModule(new MaskingModule(
                    MaskingConfig.create().setDefaultMaskChar('#')));

            String json = customMapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(json.contains("####-####-####-1111"), "per-mapper maskChar must apply, got: " + json);

            // Global mapper keeps '*'
            String globalJson = mapper.writeValueAsString(new CreditCardDTO("4111111111111111"));
            assertTrue(globalJson.contains("****-****-****-1111"));
        }

        @Test
        @DisplayName("per-mapper defaultMaskChar applies to TOTAL")
        void perMapperMaskCharTotal() throws JsonProcessingException {
            ObjectMapper customMapper = new ObjectMapper();
            customMapper.registerModule(new MaskingModule(
                    MaskingConfig.create().setDefaultMaskChar('#')));

            String json = customMapper.writeValueAsString(new TotalDTO("secret"));
            assertTrue(json.contains("########"), "got: " + json);
        }

        @Test
        @DisplayName("per-mapper defaultMaskChar applies to CUSTOM with default char")
        void perMapperMaskCharCustom() throws JsonProcessingException {
            ObjectMapper customMapper = new ObjectMapper();
            customMapper.registerModule(new MaskingModule(
                    MaskingConfig.create().setDefaultMaskChar('#')));

            String json = customMapper.writeValueAsString(new CustomDefaultCharDTO("ABCDEFGHIJK"));
            assertTrue(json.contains("AB######IJK"), "got: " + json);
        }

        @Test
        @DisplayName("custom MaskingStrategy lambdas keep working (backward compat)")
        void customStrategyBackwardCompat() {
            MaskingStrategy custom = v -> "XXX";
            assertEquals("XXX", custom.mask("secret"));
            assertEquals("XXX", custom.mask("secret", MaskingConfig.create()));
        }
    }
}

