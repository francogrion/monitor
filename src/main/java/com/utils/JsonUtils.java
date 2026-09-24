package com.utils;

import tools.jackson.databind.ObjectMapper;

public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String dataToJson(Object data) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }
}
