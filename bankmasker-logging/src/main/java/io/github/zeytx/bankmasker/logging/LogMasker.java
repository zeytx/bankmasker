package io.github.zeytx.bankmasker.logging;

import io.github.zeytx.bankmasker.MaskPatterns;
import io.github.zeytx.bankmasker.MaskType;
import io.github.zeytx.bankmasker.MaskingConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive data found anywhere inside a free-text message: secrets
 * (private keys, JWTs, API keys, credentials in URLs, headers and
 * {@code key=value} pairs) and personal data (card numbers, IBANs, SSNs,
 * national IDs, emails).
 *
 * <p>Detection favors precision so legitimate log content survives: card
 * numbers must pass Luhn, national IDs their check digit, API keys need a
 * provider prefix, and phone numbers and IP addresses are off by default.
 * See {@link Detector} for what each detector covers.
 *
 * <p>{@link #maskMessage(String)} uses the default detectors and the global
 * {@link MaskingConfig}. Build a customized instance for anything else:
 * <pre>{@code
 * LogMasker masker = LogMasker.builder()
 *     .enable(Detector.PHONE)
 *     .disable(Detector.EMAIL)
 *     .sensitiveKeys("membershipCode")
 *     .build();
 * }</pre>
 *
 * <p>Every pattern runs in linear time (no catastrophic backtracking), and
 * each detector is skipped when a cheap pre-check rules it out, so messages
 * without sensitive data cost little. Instances are immutable and thread-safe.
 *
 * @since 1.1.0
 */
public final class LogMasker {

    /**
     * Kinds of sensitive data a {@link LogMasker} looks for, applied in
     * declaration order.
     *
     * @since 1.3.0
     */
    public enum Detector {
        /** PEM private key blocks; the BEGIN/END lines stay visible. */
        PRIVATE_KEY(true),
        /** Magnetic stripe track 1/2 data (PAN, expiry, CVV...), which PCI DSS forbids storing. */
        TRACK_DATA(true),
        /** Passwords in URLs and connection strings ({@code scheme://user:password@host}). */
        URL_CREDENTIALS(true),
        /** JSON Web Tokens; the header (algorithm, key id) stays visible. */
        JWT(true),
        /** {@code Bearer}, {@code Basic}, {@code Digest} and {@code Negotiate} credentials. */
        AUTH_HEADER(true),
        /**
         * API keys and tokens with a provider prefix (AWS, GitHub, GitLab, Slack,
         * Stripe, Google, OpenAI, Anthropic, SendGrid, Twilio, npm, PyPI, Vault...,
         * after the gitleaks rules); the prefix stays visible.
         */
        API_KEY(true),
        /**
         * Values of sensitive keys in {@code key=value}, {@code key: value}, JSON,
         * query strings and XML elements: {@code password}, {@code token},
         * {@code secret}, {@code apiKey}, {@code authorization}, {@code cookie},
         * {@code sessionId}, {@code cvv}, {@code pin}... (matched as suffixes, so
         * {@code dbPassword} and {@code X-Auth-Token} count). With a pseudonym key
         * configured, session ids become their {@link MaskType#PSEUDONYM} instead,
         * so the requests of one session can be correlated (OWASP recommendation).
         */
        KEY_VALUE(true),
        /** IBANs, contiguous or printed in groups of 4. */
        IBAN(true),
        /** Luhn-validated payment card numbers. */
        PAN(true),
        /** US Social Security Numbers (dashed, assignable ranges). */
        SSN(true),
        /** Check-digit validated national IDs: Spanish DNI/NIE, Brazilian CPF, Chilean RUT, Argentine CUIT/CUIL, Mexican CURP. */
        NATIONAL_ID(true),
        /** Email addresses. */
        EMAIL(true),
        /** International phone numbers ({@code +} prefix). Off by default: numeric ids look alike. */
        PHONE(false),
        /** IPv4 and IPv6 addresses. Off by default: usually needed for operations. */
        IP_ADDRESS(false);

        private final boolean enabledByDefault;

        Detector(boolean enabledByDefault) {
            this.enabledByDefault = enabledByDefault;
        }

        /**
         * Whether a default {@link LogMasker} uses this detector.
         *
         * @return {@code true} if enabled by default
         */
        public boolean isEnabledByDefault() {
            return enabledByDefault;
        }
    }

    private static final int SECRET_MASK_LENGTH = 8;

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(-----BEGIN [A-Z0-9 ]{0,40}?PRIVATE KEY(?: BLOCK)?-----)(.*?)"
                    + "(-----END [A-Z0-9 ]{0,40}?PRIVATE KEY(?: BLOCK)?-----|\\z)", Pattern.DOTALL);
    // Track 1 (%B<PAN>^NAME^YYMM...?) and track 2 (;<PAN>=YYMM...?) data
    private static final Pattern TRACK_DATA = Pattern.compile(
            "%?B\\d{13,19}+\\^[^^\\r\\n]{2,26}+\\^\\d{7,}+[^?\\s]*+\\??|;?(?<!\\d)\\d{13,19}+=\\d{7,}+\\??");
    // Greedy password group: backtracks to the last '@' of the authority, so "p@ss" is masked whole
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "\\b[A-Za-z][A-Za-z0-9+.\\-]{1,20}+://[^\\s/:@?#]*+:([^\\s/?#]+)@");
    private static final Pattern JWT = Pattern.compile(
            "\\b(eyJ[A-Za-z0-9_-]{10,}+)\\.ey[A-Za-z0-9_-]{10,}+\\.[A-Za-z0-9_\\-+/=]*+");
    private static final Pattern AUTH_HEADER = Pattern.compile(
            "(?i)\\b(?:bearer|basic|digest|negotiate)\\s++([A-Za-z0-9\\-._~+/]{8,}+=*+)");
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(?i)(?:bearer|basic|digest|negotiate|token|apikey)\\s++");
    private static final Pattern KEY = Pattern.compile(
            "(?<![A-Za-z0-9_.\\-])([A-Za-z0-9_.\\-]{1,64}+)\\\\?[\"']?\\s*+[:=]\\s*+");
    private static final Pattern XML_ELEMENT = Pattern.compile(
            "<([A-Za-z][A-Za-z0-9_.:\\-]{0,63}+)(?:\\s[^<>]{0,256}+)?>([^<]{1,4096}+)</\\1>");
    // Contiguous IBAN or printed groups-of-4 form; validated with MaskPatterns.isIban
    private static final Pattern IBAN = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?:[A-Za-z0-9]{11,30}|(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?)\\b");
    private static final Pattern PAN = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern NATIONAL_ID = Pattern.compile(
            "\\b(?:[XYZ]-?\\d{7}-?[A-Z]|\\d{8}-?[A-Z]|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{1,2}\\.\\d{3}\\.\\d{3}-[\\dkK]"
                    + "|(?:20|23|24|27|30|33|34)-\\d{8}-\\d|[A-Z]{4}\\d{6}[HMX][A-Z]{5}[A-Z\\d]\\d)\\b");
    // The lookbehind anchors matches at the start of a local-part run and the
    // possessive quantifier forbids backtracking into it: without them, a long
    // token with no '@' costs O(n²) (40 KB ≈ 7 s), a DoS vector for log input.
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]++@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w+])\\+\\d[\\d .()\\-]{6,22}+");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?!\\d|\\.\\d)");
    private static final Pattern IPV6 = Pattern.compile(
            "(?<![\\w:])[0-9A-Fa-f]{0,4}+(?::[0-9A-Fa-f]{0,4}+){2,7}+(?![\\w:])");

    /** Provider API keys: literal pre-checks, and group 1 is the prefix kept visible. */
    private static final List<ApiKeyPattern> API_KEYS = List.of(
            apiKey("\\b(A3T[A-Z0-9]|AKIA|ASIA|ABIA|ACCA)[A-Z2-7]{16}\\b", "A3T", "AKIA", "ASIA", "ABIA", "ACCA"),
            apiKey("\\b(gh[pousr]_)[0-9A-Za-z]{36}\\b", "ghp_", "gho_", "ghu_", "ghs_", "ghr_"),
            apiKey("\\b(github_pat_)\\w{82}\\b", "github_pat_"),
            apiKey("\\b(glpat-)[\\w-]{20,}+", "glpat-"),
            apiKey("\\b(xox[abposre]-)[0-9A-Za-z-]{10,}+", "xox"),
            apiKey("\\b(xapp-)\\d-[A-Z0-9]+-\\d+-[a-z0-9]+", "xapp-"),
            apiKey("(hooks\\.slack\\.com/(?:services|workflows|triggers)/)[A-Za-z0-9+/]{20,}+", "hooks.slack.com/"),
            apiKey("(\\.webhook\\.office\\.com/webhookb2/)[\\w@/-]{20,}+", ".webhook.office.com/"),
            apiKey("\\b((?:sk|rk)_(?:live|test|prod)_)[0-9A-Za-z]{10,99}+", "sk_", "rk_"),
            apiKey("\\b(AIza)[\\w-]{35}", "AIza"),
            apiKey("\\b(sk-ant-(?:api|admin)\\d{2}-)[\\w-]{20,}+", "sk-ant-"),
            apiKey("\\b(sk-(?:proj|svcacct|admin)-)[\\w-]{20,}+", "sk-proj-", "sk-svcacct-", "sk-admin-"),
            apiKey("\\b(sk-)[A-Za-z0-9]{20}T3BlbkFJ[A-Za-z0-9]{20}\\b", "T3BlbkFJ"),
            apiKey("\\b(SG\\.)[\\w-]{22}\\.[\\w-]{43}\\b", "SG."),
            apiKey("\\b(SK)[0-9a-fA-F]{32}\\b", "SK"),
            apiKey("\\b(npm_)[A-Za-z0-9]{36}\\b", "npm_"),
            apiKey("\\b(pypi-)AgEIcHlwaS5vcmc[\\w-]{50,}+", "pypi-"),
            apiKey("\\b(hf_)[A-Za-z]{34}\\b", "hf_"),
            apiKey("\\b(shp(?:at|ca|pa|ss)_)[a-fA-F0-9]{32}\\b", "shp"),
            apiKey("\\b(EAAA|sq0atp-|sq0csp-)[\\w-]{22,60}+", "EAAA", "sq0"),
            apiKey("\\b(do[opr]_v1_)[a-f0-9]{64}\\b", "_v1_"),
            apiKey("\\b(dp\\.pt\\.)[A-Za-z0-9]{43}\\b", "dp.pt."),
            apiKey("\\b(hvs\\.)[\\w-]{90,120}+", "hvs."),
            apiKey("\\b(dapi)[a-f0-9]{32}(?:-\\d)?\\b", "dapi"),
            apiKey("\\b(lin_api_)[A-Za-z0-9]{40}\\b", "lin_api_"),
            apiKey("\\b(PMAK-)[a-f0-9]{24}-[a-f0-9]{34}\\b", "PMAK-"),
            apiKey("\\b(pul-)[a-f0-9]{40}\\b", "pul-"),
            apiKey("\\b(AGE-SECRET-KEY-1)[QPZRY9X8GF2TVDW0S3JN54KHCE6MUA7L]{58}\\b", "AGE-SECRET-KEY-1"),
            apiKey("\\b(pplx-)[A-Za-z0-9]{48}\\b", "pplx-"),
            apiKey("\\b(glsa_)[A-Za-z0-9]{32}_[A-Fa-f0-9]{8}\\b", "glsa_"),
            apiKey("\\b(glc_)[A-Za-z0-9+/]{32,400}+={0,2}", "glc_"),
            apiKey("\\b(sntr[uys]_)[A-Za-z0-9+/=_-]{40,}+", "sntr"),
            apiKey("\\b(ntn_)\\d{11}[A-Za-z0-9]{35}\\b", "ntn_"),
            apiKey("\\b(ops_)eyJ[A-Za-z0-9+/]{250,}+={0,3}", "ops_eyJ"),
            apiKey("\\b(HRKU-)AA[0-9A-Za-z_-]{58}\\b", "HRKU-"),
            apiKey("\\b(ATATT3)[A-Za-z0-9_\\-=]{186}\\b", "ATATT3"),
            apiKey("\\b(fo1_)[\\w-]{43}\\b", "fo1_"),
            apiKey("\\b(dt0c01\\.)[A-Za-z0-9]{24}\\.[A-Za-z0-9]{64}\\b", "dt0c01."),
            apiKey("\\b(rubygems_)[a-f0-9]{48}\\b", "rubygems_"),
            apiKey("\\b(\\d{8,10}:)AA[\\w-]{33}\\b", ":AA"));

    // Normalized (lowercase, alphanumeric only) key suffixes whose values are secrets or IDs
    private static final List<String> SENSITIVE_KEY_SUFFIXES = List.of(
            "password", "passwd", "passphrase", "pwd", "secret", "token", "apikey", "accesskey",
            "secretkey", "privatekey", "signingkey", "encryptionkey", "masterkey", "authorization",
            "credential", "credentials", "cookie", "sessionid", "session", "signature", "otp",
            "accountkey", "cvv", "cvv2", "cvc", "cvc2", "securitycode", "cardpin", "cardnumber", "creditcard",
            "accountnumber", "iban", "ssn", "dni", "cpf", "curp", "taxid", "nationalid", "passport");
    // Too generic as suffixes ("bypass", "japan", "spin"), sensitive as whole keys
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "pass", "pw", "auth", "sig", "pan", "pin", "pincode", "pinnumber");

    private static final LogMasker DEFAULT = builder().build();

    private final Set<Detector> detectors;
    private final MaskingConfig config;
    private final List<String> customKeySuffixes;

    private LogMasker(Builder builder) {
        this.detectors = Collections.unmodifiableSet(EnumSet.copyOf(builder.detectors));
        this.config = builder.config;
        this.customKeySuffixes = List.copyOf(builder.keys);
    }

    /**
     * Masks all detected sensitive data in the given message with the default
     * detectors and the global {@link MaskingConfig}.
     *
     * @param message the log message, may be {@code null}
     * @return the message with sensitive data masked, or the original if
     *         masking is disabled or the message is {@code null}/empty
     */
    public static String maskMessage(String message) {
        return DEFAULT.mask(message);
    }

    /**
     * Returns the shared instance used by {@link #maskMessage(String)}.
     *
     * @return the default masker
     * @since 1.3.0
     */
    public static LogMasker defaults() {
        return DEFAULT;
    }

    /**
     * Starts building a customized masker, initially with the default detectors.
     *
     * @return a new builder
     * @since 1.3.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a masker from converter options such as {@code +PHONE} or
     * {@code -EMAIL} (several per option allowed, comma-separated). Empty or
     * {@code null} options give {@link #defaults()}.
     *
     * @param options the options, may be {@code null}
     * @return the configured masker
     * @throws IllegalArgumentException on an option that is not {@code +NAME} or {@code -NAME}
     *                                  of a {@link Detector}
     * @since 1.3.0
     */
    public static LogMasker fromOptions(Collection<String> options) {
        if (options == null || options.isEmpty()) {
            return DEFAULT;
        }
        Builder builder = builder();
        for (String option : options) {
            if (option == null) {
                continue;
            }
            for (String token : option.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (!isDetectorOption(t)) {
                    throw new IllegalArgumentException("Invalid BankMasker option '" + t
                            + "': use +DETECTOR or -DETECTOR, e.g. +PHONE");
                }
                Detector detector = parseDetector(t.substring(1).trim());
                if (t.charAt(0) == '+') {
                    builder.enable(detector);
                } else {
                    builder.disable(detector);
                }
            }
        }
        return builder.build();
    }

    /**
     * Whether a converter option configures detectors ({@code +NAME} or
     * {@code -NAME}), as opposed to the converter's own options.
     *
     * @param option the option
     * @return {@code true} if it starts with {@code +} or {@code -}
     * @since 1.3.0
     */
    public static boolean isDetectorOption(String option) {
        if (option == null) {
            return false;
        }
        String t = option.trim();
        return !t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-');
    }

    private static Detector parseDetector(String name) {
        try {
            return Detector.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown BankMasker detector '" + name + "', expected one of "
                    + EnumSet.allOf(Detector.class), e);
        }
    }

    /**
     * Returns the detectors this masker applies.
     *
     * @return an unmodifiable set, in application order
     * @since 1.3.0
     */
    public Set<Detector> getDetectors() {
        return detectors;
    }

    /**
     * Masks all detected sensitive data in the given message.
     *
     * @param message the message, may be {@code null}
     * @return the masked message, or the original if masking is disabled or
     *         the message is {@code null}/empty
     * @since 1.3.0
     */
    public String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        MaskingConfig cfg = config != null ? config : MaskingConfig.getInstance();
        if (!cfg.isEnabled()) {
            return message;
        }
        String secret = String.valueOf(cfg.getDefaultMaskChar()).repeat(SECRET_MASK_LENGTH);
        // Masking only replaces characters, so traits of the original message
        // stay valid pre-checks for every detector
        Traits traits = Traits.of(message);
        String result = message;
        for (Detector detector : detectors) {
            if (traits.mayContain(detector, result)) {
                result = apply(detector, result, cfg, secret);
            }
        }
        return result;
    }

    private String apply(Detector detector, String s, MaskingConfig cfg, String secret) {
        return switch (detector) {
            case PRIVATE_KEY -> replace(PRIVATE_KEY, s, m -> m.group(1) + secret + m.group(3));
            case TRACK_DATA -> replace(TRACK_DATA, s, m -> secret);
            case URL_CREDENTIALS -> replace(URL_CREDENTIALS, s,
                    m -> s.substring(m.start(), m.start(1)) + secret + "@");
            case JWT -> replace(JWT, s, m -> m.group(1) + "." + secret);
            case AUTH_HEADER -> replace(AUTH_HEADER, s, m -> looksLikeCredential(m.group(1)) && !isKeyOfPair(s, m)
                    ? s.substring(m.start(), m.start(1)) + secret : null);
            case API_KEY -> maskApiKeys(s, secret);
            case KEY_VALUE -> maskKeyValues(s, secret, cfg);
            case IBAN -> replace(IBAN, s, m -> MaskPatterns.isIban(m.group())
                    ? MaskType.IBAN.getStrategy().mask(m.group(), cfg) : null);
            case PAN -> replace(PAN, s, m -> MaskPatterns.passesLuhn(m.group().replace(" ", "").replace("-", ""))
                    ? MaskType.CREDIT_CARD.getStrategy().mask(m.group(), cfg) : null);
            case SSN -> replace(SSN, s, m -> MaskPatterns.isSsn(m.group())
                    ? MaskType.SSN.getStrategy().mask(m.group(), cfg) : null);
            case NATIONAL_ID -> replace(NATIONAL_ID, s, m -> MaskPatterns.isNationalId(m.group())
                    ? MaskType.DNI.getStrategy().mask(m.group(), cfg) : null);
            case EMAIL -> replace(EMAIL, s, m -> MaskType.EMAIL.getStrategy().mask(m.group(), cfg));
            case PHONE -> replace(PHONE, s, m -> maskPhone(m.group(), cfg));
            case IP_ADDRESS -> maskIpAddresses(s, cfg);
        };
    }

    /**
     * Cheap facts about a message, gathered in one pass, that rule detectors
     * out before their regular expressions run: a message without 13 digits
     * holds no card number, one without a 16-character token no API key...
     */
    private record Traits(int digits, int longestDigitRun, boolean longToken, boolean ibanPrefix,
                          boolean eq, boolean colon, boolean at, boolean plus, boolean lt,
                          boolean caret, boolean dot, boolean dash) {

        static Traits of(String s) {
            int digits = 0;
            int digitRun = 0;
            int longestDigitRun = 0;
            int tokenRun = 0;
            boolean longToken = false;
            boolean ibanPrefix = false;
            boolean eq = false;
            boolean colon = false;
            boolean at = false;
            boolean plus = false;
            boolean lt = false;
            boolean caret = false;
            boolean dot = false;
            boolean dash = false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                boolean digit = c >= '0' && c <= '9';
                if (digit) {
                    digits++;
                    longestDigitRun = Math.max(longestDigitRun, ++digitRun);
                } else {
                    digitRun = 0;
                }
                // API keys and JWT segments: long runs of token characters
                if (digit || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '-' || c == '+'
                        || c == '=') {
                    longToken |= ++tokenRun >= 16;
                } else {
                    tokenRun = 0;
                }
                // IBAN: two uppercase letters followed by two digits
                ibanPrefix |= digit && i >= 3 && Character.isDigit(s.charAt(i - 1))
                        && isUpper(s.charAt(i - 2)) && isUpper(s.charAt(i - 3));
                switch (c) {
                    case '=' -> eq = true;
                    case ':' -> colon = true;
                    case '@' -> at = true;
                    case '+' -> plus = true;
                    case '<' -> lt = true;
                    case '^' -> caret = true;
                    case '.' -> dot = true;
                    case '-' -> dash = true;
                    default -> { }
                }
            }
            return new Traits(digits, longestDigitRun, longToken, ibanPrefix, eq, colon, at, plus, lt, caret, dot, dash);
        }

        private static boolean isUpper(char c) {
            return c >= 'A' && c <= 'Z';
        }

        boolean mayContain(Detector detector, String s) {
            return switch (detector) {
                case PRIVATE_KEY -> s.contains("PRIVATE KEY");
                case TRACK_DATA -> (eq || caret) && longestDigitRun >= 13;
                case URL_CREDENTIALS -> at && s.contains("://");
                case JWT -> s.contains("eyJ");
                case AUTH_HEADER -> containsIgnoreCase(s, "bearer") || containsIgnoreCase(s, "basic")
                        || containsIgnoreCase(s, "digest") || containsIgnoreCase(s, "negotiate");
                case API_KEY -> longToken;
                case KEY_VALUE -> eq || colon || lt;
                case IBAN -> ibanPrefix;
                case PAN -> digits >= 13;
                case SSN -> dash && digits >= 9;
                // DNI/NIE/CUIT: 7+ digit runs; CURP: 6; CPF/RUT: dotted and dashed
                case NATIONAL_ID -> longestDigitRun >= 6 || (dot && dash && digits >= 7);
                case EMAIL -> at;
                case PHONE -> plus && digits >= 8;
                case IP_ADDRESS -> (dot && digits >= 4) || colon;
            };
        }

        /** {@code word} must be lowercase ASCII. */
        private static boolean containsIgnoreCase(String s, String word) {
            char first = word.charAt(0);
            for (int i = 0, last = s.length() - word.length(); i <= last; i++) {
                if ((s.charAt(i) | 0x20) == first && s.regionMatches(true, i, word, 0, word.length())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String maskApiKeys(String s, String secret) {
        String result = s;
        for (ApiKeyPattern key : API_KEYS) {
            if (key.mayMatch(result)) {
                result = replace(key.pattern(), result, m -> m.group(1) + secret);
            }
        }
        return result;
    }

    /**
     * Masks the values of sensitive keys. Keys are found with a bounded,
     * possessive pattern; values are scanned by hand so quoted values (also
     * escaped JSON-in-JSON quotes) are masked whole and an auth scheme
     * ({@code Bearer}) stays visible. Session ids become a {@link MaskType#PSEUDONYM}
     * when a pseudonym key is configured, so requests can still be correlated.
     */
    private String maskKeyValues(String s, String secret, MaskingConfig cfg) {
        StringBuilder sb = null;
        int copied = 0;
        Matcher key = KEY.matcher(s);
        int pos = 0;
        while (pos < s.length() && key.find(pos)) {
            pos = key.end();
            if (!isSensitiveKey(key.group(1))) {
                continue;
            }
            int[] value = valueRange(s, key.end());
            if (value == null || s.substring(value[0], value[1]).contains(secret)) {
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder(s.length());
            }
            String replacement = isSessionKey(key.group(1))
                    ? MaskType.PSEUDONYM.getStrategy().mask(s.substring(value[0], value[1]), cfg)
                    : secret;
            sb.append(s, copied, value[0]).append(replacement);
            copied = value[1];
            pos = Math.max(pos, value[1]);
        }
        String result = sb == null ? s : sb.append(s, copied, s.length()).toString();
        return result.indexOf('<') < 0 ? result
                : replace(XML_ELEMENT, result, m -> isSensitiveKey(m.group(1)) && !m.group(2).contains(secret)
                        ? result.substring(m.start(), m.start(2)) + secret + result.substring(m.end(2), m.end())
                        : null);
    }

    /** Start (inclusive) and end (exclusive) of the value at {@code start}, or {@code null} if empty. */
    private static int[] valueRange(String s, int start) {
        int len = s.length();
        if (start >= len) {
            return null;
        }
        char c = s.charAt(start);
        // Escaped quotes: JSON embedded in a JSON string, \"value\"
        if (c == '\\' && start + 1 < len && (s.charAt(start + 1) == '"' || s.charAt(start + 1) == '\'')) {
            int from = start + 2;
            int end = s.indexOf("\\" + s.charAt(start + 1), from);
            return range(from, end < 0 ? len : end);
        }
        if (c == '"' || c == '\'') {
            int i = start + 1;
            while (i < len && s.charAt(i) != c) {
                i += s.charAt(i) == '\\' ? 2 : 1;
            }
            return range(start + 1, Math.min(i, len));
        }
        int from = start;
        Matcher scheme = AUTH_SCHEME.matcher(s).region(start, len);
        if (scheme.lookingAt()) {
            from = scheme.end();
        }
        int i = from;
        while (i < len && !isValueTerminator(s.charAt(i))) {
            i++;
        }
        return range(from, i);
    }

    private static int[] range(int from, int to) {
        return to > from ? new int[]{from, to} : null;
    }

    private static boolean isValueTerminator(char c) {
        return Character.isWhitespace(c) || "&,;\"'}])<>|\\".indexOf(c) >= 0;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = normalizeKey(key);
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        for (String suffix : SENSITIVE_KEY_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        for (String suffix : customKeySuffixes) {
            if (normalized.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSessionKey(String key) {
        String normalized = normalizeKey(key);
        return normalized.endsWith("sessionid") || normalized.endsWith("session");
    }

    private static String normalizeKey(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** Bearer/Basic values that are prose ("basic validation") are all lowercase letters. */
    private static boolean looksLikeCredential(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isLowerCase(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** "Bearer expiresIn=3600": the '=' introduces a value, it is not base64 padding. */
    private static boolean isKeyOfPair(String s, MatchResult m) {
        return m.group(1).endsWith("=") && m.end() < s.length() && Character.isLetterOrDigit(s.charAt(m.end()));
    }

    private static String maskPhone(String candidate, MaskingConfig cfg) {
        int end = candidate.length();
        while (end > 0 && !Character.isDigit(candidate.charAt(end - 1))) {
            end--;
        }
        String phone = candidate.substring(0, end);
        return MaskPatterns.isPhone(phone)
                ? MaskType.PHONE.getStrategy().mask(phone, cfg) + candidate.substring(end)
                : null;
    }

    private static String maskIpAddresses(String s, MaskingConfig cfg) {
        String result = s.indexOf('.') < 0 ? s
                : replace(IPV4, s, m -> MaskType.IP_ADDRESS.getStrategy().mask(m.group(), cfg));
        return result.indexOf(':') < 0 ? result
                : replace(IPV6, result, m -> isIpv6(m.group())
                        ? MaskType.IP_ADDRESS.getStrategy().mask(m.group(), cfg) : null);
    }

    /** Full IPv6 form (8 groups) or a single "::" compression; rejects times like 12:30:45. */
    private static boolean isIpv6(String candidate) {
        int compression = candidate.indexOf("::");
        if (compression >= 0 && candidate.indexOf("::", compression + 1) >= 0) {
            return false;
        }
        String[] groups = candidate.split(":", -1);
        int nonEmpty = 0;
        for (String group : groups) {
            if (!group.isEmpty()) {
                nonEmpty++;
            }
        }
        return compression >= 0 ? nonEmpty <= 7 && candidate.length() > 2 : groups.length == 8 && nonEmpty == 8;
    }

    /**
     * Replaces each match with {@code masker}'s result; a {@code null} result
     * keeps the match. Replacements are appended verbatim (no group references).
     */
    private static String replace(Pattern pattern, String input, Function<MatchResult, String> masker) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = null;
        int last = 0;
        while (matcher.find()) {
            String replacement = masker.apply(matcher);
            if (replacement == null) {
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder(input.length());
            }
            sb.append(input, last, matcher.start()).append(replacement);
            last = matcher.end();
        }
        return sb == null ? input : sb.append(input, last, input.length()).toString();
    }

    private static ApiKeyPattern apiKey(String regex, String... literals) {
        return new ApiKeyPattern(Pattern.compile(regex), literals);
    }

    private record ApiKeyPattern(Pattern pattern, String[] literals) {
        boolean mayMatch(String s) {
            for (String literal : literals) {
                if (s.contains(literal)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Builder for a customized {@link LogMasker}.
     *
     * @since 1.3.0
     */
    public static final class Builder {

        private final EnumSet<Detector> detectors = EnumSet.noneOf(Detector.class);
        private final Set<String> keys = new LinkedHashSet<>();
        private MaskingConfig config;

        private Builder() {
            for (Detector detector : Detector.values()) {
                if (detector.isEnabledByDefault()) {
                    detectors.add(detector);
                }
            }
        }

        /**
         * Enables detectors, e.g. the opt-in {@link Detector#PHONE}.
         *
         * @param toEnable the detectors to enable
         * @return this builder
         */
        public Builder enable(Detector... toEnable) {
            detectors.addAll(List.of(toEnable));
            return this;
        }

        /**
         * Disables detectors.
         *
         * @param toDisable the detectors to disable
         * @return this builder
         */
        public Builder disable(Detector... toDisable) {
            List.of(toDisable).forEach(detectors::remove);
            return this;
        }

        /**
         * Adds key names whose values {@link Detector#KEY_VALUE} masks, on top of
         * the built-in ones. Matched like those: ignoring case and
         * non-alphanumerics, as a suffix ({@code "memberCode"} also matches
         * {@code primary_member_code}).
         *
         * @param names the key names
         * @return this builder
         */
        public Builder sensitiveKeys(String... names) {
            for (String name : names) {
                String normalized = normalizeKey(name);
                if (!normalized.isEmpty()) {
                    keys.add(normalized);
                }
            }
            return this;
        }

        /**
         * Uses this configuration (mask character, enabled flag) instead of
         * reading the global {@link MaskingConfig#getInstance()} on every call.
         *
         * @param maskingConfig the configuration
         * @return this builder
         */
        public Builder config(MaskingConfig maskingConfig) {
            this.config = maskingConfig;
            return this;
        }

        /**
         * Builds the immutable masker.
         *
         * @return the masker
         */
        public LogMasker build() {
            return new LogMasker(this);
        }
    }
}
