package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.MaskingConfig;
import io.github.zeytx.bankmasker.MaskingSerializer;
import io.github.zeytx.bankmasker.Slf4jMaskingAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for BankMasker.
 *
 * <p>Automatically configures {@link MaskingConfig} from {@code application.yml}
 * properties and optionally enables SLF4J-based audit logging.
 *
 * <p>This configuration is activated when {@link MaskingSerializer} is on the classpath.
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MaskingSerializer.class)
@EnableConfigurationProperties
public class BankMaskerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BankMaskerAutoConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "bankmasker")
    public BankMaskerProperties bankMaskerProperties() {
        return new BankMaskerProperties();
    }

    @Bean
    public MaskingConfig maskingConfig(BankMaskerProperties properties) {
        MaskingConfig config = MaskingConfig.getInstance();
        config.setEnabled(properties.isEnabled());
        config.setDefaultMaskChar(properties.getDefaultMaskChar());

        if (properties.getAudit().isEnabled()) {
            config.setAuditLogger(new Slf4jMaskingAuditLogger());
            log.info("[BankMasker] Audit logging enabled");
        }

        log.info("[BankMasker] Auto-configured — enabled={}, maskChar='{}'",
                properties.isEnabled(), properties.getDefaultMaskChar());

        return config;
    }
}

