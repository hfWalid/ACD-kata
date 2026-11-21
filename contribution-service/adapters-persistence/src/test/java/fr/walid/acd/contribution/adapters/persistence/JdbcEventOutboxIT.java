package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.Transactions;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = JdbcEventOutboxIT.OutboxTestConfiguration.class, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml"
})
class JdbcEventOutboxIT {

    private static final Instant CREATED_AT = Instant.parse("2026-11-18T09:00:00.123456Z");
    private static final Actor AUTHOR = new Actor("walid");
    private static final Duration LEASE = Duration.ofSeconds(30);

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
    private EventOutbox outbox;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearDisposableDatabase() {
        jdbc.execute("TRUNCATE TABLE outbox_events, document_audit, document_sections, documents CASCADE");
    }

    @Test
    void recordsTheEventInOneRowWithTheVersionItWasWrittenFor() {
        var draft = draft();
        documents.add(draft);
        var event = DocumentLifecycleEvent.of(draft, UUID.randomUUID());

        transactions.inTransaction(() -> {
            outbox.append(List.of(event));
            return null;
        });

        var row = jdbc.queryForMap("SELECT * FROM outbox_events");
        assertThat(row).containsEntry("event_type", DocumentLifecycleEvent.TYPE);
        assertThat(row).containsEntry("schema_version", 1);
        assertThat(row).containsEntry("document_id", draft.id().value());
        assertThat(row).containsEntry("business_version", 1L);
        assertThat(row).containsEntry("action", DocumentAction.CREATED.name());
        assertThat(row).containsEntry("status", new DocumentStatus.Draft().name());
        assertThat(row).containsEntry("attempts", 0);
        assertThat(row.get("published_at")).isNull();
    }

    @Test
    void refusesTwoEventsForTheSameDocumentVersion() {
        var draft = draft();
        documents.add(draft);
        var first = DocumentLifecycleEvent.of(draft, UUID.randomUUID());

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
            outbox.append(List.of(first));
            outbox.append(List.of(first));
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
    }

    @Test
    void claimsAnEventOnceThenAgainOnlyAfterTheLeaseExpires() {
        var draft = draft();
        documents.add(draft);
        transactions.inTransaction(() -> {
            outbox.append(List.of(DocumentLifecycleEvent.of(draft, UUID.randomUUID())));
            return null;
        });

        var claimed = outbox.claimDue(10, Instant.now().plus(LEASE));
        var claimedAgain = outbox.claimDue(10, Instant.now().plus(LEASE));
        // Simulates a relay that claimed the batch and died before publishing it.
        jdbc.update("UPDATE outbox_events SET next_attempt_at = now() - interval '1 minute'");
        var afterLease = outbox.claimDue(10, Instant.now().plus(LEASE));

        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().attempts()).isZero();
        assertThat(claimedAgain).isEmpty();
        assertThat(afterLease).extracting(entry -> entry.id()).containsExactly(claimed.getFirst().id());
    }

    @Test
    void freesTheEventOnceItIsPublished() {
        var draft = draft();
        documents.add(draft);
        transactions.inTransaction(() -> {
            outbox.append(List.of(DocumentLifecycleEvent.of(draft, UUID.randomUUID())));
            return null;
        });
        var claimed = outbox.claimDue(10, Instant.now().plus(LEASE)).getFirst();

        outbox.markPublished(List.of(claimed.id()), Instant.now());

        assertThat(outbox.claimDue(10, Instant.now().minus(LEASE))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT published_at FROM outbox_events WHERE id = ?", Instant.class,
                claimed.id())).isNotNull();
    }

    @Test
    void recordsAFailureWithItsCauseAndPostponesTheNextAttempt() {
        var draft = draft();
        documents.add(draft);
        transactions.inTransaction(() -> {
            outbox.append(List.of(DocumentLifecycleEvent.of(draft, UUID.randomUUID())));
            return null;
        });
        var claimed = outbox.claimDue(10, Instant.now().plus(LEASE)).getFirst();

        outbox.markFailed(claimed.id(), "broker unavailable", Instant.now().plusSeconds(60));

        assertThat(outbox.claimDue(10, Instant.now().minus(LEASE))).isEmpty();
        var row = jdbc.queryForMap("SELECT attempts, last_error FROM outbox_events WHERE id = ?", claimed.id());
        assertThat(row).containsEntry("attempts", 1).containsEntry("last_error", "broker unavailable");
    }

    @Test
    void aSecondRelaySkipsEventsBeingClaimedByAnother() throws Exception {
        var draft = draft();
        documents.add(draft);
        transactions.inTransaction(() -> {
            outbox.append(List.of(DocumentLifecycleEvent.of(draft, UUID.randomUUID())));
            return null;
        });
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var inFlight = worker.submit(() -> transactions.inTransaction(() -> {
                var batch = outbox.claimDue(10, Instant.now().plus(LEASE));
                claimed.countDown();
                awaitQuietly(release);
                return batch;
            }));

            assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(outbox.claimDue(10, Instant.now().plus(LEASE))).isEmpty();

            release.countDown();
            assertThat(inFlight.get(10, TimeUnit.SECONDS)).hasSize(1);
        }
    }

    @Test
    void rollsBackTheDocumentChangeWhenTheOutboxRejectsTheEvent() {
        var draft = draft();
        documents.add(draft);

        assertThatThrownBy(() -> transactions.inTransaction(() -> {
            var edited = draft.updateContent("Nouveau titre", List.of(new Section("Résumé", "Après")),
                    AUTHOR, CREATED_AT.plusSeconds(1));
            documents.save(edited, draft.version());
            var event = DocumentLifecycleEvent.of(edited, UUID.randomUUID());
            outbox.append(List.of(event));
            outbox.append(List.of(event));
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);

        var stored = documents.findById(draft.id()).orElseThrow();
        assertThat(stored.version()).isEqualTo(1);
        assertThat(stored.title()).isEqualTo("Document");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Release was not signalled");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static Document draft() {
        return Document.create(
                new DocumentId(UUID.randomUUID()),
                new TemplateRef("report"), "Document",
                List.of(new Section("Résumé", "Contenu")), AUTHOR, CREATED_AT);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DocumentEntity.class)
    @Import({JdbcEventOutbox.class, SpringTransactions.class, JpaDocumentRepository.class})
    static class OutboxTestConfiguration {
    }
}
