package io.github.zeytx.bankmasker.logging;

import io.github.zeytx.bankmasker.MaskingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogMasker — pattern masking in free text")
class LogMaskerTest {

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    @Test
    @DisplayName("masks a PAN inside a message")
    void masksPan() {
        assertThat(LogMasker.maskMessage("charge card 4111111111111111 approved"))
                .isEqualTo("charge card ****-****-****-1111 approved");
    }

    @Test
    @DisplayName("masks a PAN with spaces or dashes")
    void masksPanWithSeparators() {
        assertThat(LogMasker.maskMessage("card 4111 1111 1111 1111 ok"))
                .isEqualTo("card ****-****-****-1111 ok");
        assertThat(LogMasker.maskMessage("card 4111-1111-1111-1111 ok"))
                .isEqualTo("card ****-****-****-1111 ok");
    }

    @Test
    @DisplayName("leaves Luhn-invalid digit runs untouched (timestamps, ids)")
    void leavesLuhnInvalidRuns() {
        String msg = "order 1234567890123456 created at 1718000000000";
        assertThat(LogMasker.maskMessage(msg)).isEqualTo(msg);
    }

    @Test
    @DisplayName("masks an email inside a message")
    void masksEmail() {
        assertThat(LogMasker.maskMessage("user john.doe@mail.com logged in"))
                .isEqualTo("user jo****@mail.com logged in");
    }

    @Test
    @DisplayName("masks an IBAN inside a message")
    void masksIban() {
        assertThat(LogMasker.maskMessage("transfer to ES6621000418401234567891 done"))
                .isEqualTo("transfer to ES******************7891 done");
    }

    @Test
    @DisplayName("masks multiple patterns in the same message")
    void masksMixedMessage() {
        String masked = LogMasker.maskMessage(
                "user a.b@mail.com paid with 378282246310005 from DE89370400440532013000");
        assertThat(masked)
                .doesNotContain("a.b@mail.com")
                .doesNotContain("378282246310005")
                .doesNotContain("DE89370400440532013000")
                .contains("****-****-****-0005")
                .contains("DE****************3000");
    }

    @Test
    @DisplayName("respects disabled config")
    void respectsDisabled() {
        MaskingConfig.getInstance().setEnabled(false);
        String msg = "card 4111111111111111";
        assertThat(LogMasker.maskMessage(msg)).isEqualTo(msg);
    }

    @Test
    @DisplayName("respects defaultMaskChar")
    void respectsMaskChar() {
        MaskingConfig.getInstance().setDefaultMaskChar('#');
        assertThat(LogMasker.maskMessage("card 4111111111111111"))
                .isEqualTo("card ####-####-####-1111");
    }

    @Test
    @DisplayName("handles null and empty messages")
    void handlesNullAndEmpty() {
        assertThat(LogMasker.maskMessage(null)).isNull();
        assertThat(LogMasker.maskMessage("")).isEmpty();
    }

    @Test
    @DisplayName("plain messages pass through unchanged")
    void plainMessageUnchanged() {
        String msg = "application started in 2.3 seconds";
        assertThat(LogMasker.maskMessage(msg)).isEqualTo(msg);
    }
}
