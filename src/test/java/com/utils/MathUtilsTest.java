package com.utils;

import com.domain.SensorData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MathUtilsTest {

    @Test
    public void shouldReturnZeroWhenListIsNullForAvg() throws Exception {

        double avg = MathUtils.calculateAverage(null);

        assertThat(avg, is(0.0));
    }

    @Test
    public void shouldReturnZeroWhenListIsEmptyForAvg() throws Exception {

        double avg = MathUtils.calculateAverage(Collections.emptyList());

        assertThat(avg, is(0.0));
    }

    @Test
    public void shouldReturnDataAverageFromValidListWithOneElement() throws Exception {

        double avg = MathUtils.calculateAverage(buildSensorDataListOneElement());

        assertThat(avg, is(35.54));
    }

    @Test
    public void shouldReturnDataAverageFromValidListWithSeveralElements() throws Exception {

        double avg = MathUtils.calculateAverage(buildSensorDataListSeveralElements());

        assertThat(avg, is(22.62));
    }

    @Test
    public void shouldReturnZeroWhenListIsNullForMax() throws Exception {

        double max = MathUtils.calculateMax(null);

        assertThat(max, is(0.0));
    }

    @Test
    public void shouldReturnZeroWhenListIsEmptyForMax() throws Exception {

        double max = MathUtils.calculateMax(null);

        assertThat(max, is(0.0));
    }

    @Test
    public void shouldReturnMaxValueFromValidListWithOneElement() throws Exception {

        double max = MathUtils.calculateMax(buildSensorDataListOneElement());

        assertThat(max, is(35.54));
    }

    @Test
    public void shouldReturnMaxValueFromValidListWithSeveralElements() throws Exception {

        double max = MathUtils.calculateMax(buildSensorDataListSeveralElements());

        assertThat(max, is(35.54));
    }

    @Test
    public void shouldReturnZeroWhenListIsNullForMin() throws Exception {

        double min = MathUtils.calculateMin(null);

        assertThat(min, is(0.0));
    }

    @Test
    public void shouldReturnZeroWhenListIsEmptyForMin() throws Exception {

        double min = MathUtils.calculateMin(null);

        assertThat(min, is(0.0));
    }

    @Test
    public void shouldReturnMinValueFromValidListWithOneElement() throws Exception {

        double min = MathUtils.calculateMin(buildSensorDataListOneElement());

        assertThat(min, is(35.54));
    }

    @Test
    public void shouldReturnMinValueFromValidListWithSeveralElements() throws Exception {

        double min = MathUtils.calculateMin(buildSensorDataListSeveralElements());

        assertThat(min, is(10.2));
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
