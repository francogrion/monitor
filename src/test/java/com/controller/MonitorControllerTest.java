package com.controller;

import com.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitorController.class)
class MonitorControllerTest {

    private static final String SENSOR_DATA_JSON =
            "{\"sensorId\":\"2\",\"data\":33.54,\"timestamp\":\"20192304123322\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorService monitorService;

    @Test
    void shouldReturn200AndEchoBodyWhenDataIsValid() throws Exception {
        mockMvc.perform(post("/monitor/data")
                        .contentType(APPLICATION_JSON)
                        .content(SENSOR_DATA_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.sensorId").value("2"))
                .andExpect(jsonPath("$.data").value(33.54));

        verify(monitorService).read(any());
    }

    @Test
    void shouldReturn500WhenServiceRejectsData() throws Exception {
        doThrow(new IllegalArgumentException("SensorData failed!")).when(monitorService).read(any());

        mockMvc.perform(post("/monitor/data")
                        .contentType(APPLICATION_JSON)
                        .content(SENSOR_DATA_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("SensorData failed!"));
    }
}
