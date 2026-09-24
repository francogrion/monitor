package com.service;

import com.domain.SensorData;
import com.domain.SensorReadingEntity;
import com.repository.SensorReadingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
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
    // No sensorId tag: it comes unvalidated from clients and could explode metric cardinality
    private final Counter readingsReceived;
    private final Counter averageAnomalies;
    private final Counter differenceAnomalies;
    private final DistributionSummary batchSize;

    public MonitorService(ConfigService configService, SensorReadingRepository sensorReadingRepository,
                          MeterRegistry meterRegistry) {
        this.configService = configService;
        this.sensorReadingRepository = sensorReadingRepository;
        this.readingsReceived = Counter.builder("monitor.readings.received")
                .description("Sensor readings accepted for aggregation")
                .register(meterRegistry);
        this.averageAnomalies = anomalyCounter(meterRegistry, "average");
        this.differenceAnomalies = anomalyCounter(meterRegistry, "difference");
        this.batchSize = DistributionSummary.builder("monitor.aggregation.batch.size")
                .description("Readings aggregated per cycle")
                .register(meterRegistry);
    }

    private static Counter anomalyCounter(MeterRegistry meterRegistry, String type) {
        return Counter.builder("monitor.anomalies")
                .description("Anomalies detected during aggregation")
                .tag("type", type)
                .register(meterRegistry);
    }

    public void read(SensorData sensorData) {
        if (sensorData == null) {
            log.error("Error to read");
            throw new IllegalArgumentException("SensorData failed!");
        }
        log.atInfo()
                .addKeyValue("sensorId", sensorData.getSensorId())
                .addKeyValue("data", sensorData.getData())
                .addKeyValue("timestamp", sensorData.getTimestamp())
                .log("Data collected: {}", sensorData);
        sensorReadingRepository.save(toEntity(sensorData));
        readingsReceived.increment();
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

        double avg = calculateAverage(snapshot);
        double max = calculateMax(snapshot);
        double min = calculateMin(snapshot);
        checkAverage(avg);
        checkDifference(max, min);

        sensorReadingRepository.deleteAllInBatch(pending);
        batchSize.record(pending.size());
        log.atInfo()
                .addKeyValue("readings", pending.size())
                .addKeyValue("average", avg)
                .addKeyValue("max", max)
                .addKeyValue("min", min)
                .log("Data processed!");
    }

    private void checkDifference(double max, double min) {
        log.info("Maximum is: {}", max);
        log.info("Minimum is: {}", min);
        double s = configService.getS();
        double diff = max - min;
        if (AnomalyChecker.isDifferenceAnomaly(diff, s)) {
            differenceAnomalies.increment();
            log.atError()
                    .addKeyValue("anomaly", "difference")
                    .addKeyValue("value", diff)
                    .addKeyValue("threshold", s)
                    .log("Difference '{}' is greater than {}", diff, s);
        }
    }

    private void checkAverage(double avg) {
        log.info("Average is: {}", avg);
        double m = configService.getM();
        if (AnomalyChecker.isAverageAnomaly(avg, m)) {
            averageAnomalies.increment();
            log.atError()
                    .addKeyValue("anomaly", "average")
                    .addKeyValue("value", avg)
                    .addKeyValue("threshold", m)
                    .log("Average '{}' is greater than {}", avg, m);
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
