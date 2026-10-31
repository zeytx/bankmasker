package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.MaskingConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingRewritePolicy — Log4j2 JSON layouts")
class MaskingRewritePolicyTest {

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    private static LogEvent event(String message, Map<String, String> context) {
        StringMap contextData = ContextDataFactory.createContextData();
        context.forEach(contextData::putValue);
        return Log4jLogEvent.newBuilder()
                .setLoggerName("test")
                .setLevel(Level.INFO)
                .setMessage(new SimpleMessage(message))
                .setContextData(contextData)
                .build();
    }

    @Test
    @DisplayName("masks the message and string context values")
    void masksMessageAndContext() {
        LogEvent rewritten = MaskingRewritePolicy.createPolicy(null).rewrite(
                event("card 4111111111111111", Map.of("request", "password=hunter2", "user", "bob")));

        assertThat(rewritten.getMessage().getFormattedMessage()).isEqualTo("card ****-****-****-1111");
        assertThat(rewritten.getContextData().<String>getValue("request")).isEqualTo("password=********");
        assertThat(rewritten.getContextData().<String>getValue("user")).isEqualTo("bob");
    }

    @Test
    @DisplayName("returns the same event when there is nothing to mask")
    void cleanEventUnchanged() {
        LogEvent event = event("server started", Map.of("user", "bob"));
        assertThat(MaskingRewritePolicy.createPolicy(null).rewrite(event)).isSameAs(event);
    }

    @Test
    @DisplayName("the detectors attribute toggles detectors")
    void detectorsAttribute() {
        LogEvent rewritten = MaskingRewritePolicy.createPolicy("+PHONE")
                .rewrite(event("call +52 55 1234 5678", Map.of()));
        assertThat(rewritten.getMessage().getFormattedMessage()).isEqualTo("call ********5678");
    }
}
