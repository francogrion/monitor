package com.service;

import com.config.ConfigDefaults;
import com.domain.ConfigEntity;
import com.repository.ConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    private static final double DEFAULT_M = 22.0;
    private static final double DEFAULT_S = 34.0;

    @Mock
    private ConfigRepository configRepository;

    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = new ConfigService(configRepository, new ConfigDefaults(DEFAULT_M, DEFAULT_S));
    }

    @Test
    void shouldReturnConfiguredDefaultMWhenNoConfigIsPersistedYet() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        assertEquals(DEFAULT_M, configService.getM());
    }

    @Test
    void shouldReturnConfiguredDefaultSWhenNoConfigIsPersistedYet() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        assertEquals(DEFAULT_S, configService.getS());
    }

    @Test
    void shouldPreferPersistedValuesOverConfiguredDefaults() {
        ConfigEntity config = new ConfigEntity();
        config.setId(1L);
        config.setM(50.0);
        config.setS(60.0);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));

        assertEquals(50.0, configService.getM());
        assertEquals(60.0, configService.getS());
    }

    @Test
    void shouldKeepDefaultSWhenSettingMForTheFirstTime() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        configService.setM("25");

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(25.0, captor.getValue().getM());
        assertEquals(DEFAULT_S, captor.getValue().getS());
    }

    @Test
    void shouldKeepDefaultMWhenSettingSForTheFirstTime() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        configService.setS("40");

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository).save(captor.capture());
        assertEquals(DEFAULT_M, captor.getValue().getM());
        assertEquals(40.0, captor.getValue().getS());
    }

    @Test
    void shouldUpdateExistingConfigWhenSettingS() {
        ConfigEntity existing = new ConfigEntity();
        existing.setId(1L);
        existing.setM(25.0);
        existing.setS(10.0);
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));

        configService.setS("34");

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository).save(captor.capture());
        assertEquals(25.0, captor.getValue().getM());
        assertEquals(34.0, captor.getValue().getS());
    }

    @Test
    void shouldThrowAndNeverPersistWhenSettingMWithInvalidNumber() {
        assertThrows(NumberFormatException.class, () -> configService.setM("not-a-number"));

        verify(configRepository, never()).save(any());
    }

    @Test
    void shouldThrowAndNeverPersistWhenSettingSWithInvalidNumber() {
        assertThrows(NumberFormatException.class, () -> configService.setS("not-a-number"));

        verify(configRepository, never()).save(any());
    }
}
