package com.service;

import com.domain.ConfigEntity;
import com.repository.ConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {

    private static final long CONFIG_ID = 1L;

    private final ConfigRepository configRepository;

    public ConfigService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public double getM() {
        return findConfig().getM();
    }

    @Transactional
    public void setM(String value) {
        double parsed = Double.parseDouble(value);
        ConfigEntity config = findConfig();
        config.setM(parsed);
        configRepository.save(config);
    }

    public double getS() {
        return findConfig().getS();
    }

    @Transactional
    public void setS(String value) {
        double parsed = Double.parseDouble(value);
        ConfigEntity config = findConfig();
        config.setS(parsed);
        configRepository.save(config);
    }

    private ConfigEntity findConfig() {
        return configRepository.findById(CONFIG_ID).orElseGet(() -> {
            ConfigEntity config = new ConfigEntity();
            config.setId(CONFIG_ID);
            return config;
        });
    }
}
