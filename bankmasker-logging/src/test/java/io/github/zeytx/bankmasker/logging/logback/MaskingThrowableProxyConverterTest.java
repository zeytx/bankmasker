package io.github.zeytx.bankmasker.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.github.zeytx.bankmasker.MaskingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingThrowableProxyConverter — Logback stack traces")
class MaskingThrowableProxyConverterTest {

    private final LoggerContext context = new LoggerContext();

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    private LoggingEvent event(Throwable throwable) {
        return new LoggingEvent("test", context.getLogger("test"), Level.ERROR,
                "payment failed", throwable, null);
    }

    private static Throwable failure() {
        return new IllegalStateException("Invalid card 4111111111111111",
                new RuntimeException("owner john.doe@mail.com"));
    }

    @Test
    @DisplayName("masks exception messages and causes in the stack trace")
    void masksStackTrace() {
        MaskingThrowableProxyConverter converter = new MaskingThrowableProxyConverter();
        converter.setContext(context);
        converter.start();

        String out = converter.convert(event(failure()));

        assertThat(out)
                .contains("IllegalStateException: Invalid card ****-****-****-1111")
                .contains("owner jo****@mail.com")
                .doesNotContain("4111111111111111", "john.doe@mail.com");
    }

    @Test
    @DisplayName("events without throwable produce an empty string")
    void noThrowable() {
        MaskingThrowableProxyConverter converter = new MaskingThrowableProxyConverter();
        converter.setContext(context);
        converter.start();

        assertThat(converter.convert(event(null))).isEmpty();
    }

    @Test
    @DisplayName("layout using %maskedEx does not append an unmasked stack trace")
    void layoutHasNoUnmaskedTrace() {
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.getInstanceConverterMap().put("maskedMsg", MaskingMessageConverter::new);
        layout.getInstanceConverterMap().put("maskedEx", MaskingThrowableProxyConverter::new);
        layout.setPattern("%maskedMsg%n%maskedEx");
        layout.start();

        String out = layout.doLayout(event(failure()));

        assertThat(out)
                .startsWith("payment failed")
                .contains("****-****-****-1111")
                .doesNotContain("4111111111111111");
    }
}
