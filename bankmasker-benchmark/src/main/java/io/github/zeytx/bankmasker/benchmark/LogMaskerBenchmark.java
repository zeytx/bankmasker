package io.github.zeytx.bankmasker.benchmark;

import io.github.zeytx.bankmasker.logging.LogMasker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for {@link LogMasker#maskMessage(String)}, which runs on
 * every log line when the Logback/Log4j2 converters are active — the cost
 * of the clean (no-match) path is the number that matters most.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class LogMaskerBenchmark {

    private static final String CLEAN_MESSAGE =
            "GET /api/v1/orders/12345 completed in 42ms with status 200";

    private static final String PAN_MESSAGE =
            "charge card 4111111111111111 approved for merchant 998877";

    private static final String MIXED_MESSAGE =
            "user a.b@mail.com paid with 378282246310005 from DE89370400440532013000";

    private static final String LUHN_INVALID_MESSAGE =
            "order 1234567890123456 created at 1718000000000";

    @Benchmark
    public String cleanMessage() {
        return LogMasker.maskMessage(CLEAN_MESSAGE);
    }

    @Benchmark
    public String panMessage() {
        return LogMasker.maskMessage(PAN_MESSAGE);
    }

    @Benchmark
    public String mixedMessage() {
        return LogMasker.maskMessage(MIXED_MESSAGE);
    }

    // 10 KB token without '@': regression guard for email-regex backtracking
    // (quadratic before 1.1.0: ~0.37 s per line at this size)
    private static final String LONG_TOKEN_MESSAGE = "token=" + "a".repeat(10_000);

    @Benchmark
    public String luhnInvalidMessage() {
        return LogMasker.maskMessage(LUHN_INVALID_MESSAGE);
    }

    @Benchmark
    public String longTokenMessage() {
        return LogMasker.maskMessage(LONG_TOKEN_MESSAGE);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(LogMaskerBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
