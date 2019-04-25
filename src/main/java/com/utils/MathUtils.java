package com.utils;

import com.domain.SensorData;

import java.util.List;

public class MathUtils {

    private MathUtils() {
    }

    public static double calculateAverage(List<SensorData> sensorDataList) {
        Double sum = 0.0;
        if (sensorDataList != null && !sensorDataList.isEmpty()) {
            for (SensorData sensorData : sensorDataList) {
                sum += sensorData.getData();
            }
            return sum / sensorDataList.size();
        }
        return sum;
    }

    public static double calculateMax(List<SensorData> sensorDataList) {
        Double max = 0.0;
        if (sensorDataList != null && !sensorDataList.isEmpty()) {
            for (SensorData sensorData : sensorDataList) {
                if (sensorData.getData() > max) {
                    max = sensorData.getData();
                }
            }
        }
        return max;
    }

    public static double calculateMin(List<SensorData> sensorDataList) {
        Double min = 0.0;
        if (sensorDataList != null && !sensorDataList.isEmpty()) {
            min = sensorDataList.get(0).getData();
            for (SensorData sensorData : sensorDataList) {
                if (sensorData.getData() < min) {
                    min = sensorData.getData();
                }
            }
        }
        return min;
    }
}
