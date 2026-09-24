package com.controller;

import com.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Readings", description = "Sensor readings to aggregate")
@RestController
@RequestMapping("/api/v2/monitor")
public class MonitorV2Controller {

    private final MonitorService monitorService;

    public MonitorV2Controller(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Operation(summary = "Send a sensor reading",
            description = "Stores the reading; it is aggregated with the other readings of its sensor in the next 30-second cycle.")
    @ApiResponse(responseCode = "202", description = "Reading stored; the body echoes it with the timestamp in UTC")
    @PostMapping("/data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SensorReadingV2Request readSensorData(@Valid @RequestBody SensorReadingV2Request request) {
        SensorReadingV2Request reading = request.inUtc();
        monitorService.read(reading.toSensorData());
        return reading;
    }
}
