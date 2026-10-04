package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.MaskingConfig;
import io.github.zeytx.bankmasker.MaskingSerializer;
import io.github.zeytx.bankmasker.Slf4jMaskingAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for BankMasker.
 *
 * <p>Automatically configures {@link MaskingConfig} from {@code application.yml}
 * properties and optionally enables SLF4J-based audit logging.
 *
 * <p>This configuration is activated when {@link MaskingSerializer} is on the classpath.
 * Declaring your own {@link MaskingConfig} bean disables it.
 *
 * <p>BankMasker masks through Jackson 2. When Jackson 3 ({@code tools.jackson})
 * is on the classpath, as with Spring Boot 4 defaults, a warning is logged:
 * Jackson 3 mappers ignore {@link io.github.zeytx.bankmasker.MaskData}.
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MaskingSerializer.class)
@EnableConfigurationProperties(BankMaskerProperties.class)
public class BankMaskerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BankMaskerAutoConfiguration.class);


    @Bean
    @ConditionalOnMissingBean
    public MaskingConfig maskingConfig(BankMaskerProperties properties) {
        MaskingConfig config = MaskingConfig.getInstance();
        config.setEnabled(properties.isEnabled());
        config.setDefaultMaskChar(properties.getDefaultMaskChar());

        if (properties.getAudit().isEnabled()) {
            Level level = resolveAuditLevel(properties.getAudit().getLevel());
            config.setAuditLogger(new Slf4jMaskingAuditLogger(level));
            log.info("[BankMasker] Audit logging enabled at {}", level);
        } else {
            // Clear any previously configured logger: the config is the global
            // singleton, so stale state must not survive a re-configuration.
            config.setAuditLogger(null);
        }

        log.info("[BankMasker] Auto-configured — enabled={}, maskChar='{}'",
                properties.isEnabled(), properties.getDefaultMaskChar());
        if (!properties.isEnabled()) {
            log.warn("[BankMasker] Masking is DISABLED (bankmasker.enabled=false): "
                    + "sensitive fields will be serialized in clear text");
        }

        return config;
    }

    private static Level resolveAuditLevel(String level) {
        try {
            return Level.valueOf(level.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("[BankMasker] Invalid audit level '{}', falling back to INFO", level);
            return Level.INFO;
        }
    }

    /**
     * Warns at startup when Jackson 3 is on the classpath: its mappers ignore
     * {@code @MaskData}, so values they serialize are written in clear text.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
    static class Jackson3Warning {

        Jackson3Warning() {
            log.warn("[BankMasker] Jackson 3 (tools.jackson) detected. @MaskData only applies to Jackson 2 "
                    + "ObjectMappers: values serialized with Jackson 3 (the Spring Boot 4 default for HTTP "
                    + "responses) are NOT masked. Serialize sensitive DTOs with Jackson 2 until Jackson 3 "
                    + "is supported.");
        }
    }
}
