package com.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

// Error responses come from ApiExceptionHandler, which springdoc can't see per operation, so they are added here
@Configuration
public class OpenApiConfig {

    static final String PROBLEM = "Problem";
    static final String VALIDATION_PROBLEM = "ValidationProblem";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI monitorOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Monitor API")
                        .version("v1")
                        .description("Receives sensor readings and manages the constants M and S used to detect anomalies. "
                                + "Readings are aggregated every 30 seconds; anomalies are reported in the service logs and metrics."))
                // Fixed, so the committed docs/openapi.yaml doesn't depend on the host that generated it
                .servers(List.of(new Server().url("http://localhost:8080").description("Local (docker compose)")));
    }

    // Runs after springdoc has built the schemas from the code; schemas declared on the OpenAPI bean would be replaced
    @Bean
    OpenApiCustomizer problemResponsesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents()
                    .addSchemas(PROBLEM, problemSchema())
                    .addSchemas(VALIDATION_PROBLEM, validationProblemSchema());
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(this::addProblemResponses));
        };
    }

    private void addProblemResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (operation.getRequestBody() != null) {
            responses.addApiResponse("400", problem("Invalid body: missing or invalid fields (listed in `errors`), malformed JSON or wrong types", VALIDATION_PROBLEM));
            responses.addApiResponse("415", problem("The body is not `application/json`", PROBLEM));
        }
        responses.addApiResponse("500", problem("Unexpected error; details are only logged, never returned", PROBLEM));
        responses.addApiResponse("503", problem("The database is unavailable; retry after `Retry-After` seconds", PROBLEM)
                .headers(Map.of("Retry-After", new Header()
                        .description("Seconds to wait before retrying")
                        .schema(new IntegerSchema()))));
    }

    private static ApiResponse problem(String description, String schemaName) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + schemaName))));
    }

    // RFC 9457 Problem Details, as Spring's ProblemDetail serializes it
    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 Problem Details")
                .addProperty("type", new StringSchema().format("uri").description("Problem type; `about:blank` when it is just the HTTP status"))
                .addProperty("title", new StringSchema().description("Short summary of the HTTP status"))
                .addProperty("status", new IntegerSchema().description("HTTP status code"))
                .addProperty("detail", new StringSchema().description("Explanation of this occurrence"))
                .addProperty("instance", new StringSchema().format("uri-reference").description("Path of the request"));
    }

    private static Schema<?> validationProblemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().description("Name of the invalid field"))
                .addProperty("message", new StringSchema().description("Why it is invalid"))
                .required(List.of("field", "message"));
        return new Schema<>()
                .description("Problem Details plus the invalid fields, when the body failed validation")
                .allOf(List.of(
                        new Schema<>().$ref("#/components/schemas/" + PROBLEM),
                        new ObjectSchema().addProperty("errors", new ArraySchema().items(fieldError))));
    }
}
