package io.github.zeytx.bankmasker.logging.logback;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.zeytx.bankmasker.logging.LogMasker;

/**
 * Logback converter that masks sensitive data (secrets, PAN, emails, IBAN...)
 * in the formatted log message.
 *
 * <p>Register it in {@code logback.xml} and use it instead of {@code %msg}:
 * <pre>{@code
 * <configuration>
 *   <conversionRule conversionWord="maskedMsg"
 *                   converterClass="io.github.zeytx.bankmasker.logging.logback.MaskingMessageConverter"/>
 *   <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder>
 *       <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %maskedMsg%n</pattern>
 *     </encoder>
 *   </appender>
 * </configuration>
 * }</pre>
 *
 * <p>Detectors can be toggled with options, e.g. {@code %maskedMsg{+PHONE, -EMAIL}}
 * (see {@link LogMasker.Detector}).
 *
 * @since 1.1.0
 * @see LogMasker
 */
public class MaskingMessageConverter extends MessageConverter {

    private LogMasker masker = LogMasker.defaults();

    @Override
    public void start() {
        try {
            masker = LogMasker.fromOptions(getOptionList());
        } catch (IllegalArgumentException e) {
            addError("[BankMasker] " + e.getMessage() + "; using the default detectors");
        }
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        return masker.mask(super.convert(event));
    }
}
