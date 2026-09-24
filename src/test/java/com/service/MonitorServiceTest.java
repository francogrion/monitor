package com.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.domain.SensorData;
import com.domain.SensorReadingEntity;
import com.repository.SensorReadingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private SensorReadingRepository sensorReadingRepository;

    private MonitorService monitorService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        monitorService = new MonitorService(configService, sensorReadingRepository);

        Logger logger = (Logger) LoggerFactory.getLogger(MonitorService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        lenient().when(sensorReadingRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
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
    void shouldPersistReadingWhenReadingValidData() {
        monitorService.read(sensorData("1", 10.0));

        ArgumentCaptor<SensorReadingEntity> captor = ArgumentCaptor.forClass(SensorReadingEntity.class);
        verify(sensorReadingRepository).save(captor.capture());
        assertEquals("1", captor.getValue().getSensorId());
        assertEquals(10.0, captor.getValue().getData());
        assertTrue(hasLogContaining(Level.INFO, "Data collected"));
    }

    @Test
    void shouldLogErrorWhenAverageExceedsConfiguredM() {
        when(configService.getM()).thenReturn(10.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(reading("1", 20.0)));

        monitorService.processData();

        assertTrue(hasLogContaining(Level.ERROR, "Average"));
    }

    @Test
    void shouldNotLogErrorWhenAverageIsWithinLimit() {
        when(configService.getM()).thenReturn(50.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(reading("1", 20.0)));

        monitorService.processData();

        assertFalse(hasLogContaining(Level.ERROR, "Average"));
    }

    @Test
    void shouldLogErrorWhenDifferenceExceedsConfiguredS() {
        when(configService.getS()).thenReturn(5.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(reading("1", 10.0), reading("2", 20.0)));

        monitorService.processData();

        assertTrue(hasLogContaining(Level.ERROR, "Difference"));
    }

    @Test
    void shouldNotLogErrorWhenDifferenceIsWithinLimit() {
        when(configService.getS()).thenReturn(50.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(reading("1", 10.0), reading("2", 20.0)));

        monitorService.processData();

        assertFalse(hasLogContaining(Level.ERROR, "Difference"));
    }

    @Test
    void shouldDeleteOnlyTheProcessedReadingsAfterProcessing() {
        SensorReadingEntity pending = reading("1", 100.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(pending));

        monitorService.processData();

        verify(sensorReadingRepository).deleteAllInBatch(List.of(pending));
    }

    @Test
    void shouldLogZeroAverageWhenNoReadingsArePending() {
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

    private static SensorReadingEntity reading(String sensorId, double data) {
        SensorReadingEntity entity = new SensorReadingEntity();
        entity.setSensorId(sensorId);
        entity.setData(data);
        entity.setTimestamp("20192304123322");
        return entity;
    }
}
