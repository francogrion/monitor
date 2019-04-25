package com.controllers;

import com.handlers.ConfigHandler;
import org.eclipse.jetty.http.MimeTypes;
import spark.Request;
import spark.Response;
import spark.Route;

import static com.utils.JsonUtils.dataToJson;

public class ConfigController {

    private static ConfigHandler configHandler = ConfigHandler.getInstance();

    public static Route getConstantM = (Request request, Response response) -> {
        try {
            response.body(dataToJson(configHandler.getM()));
            response.status(200);
        } catch (Exception e) {
            response.body("{\"status\": \"" + e.getMessage() + "\"}");
            response.status(500);
        }
        response.type(MimeTypes.Type.APPLICATION_JSON.asString());
        return response.body();
    };

    public static Route setConstantM = (Request request, Response response) -> {
        try {
            configHandler.setM(request.params("m"));
            response.body("{\"newValueForConstantM\": \"" + request.params("m") + "\"}");
            response.status(200);

        } catch (Exception e) {
            response.body("{\"status\": \"" + e.getMessage() + "\"}");
            response.status(500);
        }
        response.type(MimeTypes.Type.APPLICATION_JSON.asString());
        return response.body();
    };

    public static Route getConstantS = (Request request, Response response) -> {
        try {
            response.body(dataToJson(configHandler.getS()));
            response.status(200);

        } catch (Exception e) {
            response.body("{\"status\": \"" + e.getMessage() + "\"}");
            response.status(500);
        }
        response.type(MimeTypes.Type.APPLICATION_JSON.asString());
        return response.body();
    };

    public static Route setConstantS = (Request request, Response response) -> {
        try {
            configHandler.setS(request.params("s"));
            response.body("{\"newValueForConstantS\": \"" + request.params("s") + "\"}");
            response.status(200);

        } catch (Exception e) {
            response.body("{\"status\": \"" + e.getMessage() + "\"}");
            response.status(500);
        }
        response.type(MimeTypes.Type.APPLICATION_JSON.asString());
        return response.body();
    };

    private ConfigController() {
    }
}
