package io.github.zeytx.bankmasker;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Built-in masking types with default strategies.
 * Each type carries a {@link MaskingStrategy} that handles the transformation.
 *
 * <p>All built-in strategies respect {@link MaskingConfig#getDefaultMaskChar()},
 * so changing the global mask character will affect all types.
 *
 * <p>Strategies that keep the last characters visible fail closed: values too
 * short for the visible part to be smaller than the hidden part are fully
 * masked (e.g. {@code CREDIT_CARD} on {@code "12345"} yields {@code ****}).
 *
 * <p>Examples (using default mask char {@code '*'}):
 * <ul>
 *   <li>{@code CREDIT_CARD}: {@code 4111111111111111 → ****-****-****-1111}</li>
 *   <li>{@code EMAIL}: {@code john.doe@mail.com → jo****@mail.com}</li>
 *   <li>{@code PHONE}: {@code +525512345678 → ********5678}</li>
 *   <li>{@code DNI}: {@code ABCD123456 → ******3456}</li>
 *   <li>{@code IBAN}: {@code ES6621000418401234567891 → ES********************7891}</li>
 *   <li>{@code SSN}: {@code 123-45-6789 → ***-**-6789}</li>
 *   <li>{@code NAME}: {@code John Doe → J*** D**}</li>
 *   <li>{@code PASSPORT}: {@code AB1234567 → AB****567}</li>
 *   <li>{@code BANK_ACCOUNT}: {@code 12345678901234 → **********1234}</li>
 *   <li>{@code IP_ADDRESS}: {@code 192.168.1.100 → ***.***.***.100}</li>
 *   <li>{@code AUTO}: detects card/email/IBAN/SSN/national ID/phone, otherwise total mask</li>
 *   <li>{@code TOTAL}: {@code anything → ********}</li>
 *   <li>{@code CREDIT_CARD_BIN}: {@code 4111111111111111 → 411111******1111}</li>
 *   <li>{@code BIRTH_DATE}: {@code 1990-05-12 → 1990-**-**}</li>
 *   <li>{@code PSEUDONYM}: {@code CUS-123456 → 5f1c0a7be2d94c38} (keyed HMAC)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum MaskType {

    /**
     * Masks a credit/debit card number, keeping only the last 4 digits.
     * Input is sanitized (non-digit characters removed) before masking.
     */
    CREDIT_CARD((value, config) -> {
        char m = config.getDefaultMaskChar();
        String digits = MaskPatterns.digitsOnly(value);
        if (exposesTooMuch(digits.length(), 4)) {
            return repeat(m, 4);
        }
        String block = repeat(m, 4);
        return block + "-" + block + "-" + block + "-"
                + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks an email address keeping the first 2 characters of the local part
     * and the domain. Local parts of 1-2 characters are fully masked and
     * 3-character ones keep only the first, so the visible part never exceeds
     * the hidden one. Falls back to total mask if the format is invalid.
     */
    EMAIL((value, config) -> {
        char m = config.getDefaultMaskChar();
        int atIndex = value.lastIndexOf('@');
        if (atIndex <= 0) {
            return repeat(m, 8);
        }
        // Never reveal more of the local part than is hidden
        int visible = (atIndex <= 2) ? 0 : (atIndex == 3 ? 1 : 2);
        return value.substring(0, visible) + repeat(m, 4) + value.substring(atIndex);
    }),

    /**
     * Masks a phone number, keeping only the last 4 digits visible.
     */
    PHONE((value, config) -> {
        char m = config.getDefaultMaskChar();
        String digits = MaskPatterns.digitsOnly(value);
        if (exposesTooMuch(digits.length(), 4)) {
            return repeat(m, 4);
        }
        return repeat(m, digits.length() - 4) + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks a national ID / DNI, keeping only the last 4 characters.
     */
    DNI((value, config) -> {
        char m = config.getDefaultMaskChar();
        if (exposesTooMuch(value.length(), 4)) {
            return repeat(m, 4);
        }
        return repeat(m, value.length() - 4) + value.substring(value.length() - 4);
    }),

    /**
     * Masks an IBAN, keeping the country code (first 2 chars) and last 4 digits.
     * Example: {@code ES6621000418401234567891 → ES********************7891}
     */
    IBAN((value, config) -> {
        char m = config.getDefaultMaskChar();
        String clean = MaskPatterns.withoutWhitespace(value);
        if (exposesTooMuch(clean.length(), 6)) {
            return repeat(m, 4);
        }
        String country = clean.substring(0, 2);
        String last4 = clean.substring(clean.length() - 4);
        return country + repeat(m, clean.length() - 6) + last4;
    }),

    /**
     * Masks a US Social Security Number, keeping only the last 4 digits.
     * Example: {@code 123-45-6789 → ***-**-6789}
     */
    SSN((value, config) -> {
        char m = config.getDefaultMaskChar();
        String digits = MaskPatterns.digitsOnly(value);
        if (exposesTooMuch(digits.length(), 4)) {
            return repeat(m, 3) + "-" + repeat(m, 2) + "-" + repeat(m, 4);
        }
        return repeat(m, 3) + "-" + repeat(m, 2) + "-" + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks a person's name, keeping only the first letter of each word.
     * Example: {@code John Doe → J*** D**}
     */
    NAME((value, config) -> {
        char m = config.getDefaultMaskChar();
        String[] parts = value.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.charAt(0));
            if (part.length() > 1) {
                sb.append(repeat(m, part.length() - 1));
            }
        }
        return sb.toString();
    }),

    /**
     * Masks a passport number, keeping the first 2 and last 3 characters.
     * Example: {@code AB1234567 → AB****567}
     *
     * @since 1.1.0
     */
    PASSPORT((value, config) -> {
        char m = config.getDefaultMaskChar();
        // Fail-closed below 7 chars: keeping 2+3 visible would leave at most
        // one character actually masked.
        if (value.length() <= 6) {
            return repeat(m, 4);
        }
        return value.substring(0, 2) + repeat(m, value.length() - 5) + value.substring(value.length() - 3);
    }),

    /**
     * Masks a bank account number, keeping only the last 4 digits.
     * Example: {@code 12345678901234 → **********1234}
     *
     * @since 1.1.0
     */
    BANK_ACCOUNT((value, config) -> {
        char m = config.getDefaultMaskChar();
        String digits = MaskPatterns.digitsOnly(value);
        if (exposesTooMuch(digits.length(), 4)) {
            return repeat(m, 4);
        }
        return repeat(m, digits.length() - 4) + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks an IP address, keeping only the last group visible: each IPv4
     * octet becomes 3 mask characters and each IPv6 group 4, keeping the
     * {@code ::} compression. Values with neither {@code .} nor {@code :} are
     * fully masked.
     * Examples: {@code 192.168.1.100 → ***.***.***.100},
     * {@code 2001:db8::8a2e:7334 → ****:****::****:7334}
     *
     * @since 1.1.0
     */
    IP_ADDRESS((value, config) -> {
        char m = config.getDefaultMaskChar();
        // IPv4, including IPv4-mapped IPv6 (::ffff:192.168.1.100)
        int last = value.lastIndexOf('.');
        String groupMask = repeat(m, 3);
        if (last < 0) {
            last = value.lastIndexOf(':');
            groupMask = repeat(m, 4);
        }
        if (last < 0) {
            return repeat(m, 8);
        }
        return maskRuns(value.substring(0, last), value.charAt(last), groupMask) + value.substring(last);
    }),

    /**
     * Detects the value format automatically and applies the matching built-in
     * strategy: payment card (Luhn-validated), email, IBAN, SSN, national ID
     * (check digit validated, masked as {@link #DNI}) or phone number, in that
     * order. Falls back to a total mask when no known format matches, so
     * unknown values are never leaked.
     *
     * @since 1.1.0
     * @see MaskPatterns
     */
    AUTO((value, config) -> {
        if (MaskPatterns.isCreditCard(value)) {
            return CREDIT_CARD.getStrategy().mask(value, config);
        }
        if (MaskPatterns.isEmail(value)) {
            return EMAIL.getStrategy().mask(value, config);
        }
        if (MaskPatterns.isIban(value)) {
            return IBAN.getStrategy().mask(value, config);
        }
        if (MaskPatterns.isSsn(value)) {
            return SSN.getStrategy().mask(value, config);
        }
        if (MaskPatterns.isNationalId(value)) {
            return DNI.getStrategy().mask(value, config);
        }
        if (MaskPatterns.isPhone(value)) {
            return PHONE.getStrategy().mask(value, config);
        }
        return repeat(config.getDefaultMaskChar(), 8);
    }),

    /**
     * Replaces the entire value with mask characters.
     */
    TOTAL((value, config) -> repeat(config.getDefaultMaskChar(), 8)),

    /**
     * Placeholder for custom masking via {@link MaskData#maskChar()} and
     * {@link MaskData#visibleStart()} / {@link MaskData#visibleEnd()}.
     * The default strategy masks everything; the serializer overrides this
     * when custom parameters are provided.
     */
    CUSTOM((value, config) -> repeat(config.getDefaultMaskChar(), 8)),

    /**
     * Masks a card number keeping the BIN (first 6 digits) and the last 4,
     * the most PCI DSS (req. 3.4.1) allows to display; use it only where that
     * much is justified (support, fraud), and {@link #CREDIT_CARD} otherwise.
     * Values with fewer than 13 digits are not card numbers and are fully masked.
     * Example: {@code 4111 1111 1111 1111 → 411111******1111}
     *
     * @since 1.3.0
     */
    CREDIT_CARD_BIN((value, config) -> {
        char m = config.getDefaultMaskChar();
        String digits = MaskPatterns.digitsOnly(value);
        if (digits.length() < 13) {
            return repeat(m, 4);
        }
        return digits.substring(0, 6) + repeat(m, digits.length() - 10) + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks a date of birth keeping only the year: digits outside the first
     * 4-digit run and words (month names) are masked; separators, including
     * the ISO {@code T}, are kept. Values without a 4-digit year are fully masked.
     * Examples: {@code 1990-05-12 → 1990-**-**}, {@code May 12, 1990 → *** **, 1990}
     *
     * @since 1.3.0
     */
    BIRTH_DATE((value, config) -> {
        char m = config.getDefaultMaskChar();
        int year = indexOfYear(value);
        if (year < 0) {
            return repeat(m, 8);
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean inYear = i >= year && i < year + 4;
            boolean word = Character.isLetter(c)
                    && ((i > 0 && Character.isLetter(value.charAt(i - 1)))
                    || (i + 1 < value.length() && Character.isLetter(value.charAt(i + 1))));
            sb.append(!inYear && (Character.isDigit(c) || word) ? m : c);
        }
        return sb.toString();
    }),

    /**
     * Replaces the value with a stable pseudonym: the first 16 hex characters
     * of its HMAC-SHA256 under {@link MaskingConfig#setPseudonymKey(byte[])}.
     * Equal values give equal pseudonyms, so events can be correlated (e.g. a
     * session or customer id across log lines) without exposing the value, as
     * the OWASP Logging Cheat Sheet recommends for session identifiers. The key
     * makes pseudonyms of low-entropy data (card numbers, phones) impossible
     * to brute-force. Without a key the value is fully masked.
     * Example: {@code CUS-123456 → 5f1c0a7be2d94c38}
     *
     * @since 1.3.0
     */
    PSEUDONYM((value, config) -> {
        byte[] key = config.pseudonymKey();
        return key == null ? repeat(config.getDefaultMaskChar(), 8) : hmacHex(key, value);
    });

    private final MaskingStrategy strategy;

    MaskType(ConfigAwareStrategy strategy) {
        this.strategy = ConfigAwareStrategy.adapt(strategy);
    }

    /**
     * Returns the built-in masking strategy for this type.
     *
     * @return the masking strategy
     */
    public MaskingStrategy getStrategy() {
        return strategy;
    }

    /**
     * Fail-closed guard: a value is fully masked when keeping {@code visible}
     * characters would reveal at least as much as it hides.
     */
    private static boolean exposesTooMuch(int length, int visible) {
        return length < visible * 2;
    }

    /**
     * Replaces each maximal run of characters other than {@code separator}
     * with {@code replacement}: {@code ("192.168.1", '.', "***") → ***.***.***}.
     */
    private static String maskRuns(String value, char separator, String replacement) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        boolean inRun = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == separator) {
                sb.append(c);
                inRun = false;
            } else if (!inRun) {
                sb.append(replacement);
                inRun = true;
            }
        }
        return sb.toString();
    }

    /** Index of the first run of exactly 4 digits, or -1. */
    private static int indexOfYear(String value) {
        int i = 0;
        while (i < value.length()) {
            if (!Character.isDigit(value.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < value.length() && Character.isDigit(value.charAt(i))) {
                i++;
            }
            if (i - start == 4) {
                return start;
            }
        }
        return -1;
    }

    private static String hmacHex(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)), 0, 8);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is mandatory on every Java platform
            throw new IllegalStateException(e);
        }
    }

    /**
     * Repeats the given character {@code count} times.
     *
     * @param ch    the character to repeat
     * @param count the number of repetitions
     * @return the repeated string
     */
    static String repeat(char ch, int count) {
        return String.valueOf(ch).repeat(Math.max(0, count));
    }
}