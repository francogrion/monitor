package com.service;

import com.repository.SensorReadingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Cron that never fires during the test (Jan 1st, midnight); context is discarded so its scheduler can't leak into other tests
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "monitor.aggregation.cron=0 0 0 1 1 *",
        "monitor.aggregation.lock-at-least-for=PT45S",
        "monitor.aggregation.lock-at-most-for=PT50S"
})
@ActiveProfiles("test")
@DirtiesContext
class AggregationConfigurationTest {

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Expire rather than delete the lock: ShedLock caches that the row exists and afterwards only UPDATEs it
    @AfterEach
    void cleanUp() {
        sensorReadingRepository.deleteAll();
        jdbcTemplate.update("UPDATE shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00'");
    }

    @Test
    void shouldScheduleAggregationWithConfiguredCron() {
        boolean scheduled = scheduledTaskHolder.getScheduledTasks().stream()
                .map(scheduledTask -> scheduledTask.getTask())
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .anyMatch(cronTask -> cronTask.getExpression().equals("0 0 0 1 1 *"));

        assertTrue(scheduled);
    }

    @Test
    void shouldHoldTheLockForTheConfiguredMinimumDuration() {
        monitorService.processData();

        Double heldSeconds = jdbcTemplate.queryForObject(
                "SELECT EXTRACT(EPOCH FROM lock_until - locked_at) FROM shedlock WHERE name = ?",
                Double.class, MonitorService.PROCESS_LOCK_NAME);
        assertEquals(45.0, heldSeconds, 0.5);
    }
}
