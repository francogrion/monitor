package com.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.domain.SensorData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorServiceTest {

    private final ConfigService configService = new ConfigService();
    private final MonitorService monitorService = new MonitorService(configService);
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(MonitorService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(MonitorService.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void shouldThrowWhenReadingNullData() {
        assertThrows(IllegalArgumentException.class, () -> monitorService.read(null));
    }

    @Test
    void shouldLogCollectedDataWhenReadingValidData() {
        monitorService.read(sensorData("1", 10.0));

        assertTrue(hasLogContaining(Level.INFO, "Data collected"));
    }

    @Test
    void shouldLogErrorWhenAverageExceedsConfiguredM() {
        configService.setM("10");
        monitorService.read(sensorData("1", 20.0));

        monitorService.processData();

        assertTrue(hasLogContaining(Level.ERROR, "Average"));
    }

    @Test
    void shouldNotLogErrorWhenAverageIsWithinLimit() {
        configService.setM("50");
        monitorService.read(sensorData("1", 20.0));

        monitorService.processData();

        assertFalse(hasLogContaining(Level.ERROR, "Average"));
    }

    @Test
    void shouldLogErrorWhenDifferenceExceedsConfiguredS() {
        configService.setS("5");
        monitorService.read(sensorData("1", 10.0));
        monitorService.read(sensorData("2", 20.0));

        monitorService.processData();

        assertTrue(hasLogContaining(Level.ERROR, "Difference"));
    }

    @Test
    void shouldNotLogErrorWhenDifferenceIsWithinLimit() {
        configService.setS("50");
        monitorService.read(sensorData("1", 10.0));
        monitorService.read(sensorData("2", 20.0));

        monitorService.processData();

        assertFalse(hasLogContaining(Level.ERROR, "Difference"));
    }

    @Test
    void shouldResetBufferAfterProcessing() {
        monitorService.read(sensorData("1", 100.0));
        monitorService.processData();
        logAppender.list.clear();

        monitorService.processData();

        assertTrue(hasLogContaining(Level.INFO, "Average is: 0.0"));
    }

    private boolean hasLogContaining(Level level, String text) {
        return logAppender.list.stream()
                .anyMatch(event -> event.getLevel() == level && event.getFormattedMessage().contains(text));
    }

    private static SensorData sensorData(String sensorId, double data) {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId(sensorId);
        sensorData.setData(data);
        sensorData.setTimestamp("20192304123322");
        return sensorData;
    }
}
