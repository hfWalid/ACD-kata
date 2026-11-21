package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.exception.SourceRefAlreadyUsedException;
import fr.walid.acd.contribution.application.importing.SourceRef;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.port.out.DocumentSourceIndex;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = JdbcDocumentSourceIndexIT.SourceIndexTestConfiguration.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml"
})
class JdbcDocumentSourceIndexIT {
    private static final Instant CREATED_AT = Instant.parse("2026-11-21T09:00:00.123456Z");
    private static final Actor AUTHOR = new Actor("importateur");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private DocumentSourceIndex sources;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.execute("TRUNCATE TABLE outbox_events, document_audit, document_sections, documents CASCADE");
    }

    @Test
    void recordsAndFindsTheDocumentOfAReference() {
        var document = draft();
        documents.add(document);

        sources.record(new SourceRef("annuaire", "DOC-2026-0001"), document.id());

        assertThat(sources.find(new SourceRef("annuaire", "DOC-2026-0001"))).contains(document.id());
        assertThat(sources.find(new SourceRef("annuaire", "DOC-2026-0002"))).isEmpty();
    }

    @Test
    void refusesASecondDocumentForTheSameReference() {
        var first = draft();
        var second = draft();
        documents.add(first);
        documents.add(second);
        sources.record(new SourceRef("annuaire", "DOC-2026-0001"), first.id());

        assertThatThrownBy(() -> sources.record(new SourceRef("annuaire", "DOC-2026-0001"), second.id()))
                .isInstanceOf(SourceRefAlreadyUsedException.class)
                .hasMessage("Source reference already imported: annuaire/DOC-2026-0001");
    }

    @Test
    void acceptsTheSameReferenceFromAnotherSource() {
        var first = draft();
        var second = draft();
        documents.add(first);
        documents.add(second);

        sources.record(new SourceRef("annuaire", "DOC-2026-0001"), first.id());
        sources.record(new SourceRef("reprise", "DOC-2026-0001"), second.id());

        assertThat(sources.find(new SourceRef("reprise", "DOC-2026-0001"))).contains(second.id());
    }

    @Test
    void forgetsTheReferenceWhenTheDocumentDisappears() {
        var document = draft();
        documents.add(document);
        sources.record(new SourceRef("annuaire", "DOC-2026-0001"), document.id());

        jdbc.update("DELETE FROM documents WHERE id = ?", document.id().value());

        assertThat(sources.find(new SourceRef("annuaire", "DOC-2026-0001"))).isEmpty();
    }

    private static Document draft() {
        return Document.create(new DocumentId(UUID.randomUUID()), new TemplateRef("report"), "Document importé",
                List.of(new Section("Résumé", "Contenu")), AUTHOR, CREATED_AT);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("fr.walid.acd.contribution.adapters.persistence")
    @Import({JpaDocumentRepository.class, JdbcDocumentSourceIndex.class})
    static class SourceIndexTestConfiguration {
    }
}
