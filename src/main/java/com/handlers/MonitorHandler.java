package com.handlers;

import com.dao.DataBaseService;
import com.domain.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import static com.utils.MathUtils.calculateAverage;
import static com.utils.MathUtils.calculateMax;
import static com.utils.MathUtils.calculateMin;

public class MonitorHandler extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(MonitorHandler.class);
    private DataBaseService dao = DataBaseService.getInstance();

    private List<SensorData> dataList;

    private MonitorHandler() {
        resetDataList();
    }

    /**
     * Static Factory method of MonitorHandler class
     *
     * @return singleton instance
     */
    public static synchronized MonitorHandler getInstance() {
        return TransferHandlerInstanceContainer.instance;
    }

    private void resetDataList() {
        dataList = new ArrayList<>(960);
    }

    public void run() {
        processData();
    }

    public void read(SensorData sensorData) throws Exception {
        if (sensorData != null) {
            log.info("Data collected: {}", sensorData);
            dataList.add(sensorData);
        } else {
            log.error("Error to read");
            throw new Exception("SensorData failed!");
        }
    }

    private void processData() {
        if (dataList != null) {
            checkAverage();
            checkDifference();
            log.info("Data processed!");
            resetDataList();
        } else {
            log.error("Error to Process Data");
        }
    }

    private void checkDifference() {
        double max = calculateMax(dataList);
        log.info("Maximum is: {}", max);
        double min = calculateMin(dataList);
        log.info("Minimum is: {}", min);
        double s = dao.getS();
        double diff = max - min;
        if (diff > s) {
            log.error("Difference '{}' is greater than {}", diff, s);
        }
    }

    private void checkAverage() {
        double avg = calculateAverage(dataList);
        log.info("Average is: {}", avg);
        double m = dao.getM();
        if (avg > m) {
            log.error("Average '{}' is greater than {}", avg, m);
        }
    }

    /**
     * This method ensures that the same singleton reference is returned when
     * the object is serialized and the deserialized back
     *
     * @return singleton instance
     */
    protected Object readResolve() {
        return TransferHandlerInstanceContainer.instance;
    }

    /**
     * Private static instance holder.
     */
    private static final class TransferHandlerInstanceContainer {
        static final MonitorHandler instance = new MonitorHandler();
    }
}
