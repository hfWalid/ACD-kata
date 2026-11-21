package fr.walid.acd.contribution.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "acd.outbox.relay.enabled=false"
})
class DocumentApiIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14-alpine")
            .withDatabaseName("acd_contribution");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper json;

    @Test
    void contributesApprovesPublishesAndArchivesWithDurableVersionedAudit() {
        var created = create("Notice technique", List.of());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var draft = created.getBody();
        String id = draft.path("id").asText();
        assertThat(created.getHeaders().getLocation().toString()).endsWith("/api/documents/" + id);
        assertThat(draft.path("status").asText()).isEqualTo("Draft");
        assertThat(draft.path("version").asLong()).isEqualTo(1);

        var emptySubmission = transition(id, "submit", 1);
        assertProblem(emptySubmission, HttpStatus.CONFLICT, "EMPTY_DOCUMENT");
        assertThat(get(id).path("version").asLong()).isEqualTo(1);

        var content = Map.of("expectedVersion", 1, "title", "Notice complétée",
                "sections", List.of(Map.of("title", "Objet", "content", "Contenu de la notice")));
        var updated = http.exchange("/api/documents/" + id + "/content", HttpMethod.PUT,
                authored(content), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().path("version").asLong()).isEqualTo(2);

        assertThat(transition(id, "submit", 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(transition(id, "submit", 2), HttpStatus.CONFLICT, "VERSION_CONFLICT");
        assertProblem(transition(id, "publish", 3), HttpStatus.CONFLICT, "INVALID_TRANSITION");
        assertThat(transition(id, "start-review", 3).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(id, "approve", 4).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(id, "publish", 5).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(id, "archive", 6).getStatusCode()).isEqualTo(HttpStatus.OK);

        var archived = get(id);
        assertThat(archived.path("status").asText()).isEqualTo("Archived");
        assertThat(archived.path("version").asLong()).isEqualTo(7);
        assertThat(archived.path("auditTrail")).hasSize(7);
        assertThat(archived.path("auditTrail").get(6).path("author").asText()).isEqualTo("walid");
        assertThat(archived.path("auditTrail").get(6).path("action").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    void aRejectedDocumentCanBeCorrectedAndResubmitted() {
        var created = create("Document à corriger", List.of(Map.of("title", "Objet", "content", "Première version")));
        String id = created.getBody().path("id").asText();
        assertThat(transition(id, "submit", 1).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(id, "start-review", 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(id, "reject", 3).getStatusCode()).isEqualTo(HttpStatus.OK);
        var corrected = http.exchange("/api/documents/" + id + "/content", HttpMethod.PUT,
                authored(Map.of("expectedVersion", 4, "title", "Document corrigé",
                        "sections", List.of(Map.of("title", "Objet", "content", "Version corrigée")))), JsonNode.class);
        assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
        var submitted = transition(id, "submit", 5);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().path("status").asText()).isEqualTo("Submitted");
        assertThat(get(id).path("auditTrail")).hasSize(6);
    }

    @Test
    void paginatesAnUnalignedWindowAndCountsTheSameFilteredDocuments() {
        String prefix = "query-" + UUID.randomUUID() + "-";
        for (String suffix : List.of("Alpha", "Beta", "Gamma", "Delta")) {
            assertThat(create(prefix + suffix, List.of()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        var response = http.getForEntity("/api/documents?startRow=1&endRow=3&sort=title:asc&q=" + prefix, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var page = response.getBody();
        assertThat(page.path("rowCount").asLong()).isEqualTo(4);
        assertThat(page.path("rows")).hasSize(2);
        assertThat(page.path("rows").get(0).path("title").asText()).isEqualTo(prefix + "Beta");
        assertThat(page.path("rows").get(1).path("title").asText()).isEqualTo(prefix + "Delta");
        var noResults = http.getForEntity("/api/documents?filter.status=Approved&q=" + prefix, JsonNode.class);
        assertThat(noResults.getBody().path("rowCount").asLong()).isZero();
        assertThat(noResults.getBody().path("rows")).isEmpty();
    }

    @Test
    void returnsProblemDetailsForInvalidAndMissingResources() {
        var invalid = http.postForEntity("/api/documents", authored(Map.of("title", "Missing template")), JsonNode.class);
        assertProblem(invalid, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        var missing = http.getForEntity("/api/documents/" + UUID.randomUUID(), JsonNode.class);
        assertProblem(missing, HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND");
        assertProblem(http.getForEntity("/api/documents?sort=unknown:asc", JsonNode.class),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem(http.postForEntity("/api/documents", authored(Map.of(
                        "templateRef", "notice-v1", "title", "Test", "sections", List.of(), "unexpected", true)), JsonNode.class),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        String id = create("Version entière", List.of()).getBody().path("id").asText();
        assertProblem(http.postForEntity("/api/documents/" + id + "/submit",
                        authored(Map.of("expectedVersion", 1.5)), JsonNode.class),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertProblem(http.postForEntity("/api/documents/" + id + "/submit",
                        authored(Map.of("expectedVersion", "1")), JsonNode.class),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void exposesAnOpenApiContractMatchingTheVersionedExport() throws Exception {
        var response = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var contract = (ObjectNode) response.getBody();
        contract.remove("servers"); // The random integration-test port is not part of the API contract.
        assertThat(contract.path("openapi").asText()).startsWith("3.0.");
        assertThat(contract.path("paths").size()).isEqualTo(10);
        assertThat(contract.at("/components/schemas/ProblemDetail").isMissingNode()).isFalse();
        assertThat(contract.at("/components/schemas/DocumentListResponse/properties/rowCount").isMissingNode()).isFalse();
        int documentedOperations = 0;
        for (var path : contract.withObject("/paths").properties()) {
            for (var operation : path.getValue().properties()) {
                assertThat(operation.getValue().path("responses").has("200")
                        || operation.getValue().path("responses").has("201"))
                        .as("%s %s declares its success response", operation.getKey().toUpperCase(Locale.ROOT), path.getKey())
                        .isTrue();
                documentedOperations++;
            }
        }
        assertThat(documentedOperations).isEqualTo(11);

        Path snapshot = Path.of("../../contracts/openapi.json").toAbsolutePath().normalize();
        if (Boolean.getBoolean("updateOpenApiSnapshot")) {
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, json.writerWithDefaultPrettyPrinter().writeValueAsString(contract) + "\n");
        }
        assertThat(Files.exists(snapshot)).as("Versioned OpenAPI export exists").isTrue();
        assertThat(json.readTree(snapshot.toFile())).isEqualTo(contract);
    }

    @Test
    void exposesHealthMetricsAndPrometheusWithoutInternalHealthDetails() {
        assertThat(http.getForEntity("/actuator/health", JsonNode.class).getBody().path("status").asText()).isEqualTo("UP");
        var health = http.getForEntity("/actuator/health", JsonNode.class).getBody();
        assertThat(health.has("components")).isFalse();
        assertThat(http.getForEntity("/actuator/metrics", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var prometheus = http.getForEntity("/actuator/prometheus", String.class);
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prometheus.getBody()).contains("http_server_requests_seconds");
    }

    private ResponseEntity<JsonNode> create(String title, List<Map<String, String>> sections) {
        return http.postForEntity("/api/documents",
                authored(Map.of("templateRef", "notice-v1", "title", title, "sections", sections)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> transition(String id, String action, long expectedVersion) {
        return http.postForEntity("/api/documents/" + id + "/" + action,
                authored(Map.of("expectedVersion", expectedVersion)), JsonNode.class);
    }

    private JsonNode get(String id) {
        var response = http.getForEntity("/api/documents/" + id, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpEntity<Object> authored(Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User", "walid");
        return new HttpEntity<>(body, headers);
    }

    private void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody().path("status").asInt()).isEqualTo(status.value());
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
    }
}
