package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.ThrowablePatternConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Log4j2 pattern converter that masks sensitive data in the stack trace,
 * including exception messages and causes.
 *
 * <p>Without it, {@code PatternLayout} appends the stack trace unmasked even
 * when the pattern uses {@code %maskedMsg}. Use {@code %maskedEx} (or
 * {@code %mex}) instead of {@code %ex}; it accepts the same options plus
 * detector toggles such as {@code %maskedEx{full}{+PHONE}}:
 * <pre>{@code
 * <PatternLayout pattern="%d %-5level %logger - %maskedMsg%n%maskedEx"/>
 * }</pre>
 *
 * @since 1.1.0
 * @see LogMasker
 */
@Plugin(name = "MaskingThrowablePatternConverter", category = PatternConverter.CATEGORY)
@ConverterKeys({"maskedEx", "mex"})
public final class MaskingThrowablePatternConverter extends ThrowablePatternConverter {

    private final LogMasker masker;

    private MaskingThrowablePatternConverter(Configuration config, String[] options, LogMasker masker) {
        super("MaskedThrowable", "throwable", options, config);
        this.masker = masker;
    }

    /**
     * Factory method required by Log4j2's plugin system.
     *
     * @param config  the current configuration, may be {@code null}
     * @param options converter options, as for {@code %ex}, plus detector toggles
     * @return a new converter instance
     */
    public static MaskingThrowablePatternConverter newInstance(Configuration config, String[] options) {
        List<String> detectorOptions = new ArrayList<>();
        List<String> throwableOptions = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                (LogMasker.isDetectorOption(option) ? detectorOptions : throwableOptions).add(option);
            }
        }
        return new MaskingThrowablePatternConverter(config, throwableOptions.toArray(String[]::new),
                MaskingMessagePatternConverter.masker(detectorOptions));
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        StringBuilder trace = new StringBuilder();
        super.format(event, trace);
        toAppendTo.append(masker.mask(trace.toString()));
    }
}
