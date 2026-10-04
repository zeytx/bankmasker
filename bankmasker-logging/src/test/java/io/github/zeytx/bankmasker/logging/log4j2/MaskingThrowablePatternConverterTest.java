package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.MaskingConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingThrowablePatternConverter — Log4j2 stack traces")
class MaskingThrowablePatternConverterTest {

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    private static LogEvent event(Throwable throwable) {
        return Log4jLogEvent.newBuilder()
                .setLoggerName("test")
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage("payment failed"))
                .setThrown(throwable)
                .build();
    }

    private static Throwable failure() {
        return new IllegalStateException("Invalid card 4111111111111111",
                new RuntimeException("owner john.doe@mail.com"));
    }

    @Test
    @DisplayName("masks exception messages and causes in the stack trace")
    void masksStackTrace() {
        MaskingThrowablePatternConverter converter = MaskingThrowablePatternConverter.newInstance(null, null);
        StringBuilder sb = new StringBuilder();
        converter.format(event(failure()), sb);

        assertThat(sb.toString())
                .contains("IllegalStateException: Invalid card ****-****-****-1111")
                .contains("owner jo****@mail.com")
                .doesNotContain("4111111111111111", "john.doe@mail.com");
    }

    @Test
    @DisplayName("declares that it handles the throwable")
    void handlesThrowable() {
        assertThat(MaskingThrowablePatternConverter.newInstance(null, null).handlesThrowable()).isTrue();
    }

    @Test
    @DisplayName("layout using %maskedEx does not append an unmasked stack trace")
    void layoutHasNoUnmaskedTrace() {
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%maskedMsg%n%maskedEx")
                .build();

        String out = layout.toSerializable(event(failure()));

        assertThat(out)
                .startsWith("payment failed")
                .contains("****-****-****-1111")
                .doesNotContain("4111111111111111");
    }
}
