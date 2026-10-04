package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;

/**
 * Log4j2 pattern converter that masks sensitive data (PAN, emails, IBAN)
 * in the formatted log message.
 *
 * <p>Use {@code %maskedMsg} (or {@code %mm}) instead of {@code %msg} in
 * the pattern layout:
 * <pre>{@code
 * <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level %logger{36} - %maskedMsg%n"/>
 * }</pre>
 *
 * @since 1.1.0
 * @see LogMasker
 */
@Plugin(name = "MaskingMessagePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"maskedMsg", "mm"})
public final class MaskingMessagePatternConverter extends LogEventPatternConverter {

    private MaskingMessagePatternConverter() {
        super("maskedMsg", "maskedMsg");
    }

    /**
     * Factory method required by Log4j2's plugin system.
     *
     * @param options converter options (unused)
     * @return a new converter instance
     */
    public static MaskingMessagePatternConverter newInstance(String[] options) {
        return new MaskingMessagePatternConverter();
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        toAppendTo.append(LogMasker.maskMessage(event.getMessage().getFormattedMessage()));
    }
}
