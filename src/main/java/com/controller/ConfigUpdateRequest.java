package com.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

// Partial update: a missing field leaves that constant unchanged.
// S is a threshold for max - min, which is never negative, so a negative S would flag every cycle.
@Schema(description = "Constants to change; at least one", minProperties = 1)
public record ConfigUpdateRequest(
        @Schema(description = "Anomaly when the average of a sensor's readings in a cycle exceeds it", example = "25")
        Double m,

        @Schema(description = "Anomaly when max - min of a sensor's readings in a cycle exceeds it", example = "34")
        @PositiveOrZero
        Double s) {

    @JsonIgnore
    @AssertTrue(message = "at least one of m or s must be provided")
    public boolean isAnyValueProvided() {
        return m != null || s != null;
    }
}
