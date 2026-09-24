package com.controller;

import com.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigService configService;

    @Test
    void shouldGetConstantM() throws Exception {
        when(configService.getM()).thenReturn(22.0);

        mockMvc.perform(get("/config/m"))
                .andExpect(status().isOk())
                .andExpect(content().string("22.0"));
    }

    @Test
    void shouldSetConstantM() throws Exception {
        mockMvc.perform(post("/config/m/22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newValueForConstantM").value("22"));

        verify(configService).setM("22");
    }

    @Test
    void shouldReturn500WhenSettingMWithInvalidValue() throws Exception {
        org.mockito.Mockito.doThrow(new NumberFormatException("For input string: \"abc\""))
                .when(configService).setM("abc");

        mockMvc.perform(post("/config/m/abc"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("For input string: \"abc\""));
    }

    @Test
    void shouldGetConstantS() throws Exception {
        when(configService.getS()).thenReturn(34.0);

        mockMvc.perform(get("/config/s"))
                .andExpect(status().isOk())
                .andExpect(content().string("34.0"));
    }

    @Test
    void shouldSetConstantS() throws Exception {
        mockMvc.perform(post("/config/s/34"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newValueForConstantS").value("34"));

        verify(configService).setS("34");
    }
}
