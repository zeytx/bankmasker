package io.github.zeytx.bankmasker;

import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/**
 * Detection helpers for common sensitive data formats.
 *
 * <p>Used by {@link MaskType#AUTO} and by the optional
 * {@code bankmasker-logging} module to decide which masking strategy
 * applies to an arbitrary value.
 *
 * <p>Card detection requires 13-19 digits (optionally separated by single
 * spaces or dashes) passing the Luhn checksum, which keeps false positives
 * (timestamps, ids) low.
 *
 * @since 1.1.0
 */
public final class MaskPatterns {

    private static final Pattern CARD_CHARS = Pattern.compile("\\d(?:[ -]?\\d)*");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IBAN = Pattern.compile(
            "[A-Z]{2}\\d{2}[A-Za-z0-9]{11,30}");

    private MaskPatterns() {
        // utility class
    }

    /**
     * Whether the whole value looks like a payment card number:
     * 13-19 digits (optional single space/dash separators) passing Luhn.
     *
     * @param value the value to test
     * @return {@code true} if the value is a plausible card number
     */
    public static boolean isCreditCard(String value) {
        if (value == null || !CARD_CHARS.matcher(value).matches()) {
            return false;
        }
        String digits = filter(value, c -> c != ' ' && c != '-');
        return digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits);
    }

    /**
     * Whether the whole value looks like an email address.
     *
     * @param value the value to test
     * @return {@code true} if the value matches a simple email format
     */
    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }

    /**
     * Whether the whole value looks like an IBAN
     * (country code + 2 check digits + 11-30 alphanumerics, spaces allowed).
     *
     * @param value the value to test
     * @return {@code true} if the value matches the IBAN format
     */
    public static boolean isIban(String value) {
        if (value == null) {
            return false;
        }
        return IBAN.matcher(withoutWhitespace(value)).matches();
    }

    /**
     * Validates a digit string against the Luhn checksum.
     *
     * @param digits the digits to validate (no separators)
     * @return {@code true} if the checksum is valid
     */
    public static boolean passesLuhn(String digits) {
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    // Char filters used on every masked value: same result as replaceAll("\\D", "")
    // and replaceAll("\\s", "") without compiling a regex per call.

    static String digitsOnly(String value) {
        return filter(value, c -> c >= '0' && c <= '9');
    }

    static String withoutWhitespace(String value) {
        // \s: space, \t, \n, \x0B, \f, \r
        return filter(value, c -> c != ' ' && (c < 0x09 || c > 0x0D));
    }

    private static String filter(String value, IntPredicate keep) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (keep.test(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
