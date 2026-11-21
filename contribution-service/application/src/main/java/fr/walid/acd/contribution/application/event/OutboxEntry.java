package fr.walid.acd.contribution.application.event;

import java.util.Objects;
import java.util.UUID;

/**
 * One event waiting in the outbox, with the number of failed publications already recorded.
 */
public record OutboxEntry(UUID id, DocumentLifecycleEvent event, int attempts) {

    public OutboxEntry {
        Objects.requireNonNull(id, "Id is required");
        Objects.requireNonNull(event, "Event is required");
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempts must not be negative");
        }
    }
}
