# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **3 new mask types**: `PASSPORT`, `BANK_ACCOUNT`, `IP_ADDRESS`
- **Per-ObjectMapper configuration** via `MaskingModule` — useful for multi-tenant apps and parallel tests
- `MaskingConfig.create()` factory method for non-singleton instances
- Java **records** support — `@MaskData` now targets `RECORD_COMPONENT`
- Spring Boot Starter **tests** with `ApplicationContextRunner`
- `CHANGELOG.md` and `CONTRIBUTING.md`

### Changed
- **`defaultMaskChar` now respected by all built-in `MaskType` strategies** — previously `'*'` was hardcoded; now all strategies read from `MaskingConfig.getDefaultMaskChar()`
- `MaskUtils.applyCustomMask()` respects global `defaultMaskChar` when annotation uses default `'*'`
- Updated `README.md` with Gradle dependency snippets, new mask types table, and per-ObjectMapper docs

### Fixed
- `defaultMaskChar` configuration had no effect on built-in mask types

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

