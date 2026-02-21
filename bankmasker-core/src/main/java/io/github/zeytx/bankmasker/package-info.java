/**
 * BankMasker — a lightweight Jackson-based library for masking sensitive data
 * during JSON serialization.
 *
 * <p>Annotate your DTO fields with {@link io.github.zeytx.bankmasker.MaskData}
 * and choose a built-in {@link io.github.zeytx.bankmasker.MaskType} or provide
 * a custom mask configuration.
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * public class PaymentDTO {
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
 *
 * ObjectMapper mapper = new ObjectMapper();
 * String json = mapper.writeValueAsString(payment);
 * // cardNumber → "****-****-****-1234"
 * // email      → "jo****@mail.com"
 * // accountId  → "AB######789"
 * }</pre>
 *
 * @since 1.0.0
 */
package io.github.zeytx.bankmasker;

