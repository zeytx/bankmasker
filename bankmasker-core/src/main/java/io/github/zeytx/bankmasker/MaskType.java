package io.github.zeytx.bankmasker;

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
 *   <li>{@code AUTO}: detects card/email/IBAN, otherwise total mask</li>
 *   <li>{@code TOTAL}: {@code anything → ********}</li>
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
        String digits = value.replaceAll("\\D", "");
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
        String digits = value.replaceAll("\\D", "");
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
        String clean = value.replaceAll("\\s", "");
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
        String digits = value.replaceAll("\\D", "");
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
        String digits = value.replaceAll("\\D", "");
        if (exposesTooMuch(digits.length(), 4)) {
            return repeat(m, 4);
        }
        return repeat(m, digits.length() - 4) + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks an IP address, keeping only the last octet visible.
     * Each masked octet is replaced with 3 mask characters.
     * Example: {@code 192.168.1.100 → ***.***.***.100}
     *
     * @since 1.1.0
     */
    IP_ADDRESS((value, config) -> {
        char m = config.getDefaultMaskChar();
        int lastDot = value.lastIndexOf('.');
        if (lastDot < 0) {
            return repeat(m, 8);
        }
        String lastOctet = value.substring(lastDot + 1);
        String prefix = value.substring(0, lastDot);
        // Replace each octet in prefix with 3 mask chars
        String maskedPrefix = prefix.replaceAll("[^.]+", repeat(m, 3));
        return maskedPrefix + "." + lastOctet;
    }),

    /**
     * Detects the value format automatically and applies the matching built-in
     * strategy: payment card (Luhn-validated), email or IBAN, in that order.
     * Falls back to a total mask when no known format matches, so unknown
     * values are never leaked.
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
    CUSTOM((value, config) -> repeat(config.getDefaultMaskChar(), 8));

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