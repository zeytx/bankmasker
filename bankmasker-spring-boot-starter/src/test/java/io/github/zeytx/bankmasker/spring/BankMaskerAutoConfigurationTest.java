package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.MaskingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
    @DisplayName("audit logger is null when audit.enabled=false")
    void auditLoggerDisabledByDefault() {
        runner.run(context -> {
            MaskingConfig config = context.getBean(MaskingConfig.class);
            assertThat(config.getAuditLogger()).isNull();
        });
    }
}

