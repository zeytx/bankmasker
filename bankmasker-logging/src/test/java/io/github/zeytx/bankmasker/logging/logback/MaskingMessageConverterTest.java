package io.github.zeytx.bankmasker.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.github.zeytx.bankmasker.MaskingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingMessageConverter — Logback integration")
class MaskingMessageConverterTest {

    private final MaskingMessageConverter converter = new MaskingMessageConverter();

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    private LoggingEvent event(String message, Object... args) {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("test");
        return new LoggingEvent("test", logger, Level.INFO, message, null, args);
    }

    @Test
    @DisplayName("masks PAN in the formatted message")
    void masksPan() {
        String out = converter.convert(event("payment with card {}", "4111111111111111"));
        assertThat(out).isEqualTo("payment with card ****-****-****-1111");
    }

    @Test
    @DisplayName("masks email in the formatted message")
    void masksEmail() {
        String out = converter.convert(event("login: john.doe@mail.com"));
        assertThat(out).isEqualTo("login: jo****@mail.com");
    }

    @Test
    @DisplayName("plain messages pass through unchanged")
    void plainUnchanged() {
        String out = converter.convert(event("server started on port 8080"));
        assertThat(out).isEqualTo("server started on port 8080");
    }

    private static MaskingMessageConverter started(LoggerContext context, String... options) {
        MaskingMessageConverter configured = new MaskingMessageConverter();
        configured.setContext(context);
        configured.setOptionList(java.util.List.of(options));
        configured.start();
        return configured;
    }

    @Test
    @DisplayName("options toggle detectors: %maskedMsg{+PHONE, -EMAIL}")
    void options() {
        MaskingMessageConverter configured = started(new LoggerContext(), "+PHONE", "-EMAIL");
        assertThat(configured.convert(event("call +52 55 1234 5678 or john.doe@mail.com")))
                .isEqualTo("call ********5678 or john.doe@mail.com");
    }

    @Test
    @DisplayName("an invalid option is reported and the default detectors are kept")
    void invalidOption() {
        LoggerContext context = new LoggerContext();
        MaskingMessageConverter configured = started(context, "PHONE");

        assertThat(configured.convert(event("card 4111111111111111"))).isEqualTo("card ****-****-****-1111");
        assertThat(context.getStatusManager().getCopyOfStatusList())
                .anyMatch(status -> status.getMessage().contains("Invalid BankMasker option 'PHONE'"));
    }
}
