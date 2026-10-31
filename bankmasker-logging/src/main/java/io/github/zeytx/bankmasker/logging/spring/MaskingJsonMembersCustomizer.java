package io.github.zeytx.bankmasker.logging.spring;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Masks every string value of Spring Boot's structured JSON logs (ECS,
 * Logstash, GELF): the message, MDC / key-value pairs, the stack trace...
 * Numbers and booleans are left untouched, so the JSON stays valid.
 *
 * <p>Requires Spring Boot 3.4+. Enable it in {@code application.yml}:
 * <pre>{@code
 * logging:
 *   structured:
 *     format:
 *       console: ecs
 *     json:
 *       customizer: io.github.zeytx.bankmasker.logging.spring.MaskingJsonMembersCustomizer
 * }</pre>
 *
 * <p>Uses {@link LogMasker#maskMessage(String)}: the default detectors and
 * the global {@code MaskingConfig}.
 *
 * @since 1.3.0
 */
public class MaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, LogMasker::maskMessage));
    }
}
