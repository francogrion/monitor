package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor.defaults")
public record ConfigDefaults(double m, double s) {
}
