package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.port.out.EventPublisher;
import fr.walid.acd.contribution.application.service.OutboxRelay;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
    void claimsTheEventsOfOneDocumentInVersionOrderEvenWhenTheyShareATimestamp() {
        // One import writes create, submit, review and approve in a single transaction, so the four
        // events can carry the same microsecond. They must still reach the broker in version order,
        // otherwise the consumer sees version 4 before version 2 and drops the submission.
        var document = draft();
        documents.add(document);
        var edited = document.updateContent("Document", List.of(new Section("Résumé", "Contenu")), AUTHOR, CREATED_AT);
        var submitted = edited.submit(AUTHOR, CREATED_AT);
        var inReview = submitted.startReview(AUTHOR, CREATED_AT);
        var approved = inReview.approve(AUTHOR, CREATED_AT);
        var events = List.of(
                DocumentLifecycleEvent.of(document, UUID.randomUUID()),
                DocumentLifecycleEvent.of(edited, UUID.randomUUID()),
                DocumentLifecycleEvent.of(submitted, UUID.randomUUID()),
                DocumentLifecycleEvent.of(inReview, UUID.randomUUID()),
                DocumentLifecycleEvent.of(approved, UUID.randomUUID()));
        transactions.inTransaction(() -> {
            outbox.append(events);
            return null;
        });

        var claimed = outbox.claimDue(10, Instant.now().plus(LEASE));

        assertThat(claimed).extracting(entry -> entry.event().documentVersion())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
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

    @Test
    void reportsPendingAndFailedCountsAndPurgesOnlyPublishedEventsOlderThanTheWindow() {
        var publishedNow = eventFor(UUID.randomUUID());
        var publishedLongAgo = eventFor(UUID.randomUUID());
        var stillFailing = eventFor(UUID.randomUUID());
        transactions.inTransaction(() -> {
            outbox.append(List.of(publishedNow, publishedLongAgo, stillFailing));
            return null;
        });
        outbox.markPublished(List.of(publishedNow.eventId()), Instant.now());
        outbox.markPublished(List.of(publishedLongAgo.eventId()), Instant.now().minus(Duration.ofDays(10)));
        outbox.markFailed(stillFailing.eventId(), "broker down", Instant.now().plusSeconds(30));

        assertThat(outbox.countPending()).isEqualTo(1);
        assertThat(outbox.countFailed()).isEqualTo(1);
        assertThat(outbox.purgePublishedBefore(Instant.now().minus(Duration.ofDays(7)))).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(2);
        assertThat(outbox.countPending()).isEqualTo(1);
    }

    @Test
    void twoRelaysNeverPublishTheSameEventTwice() throws Exception {
        var events = IntStream.rangeClosed(1, 20).mapToObj(version -> eventFor(UUID.randomUUID())).toList();
        transactions.inTransaction(() -> {
            outbox.append(events);
            return null;
        });
        var publisher = new CountingPublisher();
        var relay = new OutboxRelay(outbox, publisher, Clock.systemUTC(), LEASE, Duration.ofMillis(5),
                Duration.ofMillis(50));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var running = IntStream.range(0, 2)
                    .mapToObj(worker -> pool.submit(() -> {
                        awaitQuietly(start);
                        for (int round = 0; round < 20; round++) {
                            relay.relay(5);
                            Thread.sleep(5);
                        }
                        return null;
                    }))
                    .toList();
            start.countDown();
            for (var worker : running) {
                worker.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(publisher.total()).isEqualTo(20);
        assertThat(publisher.maxPublishesPerEvent()).isEqualTo(1);
        assertThat(outbox.countPending()).isZero();
    }

    private static DocumentLifecycleEvent eventFor(UUID documentId) {
        return new DocumentLifecycleEvent(UUID.randomUUID(), DocumentLifecycleEvent.TYPE,
                DocumentLifecycleEvent.SCHEMA_VERSION, new DocumentId(documentId), 1,
                DocumentAction.CREATED, new DocumentStatus.Draft(), "Document", AUTHOR, CREATED_AT);
    }

    private static final class CountingPublisher implements EventPublisher {
        private final Map<UUID, AtomicInteger> counts = new ConcurrentHashMap<>();

        @Override
        public void publish(DocumentLifecycleEvent event) {
            counts.computeIfAbsent(event.eventId(), id -> new AtomicInteger()).incrementAndGet();
        }

        int total() {
            return counts.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        int maxPublishesPerEvent() {
            return counts.values().stream().mapToInt(AtomicInteger::get).max().orElse(0);
        }
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
