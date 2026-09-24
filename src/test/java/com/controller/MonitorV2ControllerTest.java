package com.controller;

import com.domain.SensorData;
import com.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// v2 only differs from v1 in the timestamp: an ISO-8601 date-time with offset, normalized to UTC
@WebMvcTest(MonitorV2Controller.class)
class MonitorV2ControllerTest {

    private static final String URL = "/api/v2/monitor/data";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorService monitorService;

    @Test
    void shouldAcceptAUtcTimestampWith202AndEchoTheReading() throws Exception {
        postReading("\"2026-09-24T08:12:49.515Z\"")
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.sensorId").value("sensor-2"))
                .andExpect(jsonPath("$.data").value(33.54))
                .andExpect(jsonPath("$.timestamp").value("2026-09-24T08:12:49.515Z"));

        SensorData stored = storedReading();
        assertEquals("sensor-2", stored.getSensorId());
        assertEquals(33.54, stored.getData());
        assertEquals("2026-09-24T08:12:49.515Z", stored.getTimestamp());
    }

    @Test
    void shouldNormalizeAnyOffsetToUtc() throws Exception {
        postReading("\"2026-09-24T08:12:49.515+02:00\"")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.timestamp").value("2026-09-24T06:12:49.515Z"));

        assertEquals("2026-09-24T06:12:49.515Z", storedReading().getTimestamp());
    }

    @Test
    void shouldKeepWholeSecondTimestampsWithoutFraction() throws Exception {
        postReading("\"2026-09-24T08:12:49-03:00\"")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.timestamp").value("2026-09-24T11:12:49Z"));
    }

    // Without an offset the instant is ambiguous: that's what v2 fixes
    @Test
    void shouldRejectATimestampWithoutOffsetNamingTheField() throws Exception {
        assertRejectedTimestamp("\"2026-09-24T08:12:49.515\"");
    }

    @Test
    void shouldRejectATimestampThatIsNotADate() throws Exception {
        assertRejectedTimestamp("\"yesterday\"");
    }

    @Test
    void shouldRejectAnImpossibleDate() throws Exception {
        assertRejectedTimestamp("\"2026-02-30T08:12:49Z\"");
    }

    @Test
    void shouldRejectANumericTimestamp() throws Exception {
        assertRejectedTimestamp("1727165569");
    }

    @Test
    void shouldRejectAMissingTimestamp() throws Exception {
        mockMvc.perform(post(URL).contentType(APPLICATION_JSON).content("{\"sensorId\":\"sensor-2\",\"data\":33.54}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'timestamp')]").exists());

        verify(monitorService, never()).read(any());
    }

    @Test
    void shouldKeepTheSensorIdRulesOfV1() throws Exception {
        mockMvc.perform(post(URL).contentType(APPLICATION_JSON)
                        .content("{\"sensorId\":\"1\\nFAKE LOG LINE\",\"data\":1.5,\"timestamp\":\"2026-09-24T08:12:49Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'sensorId')]").exists());

        verify(monitorService, never()).read(any());
    }

    private void assertRejectedTimestamp(String timestampJson) throws Exception {
        postReading(timestampJson)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("timestamp"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("ISO-8601")));

        verify(monitorService, never()).read(any());
    }

    private ResultActions postReading(String timestampJson) throws Exception {
        return mockMvc.perform(post(URL).contentType(APPLICATION_JSON)
                .content("{\"sensorId\":\"sensor-2\",\"data\":33.54,\"timestamp\":" + timestampJson + "}"));
    }

    private SensorData storedReading() {
        ArgumentCaptor<SensorData> captor = ArgumentCaptor.forClass(SensorData.class);
        verify(monitorService).read(captor.capture());
        return captor.getValue();
    }
}
