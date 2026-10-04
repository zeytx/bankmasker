# 🏦 BankMasker

> Lightweight Java library for masking sensitive data during JSON serialization — and beyond.

[![Java](https://img.shields.io/badge/Java-17+-orange)](https://openjdk.org/)
[![Jackson](https://img.shields.io/badge/Jackson-2.18-blue)](https://github.com/FasterXML/jackson)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-brightgreen)](LICENSE)
[![CI](https://github.com/zeytx/bankmasker/actions/workflows/ci.yml/badge.svg)](https://github.com/zeytx/bankmasker/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.zeytx/bankmasker-core)](https://central.sonatype.com/artifact/io.github.zeytx/bankmasker-core)

## ✨ Features

- **Zero configuration** — just annotate your fields with `@MaskData`
- **13 built-in mask types** — credit cards, emails, phones, IBANs, SSNs, names, passports, bank accounts, IPs, auto-detection, and more
- **Custom masking** — configurable mask character, visible start/end
- **`MaskUtils`** — use masking in `toString()`, logs, or anywhere
- **Log masking** — Logback/Log4j2 converters that scrub PANs, emails, IBANs and SSNs from log messages and stack traces
- **Fail-closed** — short values, unknown formats and arbitrary objects are fully masked, never partially leaked
- **Java records support** — works with records, classes, and methods
- **Spring Boot Starter** — auto-configuration via `application.yml`
- **Per-ObjectMapper config** — `MaskingModule` for multi-tenant / parallel-test scenarios
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
    <version>1.1.0</version>
</dependency>

<!-- Spring Boot (includes core automatically) -->
<dependency>
    <groupId>io.github.zeytx</groupId>
    <artifactId>bankmasker-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Gradle

```groovy
// Core only (any Java project)
implementation 'io.github.zeytx:bankmasker-core:1.1.0'

// Spring Boot (includes core automatically)
implementation 'io.github.zeytx:bankmasker-spring-boot-starter:1.1.0'
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

## 🌱 Spring Boot Configuration

Add the starter and configure via `application.yml`:

```yaml
bankmasker:
  enabled: true                # globally enable/disable masking
  default-mask-char: '*'       # default mask character
  audit:
    enabled: true              # enable SLF4J audit logging
    level: INFO                # audit log level (TRACE/DEBUG/INFO/WARN/ERROR)
```

The starter auto-configures `MaskingConfig` and optionally enables SLF4J-based audit logging. It backs off if you declare your own `MaskingConfig` bean, logs a `WARN` at startup when masking is disabled, and fails fast on an invalid mask character (control characters or surrogates).

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
| `IP_ADDRESS`   | `192.168.1.100`                | `***.***.***.100`               |
| `AUTO`         | *(card / email / IBAN)*        | *(matching mask, else total)*  |
| `TOTAL`        | `anything`                     | `********`                     |
| `CUSTOM`       | *(configurable)*               | *(configurable)*               |

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

The optional `bankmasker-logging` module scrubs sensitive patterns (Luhn-validated card numbers, emails, IBANs — contiguous or printed in groups of 4 — and SSNs) from free-text log messages.

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

Or programmatically for any other sink:

```java
String safe = LogMasker.maskMessage("charge card 4111111111111111 approved");
// → "charge card ****-****-****-1111 approved"
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
| DTO with 11 fields, none masked (baseline) | 2,364 ops/ms | 0.42 µs |
| Same DTO, 10 fields masked | 588 ops/ms | 1.70 µs |
| Single `CREDIT_CARD` field | 4,357 ops/ms | 0.23 µs |
| `AUTO` detection | 747 ops/ms | 1.34 µs |
| `LogMasker` — clean message (no match) | 341 ops/ms | 2.9 µs |
| `LogMasker` — PAN in message | 289 ops/ms | 3.5 µs |
| `LogMasker` — PAN + email + IBAN | 194 ops/ms | 5.2 µs |
| `LogMasker` — 10 KB token without `@` | 3.5 ops/ms | 0.29 ms |

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
│       ├── MaskingSerializer.java       ← Jackson serializer
│       ├── MaskingStrategy.java         ← Strategy interface
│       ├── MaskUtils.java              ← Programmatic masking
│       ├── MaskingConfig.java          ← Global + per-mapper config
│       ├── MaskingModule.java          ← Per-ObjectMapper module
│       ├── MaskPatterns.java           ← Format detection (card/email/IBAN)
│       ├── MaskingAuditLogger.java     ← Audit interface
│       └── Slf4jMaskingAuditLogger.java ← SLF4J audit impl
├── bankmasker-logging/                  ← Logback/Log4j2 log masking
│   └── src/main/java/.../logging/
│       ├── LogMasker.java
│       ├── logback/MaskingMessageConverter.java
│       ├── logback/MaskingThrowableProxyConverter.java
│       ├── log4j2/MaskingMessagePatternConverter.java
│       └── log4j2/MaskingThrowablePatternConverter.java
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

