package io.github.zeytx.bankmasker.logging.spring;

import io.github.zeytx.bankmasker.MaskingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MaskingJsonMembersCustomizer — Spring Boot structured logging")
class MaskingJsonMembersCustomizerTest {

    @BeforeEach
    @AfterEach
    void resetConfig() {
        MaskingConfig.getInstance().reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("masks string values, nested ones included, and keeps numbers")
    void masksStringValues() {
        JsonWriter<Map<String, Object>> writer = JsonWriter.of(members -> {
            members.add("message", event -> event.get("message"));
            members.add("mdc", event -> event.get("mdc"));
            members.add("count", event -> event.get("count"));
            new MaskingJsonMembersCustomizer().customize((JsonWriter.Members<Object>) (JsonWriter.Members<?>) members);
        });
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("message", "charge card 4111111111111111");
        event.put("mdc", Map.of("authorization", "Bearer abc123XYZ.def-456"));
        event.put("count", 4111111111111111L);

        String json = writer.writeToString(event);

        assertThat(json)
                .contains("\"message\":\"charge card ****-****-****-1111\"")
                .contains("\"authorization\":\"Bearer ********\"")
                .contains("\"count\":4111111111111111");
    }
}
