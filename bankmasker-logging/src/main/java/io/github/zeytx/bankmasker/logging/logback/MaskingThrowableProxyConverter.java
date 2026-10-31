package io.github.zeytx.bankmasker.logging.logback;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.zeytx.bankmasker.logging.LogMasker;

import java.util.ArrayList;
import java.util.List;

/**
 * Logback converter that masks sensitive data in the stack trace, including
 * exception messages and causes (e.g. {@code "Invalid card 4111111111111111"}).
 *
 * <p>Without it, Logback appends the stack trace unmasked even when the pattern
 * uses {@code %maskedMsg}. Register it and use {@code %maskedEx} instead of
 * {@code %ex}; it accepts the same options ({@code %maskedEx{short}}, …) plus
 * detector toggles such as {@code %maskedEx{full, +PHONE}}:
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

    private final List<String> detectorOptions = new ArrayList<>();
    private LogMasker masker = LogMasker.defaults();

    @Override
    public void setOptionList(List<String> optionList) {
        // Detector toggles are ours; depth and evaluator options go to Logback
        List<String> own = new ArrayList<>();
        if (optionList != null) {
            for (String option : optionList) {
                (LogMasker.isDetectorOption(option) ? detectorOptions : own).add(option);
            }
        }
        super.setOptionList(own);
    }

    @Override
    public void start() {
        try {
            masker = LogMasker.fromOptions(detectorOptions);
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
