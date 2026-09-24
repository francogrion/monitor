package com.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void shouldComputeTheIntervalOfARegularCron() {
        assertEquals(Duration.ofSeconds(30), AggregationProperties.minimumInterval("0,30 * * * * *"));
    }

    @Test
    void shouldComputeTheShortestGapOfAnIrregularCron() {
        assertEquals(Duration.ofMinutes(5), AggregationProperties.minimumInterval("0 0,5 * * * *"));
    }

    @Test
    void shouldAcceptTheDefaultConfiguration() {
        assertDoesNotThrow(() -> new AggregationProperties("0,30 * * * * *", Duration.ofSeconds(20), Duration.ofSeconds(29)));
    }

    @Test
    void shouldRejectLockAtMostForThatIsNotShorterThanTheInterval() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AggregationProperties("0,30 * * * * *", Duration.ofSeconds(20), Duration.ofSeconds(30)));

        assertTrue(e.getMessage().contains("lock-at-most-for"), e.getMessage());
        assertTrue(e.getMessage().contains("PT30S"), e.getMessage());
    }

    @Test
    void shouldRejectLockAtLeastForLongerThanLockAtMostFor() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AggregationProperties("0,30 * * * * *", Duration.ofSeconds(25), Duration.ofSeconds(20)));

        assertTrue(e.getMessage().contains("lock-at-least-for"), e.getMessage());
    }

    @Test
    void shouldRejectNegativeLockAtLeastFor() {
        assertThrows(IllegalArgumentException.class,
                () -> new AggregationProperties("0,30 * * * * *", Duration.ofSeconds(-1), Duration.ofSeconds(20)));
    }

    @Test
    void shouldRejectAnInvalidCron() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AggregationProperties("not a cron", Duration.ofSeconds(20), Duration.ofSeconds(29)));

        assertTrue(e.getMessage().contains("cron"), e.getMessage());
    }

    @Test
    void shouldPreventTheApplicationFromStartingWithAnInconsistentConfiguration() {
        contextRunner.withPropertyValues(
                        "monitor.aggregation.cron=*/10 * * * * *",
                        "monitor.aggregation.lock-at-least-for=PT5S",
                        "monitor.aggregation.lock-at-most-for=PT15S")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(rootMessageOf(failure).contains("PT10S"), rootMessageOf(failure));
                });
    }

    @Test
    void shouldStartWithAConsistentConfiguration() {
        contextRunner.withPropertyValues(
                        "monitor.aggregation.cron=0,30 * * * * *",
                        "monitor.aggregation.lock-at-least-for=PT20S",
                        "monitor.aggregation.lock-at-most-for=PT29S")
                .run(context -> assertNotNull(context.getBean(AggregationProperties.class)));
    }

    private static String rootMessageOf(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    @EnableConfigurationProperties(AggregationProperties.class)
    static class Config {
    }
}
