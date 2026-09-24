package com.service;

import com.config.ConfigDefaults;
import com.domain.ConfigEntity;
import com.domain.MonitorConfig;
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

    public MonitorConfig getConfig() {
        ConfigEntity config = findConfig();
        return new MonitorConfig(config.getM(), config.getS());
    }

    public double getM() {
        return findConfig().getM();
    }

    public double getS() {
        return findConfig().getS();
    }

    // null leaves that value unchanged
    @Transactional
    public MonitorConfig update(Double m, Double s) {
        ConfigEntity config = findConfig();
        if (m != null) {
            config.setM(m);
        }
        if (s != null) {
            config.setS(s);
        }
        configRepository.save(config);
        return new MonitorConfig(config.getM(), config.getS());
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
