package com.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

// Partial update: a missing field leaves that constant unchanged.
// S is a threshold for max - min, which is never negative, so a negative S would flag every cycle.
public record ConfigUpdateRequest(Double m, @PositiveOrZero Double s) {

    @JsonIgnore
    @AssertTrue(message = "at least one of m or s must be provided")
    public boolean isAnyValueProvided() {
        return m != null || s != null;
    }
}
