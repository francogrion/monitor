package com.repository;

import com.domain.ConfigEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ConfigRepositoryTest {

    @Autowired
    private ConfigRepository configRepository;

    @Test
    void shouldReturnEmptyWhenConfigDoesNotExistYet() {
        Optional<ConfigEntity> config = configRepository.findById(1L);

        assertTrue(config.isEmpty());
    }

    @Test
    void shouldPersistAndReloadConfig() {
        ConfigEntity config = new ConfigEntity();
        config.setId(1L);
        config.setM(25.0);
        config.setS(34.0);

        configRepository.saveAndFlush(config);

        ConfigEntity reloaded = configRepository.findById(1L).orElseThrow();
        assertEquals(25.0, reloaded.getM());
        assertEquals(34.0, reloaded.getS());
    }

    @Test
    void shouldUpdateExistingConfig() {
        ConfigEntity config = new ConfigEntity();
        config.setId(1L);
        config.setM(25.0);
        config.setS(34.0);
        configRepository.saveAndFlush(config);

        config.setM(50.0);
        configRepository.saveAndFlush(config);

        ConfigEntity reloaded = configRepository.findById(1L).orElseThrow();
        assertEquals(50.0, reloaded.getM());
        assertEquals(34.0, reloaded.getS());
    }
}
