package com.utils;

import com.domain.SensorData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MathUtilsTest {

    @Test
    void shouldReturnZeroWhenListIsNullForAvg() {
        assertEquals(0.0, MathUtils.calculateAverage(null));
    }

    @Test
    void shouldReturnZeroWhenListIsEmptyForAvg() {
        assertEquals(0.0, MathUtils.calculateAverage(Collections.emptyList()));
    }

    @Test
    void shouldReturnDataAverageFromValidListWithOneElement() {
        assertEquals(35.54, MathUtils.calculateAverage(buildSensorDataListOneElement()));
    }

    @Test
    void shouldReturnDataAverageFromValidListWithSeveralElements() {
        assertEquals(22.62, MathUtils.calculateAverage(buildSensorDataListSeveralElements()));
    }

    @Test
    void shouldReturnZeroWhenListIsNullForMax() {
        assertEquals(0.0, MathUtils.calculateMax(null));
    }

    @Test
    void shouldReturnZeroWhenListIsEmptyForMax() {
        assertEquals(0.0, MathUtils.calculateMax(Collections.emptyList()));
    }

    @Test
    void shouldReturnMaxValueFromValidListWithOneElement() {
        assertEquals(35.54, MathUtils.calculateMax(buildSensorDataListOneElement()));
    }

    @Test
    void shouldReturnMaxValueFromValidListWithSeveralElements() {
        assertEquals(35.54, MathUtils.calculateMax(buildSensorDataListSeveralElements()));
    }

    @Test
    void shouldReturnZeroWhenListIsNullForMin() {
        assertEquals(0.0, MathUtils.calculateMin(null));
    }

    @Test
    void shouldReturnZeroWhenListIsEmptyForMin() {
        assertEquals(0.0, MathUtils.calculateMin(Collections.emptyList()));
    }

    @Test
    void shouldReturnMinValueFromValidListWithOneElement() {
        assertEquals(35.54, MathUtils.calculateMin(buildSensorDataListOneElement()));
    }

    @Test
    void shouldReturnMinValueFromValidListWithSeveralElements() {
        assertEquals(10.2, MathUtils.calculateMin(buildSensorDataListSeveralElements()));
    }

    private static List<SensorData> buildSensorDataListOneElement() {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId("2");
        sensorData.setData(35.54);
        sensorData.setTimestamp("20192304123322");

        List<SensorData> sensorDataList = new ArrayList<>();
        sensorDataList.add(sensorData);

        return sensorDataList;
    }

    private static List<SensorData> buildSensorDataListSeveralElements() {
        SensorData sensorData1 = new SensorData();
        sensorData1.setSensorId("1");
        sensorData1.setData(35.54);
        sensorData1.setTimestamp("20192304123322");

        SensorData sensorData2 = new SensorData();
        sensorData2.setSensorId("2");
        sensorData2.setData(22.12);
        sensorData2.setTimestamp("20192304123521");

        SensorData sensorData3 = new SensorData();
        sensorData3.setSensorId("3");
        sensorData3.setData(10.2);
        sensorData3.setTimestamp("20192304124723");

        List<SensorData> sensorDataList = new ArrayList<>();
        sensorDataList.add(sensorData1);
        sensorDataList.add(sensorData2);
        sensorDataList.add(sensorData3);

        return sensorDataList;
    }
}
