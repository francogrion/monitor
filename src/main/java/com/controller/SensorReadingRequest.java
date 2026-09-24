package com.controller;

import com.domain.SensorData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Restrictive character sets keep line breaks and control characters out of the logs (log forging)
@Schema(description = "A reading of one sensor")
public record SensorReadingRequest(
        @Schema(description = "Sensor that took the reading", example = "sensor-2")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}", message = "must be 1-64 letters, digits, '.', '_' or '-'")
        String sensorId,

        @Schema(description = "Measured value", example = "33.54")
        @NotNull
        Double data,

        @Schema(description = "When the reading was taken. Stored as sent, not parsed", example = "2026-09-24T08:12:49.515")
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
