package com;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Actuator (probes, prometheus) must not share the public API port unless explicitly configured to
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "monitor.scheduling.enabled=false")
@ActiveProfiles("test")
class ManagementPortDefaultTest {

    @Value("${server.port:}")
    private String apiPort;

    @Value("${management.server.port:}")
    private String managementPort;

    @Test
    void shouldServeActuatorOnPort9090AndTheApiOn8080ByDefault() {
        assertEquals("8080", apiPort);
        assertEquals("9090", managementPort);
    }
}
