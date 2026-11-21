package fr.walid.acd.contribution.application.port.out;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.event.OutboxEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional outbox: events are written with the aggregate state they describe, then published
 * later by a relay. Nothing is published that was not committed.
 */
public interface EventOutbox {

    /** Appends events in the caller's transaction. */
    void append(List<DocumentLifecycleEvent> events);

    /**
     * Claims up to {@code limit} events that are due, pushing their next attempt to
     * {@code leaseUntil} so a second relay skips them. A crashed relay releases its claim when the
     * lease expires, which is why consumers must tolerate a repetition.
     */
    List<OutboxEntry> claimDue(int limit, Instant leaseUntil);

    void markPublished(List<UUID> eventIds, Instant publishedAt);

    /** Records a failed publication and postpones the next attempt. */
    void markFailed(UUID eventId, String error, Instant nextAttemptAt);
}
