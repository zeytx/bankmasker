package io.github.zeytx.bankmasker;

import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detection helpers for common sensitive data formats.
 *
 * <p>Used by {@link MaskType#AUTO} and by the optional
 * {@code bankmasker-logging} module to decide which masking strategy
 * applies to an arbitrary value.
 *
 * <p>Detection favors precision: card numbers must pass the Luhn checksum,
 * national IDs their check digit, and SSNs and phone numbers need their
 * usual separators, which keeps false positives (timestamps, ids) low.
 *
 * @since 1.1.0
 */
public final class MaskPatterns {

    private static final Pattern CARD_CHARS = Pattern.compile("\\d(?:[ -]?\\d)*");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern IBAN = Pattern.compile(
            "[A-Z]{2}\\d{2}[A-Za-z0-9]{11,30}");
    private static final Pattern SSN = Pattern.compile("(\\d{3})-(\\d{2})-(\\d{4})");
    // "+" and 8-15 digits, or a separated local format: "(555) 123-4567", "555-123-4567"
    private static final Pattern PHONE = Pattern.compile(
            "\\+\\d[\\d .()-]{6,20}|\\(?\\d{2,4}\\)?[ .-]\\d{3,4}[ .-]\\d{3,4}");

    private static final Pattern ES_DNI = Pattern.compile("(\\d{8})-?([A-Z])");
    private static final Pattern ES_NIE = Pattern.compile("([XYZ])-?(\\d{7})-?([A-Z])");
    private static final Pattern BR_CPF = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern CL_RUT = Pattern.compile("(\\d{1,2}\\.\\d{3}\\.\\d{3})-([\\dkK])");
    private static final Pattern AR_CUIT = Pattern.compile("(?:20|23|24|27|30|33|34)-\\d{8}-\\d");
    private static final Pattern MX_CURP = Pattern.compile(
            "[A-Z][AEIOUX][A-Z]{2}\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])[HMX][A-Z]{5}[A-Z\\d]\\d");

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String CURP_ALPHABET = "0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ";

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
     * Whether the whole value is a US Social Security Number in the dashed
     * format ({@code 123-45-6789}) with an assignable area, group and serial
     * (area not 000, 666 or 9xx; group not 00; serial not 0000).
     *
     * @param value the value to test
     * @return {@code true} if the value is a plausible SSN
     * @since 1.3.0
     */
    public static boolean isSsn(String value) {
        if (value == null) {
            return false;
        }
        Matcher m = SSN.matcher(value);
        if (!m.matches()) {
            return false;
        }
        int area = Integer.parseInt(m.group(1));
        return area != 0 && area != 666 && area < 900
                && !"00".equals(m.group(2)) && !"0000".equals(m.group(3));
    }

    /**
     * Whether the whole value looks like a phone number: international
     * ({@code +52 55 1234 5678}) or a separated local format
     * ({@code (555) 123-4567}), with 8 to 15 digits. Bare digit runs are not
     * phones: they are too ambiguous.
     *
     * @param value the value to test
     * @return {@code true} if the value is a plausible phone number
     * @since 1.3.0
     */
    public static boolean isPhone(String value) {
        if (value == null || !PHONE.matcher(value).matches()) {
            return false;
        }
        int digits = digitsOnly(value).length();
        return digits >= 8 && digits <= 15;
    }

    /**
     * Whether the whole value is a national ID whose check digit is valid:
     * Spanish DNI/NIE ({@code 12345678Z}, {@code X1234567L}), Brazilian CPF
     * ({@code 529.982.247-25}), Chilean RUT ({@code 12.345.678-5}), Argentine
     * CUIT/CUIL ({@code 20-12345678-6}) or Mexican CURP.
     *
     * @param value the value to test
     * @return {@code true} if the value is a valid supported national ID
     * @since 1.3.0
     */
    public static boolean isNationalId(String value) {
        if (value == null || value.length() < 9 || value.length() > 18) {
            return false;
        }
        return isSpanishId(value) || isCpf(value) || isRut(value) || isCuit(value) || isCurp(value);
    }

    private static boolean isSpanishId(String value) {
        Matcher dni = ES_DNI.matcher(value);
        if (dni.matches()) {
            return dniLetter(Integer.parseInt(dni.group(1))) == dni.group(2).charAt(0);
        }
        Matcher nie = ES_NIE.matcher(value);
        if (nie.matches()) {
            int prefix = "XYZ".indexOf(nie.group(1).charAt(0));
            return dniLetter(Integer.parseInt(prefix + nie.group(2))) == nie.group(3).charAt(0);
        }
        return false;
    }

    private static char dniLetter(int number) {
        return DNI_LETTERS.charAt(number % 23);
    }

    private static boolean isCpf(String value) {
        if (!BR_CPF.matcher(value).matches()) {
            return false;
        }
        String d = digitsOnly(value);
        if (d.chars().distinct().count() == 1) {
            return false;
        }
        return cpfDigit(d, 9) == d.charAt(9) - '0' && cpfDigit(d, 10) == d.charAt(10) - '0';
    }

    private static int cpfDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int r = sum * 10 % 11;
        return r == 10 ? 0 : r;
    }

    private static boolean isRut(String value) {
        Matcher m = CL_RUT.matcher(value);
        if (!m.matches()) {
            return false;
        }
        String body = digitsOnly(m.group(1));
        int sum = 0;
        int weight = 2;
        for (int i = body.length() - 1; i >= 0; i--) {
            sum += (body.charAt(i) - '0') * weight;
            weight = weight == 7 ? 2 : weight + 1;
        }
        int r = 11 - sum % 11;
        char expected = r == 11 ? '0' : r == 10 ? 'K' : (char) ('0' + r);
        return Character.toUpperCase(m.group(2).charAt(0)) == expected;
    }

    private static boolean isCuit(String value) {
        if (!AR_CUIT.matcher(value).matches()) {
            return false;
        }
        String d = digitsOnly(value);
        int[] weights = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int r = 11 - sum % 11;
        int expected = r == 11 ? 0 : r;
        return expected != 10 && expected == d.charAt(10) - '0';
    }

    private static boolean isCurp(String value) {
        if (!MX_CURP.matcher(value).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += CURP_ALPHABET.indexOf(value.charAt(i)) * (18 - i);
        }
        return (10 - sum % 10) % 10 == value.charAt(17) - '0';
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
