# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

