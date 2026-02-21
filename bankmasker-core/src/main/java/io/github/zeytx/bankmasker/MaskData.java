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
}