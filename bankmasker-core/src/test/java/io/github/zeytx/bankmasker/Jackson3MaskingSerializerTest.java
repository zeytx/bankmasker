package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zeytx.bankmasker.MaskingSerializerTest.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.cfg.ContextAttributes;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.std.StdSerializer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Jackson 3 (tools.jackson) support")
class Jackson3MaskingSerializerTest {

    private final ObjectMapper jackson2 = new ObjectMapper();
    private final JsonMapper jackson3 = JsonMapper.builder().build();

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    static Stream<Arguments> samples() {
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        balances.put("1234567812341111", BigDecimal.ONE);
        balances.put("8765432187651111", BigDecimal.TEN);
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("apiKey", "super-secret");
        List<String> withNull = new ArrayList<>();
        withNull.add("john.doe@mail.com");
        withNull.add(null);

        Object[] dtos = {
                new CreditCardDTO("4111111111111111"), new CreditCardDTO("12"),
                new EmailDTO("john.doe@example.com"), new EmailDTO("notanemail"),
                new PhoneDTO("+525512345678"), new DniDTO("ABCD123456"),
                new IbanDTO("ES66 2100 0418 4012 3456 7891"), new SsnDTO("123-45-6789"),
                new NameDTO("Maria del Carmen"), new TotalDTO("secret"),
                new CustomDTO("ABCDEFGHIJK"), new CustomDefaultCharDTO("ABCDEFGHIJK"),
                new NullFieldDTO(), new PassportDTO("AB1234567"),
                new BankAccountDTO("12345678901234"), new IpAddressDTO("192.168.1.100"),
                new LongCardDTO(4111111111111111L), new BigDecimalDTO(new BigDecimal("12345.67")),
                new CharArrayDTO("4111111111111111".toCharArray()),
                new EmailListDTO(withNull), new CustomListDTO(List.of("ABCDEFGHIJK")),
                new MapDTO(secrets), new ArrayDTO(new String[]{"4111111111111111", "5500000000000004"}),
                new BalanceByCardDTO(balances),
                new PojoEmailDTO(new Holder("Bob", "bob@mail.com", "123-45-6789")),
                new PojoListDTO(List.of(new Holder("Bob", "bob@mail.com", "123-45-6789"))),
                new OptionalCardDTO(Optional.of("4111111111111111")), new OptionalCardDTO(Optional.empty()),
                new CharArrayListDTO(List.<char[]>of("John".toCharArray())),
                new NonEmptyDTO("", List.of()), new NonEmptyDTO("john.doe@mail.com", List.of("ab@x.com")),
        };
        Map<String, Consumer<MaskingConfig>> configs = new LinkedHashMap<>();
        configs.put("defaults", c -> { });
        configs.put("maskChar #", c -> c.setDefaultMaskChar('#'));
        configs.put("disabled", c -> c.setEnabled(false));

        return configs.entrySet().stream().flatMap(config -> Stream.of(dtos)
                .map(dto -> Arguments.of(config.getKey(), dto.getClass().getSimpleName(), dto, config.getValue())));
    }

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("samples")
    @DisplayName("produces the same JSON as Jackson 2")
    void parityWithJackson2(String configName, String dtoName, Object dto, Consumer<MaskingConfig> setup)
            throws Exception {
        setup.accept(MaskingConfig.getInstance());

        String expected = jackson2.writeValueAsString(dto);
        String actual = jackson3.writeValueAsString(dto);

        // Tree comparison: Jackson 3 sorts properties alphabetically by default
        assertEquals(jackson2.readTree(expected), jackson2.readTree(actual),
                () -> "Jackson 2: " + expected + "\nJackson 3: " + actual);
    }

    @Test
    @DisplayName("masks with no module registered")
    void masksWithoutModule() {
        assertEquals("{\"cardNumber\":\"****-****-****-1111\"}",
                jackson3.writeValueAsString(new CreditCardDTO("4111111111111111")));
    }

    @Test
    @DisplayName("masks record components")
    void masksRecords() {
        String json = jackson3.writeValueAsString(
                new MaskingRecordTest.PaymentRecord("4111111111111111", "john@example.com", "visible"));
        assertTrue(json.contains("****-****-****-1111") && json.contains("jo****@example.com")
                && json.contains("visible"), json);
        assertFalse(json.contains("4111111111111111"), json);
    }

    @Test
    @DisplayName("notifies the audit logger")
    void auditsMaskedFields() {
        List<String> audited = new ArrayList<>();
        MaskingConfig.getInstance().setAuditLogger((field, type) -> audited.add(field + ":" + type));

        jackson3.writeValueAsString(new CreditCardDTO("4111111111111111"));

        assertEquals(List.of("cardNumber:CREDIT_CARD"), audited);
    }

    @Test
    @DisplayName("keyMask with more than one type is rejected")
    void rejectsSeveralKeyMasks() {
        assertThrows(DatabindException.class, () -> jackson3.writeValueAsString(new TwoKeyMasksDTO()));
    }

    /** Writes the "probe" attribute left by a previous call, then sets its own. */
    static class ProbeSerializer extends StdSerializer<String> {
        ProbeSerializer() {
            super(String.class);
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
            Object previous = ctxt.getAttribute("probe");
            ctxt.setAttribute("probe", value);
            gen.writeString(String.valueOf(previous));
        }
    }

    record ProbeDTO(@JsonSerialize(using = ProbeSerializer.class) String value) {}

    @Nested
    @DisplayName("Jackson3MaskingModule — per-mapper config")
    class ModuleTests {

        @Test
        @DisplayName("per-mapper config overrides the global one")
        void perMapperConfig() {
            JsonMapper custom = JsonMapper.builder()
                    .addModule(new Jackson3MaskingModule(MaskingConfig.create().setDefaultMaskChar('#')))
                    .build();

            assertEquals("{\"cardNumber\":\"####-####-####-1111\"}",
                    custom.writeValueAsString(new CreditCardDTO("4111111111111111")));
            assertEquals("{\"cardNumber\":\"****-****-****-1111\"}",
                    jackson3.writeValueAsString(new CreditCardDTO("4111111111111111")));
        }

        @Test
        @DisplayName("per-mapper disabled config writes the original value")
        void perMapperDisabled() {
            JsonMapper custom = JsonMapper.builder()
                    .addModule(new Jackson3MaskingModule(MaskingConfig.create().setEnabled(false)))
                    .build();

            assertEquals("{\"cardNumber\":\"4111111111111111\"}",
                    custom.writeValueAsString(new CreditCardDTO("4111111111111111")));
        }

        @Test
        @DisplayName("keeps default attributes set before the module")
        void keepsExistingDefaultAttributes() {
            JsonMapper custom = JsonMapper.builder()
                    .defaultAttributes(ContextAttributes.getEmpty().withSharedAttribute("tenant", "acme"))
                    .addModule(new Jackson3MaskingModule(MaskingConfig.create().setDefaultMaskChar('#')))
                    .build();

            assertEquals("acme", custom.serializationConfig().getAttributes().getAttribute("tenant"));
            assertEquals("{\"cardNumber\":\"####-####-####-1111\"}",
                    custom.writeValueAsString(new CreditCardDTO("4111111111111111")));
        }

        @Test
        @DisplayName("per-call attributes do not leak into later calls")
        void perCallAttributesDoNotLeak() {
            JsonMapper custom = JsonMapper.builder()
                    .addModule(new Jackson3MaskingModule(MaskingConfig.create()))
                    .build();

            assertEquals("{\"value\":\"null\"}", custom.writeValueAsString(new ProbeDTO("first")));
            assertEquals("{\"value\":\"null\"}", custom.writeValueAsString(new ProbeDTO("second")));
        }

        @Test
        @DisplayName("rejects a null config")
        void rejectsNullConfig() {
            assertThrows(IllegalArgumentException.class, () -> new Jackson3MaskingModule(null));
        }
    }
}
