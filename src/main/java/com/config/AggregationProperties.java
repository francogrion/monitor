package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

// Binds the same monitor.aggregation.* properties the @Scheduled/@SchedulerLock placeholders read, only to validate
// them at startup: an inconsistent combination would silently skip slots or let two instances aggregate in one slot.
@ConfigurationProperties(prefix = "monitor.aggregation")
public record AggregationProperties(String cron, Duration lockAtLeastFor, Duration lockAtMostFor) {

    // Enough firings to find the shortest gap of irregular crons (e.g. "0 0,5 * * * *") without a time horizon
    private static final int FIRINGS_TO_INSPECT = 1000;

    public AggregationProperties {
        if (lockAtLeastFor == null || lockAtLeastFor.isNegative()) {
            throw new IllegalArgumentException("monitor.aggregation.lock-at-least-for must be zero or positive, was " + lockAtLeastFor);
        }
        if (lockAtMostFor == null || lockAtLeastFor.compareTo(lockAtMostFor) > 0) {
            throw new IllegalArgumentException("monitor.aggregation.lock-at-least-for (" + lockAtLeastFor
                    + ") must not exceed monitor.aggregation.lock-at-most-for (" + lockAtMostFor + ")");
        }
        Duration interval = minimumInterval(cron);
        if (lockAtMostFor.compareTo(interval) >= 0) {
            throw new IllegalArgumentException("monitor.aggregation.lock-at-most-for (" + lockAtMostFor
                    + ") must be shorter than the shortest interval between runs of monitor.aggregation.cron '"
                    + cron + "' (" + interval + "), otherwise a crashed instance's lock can block the next run");
        }
    }

    static Duration minimumInterval(String cron) {
        CronExpression expression;
        try {
            expression = CronExpression.parse(cron);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("monitor.aggregation.cron '" + cron + "' is not a valid cron expression", e);
        }
        ZonedDateTime previous = expression.next(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        if (previous == null) {
            throw new IllegalArgumentException("monitor.aggregation.cron '" + cron + "' never fires");
        }
        Duration shortest = null;
        for (int i = 0; i < FIRINGS_TO_INSPECT; i++) {
            ZonedDateTime next = expression.next(previous);
            if (next == null) {
                break;
            }
            Duration gap = Duration.between(previous, next);
            if (shortest == null || gap.compareTo(shortest) < 0) {
                shortest = gap;
            }
            previous = next;
        }
        if (shortest == null) {
            throw new IllegalArgumentException("monitor.aggregation.cron '" + cron + "' fires only once");
        }
        return shortest;
    }
}
