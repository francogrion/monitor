package com.controller;

import com.domain.MonitorConfig;
import com.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    private static final String URL = "/api/v1/config";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigService configService;

    @Test
    void shouldReturnBothConstants() throws Exception {
        when(configService.getConfig()).thenReturn(new MonitorConfig(22.0, 34.0));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.m").value(22.0))
                .andExpect(jsonPath("$.s").value(34.0));
    }

    @Test
    void shouldUpdateOnlyMAndReturnTheResultingConfig() throws Exception {
        when(configService.update(25.0, null)).thenReturn(new MonitorConfig(25.0, 34.0));

        patchJson("{\"m\":25}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.m").value(25.0))
                .andExpect(jsonPath("$.s").value(34.0));

        verify(configService).update(25.0, null);
    }

    // v2 exposes the same resource, so a client can move its whole base path to /api/v2
    @Test
    void shouldServeTheSameResourceUnderV2() throws Exception {
        when(configService.getConfig()).thenReturn(new MonitorConfig(22.0, 34.0));
        when(configService.update(null, 40.0)).thenReturn(new MonitorConfig(22.0, 40.0));

        mockMvc.perform(get("/api/v2/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.m").value(22.0))
                .andExpect(jsonPath("$.s").value(34.0));
        mockMvc.perform(patch("/api/v2/config").contentType(APPLICATION_JSON).content("{\"s\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value(40.0));
        mockMvc.perform(patch("/api/v2/config").contentType(APPLICATION_JSON).content("{\"s\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 's')]").exists());

        verify(configService).update(null, 40.0);
    }

    @Test
    void shouldUpdateBothConstantsInOneCall() throws Exception {
        when(configService.update(25.0, 40.0)).thenReturn(new MonitorConfig(25.0, 40.0));

        patchJson("{\"m\":25,\"s\":40}").andExpect(status().isOk());

        verify(configService).update(25.0, 40.0);
    }

    @Test
    void shouldRejectNegativeS() throws Exception {
        patchJson("{\"s\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field == 's')]").exists());

        verify(configService, never()).update(any(), any());
    }

    @Test
    void shouldRejectEmptyUpdate() throws Exception {
        patchJson("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("at least one of m or s must be provided"));

        verify(configService, never()).update(any(), any());
    }

    @Test
    void shouldRejectNonNumericValue() throws Exception {
        patchJson("{\"m\":\"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));

        verify(configService, never()).update(any(), any());
    }

    @Test
    void shouldNoLongerServeUnversionedPaths() throws Exception {
        mockMvc.perform(get("/config/m")).andExpect(status().isNotFound());
        mockMvc.perform(post("/config/m/25")).andExpect(status().isNotFound());
    }

    private ResultActions patchJson(String json) throws Exception {
        return mockMvc.perform(patch(URL).contentType(APPLICATION_JSON).content(json));
    }
}
