package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.MaskData;
import io.github.zeytx.bankmasker.MaskType;
import io.github.zeytx.bankmasker.MaskingConfig;
import io.github.zeytx.bankmasker.Slf4jMaskingAuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.event.Level;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BankMaskerAutoConfiguration")
class BankMaskerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BankMaskerAutoConfiguration.class));

    @Test
    @DisplayName("creates MaskingConfig bean with defaults")
    void createsDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MaskingConfig.class);
            assertThat(context).hasSingleBean(BankMaskerProperties.class);

            MaskingConfig config = context.getBean(MaskingConfig.class);
            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getDefaultMaskChar()).isEqualTo('*');
            assertThat(config.getAuditLogger()).isNull();
        });
    }

    @Test
    @DisplayName("applies enabled=false from properties")
    void appliesDisabled() {
        runner.withPropertyValues("bankmasker.enabled=false")
                .run(context -> {
                    MaskingConfig config = context.getBean(MaskingConfig.class);
                    assertThat(config.isEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("applies custom mask char from properties")
    void appliesCustomMaskChar() {
        runner.withPropertyValues("bankmasker.default-mask-char=#")
                .run(context -> {
                    MaskingConfig config = context.getBean(MaskingConfig.class);
                    assertThat(config.getDefaultMaskChar()).isEqualTo('#');
                });
    }

    @Test
    @DisplayName("enables audit logger when audit.enabled=true")
    void enablesAuditLogger() {
        runner.withPropertyValues("bankmasker.audit.enabled=true")
                .run(context -> {
                    MaskingConfig config = context.getBean(MaskingConfig.class);
                    assertThat(config.getAuditLogger()).isNotNull();
                });
    }

    @Test
    @DisplayName("applies audit level from properties")
    void appliesAuditLevel() {
        runner.withPropertyValues("bankmasker.audit.enabled=true", "bankmasker.audit.level=debug")
                .run(context -> {
                    MaskingConfig config = context.getBean(MaskingConfig.class);
                    assertThat(config.getAuditLogger())
                            .isInstanceOfSatisfying(Slf4jMaskingAuditLogger.class,
                                    logger -> assertThat(logger.getLevel()).isEqualTo(Level.DEBUG));
                });
    }

    @Test
    @DisplayName("invalid audit level falls back to INFO")
    void invalidAuditLevelFallsBack() {
        runner.withPropertyValues("bankmasker.audit.enabled=true", "bankmasker.audit.level=nope")
                .run(context -> {
                    MaskingConfig config = context.getBean(MaskingConfig.class);
                    assertThat(config.getAuditLogger())
                            .isInstanceOfSatisfying(Slf4jMaskingAuditLogger.class,
                                    logger -> assertThat(logger.getLevel()).isEqualTo(Level.INFO));
                });
    }

    @Test
    @DisplayName("audit logger is null when audit.enabled=false")
    void auditLoggerDisabledByDefault() {
        runner.run(context -> {
            MaskingConfig config = context.getBean(MaskingConfig.class);
            assertThat(config.getAuditLogger()).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomConfig {
        static final MaskingConfig CUSTOM = MaskingConfig.create().setDefaultMaskChar('#');

        @Bean
        MaskingConfig customMaskingConfig() {
            return CUSTOM;
        }
    }

    @Test
    @DisplayName("backs off when the application defines its own MaskingConfig")
    void backsOffForUserBean() {
        runner.withUserConfiguration(CustomConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MaskingConfig.class);
                    assertThat(context.getBean(MaskingConfig.class)).isSameAs(CustomConfig.CUSTOM);
                });
    }

    @Test
    @DisplayName("fails fast on a control character as mask char")
    void rejectsControlMaskChar() {
        runner.withPropertyValues("bankmasker.default-mask-char=\u0085")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Invalid mask character"));
    }

    static class CardDto {
        @MaskData(MaskType.CREDIT_CARD)
        public String card = "4111111111111111";
    }

    @Test
    @DisplayName("Jackson 2 and Jackson 3 mappers both apply the configured mask char")
    void bothJacksonVersionsUseProperties() {
        runner.withPropertyValues("bankmasker.default-mask-char=#")
                .run(context -> {
                    String expected = "{\"card\":\"####-####-####-1111\"}";
                    assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new CardDto()))
                            .isEqualTo(expected);
                    assertThat(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(new CardDto()))
                            .isEqualTo(expected);
                });
    }

    static class LoginDto {
        public String user = "alice";
        public String password = "hunter2";
        public String customerId = "CUS-1";
    }

    @Test
    @DisplayName("bankmasker.fields rules apply through the Jackson 2 and Jackson 3 module beans")
    void fieldRules() {
        runner.withPropertyValues("bankmasker.fields.password=TOTAL", "bankmasker.fields.customer_id=PSEUDONYM")
                .run(context -> {
                    assertThat(MaskingConfig.getInstance().getFieldMask("password")).isEqualTo(MaskType.TOTAL);
                    String jackson2 = new com.fasterxml.jackson.databind.ObjectMapper()
                            .registerModule(context.getBean(io.github.zeytx.bankmasker.MaskingModule.class))
                            .writeValueAsString(new LoginDto());
                    String jackson3 = tools.jackson.databind.json.JsonMapper.builder()
                            .addModule(context.getBean(io.github.zeytx.bankmasker.Jackson3MaskingModule.class))
                            .build().writeValueAsString(new LoginDto());

                    for (String json : new String[]{jackson2, jackson3}) {
                        assertThat(json).contains("\"user\":\"alice\"", "\"password\":\"********\"",
                                "\"customerId\":\"********\"").doesNotContain("hunter2");
                    }
                });
    }

    @Test
    @DisplayName("bankmasker.pseudonym-key enables PSEUDONYM; a short key fails fast")
    void pseudonymKey() {
        runner.withPropertyValues("bankmasker.pseudonym-key=0123456789abcdef")
                .run(context -> assertThat(io.github.zeytx.bankmasker.MaskUtils.mask("CUS-1", MaskType.PSEUDONYM))
                        .matches("[0-9a-f]{16}"));
        runner.withPropertyValues("bankmasker.pseudonym-key=short")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("at least 16 bytes"));
    }

    @Test
    @DisplayName("auto-configures with Jackson 3 only (Spring Boot 4 without Jackson 2)")
    void jackson3Only() {
        runner.withClassLoader(new org.springframework.boot.test.context.FilteredClassLoader("com.fasterxml.jackson"))
                .withPropertyValues("bankmasker.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MaskingConfig.class)
                            .hasSingleBean(io.github.zeytx.bankmasker.Jackson3MaskingModule.class)
                            .doesNotHaveBean(io.github.zeytx.bankmasker.MaskingModule.class);
                    assertThat(MaskingConfig.getInstance().isEnabled()).isFalse();
                });
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    @DisplayName("warns when HTTP responses use Gson or JSON-B")
    void warnsOnNonJacksonMapper(org.springframework.boot.test.system.CapturedOutput output) {
        runner.withPropertyValues("spring.http.converters.preferred-json-mapper=gson")
                .run(context -> assertThat(output).contains("preferred-json-mapper=gson"));
    }

    @Test
    @DisplayName("Spring Boot's own JSON mapper applies bankmasker.fields rules and @MaskData")
    void springJsonMapper() throws ClassNotFoundException {
        // Spring Boot 3: Jackson 2 ObjectMapper; Spring Boot 4: Jackson 3 JsonMapper (spring-boot-jackson)
        String boot3 = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration";
        boolean isBoot3 = org.springframework.util.ClassUtils.isPresent(boot3, null);
        Class<?> jacksonAutoConfiguration = Class.forName(isBoot3 ? boot3
                : "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BankMaskerAutoConfiguration.class, jacksonAutoConfiguration))
                .withPropertyValues("bankmasker.fields.password=TOTAL")
                .run(context -> {
                    String json = isBoot3
                            ? context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                                    .writeValueAsString(new LoginDto())
                            : context.getBean(tools.jackson.databind.json.JsonMapper.class)
                                    .writeValueAsString(new LoginDto());
                    assertThat(json).contains("\"password\":\"********\"").doesNotContain("hunter2");
                });
    }
}
