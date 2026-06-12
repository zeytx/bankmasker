package io.github.zeytx.bankmasker.logging;

import io.github.zeytx.bankmasker.MaskPatterns;
import io.github.zeytx.bankmasker.MaskType;
import io.github.zeytx.bankmasker.MaskingConfig;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks sensitive data patterns (payment card numbers, emails, IBANs)
 * found anywhere inside a free-text message.
 *
 * <p>Card candidates (13-19 digits with optional single space/dash
 * separators) are only masked when they pass the Luhn checksum, so
 * timestamps and ids are left untouched.
 *
 * <p>Respects the global {@link MaskingConfig} (enabled flag and mask
 * character). Used by the Logback and Log4j2 converters in this module,
 * and usable directly for any other sink.
 *
 * @since 1.1.0
 */
public final class LogMasker {

    private static final Pattern IBAN_IN_TEXT = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}[A-Za-z0-9]{11,30}\\b");
    private static final Pattern PAN_IN_TEXT = Pattern.compile(
            "\\b\\d(?:[ -]?\\d){12,18}\\b");
    private static final Pattern EMAIL_IN_TEXT = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

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
                match -> MaskType.IBAN.getStrategy().mask(match, config));
        result = replace(PAN_IN_TEXT, result,
                match -> MaskPatterns.passesLuhn(match.replaceAll("[ -]", ""))
                        ? MaskType.CREDIT_CARD.getStrategy().mask(match, config)
                        : match);
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
