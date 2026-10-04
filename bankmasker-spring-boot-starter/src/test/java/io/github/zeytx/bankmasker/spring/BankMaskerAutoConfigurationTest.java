package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.MaskingConfig;
import io.github.zeytx.bankmasker.Slf4jMaskingAuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.event.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("warns that @MaskData is ignored when Jackson 3 is on the classpath")
    void warnsWhenJackson3Present(CapturedOutput output) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(BankMaskerAutoConfiguration.Jackson3Warning.class);
            assertThat(output).contains("Jackson 3 (tools.jackson) detected");
        });
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("no Jackson 3 warning when only Jackson 2 is present")
    void noWarningWithoutJackson3(CapturedOutput output) {
        runner.withClassLoader(new FilteredClassLoader("tools.jackson"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BankMaskerAutoConfiguration.Jackson3Warning.class);
                    assertThat(output).doesNotContain("Jackson 3 (tools.jackson) detected");
                });
    }
}
