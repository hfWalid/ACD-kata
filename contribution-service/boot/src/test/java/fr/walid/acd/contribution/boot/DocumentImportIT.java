package fr.walid.acd.contribution.boot;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "acd.outbox.relay.enabled=false"
})
class DocumentImportIT {

    private static final String CSV = """
            reference;titre;gabarit;section_titre;section_contenu
            IMPORT-0001;Politique de sauvegarde;policy;Contexte;Les sauvegardes du site principal.
            IMPORT-0001;Politique de sauvegarde;policy;Portée;Tous les serveurs de production.
            IMPORT-0002;Second document;policy;Contexte;Un second document complet.
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14-alpine")
            .withDatabaseName("acd_contribution");

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("acd.import.specs-directory",
                () -> Path.of("../../contracts/import").toAbsolutePath().normalize().toString());
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clearDisposableDatabase() {
        // The imported documents outlive one test method: each starts from an empty database.
        jdbc.execute("TRUNCATE TABLE document_sources, outbox_events, document_audit, document_sections, documents CASCADE");
    }

    @Test
    void approvesThroughEveryStepThenSkipsTheSameFileOnReplay() {
        var first = importFile("annuaire", "approved", false, CSV).getBody();

        assertThat(first.path("mode").asText()).isEqualTo("APPROVED");
        assertThat(first.path("imported").asLong()).isEqualTo(2);
        assertThat(first.path("rejected").asLong()).isZero();
        var approved = first.at("/rows/0");
        assertThat(approved.path("documentStatus").asText()).isEqualTo("Approved");
        assertThat(approved.path("version").asLong()).isEqualTo(4);

        var document = http.getForEntity("/api/documents/" + approved.path("documentId").asText(), JsonNode.class)
                .getBody();
        assertThat(document.path("status").asText()).isEqualTo("Approved");
        assertThat(document.path("sections")).hasSize(2);
        assertThat(document.path("auditTrail")).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE document_id = ?::uuid", Long.class,
                approved.path("documentId").asText())).isEqualTo(4);

        var replay = importFile("annuaire", "approved", false, CSV).getBody();
        assertThat(replay.path("alreadyImported").asLong()).isEqualTo(2);
        assertThat(replay.path("imported").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents", Long.class)).isEqualTo(2);
    }

    @Test
    void reportsARejectedRowWithoutLosingTheGoodOnes() {
        var csv = """
                reference;titre;gabarit;section_titre;section_contenu
                IMPORT-0100;Titre valide;policy;Contexte;Contenu
                IMPORT-0101;;policy;Contexte;Document sans titre
                """;

        var report = importFile("annuaire", "draft", false, csv).getBody();

        assertThat(report.path("imported").asLong()).isEqualTo(1);
        assertThat(report.path("rejected").asLong()).isEqualTo(1);
        assertThat(report.at("/rows/0/status").asText()).isEqualTo("IMPORTED");
        assertThat(report.at("/rows/1/reference").asText()).isEqualTo("IMPORT-0101");
        assertThat(report.at("/rows/1/code").asText()).isEqualTo("INVALID_DOCUMENT");
        assertThat(report.at("/rows/1/detail").asText()).contains("title");
    }

    @Test
    void aDryRunWritesNothing() {
        var report = importFile("annuaire", "approved", true, CSV).getBody();

        assertThat(report.path("dryRun").asBoolean()).isTrue();
        assertThat(report.path("planned").asLong()).isEqualTo(2);
        assertThat(report.at("/rows/0/status").asText()).isEqualTo("PLANNED");
        assertThat(report.at("/rows/0/documentStatus").asText()).isEqualTo("Approved");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_sources", Long.class)).isZero();
    }

    @Test
    void refusesAnUnknownSourceWithAProblemDetail() {
        var response = importFile("inconnue", "draft", false, CSV);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asText()).isEqualTo("UNKNOWN_SOURCE");
    }

    private ResponseEntity<JsonNode> importFile(String source, String mode, boolean dryRun, String content) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "annuaire.csv";
            }
        });
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User", "importateur");
        headers.set("X-Approver", "relecteur");
        var url = "/api/imports?source=%s&mode=%s&dryRun=%s".formatted(source, mode, dryRun);
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
