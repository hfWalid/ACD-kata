package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentSort;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = JpaDocumentRepositoryIT.PersistenceTestConfiguration.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml"
})
class JpaDocumentRepositoryIT {

    private static final Instant CREATED_AT = Instant.parse("2026-11-17T10:00:00.123456Z");
    private static final Actor AUTHOR = new Actor("walid");

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
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReadInterleaving readInterleaving;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.execute("TRUNCATE TABLE document_audit, document_sections, documents CASCADE");
    }

    @Test
    void storesAndRestoresTheCompleteAggregateIncludingSectionOrderAndAudit() {
        var draft = draft(1, "Contrat d'exploitation", List.of(
                new Section("Contexte", "Première section"),
                new Section("Décision", "Deuxième section")));

        documents.add(draft);
        var restored = documents.findById(draft.id()).orElseThrow();

        assertThat(restored.id()).isEqualTo(draft.id());
        assertThat(restored.templateRef()).isEqualTo(draft.templateRef());
        assertThat(restored.title()).isEqualTo(draft.title());
        assertThat(restored.sections()).isEqualTo(draft.sections());
        assertThat(restored.status()).isEqualTo(draft.status());
        assertThat(restored.version()).isEqualTo(1);
        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
        assertThat(restored.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(restored.auditTrail()).isEqualTo(draft.auditTrail());
        assertThat(jdbc.queryForObject("SELECT lock_version FROM documents WHERE id = ?", Long.class,
                draft.id().value())).isZero();
    }

    @Test
    void updatesContentAndStatusWithoutLosingTheAuditTrail() {
        var draft = draft(1, "Ancien titre", List.of(new Section("Résumé", "Avant")));
        documents.add(draft);

        var edited = draft.updateContent("Nouveau titre", List.of(
                new Section("Contexte", "Après"), new Section("Décision", "Valider")),
                AUTHOR, CREATED_AT.plusSeconds(1));
        documents.save(edited, draft.version());
        var submitted = documents.findById(draft.id()).orElseThrow()
                .submit(AUTHOR, CREATED_AT.plusSeconds(2));
        documents.save(submitted, edited.version());

        var restored = documents.findById(draft.id()).orElseThrow();
        assertThat(restored.title()).isEqualTo("Nouveau titre");
        assertThat(restored.sections()).isEqualTo(edited.sections());
        assertThat(restored.status()).isEqualTo(new DocumentStatus.Submitted());
        assertThat(restored.version()).isEqualTo(3);
        assertThat(restored.auditTrail()).isEqualTo(submitted.auditTrail());
    }

    @Test
    void removingSectionsPreservesTheirRemainingOrder() {
        var draft = draft(1, "Note", List.of(
                new Section("Un", "A"), new Section("Deux", "B"), new Section("Trois", "C")));
        documents.add(draft);

        var edited = draft.updateContent("Note", List.of(new Section("Trois", "C")),
                AUTHOR, CREATED_AT.plusSeconds(1));
        documents.save(edited, 1);

        assertThat(documents.findById(draft.id()).orElseThrow().sections()).isEqualTo(edited.sections());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_sections", Long.class)).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateDocumentIdWithoutReplacingItsContent() {
        var original = draft(1, "Original", List.of());
        documents.add(original);

        assertThatThrownBy(() -> documents.add(draft(1, "Autre", List.of())))
                .isInstanceOf(DocumentConflictException.class);
        assertThat(documents.findById(original.id()).orElseThrow().title()).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_audit", Long.class)).isEqualTo(1);
    }

    @Test
    void staleWritesCannotChangeEitherTheDocumentOrItsAudit() {
        var original = draft(1, "Original", List.of(new Section("Résumé", "Initial")));
        documents.add(original);
        var accepted = original.updateContent("Version acceptée", List.of(new Section("Résumé", "Accepté")),
                AUTHOR, CREATED_AT.plusSeconds(1));
        documents.save(accepted, 1);

        var stale = original.updateContent("Version périmée", List.of(new Section("Résumé", "Périmé")),
                AUTHOR, CREATED_AT.plusSeconds(2));
        assertThatThrownBy(() -> documents.save(stale, 1)).isInstanceOf(DocumentConflictException.class);

        var stored = documents.findById(original.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("Version acceptée");
        assertThat(stored.sections()).isEqualTo(accepted.sections());
        assertThat(stored.auditTrail()).isEqualTo(accepted.auditTrail());
    }

    @Test
    void rollsBackDocumentSectionsAndAuditTogether() {
        var original = draft(1, "Original", List.of(new Section("Résumé", "Initial")));
        documents.add(original);
        var edited = original.updateContent("Annulé", List.of(new Section("Résumé", "Annulé")),
                AUTHOR, CREATED_AT.plusSeconds(1));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            documents.save(edited, 1);
            throw new IllegalStateException("Abort this disposable test transaction");
        })).isInstanceOf(IllegalStateException.class);

        var restored = documents.findById(original.id()).orElseThrow();
        assertThat(restored.title()).isEqualTo(original.title());
        assertThat(restored.sections()).isEqualTo(original.sections());
        assertThat(restored.version()).isEqualTo(1);
        assertThat(restored.auditTrail()).isEqualTo(original.auditTrail());
    }

    @Test
    void onlyOneOfTwoActualConcurrentWritersCanCommit() throws Exception {
        var original = draft(1, "Original", List.of(new Section("Résumé", "Initial")));
        documents.add(original);
        var first = original.updateContent("Premier", original.sections(), AUTHOR, CREATED_AT.plusSeconds(1));
        var second = original.updateContent("Second", original.sections(), AUTHOR, CREATED_AT.plusSeconds(1));
        var loaded = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstResult = executor.submit(() -> commitAfterBothLoaded(first, loaded, start));
            var secondResult = executor.submit(() -> commitAfterBothLoaded(second, loaded, start));
            try {
                assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(List.of(firstResult.get(15, TimeUnit.SECONDS), secondResult.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        var stored = documents.findById(original.id()).orElseThrow();
        assertThat(stored.version()).isEqualTo(2);
        assertThat(stored.title()).isIn("Premier", "Second");
        assertThat(stored.auditTrail()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_audit", Long.class)).isEqualTo(2);
    }

    @Test
    void detectsAConcurrentCommitBeforeReadingTheAudit() throws Exception {
        var original = draft(1, "Original", List.of(new Section("Résumé", "Initial")));
        documents.add(original);
        var winner = original.updateContent("Accepté", original.sections(), AUTHOR, CREATED_AT.plusSeconds(1));
        var stale = original.updateContent("Périmé", original.sections(), AUTHOR, CREATED_AT.plusSeconds(1));
        var rootLoaded = new CountDownLatch(1);
        var winnerCommitted = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        entityManager.find(DocumentEntity.class, original.id().value());
                        rootLoaded.countDown();
                        try {
                            if (!winnerCommitted.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Winning transaction did not commit");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        documents.save(stale, 1);
                    });
                    return null;
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            try {
                assertThat(rootLoaded.await(10, TimeUnit.SECONDS)).isTrue();
                documents.save(winner, 1);
            } finally {
                winnerCommitted.countDown();
            }
            assertThat(result.get(15, TimeUnit.SECONDS)).isInstanceOf(DocumentConflictException.class);
        }

        var stored = documents.findById(original.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("Accepté");
        assertThat(stored.auditTrail()).hasSize(2);
    }

    @Test
    void readsOneConsistentAggregateWhileAWriterCommits() throws Exception {
        var original = draft(1, "Original", List.of(new Section("Résumé", "Initial")));
        documents.add(original);
        var edited = original.updateContent("Modifié", List.of(new Section("Résumé", "Modifié")),
                AUTHOR, CREATED_AT.plusSeconds(1));
        var rootRead = new CountDownLatch(1);
        var writerCommitted = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> {
                readInterleaving.pauseBeforeChildren(rootRead, writerCommitted);
                try {
                    return documents.findById(original.id()).orElseThrow();
                } finally {
                    readInterleaving.clearCurrentThread();
                }
            });
            try {
                assertThat(rootRead.await(10, TimeUnit.SECONDS)).isTrue();
                documents.save(edited, 1);
            } finally {
                writerCommitted.countDown();
            }
            var snapshot = result.get(15, TimeUnit.SECONDS);
            assertThat(snapshot.title()).isEqualTo(original.title());
            assertThat(snapshot.sections()).isEqualTo(original.sections());
            assertThat(snapshot.version()).isEqualTo(original.version());
            assertThat(snapshot.auditTrail()).isEqualTo(original.auditTrail());
        }
    }

    @Test
    void returnsAnArbitraryOffsetWithAnExactTotalAndStableTieBreak() {
        for (int id = 5; id >= 1; id--) {
            documents.add(draft(id, "Même titre", List.of()));
        }

        var page = documents.search(search(1, 3, Optional.empty(), ""));

        assertThat(page.rowCount()).isEqualTo(5);
        assertThat(page.rows()).extracting(row -> row.id().value())
                .containsExactly(id(2).value(), id(3).value());
        var beyondEnd = documents.search(search(9, 11, Optional.empty(), ""));
        assertThat(beyondEnd.rowCount()).isEqualTo(5);
        assertThat(beyondEnd.rows()).isEmpty();
    }

    @Test
    void rowsAndTotalShareCaseInsensitiveStatusAndLiteralTextFilters() {
        var matching = draft(1, "CONTRAT 100%_validé", List.of(new Section("Objet", "Complet")));
        documents.add(matching);
        documents.save(matching.submit(AUTHOR, CREATED_AT.plusSeconds(1)), 1);
        documents.add(draft(2, "contrat 100xxvalidé", List.of()));
        documents.add(draft(3, "contrat 100%_brouillon", List.of()));

        var page = documents.search(search(0, 50, Optional.of(new DocumentStatus.Submitted()), "contrat 100%_"));

        assertThat(page.rowCount()).isEqualTo(1);
        assertThat(page.rows()).extracting(row -> row.id()).containsExactly(matching.id());
        var noMatches = documents.search(search(0, 50, Optional.empty(), "absent"));
        assertThat(noMatches.rowCount()).isZero();
        assertThat(noMatches.rows()).isEmpty();
    }

    private boolean commitAfterBothLoaded(Document candidate, CountDownLatch loaded, CountDownLatch start) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                entityManager.find(DocumentEntity.class, candidate.id().value());
                loaded.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent writer barrier timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                documents.save(candidate, 1);
            });
            return true;
        } catch (DocumentConflictException conflict) {
            return false;
        }
    }

    private static Document draft(int number, String title, List<Section> sections) {
        return Document.create(id(number), new TemplateRef("note-v1"), title, sections, AUTHOR, CREATED_AT);
    }

    private static DocumentId id(int number) {
        return new DocumentId(new UUID(0, number));
    }

    private static DocumentSearch search(int start, int end, Optional<DocumentStatus> status, String query) {
        return new DocumentSearch(start, end,
                new DocumentSort(DocumentSort.Field.UPDATED_AT, DocumentSort.Direction.DESC), status, query);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DocumentEntity.class)
    @Import(JpaDocumentRepository.class)
    static class PersistenceTestConfiguration {

        @Bean
        ReadInterleaving readInterleaving() {
            return new ReadInterleaving();
        }

        @Bean
        HibernatePropertiesCustomizer statementInspector(ReadInterleaving interleaving) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", interleaving);
        }
    }

    /** Test-only schedule: commit a writer between the root read and its first child query. */
    static class ReadInterleaving implements StatementInspector {
        private final ConcurrentHashMap<Long, ReadWindow> windows = new ConcurrentHashMap<>();

        void pauseBeforeChildren(CountDownLatch rootRead, CountDownLatch writerCommitted) {
            windows.put(Thread.currentThread().threadId(), new ReadWindow(rootRead, writerCommitted));
        }

        void clearCurrentThread() {
            windows.remove(Thread.currentThread().threadId());
        }

        @Override
        public String inspect(String sql) {
            var window = windows.get(Thread.currentThread().threadId());
            if (window != null && sql.contains(" from document_sections ")) {
                window.rootRead().countDown();
                try {
                    if (!window.writerCommitted().await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Writer did not commit before the child read");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return sql;
        }

        private record ReadWindow(CountDownLatch rootRead, CountDownLatch writerCommitted) {
        }
    }
}
