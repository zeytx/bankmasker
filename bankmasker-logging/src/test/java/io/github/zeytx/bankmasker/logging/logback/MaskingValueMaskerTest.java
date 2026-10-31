package io.github.zeytx.bankmasker.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.github.zeytx.bankmasker.MaskingConfig;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingValueMasker — logstash-logback-encoder JSON logs")
class MaskingValueMaskerTest {

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    @Test
    @DisplayName("masks strings; returns null (keep original) for clean strings and non-strings")
    void maskContract() {
        MaskingValueMasker masker = new MaskingValueMasker();

        assertThat(masker.mask(null, "card 4111111111111111")).isEqualTo("card ****-****-****-1111");
        assertThat(masker.mask(null, "server started")).isNull();
        assertThat(masker.mask(null, 4111111111111111L)).isNull();
    }

    @Test
    @DisplayName("masks message, arguments and MDC in LogstashEncoder output")
    void logstashEncoder() {
        LoggerContext context = new LoggerContext();
        MaskingJsonGeneratorDecorator decorator = new MaskingJsonGeneratorDecorator();
        decorator.addValueMasker(new MaskingValueMasker());
        decorator.start();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.addDecorator(decorator);
        encoder.start();

        LoggingEvent event = new LoggingEvent("test", context.getLogger("test"), Level.INFO,
                "charge {} for {}", null, new Object[]{"4111111111111111", "john.doe@mail.com"});
        event.setMDCPropertyMap(Map.of("authorization", "Bearer abc123XYZ.def-456"));

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json)
                .contains("charge ****-****-****-1111 for jo****@mail.com")
                .contains("Bearer ********")
                .doesNotContain("4111111111111111", "john.doe@mail.com", "abc123XYZ");
    }
}
