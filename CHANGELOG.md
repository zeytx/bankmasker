# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- CI builds the Maven Central bundle on every change, without signing or uploading it, and checks it against Central's layout rules (a `.pom` in every directory, sources and javadoc for every jar, no local-repository files): a bundle Central would reject now fails a pull request instead of a release
- CodeQL analysis (`security-extended` queries) on every change and weekly; OpenSSF Scorecard on `main` with a README badge

### Changed
- The Maven Wrapper pins Maven 3.9.16 (SHA-256 verified) for CI, releases and contributors, so a runner upgrade can no longer change the build: the GitHub runner's move to Maven 3.10 is what broke the 1.3.0 release

### Security
- Releases publish a CycloneDX SBOM per module (Maven Central and GitHub release) and a Sigstore build provenance attestation for the jars, verifiable with `gh attestation verify <jar> --repo zeytx/bankmasker`

## [1.3.1] — 2026-10-30

First release on Maven Central of the 1.3.0 changes below: the `v1.3.0` tag exists, but its release failed and 1.3.0 was never published.

### Fixed
- Release: Central rejected the bundle ("Bundle has content that does NOT have a .pom file") because Maven 3.10 writes local-repository files (`maven-metadata-local.xml`, `_remote.repositories`, `.locks`) into the staging directory; `central-publishing-maven-plugin` 0.12.0 removes them

### Changed
- Built against Jackson 2.22.4 and 3.2.4 (both `provided`)

## [1.3.0] — 2026-10-30 (tagged, not published)

### Added
- **Secrets in logs** (`bankmasker-logging`): detectors for PEM private keys, magnetic stripe track data, passwords in URLs and connection strings, JWTs (header kept), `Bearer`/`Basic`/`Digest`/`Negotiate` credentials, 40+ provider API keys with a recognizable prefix (AWS, GitHub, GitLab, Slack, Stripe, Google, OpenAI, Anthropic, SendGrid, Twilio, npm, PyPI, Vault…, after the gitleaks rules) and values of sensitive keys (`password`, `token`, `secret`, `apiKey`, `authorization`, `cookie`, `sessionId`, `cvv`, `pin`…) in `key=value`, `key: value`, JSON (also escaped JSON-in-JSON), query strings and XML
- **National IDs** with check-digit validation (Spanish DNI/NIE, Brazilian CPF, Chilean RUT, Argentine CUIT/CUIL, Mexican CURP) in logs (`NATIONAL_ID` detector) and `MaskType.AUTO`; opt-in `PHONE` and `IP_ADDRESS` (IPv4/IPv6) log detectors
- **Configurable `LogMasker`**: `LogMasker.builder()` (enable/disable detectors, extra sensitive keys, explicit `MaskingConfig`) and converter options `%maskedMsg{+PHONE,-EMAIL}`, `%maskedEx{short,+PHONE}`
- **JSON logs**: Spring Boot 3.4+ structured logging (`MaskingJsonMembersCustomizer`), logstash-logback-encoder 9+ (`MaskingValueMasker`) and Log4j2 JSON layouts (`MaskingRewritePolicy`)
- `MaskType.CREDIT_CARD_BIN` (BIN + last 4, the PCI DSS 3.4.1 display maximum), `BIRTH_DATE` (year only) and `PSEUDONYM` (keyed HMAC-SHA256: correlate events without exposing values, as the OWASP Logging Cheat Sheet recommends for session ids)
- **Mask by property name** without annotations: `MaskingConfig.maskField(...)` / `bankmasker.fields.*`, applied by both Jackson modules; the starter registers the modules as beans so Spring Boot's JSON mapper picks them up
- `MaskUtils.toMaskedString(Object)` for the `toString()` of records and DTOs (their generated `toString()` printed `@MaskData` fields in clear text), and `MaskUtils.mask(value, type, config)`
- `MaskType.AUTO` detects SSNs, national IDs and phone numbers; `IP_ADDRESS` masks IPv6
- `MaskPatterns.isSsn`, `isPhone` and `isNationalId`
- Starter: `bankmasker.pseudonym-key`, `bankmasker.fields`, and a `WARN` when HTTP responses use Gson or JSON-B, which ignore `@MaskData`

### Changed
- `LogMasker` gathers cheap traits of each message in one pass and skips every detector that cannot match: a message with nothing sensitive is ~7x faster than in 1.2.0 despite 3x more detectors
- With a pseudonym key configured, session ids found by `KEY_VALUE` become their `PSEUDONYM` instead of `********`, so a session's requests can be correlated
- The SSN log detector only masks assignable SSNs (not area 000/666/9xx, group 00 or serial 0000)
- Jackson 2 and Jackson 3 versions are managed through their BOMs

### Security
- `MaskingConfig.setDefaultMaskChar` rejects `"` and `\`: masked text is spliced into JSON and quoted values in logs

### Fixed
- Spring Boot starter: the auto-configuration was skipped on classpaths without Jackson 2 (Spring Boot 4 with Jackson 3 only), so every `bankmasker.*` property was ignored; it now only requires `MaskingConfig`

## [1.2.0] — 2026-10-06

### Added
- **Jackson 3 (`tools.jackson`) support, Spring Boot 4 included.** `@MaskData` now works with Jackson 2 and Jackson 3 with no module or configuration: the annotation carries both versions' `@JsonSerialize`, and each Jackson reads its own (the JVM ignores the one whose classes are absent). Both are `provided` dependencies
- `Jackson3MaskingSerializer` and `Jackson3MaskingModule` (per-mapper config on `JsonMapper.builder()`)
- CI job testing the starter against Spring Boot 4 (`-Pspring-boot-4`); the starter is still built against Spring Boot 3.x, the oldest supported line
- Jackson 3 JMH benchmarks

### Changed
- The starter no longer warns about Jackson 3: the configured `MaskingConfig` applies to Jackson 2 and Jackson 3 mappers
- Masking logic moved to an internal, Jackson-independent `MaskingSupport` shared by both serializers; Jackson 3 output is verified to match Jackson 2 for every supported field type and configuration

### Notes
- With `-Xlint:all -Werror` and a single Jackson version on the classpath, javac reports the other version's missing annotation (`[classfile]` lint); add `-Xlint:-classfile`. Default javac settings print nothing

## [1.1.1] — 2026-10-04

### Security
- **Jackson 3 / Spring Boot 4 warning**: `@MaskData` is only applied by Jackson 2; Jackson 3 (`tools.jackson`, the Spring Boot 4 default for HTTP responses) ignores it and serializes values in clear text. The starter now logs a `WARN` at startup when Jackson 3 is on the classpath, and the limitation is documented. Native Jackson 3 support is planned for 1.2.0

### Changed
- Compiled and tested against the latest compatible versions: Jackson 2.22, Spring Boot 3.5, Logback 1.6, Log4j 2.26, SLF4J 2.0.20 (all `provided`/test scope, nothing new is pulled transitively)
- Dependabot groups minor/patch updates and opens a separate PR per major update
- Tests run on JUnit 6; GitHub Actions upgraded (checkout 7, setup-java 6, upload-artifact 7, action-gh-release 3)

### Fixed
- Module POMs published to Central linked to `https://github.com/zeytx/bankmasker/<module>` (404): project and SCM URLs are no longer suffixed with the artifactId; SCM connections use valid HTTPS/SSH URLs
- Release workflow failed after publishing because `central-publishing-maven-plugin` 0.7.0 could not parse the current Central API response; upgraded to 0.11.0

## [1.1.0] — 2026-10-04

### Added
- **4 new mask types**: `PASSPORT`, `BANK_ACCOUNT`, `IP_ADDRESS`, `AUTO` (detects card/email/IBAN and applies the matching mask, total mask otherwise)
- **`bankmasker-logging` module** — Logback/Log4j2 converters and `LogMasker` that scrub Luhn-validated PANs, emails, IBANs (contiguous or printed in groups of 4) and SSNs from free-text log messages
- `MaskPatterns` detection helpers (`isCreditCard`, `isEmail`, `isIban`, `passesLuhn`)
- Non-String field support in `@MaskData`: `Number`, `UUID`, `char[]` (treated as a single secret)
- Collections, maps and arrays are masked element by element, preserving JSON structure
- `Slf4jMaskingAuditLogger` accepts a configurable SLF4J level; new `bankmasker.audit.level` property in the Spring Boot starter
- **`%maskedEx` throwable converters** for Logback (`MaskingThrowableProxyConverter`) and Log4j2 (`MaskingThrowablePatternConverter`): mask exception messages, causes and stack traces, which were otherwise appended unmasked
- `@MaskData(keyMask = …)` masks `Map` keys with their own type (e.g. maps keyed by card number); colliding masked keys get a `~2`, `~3`, … suffix
- `Optional` fields are unwrapped and their content masked (`Optional.empty()` → `null`)
- `@JsonInclude(NON_EMPTY)` / `NON_ABSENT` skip empty masked values (empty strings, collections, maps, arrays, `Optional`)
- Spring Boot starter backs off when the application declares its own `MaskingConfig` bean (`@ConditionalOnMissingBean`) and logs a `WARN` at startup when `bankmasker.enabled=false`
- `Automatic-Module-Name` in every published jar (`io.github.zeytx.bankmasker`, `.logging`, `.spring`) for JPMS users
- `SECURITY.md` with the vulnerability reporting process, Dependabot for Maven and GitHub Actions
- JMH benchmarks for `LogMasker` (clean/PAN/mixed/Luhn-invalid paths) and `MaskType.AUTO`
- CI matrix now also builds on Java 25
- **Per-ObjectMapper configuration** via `MaskingModule` — useful for multi-tenant apps and parallel tests
- `MaskingConfig.create()` factory method for non-singleton instances
- Java **records** support — `@MaskData` now targets `RECORD_COMPONENT`
- Spring Boot Starter **tests** with `ApplicationContextRunner`
- `CHANGELOG.md` and `CONTRIBUTING.md`

### Changed
- `BankMaskerProperties` is now bound via `@ConfigurationProperties` on the class (instead of a `@Bean` factory), so the configuration processor generates IDE metadata for `bankmasker.*` properties
- **`defaultMaskChar` now respected by all built-in `MaskType` strategies** — previously `'*'` was hardcoded; now all strategies read from `MaskingConfig.getDefaultMaskChar()`
- `MaskUtils.applyCustomMask()` respects global `defaultMaskChar` when annotation uses default `'*'`
- Updated `README.md` with Gradle dependency snippets, new mask types table, and per-ObjectMapper docs
- Removed the unused Lombok dependency from `bankmasker-core`
- Built-in strategies strip digits/whitespace with a char loop instead of `String.replaceAll` (a regex compiled per call): +22-28% throughput when serializing masked DTOs

### Security
- **ReDoS in log masking**: the email pattern backtracked quadratically on long tokens without `@` (a 40 KB token took ~7 s per log line); it now runs in linear time
- **POJOs annotated with `@MaskData` are fully masked** instead of applying the strategy to their `toString()`, which could expose other fields (e.g. `EMAIL` kept everything after the `@`, `DNI` the last characters)
- **`MaskingModule` no longer shares mutable per-call state across serializations**: the per-mapper config was stored as a per-call attribute backed by a map shared by every call, so attributes set during one serialization leaked into later ones (and across threads); it also discarded default attributes already set on the mapper
- Last-characters mask types (`CREDIT_CARD`, `PHONE`, `DNI`, `IBAN`, `SSN`, `BANK_ACCOUNT`) fail closed when the visible part would be as large as the hidden one (e.g. `CREDIT_CARD` on `12345` previously revealed `2345`); `EMAIL` reveals only 1 char of 3-char local parts
- `MaskingConfig.setDefaultMaskChar` rejects control characters (e.g. `\n`, which allows forging log lines) and lone surrogates; the starter fails fast on such a `bankmasker.default-mask-char`
- `char[]` elements inside collections are masked as a single value (previously each character was an element, so `NAME` exposed every character)
- Release workflow: actions pinned to commit SHAs, `persist-credentials: false`, versions plugin pinned, tag format validated; `bankmasker-logging` jar is now attached to GitHub releases

### Fixed
- `CUSTOM` masking no longer throws on huge `visibleStart`/`visibleEnd` (int overflow)
- `defaultMaskChar` configuration had no effect on built-in mask types
- `EMAIL` never exposes 1-2 char local parts and anchors on the last `@`
- `PASSPORT` fully masks values of 6 chars or less (previously left only 1 char masked at length 6)
- `NAME` no longer emits a stray leading space for values with leading whitespace
- JaCoCo upgraded to 0.8.15 (supports modern JDKs)
- Spring Boot auto-configuration clears a previously set audit logger when `bankmasker.audit.enabled=false` (the global config is a singleton, so stale state could survive re-configuration)

## [1.0.0] — Initial Release

### Added
- `@MaskData` annotation for Jackson serialization masking
- 9 built-in mask types: `CREDIT_CARD`, `EMAIL`, `PHONE`, `DNI`, `IBAN`, `SSN`, `NAME`, `TOTAL`, `CUSTOM`
- `MaskUtils` for programmatic masking outside Jackson
- `MaskingConfig` global singleton with enable/disable toggle
- `MaskingAuditLogger` interface with `Slf4jMaskingAuditLogger` implementation
- `MaskingStrategy` functional interface for custom logic
- Spring Boot Starter with auto-configuration via `application.yml`
- JMH benchmark suite
- JaCoCo code coverage (91%+)
- GitHub Actions CI pipeline (Java 17 & 21)
- MIT License

