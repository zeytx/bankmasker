package io.github.zeytx.bankmasker.logging.log4j2;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.PatternConverter;
import org.apache.logging.log4j.core.pattern.ThrowablePatternConverter;

/**
 * Log4j2 pattern converter that masks sensitive data in the stack trace,
 * including exception messages and causes.
 *
 * <p>Without it, {@code PatternLayout} appends the stack trace unmasked even
 * when the pattern uses {@code %maskedMsg}. Use {@code %maskedEx} (or
 * {@code %mex}) instead of {@code %ex}; it accepts the same options:
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

    private MaskingThrowablePatternConverter(Configuration config, String[] options) {
        super("MaskedThrowable", "throwable", options, config);
    }

    /**
     * Factory method required by Log4j2's plugin system.
     *
     * @param config  the current configuration, may be {@code null}
     * @param options converter options, as for {@code %ex}
     * @return a new converter instance
     */
    public static MaskingThrowablePatternConverter newInstance(Configuration config, String[] options) {
        return new MaskingThrowablePatternConverter(config, options);
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        StringBuilder trace = new StringBuilder();
        super.format(event, trace);
        toAppendTo.append(LogMasker.maskMessage(trace.toString()));
    }
}
