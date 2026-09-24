package com.repository;

import com.domain.SensorReadingEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SensorReadingRepositoryTest {

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @Test
    void shouldReturnEmptyWhenNoReadingsArePending() {
        List<SensorReadingEntity> pending = sensorReadingRepository.findAllByOrderByIdAsc();

        assertTrue(pending.isEmpty());
    }

    @Test
    void shouldReturnReadingsInInsertionOrder() {
        sensorReadingRepository.saveAndFlush(reading("1", 10.0));
        sensorReadingRepository.saveAndFlush(reading("2", 20.0));
        sensorReadingRepository.saveAndFlush(reading("3", 30.0));

        List<SensorReadingEntity> pending = sensorReadingRepository.findAllByOrderByIdAsc();

        assertEquals(3, pending.size());
        assertEquals("1", pending.get(0).getSensorId());
        assertEquals("2", pending.get(1).getSensorId());
        assertEquals("3", pending.get(2).getSensorId());
    }

    @Test
    void shouldDeleteOnlyTheGivenReadings() {
        SensorReadingEntity first = sensorReadingRepository.saveAndFlush(reading("1", 10.0));
        SensorReadingEntity second = sensorReadingRepository.saveAndFlush(reading("2", 20.0));

        sensorReadingRepository.deleteAllInBatch(List.of(first));

        List<SensorReadingEntity> remaining = sensorReadingRepository.findAllByOrderByIdAsc();
        assertEquals(1, remaining.size());
        assertEquals(second.getId(), remaining.get(0).getId());
    }

    private static SensorReadingEntity reading(String sensorId, double data) {
        SensorReadingEntity entity = new SensorReadingEntity();
        entity.setSensorId(sensorId);
        entity.setData(data);
        entity.setTimestamp("20192304123322");
        return entity;
    }
}
