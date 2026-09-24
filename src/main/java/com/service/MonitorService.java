package com.service;

import com.domain.SensorData;
import com.domain.SensorReadingEntity;
import com.repository.SensorReadingRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.utils.MathUtils.calculateAverage;
import static com.utils.MathUtils.calculateMax;
import static com.utils.MathUtils.calculateMin;

@Service
public class MonitorService {

    public static final String PROCESS_LOCK_NAME = "processSensorData";

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final ConfigService configService;
    private final SensorReadingRepository sensorReadingRepository;

    public MonitorService(ConfigService configService, SensorReadingRepository sensorReadingRepository) {
        this.configService = configService;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    public void read(SensorData sensorData) {
        if (sensorData == null) {
            log.error("Error to read");
            throw new IllegalArgumentException("SensorData failed!");
        }
        log.info("Data collected: {}", sensorData);
        sensorReadingRepository.save(toEntity(sensorData));
    }

    // Wall-clock aligned cron so every instance fires in the same slots; the lock lets exactly one win each slot.
    // lockAtLeastFor absorbs clock skew between instances; see monitor.aggregation.* in application.yml.
    @Scheduled(cron = "${monitor.aggregation.cron}")
    @SchedulerLock(name = PROCESS_LOCK_NAME,
            lockAtLeastFor = "${monitor.aggregation.lock-at-least-for}",
            lockAtMostFor = "${monitor.aggregation.lock-at-most-for}")
    @Transactional
    public void processData() {
        List<SensorReadingEntity> pending = sensorReadingRepository.findAllByOrderByIdAsc();
        List<SensorData> snapshot = pending.stream().map(MonitorService::toSensorData).toList();

        checkAverage(snapshot);
        checkDifference(snapshot);

        sensorReadingRepository.deleteAllInBatch(pending);
        log.info("Data processed!");
    }

    private void checkDifference(List<SensorData> snapshot) {
        double max = calculateMax(snapshot);
        log.info("Maximum is: {}", max);
        double min = calculateMin(snapshot);
        log.info("Minimum is: {}", min);
        double s = configService.getS();
        double diff = max - min;
        if (AnomalyChecker.isDifferenceAnomaly(diff, s)) {
            log.error("Difference '{}' is greater than {}", diff, s);
        }
    }

    private void checkAverage(List<SensorData> snapshot) {
        double avg = calculateAverage(snapshot);
        log.info("Average is: {}", avg);
        double m = configService.getM();
        if (AnomalyChecker.isAverageAnomaly(avg, m)) {
            log.error("Average '{}' is greater than {}", avg, m);
        }
    }

    private static SensorReadingEntity toEntity(SensorData sensorData) {
        SensorReadingEntity entity = new SensorReadingEntity();
        entity.setSensorId(sensorData.getSensorId());
        entity.setData(sensorData.getData());
        entity.setTimestamp(sensorData.getTimestamp());
        return entity;
    }

    private static SensorData toSensorData(SensorReadingEntity entity) {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId(entity.getSensorId());
        sensorData.setData(entity.getData());
        sensorData.setTimestamp(entity.getTimestamp());
        return sensorData;
    }
}
