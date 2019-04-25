package com.main;

import com.controllers.ConfigController;
import com.controllers.MonitorController;
import com.handlers.MonitorHandler;

import java.util.Timer;

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;

public class RestServer {

    private static MonitorHandler monitorHandler = MonitorHandler.getInstance();

    public static void main(String[] args) {

        port(8080);

        // Set up routes
        get("/config/m", ConfigController.getConstantM);
        post("/config/m/:m", ConfigController.setConstantM);
        get("/config/s", ConfigController.getConstantS);
        post("/config/s/:s", ConfigController.setConstantS);
        post("/monitor/data", MonitorController.readSensorData);
        get("*", (request, response) -> "Service Not Found");

        Timer time = new Timer(); // Instantiate Timer Object
        time.schedule(monitorHandler, 0, 30000); // Create repetitively task for every 30 secs
    }
}