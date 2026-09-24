package com.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyCheckerTest {

    @Test
    void shouldDetectAverageAnomalyWhenAverageIsGreaterThanM() {
        assertTrue(AnomalyChecker.isAverageAnomaly(23.0, 22.0));
    }

    @Test
    void shouldNotDetectAverageAnomalyWhenAverageEqualsM() {
        assertFalse(AnomalyChecker.isAverageAnomaly(22.0, 22.0));
    }

    @Test
    void shouldNotDetectAverageAnomalyWhenAverageIsLowerThanM() {
        assertFalse(AnomalyChecker.isAverageAnomaly(20.0, 22.0));
    }

    @Test
    void shouldDetectDifferenceAnomalyWhenDifferenceIsGreaterThanS() {
        assertTrue(AnomalyChecker.isDifferenceAnomaly(35.0, 34.0));
    }

    @Test
    void shouldNotDetectDifferenceAnomalyWhenDifferenceEqualsS() {
        assertFalse(AnomalyChecker.isDifferenceAnomaly(34.0, 34.0));
    }

    @Test
    void shouldNotDetectDifferenceAnomalyWhenDifferenceIsLowerThanS() {
        assertFalse(AnomalyChecker.isDifferenceAnomaly(30.0, 34.0));
    }
}
