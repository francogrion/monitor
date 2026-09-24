package com.controller;

import com.service.MonitorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    // 202: the reading is stored and will be aggregated in the next cycle, not processed now
    @PostMapping("/data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SensorReadingRequest readSensorData(@Valid @RequestBody SensorReadingRequest request) {
        monitorService.read(request.toSensorData());
        return request;
    }
}
