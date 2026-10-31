package io.github.zeytx.bankmasker.spring;

import io.github.zeytx.bankmasker.Jackson3MaskingModule;
import io.github.zeytx.bankmasker.MaskingConfig;
import io.github.zeytx.bankmasker.MaskingModule;
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
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;

/**
 * Auto-configuration for BankMasker.
 *
 * <p>Configures {@link MaskingConfig} from {@code application.yml} properties,
 * optionally enables SLF4J-based audit logging, and registers
 * {@link MaskingModule} (Jackson 2) / {@link Jackson3MaskingModule} (Jackson 3)
 * beans, which Spring Boot adds to its JSON mappers so {@code bankmasker.fields}
 * rules apply. Declaring your own {@link MaskingConfig} or module bean
 * replaces the corresponding default.
 *
 * <p>The configured {@link MaskingConfig} applies to both Jackson 2 and
 * Jackson 3 mappers, so it covers Spring Boot 3 and Spring Boot 4 defaults.
 *
 * @since 1.0.0
 */
@AutoConfiguration
// MaskingConfig has no Jackson dependency: the serializers would make the
// condition fail on a classpath with only one Jackson version
@ConditionalOnClass(MaskingConfig.class)
@EnableConfigurationProperties(BankMaskerProperties.class)
public class BankMaskerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BankMaskerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MaskingConfig maskingConfig(BankMaskerProperties properties) {
        MaskingConfig config = MaskingConfig.getInstance();
        config.setEnabled(properties.isEnabled());
        config.setDefaultMaskChar(properties.getDefaultMaskChar());
        String key = properties.getPseudonymKey();
        config.setPseudonymKey(key == null || key.isEmpty() ? null : key.getBytes(StandardCharsets.UTF_8));
        // Global singleton: replace the rules so stale ones do not survive a re-configuration
        config.clearFieldMasks();
        properties.getFields().forEach(config::maskField);

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

    /** Jackson 2 module: Spring Boot 3 adds {@code Module} beans to its ObjectMapper. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    static class Jackson2ModuleConfiguration {

        @Bean
        @ConditionalOnMissingBean
        MaskingModule bankMaskerModule(MaskingConfig config) {
            return new MaskingModule(config);
        }
    }

    /** Jackson 3 module: Spring Boot 4 adds {@code JacksonModule} beans to its JsonMapper. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "tools.jackson.databind.JacksonModule")
    static class Jackson3ModuleConfiguration {

        @Bean
        @ConditionalOnMissingBean
        Jackson3MaskingModule bankMaskerJackson3Module(MaskingConfig config) {
            return new Jackson3MaskingModule(config);
        }
    }

    /** Warns when HTTP responses are serialized by a JSON library that ignores {@code @MaskData}. */
    @Configuration(proxyBeanMethods = false)
    static class JsonMapperCheck {

        JsonMapperCheck(Environment environment, ResourceLoader resourceLoader) {
            String preferred = environment.getProperty("spring.http.converters.preferred-json-mapper",
                    environment.getProperty("spring.mvc.converters.preferred-json-mapper", ""));
            ClassLoader classLoader = resourceLoader.getClassLoader();
            boolean jackson = ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader)
                    || ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", classLoader);
            if ("gson".equalsIgnoreCase(preferred) || "jsonb".equalsIgnoreCase(preferred)) {
                log.warn("[BankMasker] preferred-json-mapper={}: HTTP responses are serialized with a library "
                        + "that ignores @MaskData, so annotated fields are written in clear text. "
                        + "Use Jackson for responses that carry sensitive data", preferred);
            } else if (!jackson && (ClassUtils.isPresent("com.google.gson.Gson", classLoader)
                    || ClassUtils.isPresent("jakarta.json.bind.Jsonb", classLoader))) {
                log.warn("[BankMasker] No Jackson on the classpath: Gson/JSON-B ignore @MaskData, "
                        + "so annotated fields are written in clear text");
            }
        }
    }
}
