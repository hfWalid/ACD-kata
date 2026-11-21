package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.event.OutboxEntry;
import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.EventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Publishes what the outbox holds. At-least-once by construction: a claimed event whose relay
 * crashes is claimed again once the lease expires, and a failed publication keeps the event with
 * an exponential backoff. Consumers therefore deduplicate by event id.
 */
public final class OutboxRelay {
    private static final int MAX_ERROR_LENGTH = 500;
    private static final int MAX_BACKOFF_DOUBLINGS = 20;

    private final EventOutbox outbox;
    private final EventPublisher publisher;
    private final Clock clock;
    private final Duration lease;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public OutboxRelay(EventOutbox outbox, EventPublisher publisher, Clock clock,
                       Duration lease, Duration baseBackoff, Duration maxBackoff) {
        this.outbox = Objects.requireNonNull(outbox, "Outbox is required");
        this.publisher = Objects.requireNonNull(publisher, "Publisher is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.lease = Objects.requireNonNull(lease, "Lease is required");
        this.baseBackoff = Objects.requireNonNull(baseBackoff, "Base backoff is required");
        this.maxBackoff = Objects.requireNonNull(maxBackoff, "Max backoff is required");
    }

    /** Publishes one batch and returns how many events the broker accepted. */
    public int relay(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        var now = clock.instant();
        var claimed = outbox.claimDue(batchSize, now.plus(lease));
        if (claimed.isEmpty()) {
            return 0;
        }
        var published = new ArrayList<UUID>(claimed.size());
        for (var entry : claimed) {
            publish(entry, now, published);
        }
        if (!published.isEmpty()) {
            outbox.markPublished(published, now);
        }
        return published.size();
    }

    private void publish(OutboxEntry entry, Instant now, List<UUID> published) {
        try {
            publisher.publish(entry.event());
            published.add(entry.id());
        } catch (RuntimeException failure) {
            outbox.markFailed(entry.id(), describe(failure), now.plus(backoff(entry.attempts() + 1)));
        }
    }

    private Duration backoff(int attempts) {
        var doublings = Math.min(attempts - 1, MAX_BACKOFF_DOUBLINGS);
        var millis = baseBackoff.toMillis() * (1L << doublings);
        return Duration.ofMillis(Math.min(millis, maxBackoff.toMillis()));
    }

    private static String describe(RuntimeException failure) {
        var message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
