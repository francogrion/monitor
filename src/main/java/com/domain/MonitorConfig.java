package com.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Constants used to detect anomalies", requiredProperties = {"m", "s"})
public record MonitorConfig(double m, double s) {
}
