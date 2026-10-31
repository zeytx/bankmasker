package io.github.zeytx.bankmasker.logging;

import io.github.zeytx.bankmasker.MaskingConfig;
import io.github.zeytx.bankmasker.logging.LogMasker.Detector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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
    @DisplayName("masks an IBAN printed in groups of 4")
    void masksSpacedIban() {
        assertThat(LogMasker.maskMessage("transfer to ES66 2100 0418 4012 3456 7891 done"))
                .isEqualTo("transfer to ES******************7891 done");
    }

    @Test
    @DisplayName("leaves short IBAN-like tokens untouched")
    void leavesIbanLookalikes() {
        String msg = "ref ID12 3456 7890 processed";
        assertThat(LogMasker.maskMessage(msg)).isEqualTo(msg);
    }

    @Test
    @DisplayName("masks an SSN inside a message")
    void masksSsn() {
        assertThat(LogMasker.maskMessage("applicant ssn 123-45-6789 verified"))
                .isEqualTo("applicant ssn ***-**-6789 verified");
    }

    @Test
    @DisplayName("leaves dates untouched by SSN detection")
    void leavesDates() {
        String msg = "created on 2026-07-02 at noon";
        assertThat(LogMasker.maskMessage(msg)).isEqualTo(msg);
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

    @Test
    @DisplayName("masks emails delimited by punctuation")
    void masksDelimitedEmail() {
        assertThat(LogMasker.maskMessage("to=<john.doe@mail.com>, cc:jane.roe@mail.com"))
                .isEqualTo("to=<jo****@mail.com>, cc:ja****@mail.com");
    }

    @Test
    @DisplayName("long tokens without '@' are processed in linear time (no ReDoS)")
    void noCatastrophicBacktracking() {
        String hostile = "a".repeat(200_000);
        String result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> LogMasker.maskMessage(hostile));
        assertThat(result).isEqualTo(hostile);
    }

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9"
            + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    @Nested
    @DisplayName("Secrets")
    class Secrets {

        @Test
        @DisplayName("masks PEM private key bodies, also when the END line is missing")
        void privateKeys() {
            assertThat(LogMasker.maskMessage("key -----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA\n"
                    + "-----END RSA PRIVATE KEY----- loaded"))
                    .isEqualTo("key -----BEGIN RSA PRIVATE KEY-----********-----END RSA PRIVATE KEY----- loaded");
            assertThat(LogMasker.maskMessage("-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg"))
                    .isEqualTo("-----BEGIN PRIVATE KEY-----********");
        }

        @ParameterizedTest
        @DisplayName("masks magnetic stripe track data (PCI sensitive authentication data)")
        @ValueSource(strings = {";4111111111111111=25121010000012300000?",
                "%B4111111111111111^DOE/JOHN^2512101000001230000000?"})
        void trackData(String track) {
            assertThat(LogMasker.maskMessage("swipe " + track + " ok")).isEqualTo("swipe ******** ok");
        }

        @ParameterizedTest
        @DisplayName("masks passwords in URLs and connection strings")
        @CsvSource(delimiter = '|', value = {
                "jdbc:postgresql://admin:S3cr3t@db:5432/app | jdbc:postgresql://admin:********@db:5432/app",
                "https://user:p@ss@host/x | https://user:********@host/x",
                "redis://:hunter2@cache:6379 | redis://:********@cache:6379",
                "https://host/x?y=1 | https://host/x?y=1"
        })
        void urlCredentials(String input, String expected) {
            assertThat(LogMasker.maskMessage(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("masks JWTs keeping the header")
        void jwt() {
            assertThat(LogMasker.maskMessage("token " + JWT + " ok"))
                    .isEqualTo("token eyJhbGciOiJIUzI1NiJ9.******** ok");
        }

        @ParameterizedTest
        @DisplayName("masks Bearer/Basic credentials but not prose")
        @CsvSource(delimiter = '|', value = {
                "Authorization: Bearer abc123XYZ.def-456 | Authorization: Bearer ********",
                "auth Basic dXNlcjpwYXNzd29yZA== sent | auth Basic ******** sent",
                "basic validation failed for request | basic validation failed for request"
        })
        void authHeaders(String input, String expected) {
            assertThat(LogMasker.maskMessage(input)).isEqualTo(expected);
        }

        static Stream<String[]> apiKeys() {
            return Stream.of(
                    new String[]{"AKIAIOSFODNN7EXAMPLE", "AKIA********"},
                    new String[]{"ghp_" + "a1B2c3D4e5".repeat(3) + "f6G7h8", "ghp_********"},
                    new String[]{"github_pat_" + "A1".repeat(41), "github_pat_********"},
                    new String[]{"glpat-" + "x1Y2z3W4v5U6t7S8r9Q0", "glpat-********"},
                    // Fake token assembled at runtime so secret scanners do not flag the source
                    new String[]{"xox" + "b-" + "1234567890-1234567890123-" + "abcdefghij".repeat(2), "xoxb-********"},
                    new String[]{"sk_live_" + "51H8xYz".repeat(4), "sk_live_********"},
                    new String[]{"AIza" + "SyD-9tSrke72PouQMnMX-a7eZSW0jkFMBWY".substring(0, 35), "AIza********"},
                    new String[]{"sk-proj-" + "Ab3_dE6-gH9".repeat(4), "sk-proj-********"},
                    new String[]{"sk-ant-api03-" + "Zx9_Yw8-Vu7".repeat(9), "sk-ant-api03-********"},
                    new String[]{"SG." + "a".repeat(22) + "." + "b".repeat(43), "SG.********"},
                    new String[]{"npm_" + "a1B2c3".repeat(6), "npm_********"},
                    new String[]{"hvs." + "C".repeat(95), "hvs.********"},
                    new String[]{"https://hooks.slack.com/services/T000/B000/" + "X".repeat(24),
                            "https://hooks.slack.com/services/********"});
        }

        @ParameterizedTest
        @MethodSource("apiKeys")
        @DisplayName("masks provider API keys keeping the prefix")
        void apiKeys(String key, String expected) {
            assertThat(LogMasker.maskMessage("using " + key + " now")).isEqualTo("using " + expected + " now");
        }

        @ParameterizedTest
        @DisplayName("leaves look-alikes of API keys alone")
        @ValueSource(strings = {"task_live_update done", "AKIA prefix only", "sk_live_short", "ghp_too_short"})
        void apiKeyLookAlikes(String input) {
            assertThat(LogMasker.maskMessage(input)).isEqualTo(input);
        }
    }

    @Nested
    @DisplayName("Sensitive keys")
    class SensitiveKeys {

        @ParameterizedTest
        @DisplayName("masks values of sensitive keys in every common syntax")
        @CsvSource(delimiter = '|', value = {
                "password=hunter2 user=bob | password=******** user=bob",
                "{\"password\":\"s3cr3t pass\",\"user\":\"bob\"} | {\"password\":\"********\",\"user\":\"bob\"}",
                "{\\\"apiKey\\\":\\\"abc123\\\"} | {\\\"apiKey\\\":\\\"********\\\"}",
                "GET /cb?code=1&access_token=abc123&state=x | GET /cb?code=1&access_token=********&state=x",
                "X-Api-Key: abc123 | X-Api-Key: ********",
                "dbPassword: changeit | dbPassword: ********",
                "spring.datasource.password=changeit | spring.datasource.password=********",
                "Cookie: JSESSIONID=abc123; theme=dark | Cookie: ********; theme=dark",
                "User{name='Bob', pin='1234'} | User{name='Bob', pin='********'}",
                "<password>s3cr3t</password> | <password>********</password>",
                "<ns:Token type=\"x\">abc</ns:Token> | <ns:Token type=\"x\">********</ns:Token>",
                "jdbc:sqlserver://h;user=sa;password=Secret123; | jdbc:sqlserver://h;user=sa;password=********;",
                "X-Amz-Signature=abc123def&X-Amz-Credential=AKID | X-Amz-Signature=********&X-Amz-Credential=********"
        })
        void keyValues(String input, String expected) {
            assertThat(LogMasker.maskMessage(input)).isEqualTo(expected);
        }

        @ParameterizedTest
        @DisplayName("keeps non-sensitive keys and prose")
        @ValueSource(strings = {"tokenType=Bearer expiresIn=3600 status: 200",
                "the password was reset", "cacheKey=users:1 at 12:30:45", "https://host/path?page=2",
                "spin=3 compass=north"})
        void nonSensitive(String input) {
            assertThat(LogMasker.maskMessage(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("session ids become stable pseudonyms when a pseudonym key is configured")
        void sessionPseudonyms() {
            LogMasker masker = LogMasker.builder()
                    .config(MaskingConfig.create().setPseudonymKey("0123456789abcdef".getBytes()))
                    .build();
            String first = masker.mask("sessionId=abc123 user=bob");
            String again = masker.mask("JSESSIONID=abc123");

            assertThat(first).matches("sessionId=[0-9a-f]{16} user=bob");
            assertThat(again.substring("JSESSIONID=".length())).isEqualTo(first.substring("sessionId=".length(), 26));
            assertThat(masker.mask("password=abc123")).isEqualTo("password=********");
            assertThat(LogMasker.maskMessage("sessionId=abc123")).isEqualTo("sessionId=********");
        }

        @Test
        @DisplayName("masks Azure storage account keys in connection strings")
        void azureAccountKey() {
            assertThat(LogMasker.maskMessage("DefaultEndpointsProtocol=https;AccountName=acme;AccountKey=bG9uZ2tleQ==;"))
                    .isEqualTo("DefaultEndpointsProtocol=https;AccountName=acme;AccountKey=********;");
        }

        @Test
        @DisplayName("custom keys are matched like built-in ones")
        void customKeys() {
            LogMasker masker = LogMasker.builder().sensitiveKeys("membershipCode").build();
            assertThat(masker.mask("primary_membership_code=XYZ-1 other=1"))
                    .isEqualTo("primary_membership_code=******** other=1");
            assertThat(LogMasker.maskMessage("membershipCode=XYZ-1")).isEqualTo("membershipCode=XYZ-1");
        }
    }

    @Nested
    @DisplayName("Personal data")
    class PersonalData {

        @ParameterizedTest
        @DisplayName("masks check-digit validated national IDs")
        @CsvSource(delimiter = '|', value = {
                "cliente 12345678Z | cliente *****678Z",
                "NIE X1234567L | NIE *****567L",
                "CPF 529.982.247-25 | CPF **********7-25",
                "RUT 12.345.678-5 | RUT ********78-5",
                "CUIT 20-12345678-6 | CUIT *********78-6",
                "CURP GOMA850512HDFRRN07 | CURP **************RN07",
                "ref 12345678A | ref 12345678A"
        })
        void nationalIds(String input, String expected) {
            assertThat(LogMasker.maskMessage(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("skips SSN look-alikes outside assignable ranges")
        void unassignableSsn() {
            assertThat(LogMasker.maskMessage("id 000-12-3456 and 900-12-3456"))
                    .isEqualTo("id 000-12-3456 and 900-12-3456");
        }

        @Test
        @DisplayName("phone numbers and IP addresses are opt-in")
        void optInDetectors() {
            String message = "call +52 55 1234 5678 from 192.168.1.100 / 2001:db8::8a2e:7334 at 12:30:45";
            assertThat(LogMasker.maskMessage(message)).isEqualTo(message);

            LogMasker masker = LogMasker.builder().enable(Detector.PHONE, Detector.IP_ADDRESS).build();
            assertThat(masker.mask(message))
                    .isEqualTo("call ********5678 from ***.***.***.100 / ****:****::****:7334 at 12:30:45");
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("options toggle detectors")
        void options() {
            LogMasker masker = LogMasker.fromOptions(List.of("+PHONE, -EMAIL"));
            assertThat(masker.getDetectors()).contains(Detector.PHONE).doesNotContain(Detector.EMAIL);
            assertThat(LogMasker.fromOptions(null)).isSameAs(LogMasker.defaults());
        }

        @ParameterizedTest
        @DisplayName("rejects malformed options")
        @ValueSource(strings = {"PHONE", "+FOO"})
        void invalidOptions(String option) {
            assertThatThrownBy(() -> LogMasker.fromOptions(List.of(option)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("an explicit config overrides the global one")
        void explicitConfig() {
            LogMasker masker = LogMasker.builder().config(MaskingConfig.create().setDefaultMaskChar('#')).build();
            assertThat(masker.mask("password=hunter2")).isEqualTo("password=########");
            MaskingConfig.getInstance().setEnabled(false);
            assertThat(masker.mask("password=hunter2")).isEqualTo("password=########");
            assertThat(LogMasker.maskMessage("password=hunter2")).isEqualTo("password=hunter2");
        }
    }

    static Stream<String> hostileInputs() {
        int n = 100_000;
        return Stream.of("a".repeat(n), "a=".repeat(n / 2), "a:".repeat(n / 2), "eyJ".repeat(n / 3),
                "eyJaaaaaaaaaaaaa.".repeat(n / 17), "1".repeat(n), "1-".repeat(n / 2), "Bearer ".repeat(n / 7),
                "x://".repeat(n / 4), "a://a:".repeat(n / 6), "@".repeat(n),
                "-----BEGIN PRIVATE KEY-----" + "a".repeat(n), "+1 ".repeat(n / 3), "1.1.".repeat(n / 4),
                "a:a:".repeat(n / 4), "\"password\":\"" + "a".repeat(n), "<a>".repeat(n / 3),
                "<password>" + "a".repeat(n), "%B" + "1".repeat(n), ";" + "1".repeat(n) + "=",
                "ghp_".repeat(n / 4), "sk_live_".repeat(n / 8), "12345678Z ".repeat(n / 10),
                "password=".repeat(n / 9), "AKIA".repeat(n / 4), "ES66 ".repeat(n / 5));
    }

    @ParameterizedTest
    @MethodSource("hostileInputs")
    @DisplayName("every detector runs in linear time on hostile input (no ReDoS)")
    void allDetectorsLinear(String hostile) {
        LogMasker all = LogMasker.builder().enable(Detector.values()).build();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> all.mask(hostile));
    }
}
