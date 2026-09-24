package com.controller;

import com.domain.MonitorConfig;
import com.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Configuration", description = "Constants M and S used to detect anomalies")
@RestController
// Unchanged in v2; served under both versions so a client can move its whole base path
@RequestMapping({"/api/v1/config", "/api/v2/config"})
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary = "Read the constants")
    @ApiResponse(responseCode = "200", description = "Current constants")
    @GetMapping
    public MonitorConfig getConfig() {
        return configService.getConfig();
    }

    @Operation(summary = "Update the constants",
            description = "Partial update: send one or both; a missing one stays unchanged. Applies from the next aggregation cycle.")
    @ApiResponse(responseCode = "200", description = "Resulting constants")
    @PatchMapping
    public MonitorConfig updateConfig(@Valid @RequestBody ConfigUpdateRequest request) {
        return configService.update(request.m(), request.s());
    }
}
