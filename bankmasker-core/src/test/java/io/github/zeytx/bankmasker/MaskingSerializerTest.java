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
        @DisplayName("masks short local part")
        void masksShortLocalPart() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new EmailDTO("a@example.com"));
            assertTrue(json.contains("a****@example.com"));
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
        @DisplayName("returns original if visible >= length")
        void returnsOriginalIfAllVisible() throws JsonProcessingException {
            String json = mapper.writeValueAsString(new CustomDTO("AB"));
            assertTrue(json.contains("AB"));
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
    }
}

