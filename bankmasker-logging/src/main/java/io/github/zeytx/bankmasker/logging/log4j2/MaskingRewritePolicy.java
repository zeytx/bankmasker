package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.StringMap;

import java.util.List;

/**
 * Log4j2 rewrite policy that masks the message and the string values of the
 * context data (MDC) before any layout runs, so it also covers JSON layouts
 * such as {@code JsonTemplateLayout}, where {@code %maskedMsg} does not apply:
 * <pre>{@code
 * <Rewrite name="Masked">
 *   <MaskingRewritePolicy detectors="+PHONE"/>   <!-- detectors is optional -->
 *   <AppenderRef ref="Json"/>
 * </Rewrite>
 * }</pre>
 *
 * <p>A masked message is passed on as plain text. Exceptions attached to the
 * event are not rewritten: with pattern layouts use {@code %maskedEx}.
 *
 * @since 1.3.0
 * @see LogMasker
 */
@Plugin(name = "MaskingRewritePolicy", category = Core.CATEGORY_NAME, elementType = "rewritePolicy", printObject = true)
public final class MaskingRewritePolicy implements RewritePolicy {

    private final LogMasker masker;

    private MaskingRewritePolicy(LogMasker masker) {
        this.masker = masker;
    }

    /**
     * Factory method required by Log4j2's plugin system.
     *
     * @param detectors optional detector toggles, e.g. {@code "+PHONE,-EMAIL"}
     * @return a new policy
     */
    @PluginFactory
    public static MaskingRewritePolicy createPolicy(@PluginAttribute("detectors") String detectors) {
        return new MaskingRewritePolicy(MaskingMessagePatternConverter.masker(
                detectors == null ? List.of() : List.of(detectors)));
    }

    @Override
    public LogEvent rewrite(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        String maskedMessage = masker.mask(message);

        StringMap maskedContext = ContextDataFactory.createContextData(event.getContextData());
        boolean contextChanged = false;
        for (var entry : event.getContextData().toMap().entrySet()) {
            String masked = masker.mask(entry.getValue());
            if (masked != null && !masked.equals(entry.getValue())) {
                maskedContext.putValue(entry.getKey(), masked);
                contextChanged = true;
            }
        }

        boolean messageChanged = maskedMessage != null && !maskedMessage.equals(message);
        if (!messageChanged && !contextChanged) {
            return event;
        }
        Log4jLogEvent.Builder builder = new Log4jLogEvent.Builder(event);
        if (messageChanged) {
            builder.setMessage(new SimpleMessage(maskedMessage));
        }
        if (contextChanged) {
            builder.setContextData(maskedContext);
        }
        return builder.build();
    }

    @Override
    public String toString() {
        return "MaskingRewritePolicy[" + masker.getDetectors() + "]";
    }
}
