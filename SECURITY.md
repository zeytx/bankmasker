# Security Policy

## Supported versions

Security fixes are released for the latest minor version only.

## Reporting a vulnerability

**Do not open a public issue.** Report it privately through
[GitHub Security Advisories](https://github.com/zeytx/bankmasker/security/advisories/new).

Please include the affected version, a minimal reproduction and the impact
(e.g. which value is exposed and in which output: JSON, logs, `MaskUtils`).
Fixes are released as patch versions and credited in the advisory unless you
prefer otherwise.

## Verifying a release

Releases are built and published by GitHub Actions from a `vX.Y.Z` tag, never
from a developer machine. From 1.4.0:

- **Build provenance**: the jars carry a Sigstore attestation proving they were
  built by this repository's release workflow:

  ```bash
  gh attestation verify bankmasker-core-1.4.0.jar --repo zeytx/bankmasker
  ```

- **SBOM**: a CycloneDX SBOM per module is published to Maven Central next to
  the jars (`bankmasker-core-<version>-cyclonedx.json`) and attached to the
  GitHub release.
- **Signatures**: every artifact on Maven Central has a PGP signature (`.asc`).

## What counts as a vulnerability

BankMasker is a defense-in-depth tool, so anything that **exposes more of a
sensitive value than documented** is treated as a security issue, for example:

- a mask type revealing more characters than its documented visible window;
- sensitive data bypassing `@MaskData`, `MaskUtils` or the log converters;
- configuration or state leaking between `ObjectMapper` instances or calls;
- inputs causing excessive CPU/memory use (e.g. regex backtracking in log masking).

## Scope and limitations

- `@MaskData` is applied by Jackson 2 and Jackson 3 only. Other serializers
  (Gson, JSON-B, `toString()`) write annotated fields in clear text.
- Masking happens **only at serialization/formatting time**. Values remain in
  clear text in memory, in your database and in any `toString()` you write.
- `bankmasker.enabled=false` / `MaskingConfig.setEnabled(false)` disables
  masking globally; never ship it to production.
- Log masking is pattern-based: it finds the formats listed in the README
  (secrets with a provider prefix or a sensitive key such as `password=`,
  Luhn-valid cards, check-digit valid IDs...), not arbitrary secrets logged
  without context. Use `%maskedMsg` **and** `%maskedEx`, or stack traces are
  written unmasked; for JSON logs use the Spring Boot, logstash or Log4j2
  integrations.
- Generated `toString()` methods (records, Lombok) print `@MaskData` fields in
  clear text; use `MaskUtils.toMaskedString(this)`.
- Map keys are written as is unless `@MaskData(keyMask = …)` is set.
