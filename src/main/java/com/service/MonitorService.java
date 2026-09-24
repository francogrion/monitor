package com.service;

import com.domain.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.utils.MathUtils.calculateAverage;
import static com.utils.MathUtils.calculateMax;
import static com.utils.MathUtils.calculateMin;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final ConfigService configService;
    private final List<SensorData> dataList = Collections.synchronizedList(new ArrayList<>());

    public MonitorService(ConfigService configService) {
        this.configService = configService;
    }

    public void read(SensorData sensorData) {
        if (sensorData == null) {
            log.error("Error to read");
            throw new IllegalArgumentException("SensorData failed!");
        }
        log.info("Data collected: {}", sensorData);
        dataList.add(sensorData);
    }

    @Scheduled(fixedRate = 30000)
    public void processData() {
        List<SensorData> snapshot;
        synchronized (dataList) {
            snapshot = new ArrayList<>(dataList);
            dataList.clear();
        }
        checkAverage(snapshot);
        checkDifference(snapshot);
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
}
