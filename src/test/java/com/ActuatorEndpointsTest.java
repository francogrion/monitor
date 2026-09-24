package com;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Real server on random ports: with a separate management port, actuator lives in its own web server
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "monitor.scheduling.enabled=false",
        "management.server.port=0"
})
@ActiveProfiles("test")
class ActuatorEndpointsTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void shouldServeActuatorOnADifferentPortThanTheApi() {
        assertTrue(managementPort > 0 && managementPort != apiPort);
    }

    @Test
    void shouldReportUpIncludingDatabaseWithoutDetails() throws Exception {
        HttpResponse<String> response = getManagement("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        assertTrue(response.body().contains("\"db\":{\"status\":\"UP\"}"), response.body());
        assertFalse(response.body().contains("\"details\""), response.body());
    }

    @Test
    void shouldExposeLivenessProbeIndependentOfDatabase() throws Exception {
        HttpResponse<String> response = getManagement("/actuator/health/liveness");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        assertFalse(response.body().contains("\"db\""), response.body());
    }

    @Test
    void shouldExposeReadinessProbeIncludingDatabase() throws Exception {
        HttpResponse<String> response = getManagement("/actuator/health/readiness");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"db\":{\"status\":\"UP\"}"), response.body());
    }

    @Test
    void shouldExposeBusinessMetricsInPrometheusFormat() throws Exception {
        HttpResponse<String> response = getManagement("/actuator/prometheus");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("monitor_readings_received_total"));
        assertTrue(response.body().contains("monitor_anomalies_total{type=\"average\"}"));
        assertTrue(response.body().contains("monitor_aggregation_batch_size_count"));
    }

    @Test
    void shouldNotExposeOtherActuatorEndpoints() throws Exception {
        assertEquals(404, getManagement("/actuator/env").statusCode());
        assertEquals(404, getManagement("/actuator/beans").statusCode());
        assertEquals(404, getManagement("/actuator/configprops").statusCode());
        assertEquals(404, getManagement("/actuator/metrics").statusCode());
    }

    @Test
    void shouldNotExposeActuatorOnTheApiPort() throws Exception {
        assertEquals(404, getApi("/actuator/health").statusCode());
        assertEquals(404, getApi("/actuator/prometheus").statusCode());
    }

    @Test
    void shouldStillServeTheApiOnTheApiPort() throws Exception {
        assertEquals(200, getApi("/api/v1/config").statusCode());
    }

    private HttpResponse<String> getManagement(String path) throws IOException, InterruptedException {
        return get(managementPort, path);
    }

    private HttpResponse<String> getApi(String path) throws IOException, InterruptedException {
        return get(apiPort, path);
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
