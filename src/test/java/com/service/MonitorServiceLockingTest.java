package com.service;

import com.domain.SensorData;
import com.repository.SensorReadingRepository;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "monitor.scheduling.enabled=false")
@ActiveProfiles("test")
class MonitorServiceLockingTest {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Expire rather than delete the lock: ShedLock caches that the row exists and afterwards only UPDATEs it
    @AfterEach
    void cleanUp() {
        sensorReadingRepository.deleteAll();
        jdbcTemplate.update("UPDATE shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00'");
    }

    @Test
    void shouldProcessPendingReadingsWhenNoOtherInstanceHoldsTheLock() {
        monitorService.read(sensorData());

        monitorService.processData();

        assertEquals(0, sensorReadingRepository.count());
    }

    @Test
    void shouldSkipProcessingWhileAnotherInstanceHoldsTheLock() {
        monitorService.read(sensorData());
        SimpleLock heldByOtherInstance = lockProvider.lock(new LockConfiguration(
                Instant.now(), MonitorService.PROCESS_LOCK_NAME, Duration.ofMinutes(1), Duration.ZERO)).orElseThrow();
        try {
            monitorService.processData();

            assertEquals(1, sensorReadingRepository.count());
        } finally {
            heldByOtherInstance.unlock();
        }
    }

    private static SensorData sensorData() {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId("1");
        sensorData.setData(10.0);
        sensorData.setTimestamp("20192304123322");
        return sensorData;
    }
}
