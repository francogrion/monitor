package com;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The OpenAPI document is generated from the code; docs/openapi.yaml is the committed, reviewable copy
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "monitor.scheduling.enabled=false",
        "management.server.port=0"
})
@ActiveProfiles("test")
class OpenApiSpecTest {

    // mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true rewrites docs/openapi.yaml from the code
    static final Path COMMITTED_SPEC = Path.of("docs/openapi.yaml");

    private static final String PROBLEM_JSON = "application/problem+json";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int apiPort;

    @Test
    void shouldServeAnOpenApi31DocumentForV1() throws Exception {
        JsonNode spec = spec();

        assertTrue(spec.path("openapi").asString().startsWith("3.1"), spec.path("openapi").asString());
        assertEquals("v1", spec.path("info").path("version").asString());
        assertFalse(spec.path("info").path("title").asString().isBlank());
    }

    @Test
    void shouldDocumentExactlyTheV1Endpoints() throws Exception {
        JsonNode paths = spec().path("paths");

        assertEquals(Set.of("/api/v1/monitor/data", "/api/v1/config"), Set.copyOf(paths.propertyNames()));
        assertEquals(Set.of("post"), Set.copyOf(paths.path("/api/v1/monitor/data").propertyNames()));
        assertEquals(Set.of("get", "patch"), Set.copyOf(paths.path("/api/v1/config").propertyNames()));
    }

    @Test
    void shouldDocumentSensorReadingWithItsValidationRules() throws Exception {
        JsonNode spec = spec();
        JsonNode post = spec.path("paths").path("/api/v1/monitor/data").path("post");

        assertEquals(Set.of("202", "400", "415", "500", "503"), responseCodes(post));
        JsonNode reading = schema(spec, post.path("requestBody").path("content").path("application/json").path("schema"));
        assertEquals(List.of("data", "sensorId", "timestamp"), sorted(reading.path("required")));
        assertEquals("[A-Za-z0-9._-]{1,64}", reading.path("properties").path("sensorId").path("pattern").asString());
        assertEquals("[0-9A-Za-z:.+-]{1,64}", reading.path("properties").path("timestamp").path("pattern").asString());
    }

    @Test
    void shouldDocumentConfigReadAndPartialUpdate() throws Exception {
        JsonNode spec = spec();
        JsonNode get = spec.path("paths").path("/api/v1/config").path("get");
        JsonNode patch = spec.path("paths").path("/api/v1/config").path("patch");

        assertEquals(Set.of("200", "500", "503"), responseCodes(get));
        assertEquals(Set.of("200", "400", "415", "500", "503"), responseCodes(patch));
        JsonNode update = schema(spec, patch.path("requestBody").path("content").path("application/json").path("schema"));
        assertEquals(Set.of("m", "s"), Set.copyOf(update.path("properties").propertyNames()));
        assertEquals(0, update.path("properties").path("s").path("minimum").asInt(-1));
        assertEquals(1, update.path("minProperties").asInt());
        JsonNode config = schema(spec, get.path("responses").path("200").path("content").path("application/json").path("schema"));
        assertEquals(List.of("m", "s"), sorted(config.path("required")));
    }

    @Test
    void shouldDocumentErrorsAsProblemDetails() throws Exception {
        JsonNode spec = spec();
        JsonNode post = spec.path("paths").path("/api/v1/monitor/data").path("post");

        for (String code : List.of("400", "415", "500", "503")) {
            JsonNode content = post.path("responses").path(code).path("content");
            assertEquals(Set.of(PROBLEM_JSON), Set.copyOf(content.propertyNames()), "response " + code);
        }
        JsonNode validationProblem = schema(spec, post.path("responses").path("400").path("content").path(PROBLEM_JSON).path("schema"));
        assertTrue(validationProblem.toString().contains("\"errors\""), validationProblem.toString());
        assertTrue(post.path("responses").path("503").path("headers").has("Retry-After"));
    }

    @Test
    void shouldMatchTheCommittedSpec() throws Exception {
        String generated = get("/v3/api-docs.yaml").body();
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(COMMITTED_SPEC, generated);
        }

        assertTrue(Files.exists(COMMITTED_SPEC), COMMITTED_SPEC + " is missing; generate it with -Dopenapi.update=true");
        assertEquals(Files.readString(COMMITTED_SPEC), generated,
                COMMITTED_SPEC + " is out of date; regenerate it with mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true");
    }

    private JsonNode spec() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/v3/api-docs");
        assertEquals(200, response.statusCode(), response.body());
        return JsonMapper.builder().build().readTree(response.body());
    }

    // Follows a local $ref ("#/components/schemas/X") if there is one
    private static JsonNode schema(JsonNode spec, JsonNode schemaOrRef) {
        String ref = schemaOrRef.path("$ref").asString("");
        if (ref.isEmpty()) {
            return schemaOrRef;
        }
        return spec.path("components").path("schemas").path(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static Set<String> responseCodes(JsonNode operation) {
        return Set.copyOf(operation.path("responses").propertyNames());
    }

    private static List<String> sorted(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).sorted().collect(Collectors.toList());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + apiPort + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
