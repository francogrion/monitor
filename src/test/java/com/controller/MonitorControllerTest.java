package com.controller;

import com.domain.SensorData;
import com.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitorController.class)
class MonitorControllerTest {

    private static final String URL = "/api/v1/monitor/data";
    private static final String VALID_JSON =
            "{\"sensorId\":\"sensor-2\",\"data\":33.54,\"timestamp\":\"2026-09-24T08:12:49.515\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorService monitorService;

    @Test
    void shouldAcceptValidReadingWith202AndEchoIt() throws Exception {
        postJson(VALID_JSON)
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.sensorId").value("sensor-2"))
                .andExpect(jsonPath("$.data").value(33.54))
                .andExpect(jsonPath("$.timestamp").value("2026-09-24T08:12:49.515"));

        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(monitorService).read(captor.capture());
        assertEquals("sensor-2", captor.getValue().getSensorId());
        assertEquals(33.54, captor.getValue().getData());
        assertEquals("2026-09-24T08:12:49.515", captor.getValue().getTimestamp());
    }

    @Test
    void shouldRejectMissingDataWith400NamingTheField() throws Exception {
        postJson("{\"sensorId\":\"1\",\"timestamp\":\"t\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'data')]").exists());

        verify(monitorService, never()).read(any());
    }

    @Test
    void shouldRejectMissingSensorIdWith400() throws Exception {
        postJson("{\"data\":1.5,\"timestamp\":\"t\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'sensorId')]").exists());
    }

    @Test
    void shouldRejectSensorIdWithLineBreaksToPreventLogForging() throws Exception {
        postJson("{\"sensorId\":\"1\\nFAKE LOG LINE\",\"data\":1.5,\"timestamp\":\"t\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'sensorId')]").exists());
    }

    @Test
    void shouldRejectTooLongSensorId() throws Exception {
        postJson("{\"sensorId\":\"" + "x".repeat(65) + "\",\"data\":1.5,\"timestamp\":\"t\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'sensorId')]").exists());
    }

    @Test
    void shouldRejectTimestampWithLineBreaks() throws Exception {
        postJson("{\"sensorId\":\"1\",\"data\":1.5,\"timestamp\":\"t\\r\\nFAKE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'timestamp')]").exists());
    }

    @Test
    void shouldRejectMalformedJsonWith400ProblemDetail() throws Exception {
        postJson("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldRejectNonJsonContentTypeWith415() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(VALID_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldReturn503WithRetryAfterWhenDatabaseIsUnavailable() throws Exception {
        doThrow(new CannotCreateTransactionException("Could not open JPA EntityManager"))
                .when(monitorService).read(any());

        postJson(VALID_JSON)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", not(containsString("EntityManager"))));
    }

    @Test
    void shouldReturn500WithoutLeakingInternalDetails() throws Exception {
        doThrow(new IllegalStateException("secret internal detail")).when(monitorService).read(any());

        postJson(VALID_JSON)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void shouldNoLongerServeUnversionedPath() throws Exception {
        mockMvc.perform(post("/monitor/data").contentType(APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isNotFound());
    }

    private ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content(json));
    }
}
