package io.github.zeytx.bankmasker.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zeytx.bankmasker.MaskData;
import io.github.zeytx.bankmasker.MaskType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks measuring BankMasker serialization overhead.
 *
 * <p>Run with:
 * <pre>
 * java -jar bankmasker-benchmark/target/bankmasker-benchmark-1.0.0-SNAPSHOT.jar
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MaskingSerializerBenchmark {

    private ObjectMapper mapper;
    private MaskedDTO maskedDto;
    private PlainDTO plainDto;

    // DTO with masking annotations
    public static class MaskedDTO {
        @MaskData(MaskType.CREDIT_CARD)
        public String cardNumber = "4111111111111111";

        @MaskData(MaskType.EMAIL)
        public String email = "john.doe@example.com";

        @MaskData(MaskType.PHONE)
        public String phone = "+525512345678";

        @MaskData(MaskType.DNI)
        public String dni = "ABCD123456";

        @MaskData(MaskType.IBAN)
        public String iban = "ES6621000418401234567891";

        @MaskData(MaskType.SSN)
        public String ssn = "123-45-6789";

        @MaskData(MaskType.NAME)
        public String name = "John Doe";

        @MaskData(MaskType.PASSPORT)
        public String passport = "AB1234567";

        @MaskData(MaskType.BANK_ACCOUNT)
        public String bankAccount = "12345678901234";

        @MaskData(MaskType.IP_ADDRESS)
        public String ip = "192.168.1.100";

        public String plainField = "this is not masked";
    }

    // DTO without masking — baseline
    public static class PlainDTO {
        public String cardNumber = "4111111111111111";
        public String email = "john.doe@example.com";
        public String phone = "+525512345678";
        public String dni = "ABCD123456";
        public String iban = "ES6621000418401234567891";
        public String ssn = "123-45-6789";
        public String name = "John Doe";
        public String passport = "AB1234567";
        public String bankAccount = "12345678901234";
        public String ip = "192.168.1.100";
        public String plainField = "this is not masked";
    }

    @Setup
    public void setup() {
        mapper = new ObjectMapper();
        maskedDto = new MaskedDTO();
        plainDto = new PlainDTO();
    }

    @Benchmark
    public String serializeWithMasking() throws Exception {
        return mapper.writeValueAsString(maskedDto);
    }

    @Benchmark
    public String serializeWithoutMasking() throws Exception {
        return mapper.writeValueAsString(plainDto);
    }

    @Benchmark
    public String serializeSingleCreditCard() throws Exception {
        return mapper.writeValueAsString(new Object() {
            @MaskData(MaskType.CREDIT_CARD)
            public final String card = "4111111111111111";
        });
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MaskingSerializerBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

