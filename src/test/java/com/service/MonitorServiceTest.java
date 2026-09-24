package com.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.domain.SensorData;
import com.domain.SensorReadingEntity;
import com.repository.SensorReadingRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private SimpleMeterRegistry meterRegistry;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitorService = new MonitorService(configService, sensorReadingRepository, meterRegistry);

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

    @Test
    void shouldCountReceivedReadings() {
        monitorService.read(sensorData("1", 10.0));
        monitorService.read(sensorData("2", 20.0));

        assertEquals(2.0, meterRegistry.get("monitor.readings.received").counter().count());
    }

    @Test
    void shouldNotCountRejectedReadings() {
        assertThrows(IllegalArgumentException.class, () -> monitorService.read(null));

        assertEquals(0.0, meterRegistry.get("monitor.readings.received").counter().count());
    }

    @Test
    void shouldCountAverageAnomalies() {
        when(configService.getM()).thenReturn(10.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(reading("1", 20.0)));

        monitorService.processData();

        assertEquals(1.0, anomalies("average"));
        assertEquals(0.0, anomalies("difference"));
    }

    @Test
    void shouldCountDifferenceAnomalies() {
        when(configService.getM()).thenReturn(50.0);
        when(configService.getS()).thenReturn(5.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(reading("1", 10.0), reading("2", 20.0)));

        monitorService.processData();

        assertEquals(0.0, anomalies("average"));
        assertEquals(1.0, anomalies("difference"));
    }

    @Test
    void shouldRecordHowManyReadingsEachCycleProcessed() {
        when(sensorReadingRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(reading("1", 10.0), reading("2", 20.0)))
                .thenReturn(List.of());

        monitorService.processData();
        monitorService.processData();

        DistributionSummary batchSize = meterRegistry.get("monitor.aggregation.batch.size").summary();
        assertEquals(2, batchSize.count());
        assertEquals(2.0, batchSize.totalAmount());
        assertEquals(2.0, batchSize.max());
    }

    @Test
    void shouldAttachSensorFieldsToCollectedLog() {
        monitorService.read(sensorData("1", 10.0));

        Map<String, Object> fields = keyValuesOf(Level.INFO, "Data collected");
        assertEquals("1", fields.get("sensorId"));
        assertEquals(10.0, fields.get("data"));
    }

    @Test
    void shouldAttachAnomalyFieldsToAnomalyLog() {
        when(configService.getM()).thenReturn(10.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc()).thenReturn(List.of(reading("1", 20.0)));

        monitorService.processData();

        Map<String, Object> fields = keyValuesOf(Level.ERROR, "Average");
        assertEquals("average", fields.get("anomaly"));
        assertEquals(20.0, fields.get("value"));
        assertEquals(10.0, fields.get("threshold"));
    }

    @Test
    void shouldAttachCycleSummaryToProcessedLog() {
        when(configService.getM()).thenReturn(50.0);
        when(configService.getS()).thenReturn(50.0);
        when(sensorReadingRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(reading("1", 10.0), reading("2", 20.0)));

        monitorService.processData();

        Map<String, Object> fields = keyValuesOf(Level.INFO, "Data processed");
        assertEquals(2, fields.get("readings"));
        assertEquals(15.0, fields.get("average"));
        assertEquals(20.0, fields.get("max"));
        assertEquals(10.0, fields.get("min"));
    }

    private double anomalies(String type) {
        return meterRegistry.get("monitor.anomalies").tag("type", type).counter().count();
    }

    private Map<String, Object> keyValuesOf(Level level, String messageFragment) {
        ILoggingEvent event = logAppender.list.stream()
                .filter(e -> e.getLevel() == level && e.getFormattedMessage().contains(messageFragment))
                .findFirst()
                .orElseThrow();
        List<KeyValuePair> pairs = event.getKeyValuePairs() == null ? List.of() : event.getKeyValuePairs();
        return pairs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
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
