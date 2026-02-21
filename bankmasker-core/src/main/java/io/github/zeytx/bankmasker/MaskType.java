package io.github.zeytx.bankmasker;

/**
 * Built-in masking types with default strategies.
 * Each type carries a {@link MaskingStrategy} that handles the transformation.
 *
 * <p>All built-in strategies respect {@link MaskingConfig#getDefaultMaskChar()},
 * so changing the global mask character will affect all types.
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
    CREDIT_CARD(value -> {
        char m = maskChar();
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return repeat(m, 4);
        }
        String block = repeat(m, 4);
        return block + "-" + block + "-" + block + "-"
                + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks an email address keeping the first 2 characters and the domain.
     * Falls back to total mask if the format is invalid.
     */
    EMAIL(value -> {
        char m = maskChar();
        int atIndex = value.indexOf('@');
        if (atIndex <= 0) {
            return repeat(m, 8);
        }
        int visible = Math.min(2, atIndex);
        return value.substring(0, visible) + repeat(m, 4) + value.substring(atIndex);
    }),

    /**
     * Masks a phone number, keeping only the last 4 digits visible.
     */
    PHONE(value -> {
        char m = maskChar();
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return repeat(m, 4);
        }
        return repeat(m, digits.length() - 4) + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks a national ID / DNI, keeping only the last 4 characters.
     */
    DNI(value -> {
        char m = maskChar();
        if (value.length() <= 4) {
            return repeat(m, 4);
        }
        return repeat(m, value.length() - 4) + value.substring(value.length() - 4);
    }),

    /**
     * Masks an IBAN, keeping the country code (first 2 chars) and last 4 digits.
     * Example: {@code ES6621000418401234567891 → ES********************7891}
     */
    IBAN(value -> {
        char m = maskChar();
        String clean = value.replaceAll("\\s", "");
        if (clean.length() <= 6) {
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
    SSN(value -> {
        char m = maskChar();
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return repeat(m, 3) + "-" + repeat(m, 2) + "-" + repeat(m, 4);
        }
        return repeat(m, 3) + "-" + repeat(m, 2) + "-" + digits.substring(digits.length() - 4);
    }),

    /**
     * Masks a person's name, keeping only the first letter of each word.
     * Example: {@code John Doe → J*** D**}
     */
    NAME(value -> {
        char m = maskChar();
        String[] parts = value.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String part = parts[i];
            if (part.isEmpty()) continue;
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
    PASSPORT(value -> {
        char m = maskChar();
        if (value.length() <= 5) {
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
    BANK_ACCOUNT(value -> {
        char m = maskChar();
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) {
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
    IP_ADDRESS(value -> {
        char m = maskChar();
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
     * Replaces the entire value with mask characters.
     */
    TOTAL(value -> repeat(maskChar(), 8)),

    /**
     * Placeholder for custom masking via {@link MaskData#maskChar()} and
     * {@link MaskData#visibleStart()} / {@link MaskData#visibleEnd()}.
     * The default strategy masks everything; the serializer overrides this
     * when custom parameters are provided.
     */
    CUSTOM(value -> repeat(maskChar(), 8));

    private final MaskingStrategy strategy;

    MaskType(MaskingStrategy strategy) {
        this.strategy = strategy;
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
     * Returns the current default mask character from global configuration.
     *
     * @return the mask character
     */
    private static char maskChar() {
        return MaskingConfig.getInstance().getDefaultMaskChar();
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