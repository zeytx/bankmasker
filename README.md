# 🏦 BankMasker

> Lightweight Java library for masking sensitive data during JSON serialization — and beyond.

[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![Jackson](https://img.shields.io/badge/Jackson-2.x_|_3.x-blue)](https://github.com/FasterXML/jackson)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x_|_4.x-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-brightgreen)](LICENSE)
[![CI](https://github.com/zeytx/bankmasker/actions/workflows/ci.yml/badge.svg)](https://github.com/zeytx/bankmasker/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.zeytx/bankmasker-core)](https://central.sonatype.com/artifact/io.github.zeytx/bankmasker-core)

## ✨ Features

- **Zero configuration** — just annotate your fields with `@MaskData`
- **Jackson 2 and Jackson 3** — the same annotation works with `com.fasterxml.jackson` and `tools.jackson` (Spring Boot 3 and 4)
- **16 built-in mask types** — credit cards (last 4, or PCI-maximum BIN + last 4), emails, phones, IBANs, SSNs, names, passports, bank accounts, IPv4/IPv6, birth dates, keyed pseudonyms, auto-detection, and more
- **Mask by field name** — `bankmasker.fields.password=TOTAL` also covers DTOs you cannot annotate
- **Custom masking** — configurable mask character, visible start/end
- **`MaskUtils`** — use masking anywhere, and `toMaskedString()` for the `toString()` of records and DTOs
- **Log masking** — 14 detectors for secrets (private keys, JWTs, 40+ provider API keys, Bearer/Basic credentials, passwords in URLs and in `key=value`/JSON/XML) and personal data (cards, track data, IBANs, SSNs, national IDs, emails, phones, IPs) in messages, stack traces and JSON logs (Spring Boot structured logging, logstash-logback-encoder, Log4j2)
- **Fail-closed** — short values, unknown formats and arbitrary objects are fully masked, never partially leaked
- **Java records support** — works with records, classes, and methods
- **Spring Boot Starter** — auto-configuration via `application.yml`
- **Per-ObjectMapper config** — `MaskingModule` (Jackson 2) / `Jackson3MaskingModule` (Jackson 3) for multi-tenant / parallel-test scenarios
- **Audit logging** — track masked field access (SLF4J or custom)
- **Global config** — enable/disable at runtime, customizable mask character
- **JMH benchmarks** — proven performance
- **Extensible** — implement `MaskingStrategy` for custom logic

## 📦 Modules

| Module | Description |
|--------|-------------|
| `bankmasker-core` | Core library — annotations, strategies, serializer, `MaskUtils` |
| `bankmasker-logging` | Logback/Log4j2 converters that mask sensitive patterns in log messages |
| `bankmasker-spring-boot-starter` | Auto-configuration for Spring Boot |
| `bankmasker-benchmark` | JMH performance benchmarks |

## 🚀 Quick Start

### Maven

```xml
<!-- Core only (any Java project) -->
<dependency>
    <groupId>io.github.zeytx</groupId>
    <artifactId>bankmasker-core</artifactId>
    <version>1.3.0</version>
</dependency>

<!-- Spring Boot (includes core automatically) -->
<dependency>
    <groupId>io.github.zeytx</groupId>
    <artifactId>bankmasker-spring-boot-starter</artifactId>
    <version>1.3.0</version>
</dependency>
```

### Gradle

```groovy
// Core only (any Java project)
implementation 'io.github.zeytx:bankmasker-core:1.3.0'

// Spring Boot (includes core automatically)
implementation 'io.github.zeytx:bankmasker-spring-boot-starter:1.3.0'
```

### Annotate your DTO fields

```java
public class PaymentDTO {

    @MaskData(MaskType.CREDIT_CARD)
    private String cardNumber;

    @MaskData(MaskType.EMAIL)
    private String email;

    @MaskData(MaskType.PHONE)
    private String phone;

    @MaskData(MaskType.IBAN)
    private String iban;

    @MaskData(MaskType.SSN)
    private String ssn;

    @MaskData(MaskType.NAME)
    private String fullName;

    @MaskData(MaskType.DNI)
    private String nationalId;

    @MaskData(MaskType.PASSPORT)
    private String passport;

    @MaskData(MaskType.BANK_ACCOUNT)
    private String bankAccount;

    @MaskData(MaskType.IP_ADDRESS)
    private String serverIp;

    @MaskData  // defaults to TOTAL
    private String secret;
}
```

### Serialize with Jackson

```java
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(payment);
```

### Output

```json
{
  "cardNumber": "****-****-****-1234",
  "email": "jo****@example.com",
  "phone": "********5678",
  "iban": "ES******************7891",
  "ssn": "***-**-6789",
  "fullName": "J*** D**",
  "nationalId": "******3456",
  "passport": "AB****567",
  "bankAccount": "**********1234",
  "serverIp": "***.***.***.100",
  "secret": "********"
}
```

## 🎨 Custom Masking

```java
@MaskData(value = MaskType.CUSTOM, maskChar = '#', visibleStart = 2, visibleEnd = 3)
private String accountId;
// "ABCDEFGHIJK" → "AB######IJK"
```

| Parameter      | Default | Description                         |
|----------------|---------|-------------------------------------|
| `maskChar`     | `*`     | Character used for masking          |
| `visibleStart` | `0`     | Characters visible at the beginning |
| `visibleEnd`   | `0`     | Characters visible at the end       |

## 🛠️ MaskUtils — Masking in toString() & Logs

Use masking anywhere, not just during JSON serialization:

```java
// In toString()
@Override
public String toString() {
    return "User{card=" + MaskUtils.mask(cardNumber, MaskType.CREDIT_CARD) + "}";
    // → User{card=****-****-****-1234}
}

// With custom parameters
String masked = MaskUtils.mask("ABCDEFGHIJK", '#', 2, 3);
// → "AB######IJK"

// In log statements
log.info("Processing payment for card {}", MaskUtils.mask(card, MaskType.CREDIT_CARD));
```

Records' generated `toString()` and Lombok's `@ToString` print `@MaskData` fields **in clear text**, so a logged DTO leaks them. `toMaskedString()` builds the same representation with those fields masked:

```java
public record Payment(@MaskData(MaskType.CREDIT_CARD) String card, BigDecimal amount) {
    @Override
    public String toString() {
        return MaskUtils.toMaskedString(this);   // Payment[card=****-****-****-1111, amount=10.00]
    }
}
```

## 🌱 Spring Boot Configuration

Add the starter and configure via `application.yml`:

```yaml
bankmasker:
  enabled: true                # globally enable/disable masking
  default-mask-char: '*'       # default mask character
  pseudonym-key: ${BANKMASKER_PSEUDONYM_KEY}   # secret for PSEUDONYM (>= 16 chars)
  fields:                      # mask properties by name, no annotation needed
    password: TOTAL
    cardNumber: CREDIT_CARD    # also matches card_number
  audit:
    enabled: true              # enable SLF4J audit logging
    level: INFO                # audit log level (TRACE/DEBUG/INFO/WARN/ERROR)
```

The starter auto-configures `MaskingConfig`, optionally enables SLF4J-based audit logging, and registers `MaskingModule` / `Jackson3MaskingModule` beans that Spring Boot adds to its JSON mapper, so `bankmasker.fields` rules apply to every response. It works with Jackson 2, Jackson 3 or both on the classpath, backs off if you declare your own `MaskingConfig` or module bean, logs a `WARN` at startup when masking is disabled or HTTP responses use Gson/JSON-B (which ignore `@MaskData`), and fails fast on an invalid mask character or a short pseudonym key.

## ⚙️ Global Configuration (without Spring)

```java
MaskingConfig.getInstance()
    .setEnabled(true)
    .setDefaultMaskChar('#')
    .setAuditLogger((field, type) ->
        log.info("Masked '{}' with {}", field, type));
```

## 📝 Audit Logging

Track every masked field for compliance:

```java
// Using built-in SLF4J logger
MaskingConfig.getInstance()
    .setAuditLogger(new Slf4jMaskingAuditLogger());

// Or custom
MaskingConfig.getInstance()
    .setAuditLogger((field, type) ->
        auditService.record(field, type, Instant.now()));
```

## 📋 Built-in Mask Types

| Type           | Input                          | Output                         |
|----------------|--------------------------------|--------------------------------|
| `CREDIT_CARD`  | `4111111111111111`             | `****-****-****-1111`          |
| `EMAIL`        | `john.doe@example.com`         | `jo****@example.com`           |
| `PHONE`        | `+525512345678`                | `********5678`                 |
| `DNI`          | `ABCD123456`                   | `******3456`                   |
| `IBAN`         | `ES6621000418401234567891`     | `ES******************7891`     |
| `SSN`          | `123-45-6789`                  | `***-**-6789`                  |
| `NAME`         | `John Doe`                     | `J*** D**`                     |
| `PASSPORT`     | `AB1234567`                    | `AB****567`                    |
| `BANK_ACCOUNT` | `12345678901234`               | `**********1234`               |
| `IP_ADDRESS`   | `192.168.1.100` / `2001:db8::8a2e:7334` | `***.***.***.100` / `****:****::****:7334` |
| `AUTO`         | *(card / email / IBAN / SSN / national ID / phone)* | *(matching mask, else total)* |
| `TOTAL`        | `anything`                     | `********`                     |
| `CUSTOM`       | *(configurable)*               | *(configurable)*               |
| `CREDIT_CARD_BIN` | `4111111111111111`          | `411111******1111`             |
| `BIRTH_DATE`   | `1990-05-12`                   | `1990-**-**`                   |
| `PSEUDONYM`    | `CUS-123456`                   | `5f1c0a7be2d94c38` *(keyed HMAC, 16 hex)* |

- `CREDIT_CARD_BIN` shows the BIN and last 4 digits, the most PCI DSS (req. 3.4.1) allows to display; use it only where that much is justified (support, fraud) and `CREDIT_CARD` otherwise.
- `PSEUDONYM` replaces a value with a stable HMAC-SHA256 pseudonym, so events can be correlated (a session or customer id across log lines) without exposing it, as the OWASP Logging Cheat Sheet recommends. It needs `MaskingConfig.setPseudonymKey(...)` / `bankmasker.pseudonym-key`; without a key values are fully masked.
- `AUTO` only recognizes SSNs and phone numbers with their usual separators and national IDs with a valid check digit; bare digit runs stay fully masked.

Types that keep the last characters visible **fail closed**: if the visible part would be as large as the hidden one, the value is fully masked (e.g. `CREDIT_CARD` on `12345` → `****`).

`@MaskData` supports `String` and other scalars (`Number`, `UUID`, `char[]`, dates, enums), `Optional` of those, and collections/maps/arrays of them (masked element by element). Any other object is fully masked — never through its `toString()`, which could expose other fields.

Map keys stay visible by default (they are usually structural, like `"apiKey"`). When the keys are sensitive themselves, mask them with their own type:

```java
@MaskData(value = MaskType.TOTAL, keyMask = MaskType.CREDIT_CARD)
private Map<String, BigDecimal> balanceByCard;
// → {"****-****-****-1111":"********", "****-****-****-1111~2":"********"}
```

Keys that mask to the same value get a `~2`, `~3`, … suffix so no entry is lost.

## 🔀 Per-ObjectMapper Configuration

For multi-tenant apps or parallel tests, use `MaskingModule` instead of the global singleton:

```java
MaskingConfig tenantConfig = MaskingConfig.create()
    .setEnabled(true)
    .setDefaultMaskChar('#');

ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new MaskingModule(tenantConfig));

// This mapper uses '#' while others keep using '*'
```

With Jackson 3 the mapper is immutable, so register the module on the builder:

```java
JsonMapper mapper = JsonMapper.builder()
    .addModule(new Jackson3MaskingModule(tenantConfig))
    .build();
```

## 🧩 Jackson 2 & Jackson 3

`@MaskData` works with **Jackson 2** (`com.fasterxml.jackson`, Spring Boot 3) and **Jackson 3** (`tools.jackson`, Spring Boot 4) with no extra setup: it carries the `@JsonSerialize` of both versions, and each Jackson picks up its own. Have either one (or both) on the classpath.

If you compile with `-Xlint:all -Werror` and only one Jackson version, javac reports the other version's missing annotation (`[classfile] Cannot find annotation method 'using()'`); add `-Xlint:-classfile`. The default javac settings print nothing.

## ☕ Java Records

`@MaskData` works on Java records out of the box:

```java
public record PaymentRecord(
    @MaskData(MaskType.CREDIT_CARD) String cardNumber,
    @MaskData(MaskType.EMAIL) String email,
    String plainField
) {}
```

## 🪵 Log Masking

The optional `bankmasker-logging` module finds and masks sensitive data in free-text log messages, stack traces and JSON logs. Detection favors precision so ordinary log content survives: card numbers must pass Luhn, national IDs their check digit, API keys need a provider prefix. Every pattern runs in linear time, so hostile input cannot stall logging (ReDoS).

| Detector | Masks | Example output |
|---|---|---|
| `PRIVATE_KEY` | PEM private keys | `-----BEGIN RSA PRIVATE KEY-----********-----END RSA PRIVATE KEY-----` |
| `TRACK_DATA` | Card track 1/2 data (PCI sensitive authentication data) | `********` |
| `URL_CREDENTIALS` | Passwords in URLs and connection strings | `jdbc:postgresql://admin:********@db/app` |
| `JWT` | JSON Web Tokens (header kept) | `eyJhbGciOiJIUzI1NiJ9.********` |
| `AUTH_HEADER` | `Bearer` / `Basic` / `Digest` / `Negotiate` credentials | `Authorization: Bearer ********` |
| `API_KEY` | 40+ provider keys (AWS, GitHub, GitLab, Slack, Stripe, Google, OpenAI, Anthropic, SendGrid, Twilio, npm, PyPI, Vault…), prefix kept | `ghp_********`, `sk_live_********` |
| `KEY_VALUE` | Values of `password`, `token`, `secret`, `apiKey`, `authorization`, `cookie`, `sessionId`, `cvv`, `pin`… in `k=v`, `k: v`, JSON, query strings and XML | `{"password":"********"}` |
| `IBAN` | IBANs, contiguous or in groups of 4 | `ES******************7891` |
| `PAN` | Luhn-valid card numbers | `****-****-****-1111` |
| `SSN` | US SSNs in assignable ranges | `***-**-6789` |
| `NATIONAL_ID` | Spanish DNI/NIE, Brazilian CPF, Chilean RUT, Argentine CUIT/CUIL, Mexican CURP (check digit validated) | `*****678Z` |
| `EMAIL` | Email addresses | `jo****@mail.com` |
| `PHONE` *(opt-in)* | International numbers (`+…`) | `********5678` |
| `IP_ADDRESS` *(opt-in)* | IPv4 and IPv6 addresses | `***.***.***.100` |

**Logback** (`logback.xml`):

```xml
<conversionRule conversionWord="maskedMsg"
                converterClass="io.github.zeytx.bankmasker.logging.logback.MaskingMessageConverter"/>
<conversionRule conversionWord="maskedEx"
                converterClass="io.github.zeytx.bankmasker.logging.logback.MaskingThrowableProxyConverter"/>
<!-- then use %maskedMsg instead of %msg and %maskedEx instead of %ex -->
<pattern>%d %-5level %logger - %maskedMsg%n%maskedEx</pattern>
```

**Log4j2** (`log4j2.xml`): use `%maskedMsg` and `%maskedEx` in the pattern layout (both converters are auto-discovered via their plugin annotation).

> ⚠️ Always add `%maskedEx`: if the pattern has no throwable converter, Logback and Log4j2 append the stack trace **unmasked**, and exception messages often carry the same data (`Invalid card 4111…`).

Toggle detectors with converter options: `%maskedMsg{+PHONE, -EMAIL}` in Logback, `%maskedMsg{+PHONE,-EMAIL}` in Log4j2, and `%maskedEx{short, +PHONE}` alongside the usual `%ex` options.

**JSON logs**, where `%maskedMsg` does not apply:

- **Spring Boot 3.4+ structured logging** (ECS, Logstash, GELF): masks every string value (message, MDC, stack trace).

  ```yaml
  logging.structured.json.customizer: io.github.zeytx.bankmasker.logging.spring.MaskingJsonMembersCustomizer
  ```

- **logstash-logback-encoder 9+**:

  ```xml
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <decorator class="net.logstash.logback.mask.MaskingJsonGeneratorDecorator">
      <valueMasker class="io.github.zeytx.bankmasker.logging.logback.MaskingValueMasker"/>
    </decorator>
  </encoder>
  ```

- **Log4j2 JSON layouts**: a rewrite appender masks the message and MDC before any layout.

  ```xml
  <Rewrite name="Masked">
    <MaskingRewritePolicy detectors="+PHONE"/>   <!-- detectors is optional -->
    <AppenderRef ref="Json"/>
  </Rewrite>
  ```

Or programmatically for any other sink:

```java
String safe = LogMasker.maskMessage("charge card 4111111111111111 approved");
// → "charge card ****-****-****-1111 approved"

LogMasker masker = LogMasker.builder()
    .enable(LogMasker.Detector.PHONE)
    .sensitiveKeys("membershipCode")      // extra keys for KEY_VALUE
    .config(MaskingConfig.create().setDefaultMaskChar('#'))
    .build();
```

## 🔌 Extensibility

Implement `MaskingStrategy` for fully custom logic:

```java
MaskingStrategy myStrategy = value -> value.charAt(0) + "***";
```

## 🏎️ Benchmarks

Run JMH benchmarks to measure serialization overhead:

```bash
mvn -pl bankmasker-benchmark -am package -DskipTests
java -jar bankmasker-benchmark/target/bankmasker-benchmark-*.jar
```

Reference results (JDK 25, AMD Ryzen 7 5800XT, 1 fork, 5 × 2 s iterations; higher is better):

| Benchmark | Throughput | Per op |
|---|---|---|
| DTO with 11 fields, none masked (baseline) | 2,462 ops/ms | 0.41 µs |
| Same DTO, 10 fields masked | 647 ops/ms | 1.55 µs |
| Same DTO, 10 fields masked — Jackson 3 | 649 ops/ms | 1.54 µs |
| Single `CREDIT_CARD` field | 4,866 ops/ms | 0.21 µs |
| `AUTO` detection | 822 ops/ms | 1.22 µs |
| `LogMasker` — clean message (no match, all 12 default detectors) | 2,448 ops/ms | 0.41 µs |
| `LogMasker` — PAN in message | 309 ops/ms | 3.2 µs |
| `LogMasker` — PAN + email + IBAN | 117 ops/ms | 8.5 µs |
| `LogMasker` — JSON body with password, card and PIN | 105 ops/ms | 9.5 µs |
| `LogMasker` — Bearer token + JWT + AWS key | 74 ops/ms | 13.5 µs |
| `LogMasker` — 10 KB token without `@` | 6.2 ops/ms | 0.16 ms |

`LogMasker` checks cheap traits of each message (digit count, long tokens, `=`, `@`…) in one pass and skips every detector that cannot match, so the common case — a log line with nothing sensitive — costs well under a microsecond.

## 🧪 Running Tests

```bash
mvn clean verify
```

Coverage report: `bankmasker-core/target/site/jacoco/index.html`

## 📁 Project Structure

```
bankmasker/
├── pom.xml                              ← Parent POM
├── bankmasker-core/                     ← Core library
│   ├── pom.xml
│   └── src/main/java/.../bankmasker/
│       ├── MaskData.java                ← @MaskData annotation
│       ├── MaskType.java                ← Built-in mask types (13)
│       ├── MaskingSerializer.java       ← Jackson 2 serializer
│       ├── Jackson3MaskingSerializer.java ← Jackson 3 serializer
│       ├── MaskingSupport.java          ← Shared masking logic (no Jackson types)
│       ├── MaskingStrategy.java         ← Strategy interface
│       ├── MaskUtils.java              ← Programmatic masking
│       ├── MaskingConfig.java          ← Global + per-mapper config
│       ├── MaskingModule.java          ← Per-ObjectMapper module (Jackson 2)
│       ├── Jackson3MaskingModule.java  ← Per-mapper module (Jackson 3)
│       ├── MaskPatterns.java           ← Format detection (card/email/IBAN)
│       ├── MaskingAuditLogger.java     ← Audit interface
│       └── Slf4jMaskingAuditLogger.java ← SLF4J audit impl
├── bankmasker-logging/                  ← Logback/Log4j2 log masking
│   └── src/main/java/.../logging/
│       ├── LogMasker.java
│       ├── logback/MaskingMessageConverter.java
│       ├── logback/MaskingThrowableProxyConverter.java
│       ├── logback/MaskingValueMasker.java       ← logstash-logback-encoder 9+
│       ├── log4j2/MaskingMessagePatternConverter.java
│       ├── log4j2/MaskingThrowablePatternConverter.java
│       ├── log4j2/MaskingRewritePolicy.java      ← Log4j2 JSON layouts
│       └── spring/MaskingJsonMembersCustomizer.java ← Spring Boot structured logging
├── bankmasker-spring-boot-starter/      ← Spring Boot auto-config
│   ├── pom.xml
│   └── src/main/java/.../spring/
│       ├── BankMaskerAutoConfiguration.java
│       └── BankMaskerProperties.java
├── bankmasker-benchmark/                ← JMH benchmarks
│   └── src/main/java/.../benchmark/
│       └── MaskingSerializerBenchmark.java
├── .github/workflows/ci.yml            ← GitHub Actions CI
├── .github/copilot-instructions.md     ← GitHub Copilot context
├── docs/                                ← GitHub Pages site
│   └── index.html
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE
├── README.md
├── SECURITY.md                          ← Vulnerability reporting
├── llms.txt                             ← AI/LLM quick reference
└── llms-full.txt                        ← AI/LLM full API docs
```

## 🤖 AI/LLM Integration

This project includes [`llms.txt`](llms.txt) and [`llms-full.txt`](llms-full.txt) files following the [llms.txt standard](https://llmstxt.org/). These files help AI assistants (ChatGPT, Claude, Copilot, Gemini, etc.) understand how to use BankMasker correctly when generating code.

If you're building with an AI assistant, point it to `llms.txt` for quick reference or `llms-full.txt` for complete API documentation.

## 🔒 Security

See [SECURITY.md](SECURITY.md) for how to report a vulnerability and the library's scope and limitations.

## 📄 License

MIT © zeytx

