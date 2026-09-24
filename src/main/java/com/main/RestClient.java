package com.main;

import com.domain.SensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import static com.utils.JsonUtils.dataToJson;

//Monolithic Client to test the server
public class RestClient {

    private static final Logger log = LoggerFactory.getLogger(RestClient.class);
    static final String BASE_URL = System.getenv().getOrDefault("MONITOR_BASE_URL", "http://localhost:8080");

    public static void main(String[] args) {

        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            //config constants
            sendConfig(httpClient, "/config/m/25");
            sendConfig(httpClient, "/config/s/34");
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        //Creating 4 threads to simulate 4 sensors
        for (int i = 0; i < 4; i++) {
            ClientThread sensor = new ClientThread(String.valueOf(i + 1));
            sensor.start();
            log.info("{} started", sensor.getName());
        }
    }

    private static void sendConfig(HttpClient httpClient, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info(response.body());
    }
}

class ClientThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ClientThread.class);
    private static final String MONITOR_DATA_URL = RestClient.BASE_URL + "/monitor/data";

    private final SensorData sensorData;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    ClientThread(String sensorId) {
        super("sensor" + sensorId);
        sensorData = new SensorData();
        sensorData.setSensorId(sensorId);
    }

    @Override
    public void run() {
        sendRequest(sensorData);
    }

    private void sendRequest(SensorData sensorData) {
        //simulating random data sent by the sensors via http
        for (int i = 0; i < 500; i++) {
            try {
                //simulate two readings per second
                sleep(500);
                sensorData.setData(ThreadLocalRandom.current().nextDouble() * 100);
                sensorData.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

                HttpRequest request = HttpRequest.newBuilder(URI.create(MONITOR_DATA_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(dataToJson(sensorData)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info(response.body());
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }
}
