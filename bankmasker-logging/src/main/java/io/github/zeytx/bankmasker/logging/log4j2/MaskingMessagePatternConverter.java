package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.status.StatusLogger;

import java.util.Arrays;
import java.util.List;

/**
 * Log4j2 pattern converter that masks sensitive data (secrets, PAN, emails,
 * IBAN...) in the formatted log message.
 *
 * <p>Use {@code %maskedMsg} (or {@code %mm}) instead of {@code %msg} in
 * the pattern layout:
 * <pre>{@code
 * <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level %logger{36} - %maskedMsg%n"/>
 * }</pre>
 *
 * <p>Detectors can be toggled with options, e.g. {@code %maskedMsg{+PHONE,-EMAIL}}
 * (see {@link LogMasker.Detector}).
 *
 * @since 1.1.0
 * @see LogMasker
 */
@Plugin(name = "MaskingMessagePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"maskedMsg", "mm"})
public final class MaskingMessagePatternConverter extends LogEventPatternConverter {

    private final LogMasker masker;

    private MaskingMessagePatternConverter(LogMasker masker) {
        super("maskedMsg", "maskedMsg");
        this.masker = masker;
    }

    /**
     * Factory method required by Log4j2's plugin system.
     *
     * @param options detector toggles such as {@code +PHONE}, may be {@code null}
     * @return a new converter instance
     */
    public static MaskingMessagePatternConverter newInstance(String[] options) {
        return new MaskingMessagePatternConverter(masker(options == null ? List.of() : Arrays.asList(options)));
    }

    /** Builds the masker for detector options, reporting invalid ones to the status logger. */
    static LogMasker masker(List<String> options) {
        try {
            return LogMasker.fromOptions(options);
        } catch (IllegalArgumentException e) {
            StatusLogger.getLogger().error("[BankMasker] {}; using the default detectors", e.getMessage());
            return LogMasker.defaults();
        }
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        toAppendTo.append(masker.mask(event.getMessage().getFormattedMessage()));
    }
}
