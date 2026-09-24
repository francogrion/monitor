package com.service;

import com.config.ConfigDefaults;
import com.domain.ConfigEntity;
import com.repository.ConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigService {

    private static final long CONFIG_ID = 1L;

    private final ConfigRepository configRepository;
    private final ConfigDefaults defaults;

    public ConfigService(ConfigRepository configRepository, ConfigDefaults defaults) {
        this.configRepository = configRepository;
        this.defaults = defaults;
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
            config.setM(defaults.m());
            config.setS(defaults.s());
            return config;
        });
    }
}
