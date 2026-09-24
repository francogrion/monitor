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

// One OpenAPI document per API version, generated from the code; docs/openapi-<version>.yaml are the committed,
// reviewable copies. Adding a version must not change the documents of the previous ones
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "monitor.scheduling.enabled=false",
        "management.server.port=0"
})
@ActiveProfiles("test")
class OpenApiSpecTest {

    // mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true rewrites docs/openapi-<version>.yaml from the code
    private static final List<String> VERSIONS = List.of("v1", "v2");

    private static final String PROBLEM_JSON = "application/problem+json";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int apiPort;

    @Test
    void shouldServeAnOpenApi31DocumentPerVersion() throws Exception {
        for (String version : VERSIONS) {
            JsonNode spec = spec(version);

            assertTrue(spec.path("openapi").asString().startsWith("3.1"), spec.path("openapi").asString());
            assertEquals(version, spec.path("info").path("version").asString());
            assertFalse(spec.path("info").path("title").asString().isBlank());
        }
    }

    @Test
    void shouldDocumentExactlyTheEndpointsOfEachVersion() throws Exception {
        for (String version : VERSIONS) {
            JsonNode paths = spec(version).path("paths");
            String base = "/api/" + version;

            assertEquals(Set.of(base + "/monitor/data", base + "/config"), Set.copyOf(paths.propertyNames()), version);
            assertEquals(Set.of("post"), Set.copyOf(paths.path(base + "/monitor/data").propertyNames()));
            assertEquals(Set.of("get", "patch"), Set.copyOf(paths.path(base + "/config").propertyNames()));
        }
    }

    // Only the per-version documents exist: an ungrouped one would mix versions and lack the error responses
    @Test
    void shouldNotServeAnUngroupedDocument() throws Exception {
        assertEquals(404, get("/v3/api-docs").statusCode());
        assertEquals(404, get("/v3/api-docs.yaml").statusCode());
    }

    @Test
    void shouldDocumentTheV2TimestampAsADateTime() throws Exception {
        JsonNode spec = spec("v2");
        JsonNode post = spec.path("paths").path("/api/v2/monitor/data").path("post");

        assertEquals(Set.of("202", "400", "415", "500", "503"), responseCodes(post));
        JsonNode reading = schema(spec, post.path("requestBody").path("content").path("application/json").path("schema"));
        assertEquals(List.of("data", "sensorId", "timestamp"), sorted(reading.path("required")));
        assertEquals("string", reading.path("properties").path("timestamp").path("type").asString());
        assertEquals("date-time", reading.path("properties").path("timestamp").path("format").asString());
        assertEquals("[A-Za-z0-9._-]{1,64}", reading.path("properties").path("sensorId").path("pattern").asString());
    }

    @Test
    void shouldDocumentSensorReadingWithItsValidationRules() throws Exception {
        JsonNode spec = spec("v1");
        JsonNode post = spec.path("paths").path("/api/v1/monitor/data").path("post");

        assertEquals(Set.of("202", "400", "415", "500", "503"), responseCodes(post));
        JsonNode reading = schema(spec, post.path("requestBody").path("content").path("application/json").path("schema"));
        assertEquals(List.of("data", "sensorId", "timestamp"), sorted(reading.path("required")));
        assertEquals("[A-Za-z0-9._-]{1,64}", reading.path("properties").path("sensorId").path("pattern").asString());
        assertEquals("[0-9A-Za-z:.+-]{1,64}", reading.path("properties").path("timestamp").path("pattern").asString());
    }

    @Test
    void shouldDocumentConfigReadAndPartialUpdate() throws Exception {
        JsonNode spec = spec("v1");
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
        for (String version : VERSIONS) {
            JsonNode spec = spec(version);
            JsonNode post = spec.path("paths").path("/api/" + version + "/monitor/data").path("post");

            for (String code : List.of("400", "415", "500", "503")) {
                JsonNode content = post.path("responses").path(code).path("content");
                assertEquals(Set.of(PROBLEM_JSON), Set.copyOf(content.propertyNames()), version + " response " + code);
            }
            JsonNode validationProblem = schema(spec, post.path("responses").path("400").path("content").path(PROBLEM_JSON).path("schema"));
            assertTrue(validationProblem.toString().contains("\"errors\""), validationProblem.toString());
            assertTrue(post.path("responses").path("503").path("headers").has("Retry-After"));
        }
    }

    @Test
    void shouldMatchTheCommittedSpecs() throws Exception {
        for (String version : VERSIONS) {
            Path committed = Path.of("docs/openapi-" + version + ".yaml");
            HttpResponse<String> response = get("/v3/api-docs.yaml/" + version);
            assertEquals(200, response.statusCode(), response.body());
            if (Boolean.getBoolean("openapi.update")) {
                Files.writeString(committed, response.body());
            }

            assertTrue(Files.exists(committed), committed + " is missing; generate it with -Dopenapi.update=true");
            assertEquals(Files.readString(committed), response.body(),
                    committed + " is out of date; regenerate it with mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true");
        }
    }

    private JsonNode spec(String version) throws IOException, InterruptedException {
        HttpResponse<String> response = get("/v3/api-docs/" + version);
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
