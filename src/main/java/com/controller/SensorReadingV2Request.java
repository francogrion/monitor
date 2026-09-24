package com.controller;

import com.domain.SensorData;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Same as v1 except the timestamp, which is a typed instant instead of free text
@Schema(description = "A reading of one sensor")
public record SensorReadingV2Request(
        @Schema(description = "Sensor that took the reading", example = "sensor-2")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,64}", message = "must be 1-64 letters, digits, '.', '_' or '-'")
        String sensorId,

        @Schema(description = "Measured value", example = "33.54")
        @NotNull
        Double data,

        @Schema(description = "When the reading was taken: ISO-8601 date-time with offset (`Z` or `±hh:mm`). "
                + "Stored and returned in UTC", example = "2026-09-24T08:12:49.515Z")
        @NotNull
        @JsonDeserialize(using = IsoOffsetDateTimeDeserializer.class)
        OffsetDateTime timestamp) {

    SensorReadingV2Request inUtc() {
        return new SensorReadingV2Request(sensorId, data, timestamp.withOffsetSameInstant(ZoneOffset.UTC));
    }

    SensorData toSensorData() {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId(sensorId);
        sensorData.setData(data);
        sensorData.setTimestamp(timestamp.toInstant().toString());
        return sensorData;
    }
}
