package com.service;

import com.domain.ConfigEntity;
import com.repository.ConfigRepository;
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

    @Mock
    private ConfigRepository configRepository;

    private ConfigService configService;

    private ConfigService newConfigService() {
        return new ConfigService(configRepository);
    }

    @Test
    void shouldDefaultMToZeroWhenNoConfigIsPersistedYet() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());
        configService = newConfigService();

        assertEquals(0.0, configService.getM());
    }

    @Test
    void shouldDefaultSToZeroWhenNoConfigIsPersistedYet() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());
        configService = newConfigService();

        assertEquals(0.0, configService.getS());
    }

    @Test
    void shouldReturnPersistedM() {
        ConfigEntity config = new ConfigEntity();
        config.setId(1L);
        config.setM(22.0);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config));
        configService = newConfigService();

        assertEquals(22.0, configService.getM());
    }

    @Test
    void shouldCreateAndPersistConfigWhenSettingMForTheFirstTime() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());
        configService = newConfigService();

        configService.setM("25");

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(25.0, captor.getValue().getM());
    }

    @Test
    void shouldUpdateExistingConfigWhenSettingS() {
        ConfigEntity existing = new ConfigEntity();
        existing.setId(1L);
        existing.setM(25.0);
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));
        configService = newConfigService();

        configService.setS("34");

        ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
        verify(configRepository).save(captor.capture());
        assertEquals(25.0, captor.getValue().getM());
        assertEquals(34.0, captor.getValue().getS());
    }

    @Test
    void shouldThrowAndNeverPersistWhenSettingMWithInvalidNumber() {
        configService = newConfigService();

        assertThrows(NumberFormatException.class, () -> configService.setM("not-a-number"));

        verify(configRepository, never()).save(any());
    }

    @Test
    void shouldThrowAndNeverPersistWhenSettingSWithInvalidNumber() {
        configService = newConfigService();

        assertThrows(NumberFormatException.class, () -> configService.setS("not-a-number"));

        verify(configRepository, never()).save(any());
    }
}
