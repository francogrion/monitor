package com.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigServiceTest {

    private final ConfigService configService = new ConfigService();

    @Test
    void shouldDefaultMToZero() {
        assertEquals(0.0, configService.getM());
    }

    @Test
    void shouldDefaultSToZero() {
        assertEquals(0.0, configService.getS());
    }

    @Test
    void shouldSetAndGetM() {
        configService.setM("25");

        assertEquals(25.0, configService.getM());
    }

    @Test
    void shouldSetAndGetS() {
        configService.setS("34.5");

        assertEquals(34.5, configService.getS());
    }

    @Test
    void shouldThrowWhenSettingMWithInvalidNumber() {
        assertThrows(NumberFormatException.class, () -> configService.setM("not-a-number"));
    }

    @Test
    void shouldThrowWhenSettingSWithInvalidNumber() {
        assertThrows(NumberFormatException.class, () -> configService.setS("not-a-number"));
    }
}
