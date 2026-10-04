package io.github.zeytx.bankmasker.logging.logback;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.zeytx.bankmasker.logging.LogMasker;

/**
 * Logback converter that masks sensitive data in the stack trace, including
 * exception messages and causes (e.g. {@code "Invalid card 4111111111111111"}).
 *
 * <p>Without it, Logback appends the stack trace unmasked even when the pattern
 * uses {@code %maskedMsg}. Register it and use {@code %maskedEx} instead of
 * {@code %ex}; it accepts the same options ({@code %maskedEx{short}}, …):
 * <pre>{@code
 * <conversionRule conversionWord="maskedEx"
 *                 converterClass="io.github.zeytx.bankmasker.logging.logback.MaskingThrowableProxyConverter"/>
 * <pattern>%d %-5level %logger - %maskedMsg%n%maskedEx</pattern>
 * }</pre>
 *
 * @since 1.1.0
 * @see LogMasker
 */
public class MaskingThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.maskMessage(super.convert(event));
    }
}
