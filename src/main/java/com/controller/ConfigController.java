package com.controller;

import com.service.ConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/m")
    public double getM() {
        return configService.getM();
    }

    @PostMapping("/m/{m}")
    public Map<String, String> setM(@PathVariable String m) {
        configService.setM(m);
        return Map.of("newValueForConstantM", m);
    }

    @GetMapping("/s")
    public double getS() {
        return configService.getS();
    }

    @PostMapping("/s/{s}")
    public Map<String, String> setS(@PathVariable String s) {
        configService.setS(s);
        return Map.of("newValueForConstantS", s);
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<Map<String, String>> handleInvalidNumber(NumberFormatException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", e.getMessage()));
    }
}
