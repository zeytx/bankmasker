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
- Log masking is pattern-based (Luhn-validated PANs, emails, IBANs, SSNs). It
  does not detect arbitrary secrets; use `%maskedMsg` **and** `%maskedEx`, or
  stack traces are written unmasked.
- Map keys are written as is unless `@MaskData(keyMask = …)` is set.
