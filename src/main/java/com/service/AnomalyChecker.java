package com.service;

class AnomalyChecker {

    private AnomalyChecker() {
    }

    static boolean isAverageAnomaly(double average, double m) {
        return average > m;
    }

    static boolean isDifferenceAnomaly(double difference, double s) {
        return difference > s;
    }
}
