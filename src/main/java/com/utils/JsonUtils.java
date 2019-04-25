package com.utils;

import com.domain.SensorData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;
import java.io.StringWriter;

public class JsonUtils {

    private JsonUtils() {
    }

    public static String dataToJson(Object data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            StringWriter sw = new StringWriter();
            mapper.writeValue(sw, data);
            return sw.toString();
        } catch (IOException e) {
            throw new RuntimeException("IOEXception while mapping object (" + data + ") to JSON");
        }
    }

    public static SensorData jsonToSensorData(String json) {
        ObjectMapper mapper = new ObjectMapper();
        if (json != null) {
            try {
                return mapper.readValue(json, SensorData.class);
            } catch (IOException e) {
                throw new RuntimeException("IOEXception while mapping json (" + json + ") to SensorData");
            }
        } else {
            return null;
        }
    }

    public static String printResponseBody(HttpResponse response) throws IOException {
        return new BasicResponseHandler().handleResponse(response);
    }
}
