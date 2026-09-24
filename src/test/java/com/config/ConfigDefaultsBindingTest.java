package com.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigDefaultsBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void shouldBindDefaultsFromProperties() {
        contextRunner.withPropertyValues("monitor.defaults.m=22.5", "monitor.defaults.s=10")
                .run(context -> {
                    ConfigDefaults defaults = context.getBean(ConfigDefaults.class);
                    assertEquals(22.5, defaults.m());
                    assertEquals(10.0, defaults.s());
                });
    }

    @Test
    void shouldDefaultToZeroWhenPropertiesAreAbsent() {
        contextRunner.run(context -> {
            ConfigDefaults defaults = context.getBean(ConfigDefaults.class);
            assertEquals(0.0, defaults.m());
            assertEquals(0.0, defaults.s());
        });
    }

    @EnableConfigurationProperties(ConfigDefaults.class)
    static class Config {
    }
}
