package com.utils;

import com.domain.SensorData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonUtilsTest {

    private static final String JSON_SENSOR_DATA = "{\n" +
            "  \"sensorId\" : \"2\",\n" +
            "  \"data\" : 35.54,\n" +
            "  \"timestamp\" : \"20192304123322\"\n" +
            "}";

    @Test
    void shouldReturnStringNullWhenParseDataToJsonReceivesNull() {
        assertEquals("null", JsonUtils.dataToJson(null));
    }

    @Test
    void shouldParseDataToJson() {
        SensorData sensorData = buildSensorData();

        String json = JsonUtils.dataToJson(sensorData);

        assertNotNull(json);
        assertEquals(JSON_SENSOR_DATA, json);
    }

    private static SensorData buildSensorData() {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId("2");
        sensorData.setData(35.54);
        sensorData.setTimestamp("20192304123322");

        return sensorData;
    }
}
