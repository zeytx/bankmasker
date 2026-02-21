package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Java Records support")
class MaskingRecordTest {

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

    // --- Record definitions ---

    record PaymentRecord(
            @MaskData(MaskType.CREDIT_CARD) String cardNumber,
            @MaskData(MaskType.EMAIL) String email,
            String plainField
    ) {}

    record PhoneRecord(
            @MaskData(MaskType.PHONE) String phone
    ) {}

    record FullRecord(
            @MaskData(MaskType.CREDIT_CARD) String card,
            @MaskData(MaskType.EMAIL) String email,
            @MaskData(MaskType.PHONE) String phone,
            @MaskData(MaskType.IBAN) String iban,
            @MaskData(MaskType.SSN) String ssn,
            @MaskData(MaskType.NAME) String name,
            @MaskData(MaskType.DNI) String dni,
            @MaskData(MaskType.PASSPORT) String passport,
            @MaskData(MaskType.BANK_ACCOUNT) String bankAccount,
            @MaskData(MaskType.IP_ADDRESS) String ip,
            @MaskData String secret
    ) {}

    record CustomRecord(
            @MaskData(value = MaskType.CUSTOM, maskChar = '#', visibleStart = 2, visibleEnd = 3) String accountId
    ) {}

    // --- Tests ---

    @Test
    @DisplayName("masks credit card and email in a record")
    void masksPaymentRecord() throws JsonProcessingException {
        var record = new PaymentRecord("4111111111111111", "john@example.com", "visible");
        String json = mapper.writeValueAsString(record);
        assertTrue(json.contains("****-****-****-1111"), "card should be masked");
        assertTrue(json.contains("jo****@example.com"), "email should be masked");
        assertTrue(json.contains("visible"), "plain field should remain");
    }

    @Test
    @DisplayName("masks phone in a single-field record")
    void masksPhoneRecord() throws JsonProcessingException {
        var record = new PhoneRecord("+525512345678");
        String json = mapper.writeValueAsString(record);
        assertTrue(json.contains("********5678"));
    }

    @Test
    @DisplayName("masks all types in a record")
    void masksFullRecord() throws JsonProcessingException {
        var record = new FullRecord(
                "4111111111111111",
                "john@example.com",
                "+525512345678",
                "ES6621000418401234567891",
                "123-45-6789",
                "John Doe",
                "ABCD123456",
                "AB1234567",
                "12345678901234",
                "192.168.1.100",
                "top-secret"
        );
        String json = mapper.writeValueAsString(record);
        assertTrue(json.contains("****-****-****-1111"), "card");
        assertTrue(json.contains("jo****@example.com"), "email");
        assertTrue(json.contains("********5678"), "phone");
        assertTrue(json.contains("ES******************7891"), "iban");
        assertTrue(json.contains("***-**-6789"), "ssn");
        assertTrue(json.contains("J*** D**"), "name");
        assertTrue(json.contains("******3456"), "dni");
        assertTrue(json.contains("AB****567"), "passport");
        assertTrue(json.contains("**********1234"), "bank account");
        assertTrue(json.contains("***.***.***.100"), "ip");
        assertTrue(json.contains("********"), "total");
        assertFalse(json.contains("top-secret"), "secret should be masked");
    }

    @Test
    @DisplayName("applies custom masking on a record field")
    void masksCustomRecord() throws JsonProcessingException {
        var record = new CustomRecord("ABCDEFGHIJK");
        String json = mapper.writeValueAsString(record);
        assertTrue(json.contains("AB######IJK"));
    }

    @Test
    @DisplayName("respects global disable on records")
    void respectsGlobalDisableOnRecords() throws JsonProcessingException {
        MaskingConfig.getInstance().setEnabled(false);
        var record = new PaymentRecord("4111111111111111", "john@example.com", "visible");
        String json = mapper.writeValueAsString(record);
        assertTrue(json.contains("4111111111111111"), "original card should appear");
        assertTrue(json.contains("john@example.com"), "original email should appear");
    }
}

