package com;

import com.domain.SensorData;
import com.repository.SensorReadingRepository;
import com.service.MonitorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "monitor.scheduling.enabled=false",
        "logging.structured.format.console=ecs"
})
@ActiveProfiles("test")
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private SensorReadingRepository sensorReadingRepository;

    @AfterEach
    void cleanUp() {
        sensorReadingRepository.deleteAll();
    }

    @Test
    void shouldWriteCollectedReadingAsJsonWithSensorFields(CapturedOutput output) {
        SensorData sensorData = new SensorData();
        sensorData.setSensorId("7");
        sensorData.setData(42.5);
        sensorData.setTimestamp("t-structured");

        monitorService.read(sensorData);

        String line = output.getOut().lines()
                .filter(l -> l.contains("t-structured"))
                .findFirst()
                .orElseThrow();
        assertTrue(line.startsWith("{"), line);
        assertTrue(line.contains("\"sensorId\":\"7\""), line);
        assertTrue(line.contains("\"data\":42.5"), line);
        assertTrue(line.contains("\"log\":{\"level\":\"INFO\""), line);
    }
}
