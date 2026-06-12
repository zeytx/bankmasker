package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.MaskingConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingMessagePatternConverter — Log4j2 integration")
class MaskingMessagePatternConverterTest {

    private final MaskingMessagePatternConverter converter =
            MaskingMessagePatternConverter.newInstance(null);

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    private String format(String message) {
        LogEvent event = Log4jLogEvent.newBuilder()
                .setLoggerName("test")
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage(message))
                .build();
        StringBuilder sb = new StringBuilder();
        converter.format(event, sb);
        return sb.toString();
    }

    @Test
    @DisplayName("masks PAN in the formatted message")
    void masksPan() {
        assertThat(format("payment with card 4111111111111111"))
                .isEqualTo("payment with card ****-****-****-1111");
    }

    @Test
    @DisplayName("masks IBAN in the formatted message")
    void masksIban() {
        assertThat(format("transfer to ES6621000418401234567891"))
                .isEqualTo("transfer to ES******************7891");
    }

    @Test
    @DisplayName("plain messages pass through unchanged")
    void plainUnchanged() {
        assertThat(format("server started on port 8080"))
                .isEqualTo("server started on port 8080");
    }
}
