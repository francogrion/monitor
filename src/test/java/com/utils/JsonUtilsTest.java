package com.utils;

import com.domain.SensorData;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class JsonUtilsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final String JSON_SENSOR_DATA = "{\r\n" +
            "  \"sensorId\" : \"2\",\r\n" +
            "  \"data\" : 35.54,\r\n" +
            "  \"timestamp\" : \"20192304123322\"\r\n" +
            "}";

    @Test
    public void shouldReturnStringNullWhenParseDataToJsonReceivesNull() throws Exception {

        String json = JsonUtils.dataToJson(null);

        assertThat(json, is("null"));
    }

    @Test
    public void shouldParseDataToJson() throws Exception {
        SensorData sensorData = buildSensorData();

        String json = JsonUtils.dataToJson(sensorData);

        assertNotNull(json);
        assertThat(json, is(JSON_SENSOR_DATA));
    }

    private static SensorData buildSensorData() {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId("2");
        sensorData.setData(35.54);
        sensorData.setTimestamp("20192304123322");

        return sensorData;
    }

    @Test
    public void shouldReturnNullWhenParseJsonToSensorDataReceivesNull() throws Exception {

        SensorData sensorData = JsonUtils.jsonToSensorData(null);

        assertNull(sensorData);
    }

    @Test
    public void shouldThrowExceptionWhenParseJsonToSensorDataReceivesEmptyString() throws Exception {
        expectedException.expect(RuntimeException.class);

        JsonUtils.jsonToSensorData("");
    }

    @Test
    public void shouldParseJsonToSensorData() throws Exception {

        SensorData sensorData = JsonUtils.jsonToSensorData(JSON_SENSOR_DATA);

        assertNotNull(sensorData);
        assertThat(sensorData.getSensorId(), is("2"));
        assertThat(sensorData.getData(), is(35.54));
        assertThat(sensorData.getTimestamp(), is("20192304123322"));
    }

}