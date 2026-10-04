package io.github.zeytx.bankmasker.logging;

import io.github.zeytx.bankmasker.MaskPatterns;
import io.github.zeytx.bankmasker.MaskType;
import io.github.zeytx.bankmasker.MaskingConfig;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive data patterns (payment card numbers, emails, IBANs, SSNs)
 * found anywhere inside a free-text message.
 *
 * <p>Card candidates (13-19 digits with optional single space/dash
 * separators) are only masked when they pass the Luhn checksum, so
 * timestamps and ids are left untouched. IBANs are detected both in
 * contiguous form and in the printed groups-of-4 format
 * ({@code ES66 2100 0418 4012 3456 7891}), gated by
 * {@link MaskPatterns#isIban(String)}.
 *
 * <p>Respects the global {@link MaskingConfig} (enabled flag and mask
 * character). Used by the Logback and Log4j2 converters in this module,
 * and usable directly for any other sink.
 *
 * @since 1.1.0
 */
public final class LogMasker {

    // Contiguous IBAN or printed groups-of-4 form; candidates are validated
    // with MaskPatterns.isIban before masking to keep false positives low.
    private static final Pattern IBAN_IN_TEXT = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?:[A-Za-z0-9]{11,30}|(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?)\\b");
    private static final Pattern PAN_IN_TEXT = Pattern.compile(
            "\\b\\d(?:[ -]?\\d){12,18}\\b");
    // The lookbehind anchors matches at the start of a local-part run and the
    // possessive quantifier forbids backtracking into it: without them, a long
    // token with no '@' costs O(n²) (40 KB ≈ 7 s), a DoS vector for log input.
    private static final Pattern EMAIL_IN_TEXT = Pattern.compile(
            "(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]++@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SSN_IN_TEXT = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private LogMasker() {
        // utility class
    }

    /**
     * Masks all detected sensitive patterns in the given message.
     *
     * @param message the log message, may be {@code null}
     * @return the message with sensitive data masked, or the original if
     *         masking is disabled or the message is {@code null}/empty
     */
    public static String maskMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        MaskingConfig config = MaskingConfig.getInstance();
        if (!config.isEnabled()) {
            return message;
        }

        String result = replace(IBAN_IN_TEXT, message,
                match -> MaskPatterns.isIban(match)
                        ? MaskType.IBAN.getStrategy().mask(match, config)
                        : match);
        result = replace(PAN_IN_TEXT, result,
                match -> MaskPatterns.passesLuhn(match.replaceAll("[ -]", ""))
                        ? MaskType.CREDIT_CARD.getStrategy().mask(match, config)
                        : match);
        result = replace(SSN_IN_TEXT, result,
                match -> MaskType.SSN.getStrategy().mask(match, config));
        return replace(EMAIL_IN_TEXT, result,
                match -> MaskType.EMAIL.getStrategy().mask(match, config));
    }

    private static String replace(Pattern pattern, String input, UnaryOperator<String> masker) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        do {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masker.apply(matcher.group())));
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }
}
