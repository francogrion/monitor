package com.controllers;

import com.handlers.MonitorHandler;
import org.eclipse.jetty.http.MimeTypes;
import spark.Request;
import spark.Response;
import spark.Route;

import static com.utils.JsonUtils.jsonToSensorData;

public class MonitorController {

    private static MonitorHandler monitorHandler = MonitorHandler.getInstance();

    public static Route readSensorData = (Request request, Response response) -> {
        try {
            monitorHandler.read(jsonToSensorData(request.body()));
            response.body(request.body());
            response.status(200);

        } catch (Exception e) {
            response.body("{\"status\": \"" + e.getMessage() + "\"}");
            response.status(500);
        }
        response.type(MimeTypes.Type.APPLICATION_JSON.asString());
        return response.body();
    };

    private MonitorController() {
    }
}
