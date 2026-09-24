package com.controller;

import com.domain.MonitorConfig;
import com.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public MonitorConfig getConfig() {
        return configService.getConfig();
    }

    @PatchMapping
    public MonitorConfig updateConfig(@Valid @RequestBody ConfigUpdateRequest request) {
        return configService.update(request.m(), request.s());
    }
}
