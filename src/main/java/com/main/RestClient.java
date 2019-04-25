package com.main;

import com.domain.SensorData;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import static com.utils.JsonUtils.dataToJson;
import static com.utils.JsonUtils.printResponseBody;

//Monolithic Client to test the server
public class RestClient {

    private static final Logger log = LoggerFactory.getLogger(RestClient.class);

    public static void main(String[] args) {

        try {
            //config constants
            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost post;

            post = new HttpPost("http://localhost:8080/config/m/25");
            log.info(printResponseBody(httpClient.execute(post)));

            post = new HttpPost("http://localhost:8080/config/s/34");
            log.info(printResponseBody(httpClient.execute(post)));
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
}

class ClientThread extends Thread {

    private static final Logger log = LoggerFactory.getLogger(ClientThread.class);

    private SensorData sensorData;

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
        HttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost("http://localhost:8080/monitor/data");

        for (int i = 0; i < 500; i++) {
            try {
                //simulate two readings per second
                sleep(500);
                sensorData.setData(ThreadLocalRandom.current().nextDouble() * 100);
                sensorData.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

                post.setEntity(new StringEntity(dataToJson(sensorData)));
                log.info(printResponseBody(httpClient.execute(post)));
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }
}