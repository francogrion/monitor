package com.service;

import com.config.ConfigDefaults;
import com.domain.MonitorConfig;
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
import static org.mockito.Mockito.times;
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
    void shouldReturnConfiguredDefaultsAsConfigWhenNothingIsPersisted() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        assertEquals(new MonitorConfig(DEFAULT_M, DEFAULT_S), configService.getConfig());
    }

    @Test
    void shouldKeepDefaultSWhenUpdatingOnlyMForTheFirstTime() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        MonitorConfig result = configService.update(25.0, null);

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(25.0, captor.getValue().getM());
        assertEquals(DEFAULT_S, captor.getValue().getS());
        assertEquals(new MonitorConfig(25.0, DEFAULT_S), result);
    }

    @Test
    void shouldKeepDefaultMWhenUpdatingOnlySForTheFirstTime() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());

        MonitorConfig result = configService.update(null, 40.0);

        assertEquals(new MonitorConfig(DEFAULT_M, 40.0), result);
    }

    @Test
    void shouldUpdateBothValuesOfExistingConfigWithASingleSave() {
        ConfigEntity existing = new ConfigEntity();
        existing.setId(1L);
        existing.setM(25.0);
        existing.setS(10.0);
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));

        MonitorConfig result = configService.update(30.0, 34.0);

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository, times(1)).save(captor.capture());
        assertEquals(30.0, captor.getValue().getM());
        assertEquals(34.0, captor.getValue().getS());
        assertEquals(new MonitorConfig(30.0, 34.0), result);
    }
}
