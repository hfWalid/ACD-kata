package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.event.OutboxEntry;
import fr.walid.acd.contribution.application.port.out.EventPublisher;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRelayTest {
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final UUID FIRST = UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11");
    private static final UUID SECOND = UUID.fromString("8f8f5b1c-3d2e-4a4b-9c6d-7e8f9a0b1c2d");

    @Test
    void publishesTheClaimedEventsThenMarksThemPublished() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 0));
        outbox.due.add(entry(SECOND, 0));
        var publisher = new RecordingPublisher();

        var published = relay(outbox, publisher).relay(10);

        assertThat(published).isEqualTo(2);
        assertThat(publisher.published).extracting(DocumentLifecycleEvent::eventId).containsExactly(FIRST, SECOND);
        assertThat(outbox.published).containsExactly(FIRST, SECOND);
        assertThat(outbox.lastLimit).isEqualTo(10);
        assertThat(outbox.lastLease).isEqualTo(NOW.plus(LEASE));
        assertThat(outbox.failures).isEmpty();
    }

    @Test
    void doesNothingWhenNoEventIsDue() {
        var outbox = new FakeEventOutbox();

        var published = relay(outbox, new RecordingPublisher()).relay(10);

        assertThat(published).isZero();
        assertThat(outbox.published).isEmpty();
    }

    @Test
    void keepsAFailedEventAndPostponesItWithTheBaseBackoff() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 0));
        outbox.due.add(entry(SECOND, 0));
        var publisher = new RecordingPublisher();
        publisher.failOn(SECOND, new IllegalStateException("broker unavailable"));

        var published = relay(outbox, publisher).relay(10);

        assertThat(published).isEqualTo(1);
        assertThat(outbox.published).containsExactly(FIRST);
        assertThat(outbox.failures).containsEntry(SECOND, "broker unavailable");
        assertThat(outbox.nextAttempts).containsEntry(SECOND, NOW.plus(BASE_BACKOFF));
    }

    @Test
    void doublesTheBackoffWithEachFailureAndStopsAtTheMaximum() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 1));
        outbox.due.add(entry(SECOND, 20));
        var publisher = new RecordingPublisher();
        publisher.failOn(FIRST, new IllegalStateException("first"));
        publisher.failOn(SECOND, new IllegalStateException("second"));

        relay(outbox, publisher).relay(10);

        assertThat(outbox.nextAttempts).containsEntry(FIRST, NOW.plus(BASE_BACKOFF.multipliedBy(2)));
        assertThat(outbox.nextAttempts).containsEntry(SECOND, NOW.plus(MAX_BACKOFF));
    }

    @Test
    void marksNothingPublishedWhenEveryEventFails() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 0));
        var publisher = new RecordingPublisher();
        publisher.failOn(FIRST, new IllegalStateException("down"));

        var published = relay(outbox, publisher).relay(10);

        assertThat(published).isZero();
        assertThat(outbox.published).isEmpty();
    }

    @Test
    void namesTheFailureWhenTheExceptionCarriesNoMessage() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 0));
        var publisher = new RecordingPublisher();
        publisher.failOn(FIRST, new IllegalStateException());

        relay(outbox, publisher).relay(10);

        assertThat(outbox.failures).containsEntry(FIRST, "IllegalStateException");
    }

    @Test
    void namesTheFailureWhenTheMessageIsBlank() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 0));
        var publisher = new RecordingPublisher();
        publisher.failOn(FIRST, new IllegalStateException("   "));

        relay(outbox, publisher).relay(10);

        assertThat(outbox.failures).containsEntry(FIRST, "IllegalStateException");
    }

    @Test
    void truncatesALongFailureMessageBeforeStoringIt() {
        var outbox = new FakeEventOutbox();
        outbox.due.add(entry(FIRST, 0));
        var publisher = new RecordingPublisher();
        publisher.failOn(FIRST, new IllegalStateException("x".repeat(600)));

        relay(outbox, publisher).relay(10);

        assertThat(outbox.failures.get(FIRST)).hasSize(500);
    }

    @Test
    void refusesANonPositiveBatchSize() {
        assertThatThrownBy(() -> relay(new FakeEventOutbox(), new RecordingPublisher()).relay(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    private static OutboxRelay relay(FakeEventOutbox outbox, EventPublisher publisher) {
        return new OutboxRelay(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC), LEASE, BASE_BACKOFF, MAX_BACKOFF);
    }

    private static OutboxEntry entry(UUID id, int attempts) {
        var event = new DocumentLifecycleEvent(id, DocumentLifecycleEvent.TYPE,
                DocumentLifecycleEvent.SCHEMA_VERSION, new DocumentId(UUID.randomUUID()), 1,
                DocumentAction.CREATED, new DocumentStatus.Draft(), "Report", new Actor("walid"), NOW);
        return new OutboxEntry(id, event, attempts);
    }

    private static final class RecordingPublisher implements EventPublisher {
        final List<DocumentLifecycleEvent> published = new ArrayList<>();
        private final List<UUID> failing = new ArrayList<>();
        private RuntimeException failure;

        void failOn(UUID eventId, RuntimeException error) {
            failing.add(eventId);
            failure = error;
        }

        @Override
        public void publish(DocumentLifecycleEvent event) {
            if (failing.contains(event.eventId())) {
                throw failure;
            }
            published.add(event);
        }
    }
}
