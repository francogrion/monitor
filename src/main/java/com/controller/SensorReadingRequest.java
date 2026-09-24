package com.controller;

import com.domain.SensorData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Restrictive character sets keep line breaks and control characters out of the logs (log forging)
public record SensorReadingRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}", message = "must be 1-64 letters, digits, '.', '_' or '-'")
        String sensorId,

        @NotNull
        Double data,

        @NotBlank
        @Pattern(regexp = "[0-9A-Za-z:.+-]{1,64}", message = "must be 1-64 characters of a date-time (digits, letters, ':', '.', '+', '-')")
        String timestamp) {

    SensorData toSensorData() {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId(sensorId);
        sensorData.setData(data);
        sensorData.setTimestamp(timestamp);
        return sensorData;
    }
}
