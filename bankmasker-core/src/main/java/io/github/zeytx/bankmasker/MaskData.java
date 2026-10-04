package io.github.zeytx.bankmasker;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be masked during JSON serialization.
 *
 * <p>Usage:
 * <pre>{@code
 * public class UserDTO {
 *
 *     @MaskData(MaskType.CREDIT_CARD)
 *     private String cardNumber;
 *
 *     @MaskData(MaskType.EMAIL)
 *     private String email;
 *
 *     @MaskData(value = MaskType.CUSTOM, maskChar = '#', visibleStart = 2, visibleEnd = 3)
 *     private String accountId;
 * }
 * }</pre>
 *
 * <p>Supported field types: {@code String} and other scalars ({@code Number},
 * {@code UUID}, {@code char[]}, dates, enums), {@code Optional} of those, and
 * collections, maps and arrays of them (masked element by element; map keys
 * stay visible unless {@link #keyMask()} is set). Any other object is fully
 * masked, never through its {@code toString()}.
 *
 * @since 1.0.0
 * @see MaskType
 * @see MaskingSerializer
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonSerialize(using = MaskingSerializer.class)
public @interface MaskData {

    /**
     * The masking type to apply.
     *
     * @return the mask type (defaults to {@link MaskType#TOTAL})
     */
    MaskType value() default MaskType.TOTAL;

    /**
     * Character used for masking. Only used when {@link #value()} is {@link MaskType#CUSTOM}.
     *
     * @return the mask character (defaults to '*')
     */
    char maskChar() default '*';

    /**
     * Number of characters to keep visible at the start.
     * Only used when {@link #value()} is {@link MaskType#CUSTOM}.
     *
     * @return visible characters from the start (defaults to 0)
     */
    int visibleStart() default 0;

    /**
     * Number of characters to keep visible at the end.
     * Only used when {@link #value()} is {@link MaskType#CUSTOM}.
     *
     * @return visible characters from the end (defaults to 0)
     */
    int visibleEnd() default 0;

    /**
     * Mask type applied to the keys of a {@code Map} field. Empty (the default)
     * keeps keys visible, since they are usually structural ({@code "apiKey"}).
     * Set a single type when keys are sensitive themselves, e.g. a map keyed by
     * card number:
     * <pre>{@code
     * @MaskData(value = MaskType.TOTAL, keyMask = MaskType.CREDIT_CARD)
     * Map<String, BigDecimal> balanceByCard;   // {"****-****-****-1111":"********"}
     * }</pre>
     * Keys that mask to the same value get a {@code ~2}, {@code ~3}, … suffix so
     * no entry is lost. {@link MaskType#CUSTOM} uses this annotation's
     * {@link #maskChar()}, {@link #visibleStart()} and {@link #visibleEnd()}.
     *
     * @return at most one mask type for map keys (defaults to none)
     * @since 1.1.0
     */
    MaskType[] keyMask() default {};
}