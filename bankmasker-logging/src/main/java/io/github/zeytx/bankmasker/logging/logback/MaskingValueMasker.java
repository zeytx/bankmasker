package io.github.zeytx.bankmasker.logging.logback;

import io.github.zeytx.bankmasker.logging.LogMasker;
import net.logstash.logback.mask.ValueMasker;
import tools.jackson.core.TokenStreamContext;

/**
 * {@code logstash-logback-encoder} value masker that runs {@link LogMasker}
 * on every string written to the JSON log (message, MDC, arguments, stack
 * trace...). Numbers are left untouched, so the JSON stays valid.
 *
 * <p>Requires logstash-logback-encoder 9+ (Jackson 3):
 * <pre>{@code
 * <encoder class="net.logstash.logback.encoder.LogstashEncoder">
 *   <decorator class="net.logstash.logback.mask.MaskingJsonGeneratorDecorator">
 *     <valueMasker class="io.github.zeytx.bankmasker.logging.logback.MaskingValueMasker"/>
 *   </decorator>
 * </encoder>
 * }</pre>
 *
 * @since 1.3.0
 * @see LogMasker
 */
public class MaskingValueMasker implements ValueMasker {

    @Override
    public Object mask(TokenStreamContext context, Object value) {
        if (value instanceof String text) {
            String masked = LogMasker.maskMessage(text);
            // null tells the encoder to keep the original value
            return masked.equals(text) ? null : masked;
        }
        return null;
    }
}
