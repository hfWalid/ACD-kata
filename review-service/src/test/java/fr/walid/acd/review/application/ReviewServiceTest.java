package fr.walid.acd.review.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {
    private static final UUID DOCUMENT_ID = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");

    private final FakeReviewTasks tasks = new FakeReviewTasks();
    private final ReviewService service = new ReviewService(tasks, new Transactions() {
        @Override
        public <T> T inTransaction(java.util.function.Supplier<T> work) {
            return work.get();
        }
    });

    @Test
    void opensAReviewTaskWhenADocumentIsSubmitted() {
        assertThat(service.handle(event("SUBMITTED", 1))).isTrue();

        assertThat(tasks.opened).containsExactly("SUBMITTED/1");
        assertThat(tasks.decided).isEmpty();
        assertThat(tasks.remembered).hasSize(1);
    }

    @Test
    void closesThePendingTaskWhenTheDecisionArrives() {
        service.handle(event("SUBMITTED", 1));

        service.handle(event("APPROVED", 3));

        assertThat(tasks.decided).containsExactly("APPROVED/3");
    }

    @Test
    void recordsARejectionAsADecisionToo() {
        service.handle(event("SUBMITTED", 1));

        service.handle(event("REJECTED", 3));

        assertThat(tasks.decided).containsExactly("REJECTED/3");
    }

    @Test
    void ignoresAnEventAlreadyProcessed() {
        tasks.duplicate = true;

        assertThat(service.handle(event("SUBMITTED", 1))).isFalse();

        assertThat(tasks.opened).isEmpty();
    }

    @Test
    void ignoresAnEventOlderThanWhatWasAlreadyApplied() {
        tasks.stale = true;

        assertThat(service.handle(event("SUBMITTED", 1))).isFalse();

        assertThat(tasks.opened).isEmpty();
    }

    @Test
    void movesTheVersionForAnActionThatOpensNoReview() {
        assertThat(service.handle(event("CONTENT_UPDATED", 1))).isTrue();

        assertThat(tasks.versionAdvanced).isTrue();
        assertThat(tasks.opened).isEmpty();
        assertThat(tasks.decided).isEmpty();
    }

    private static DocumentLifecycleEvent event(String action, long version) {
        return new DocumentLifecycleEvent(UUID.randomUUID(), "document.lifecycle.changed", 1, DOCUMENT_ID,
                version, action, "walid", Instant.parse("2026-11-18T10:00:00Z").plusSeconds(version));
    }

    private static final class FakeReviewTasks implements ReviewTasks {
        final List<String> opened = new ArrayList<>();
        final List<String> decided = new ArrayList<>();
        final List<UUID> remembered = new ArrayList<>();
        boolean duplicate;
        boolean stale;
        boolean versionAdvanced;

        @Override
        public boolean remember(DocumentLifecycleEvent event) {
            if (duplicate) {
                return false;
            }
            remembered.add(event.eventId());
            return true;
        }

        @Override
        public boolean advanceTo(DocumentLifecycleEvent event) {
            if (stale) {
                return false;
            }
            versionAdvanced = true;
            return true;
        }

        @Override
        public void openReview(DocumentLifecycleEvent event) {
            opened.add(event.action() + "/" + event.documentVersion());
        }

        @Override
        public void decide(DocumentLifecycleEvent event, String decision) {
            decided.add(decision + "/" + event.documentVersion());
        }
    }
}
