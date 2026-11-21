package fr.walid.acd.review.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What this service needs from a lifecycle event: which document reached which version, by which
 * action. Only version 1 of the contract is accepted; a later schema version arrives with its own
 * fields and its own model.
 */
public record DocumentLifecycleEvent(
        UUID eventId,
        String type,
        int schemaVersion,
        UUID documentId,
        long documentVersion,
        String action,
        String actor,
        Instant occurredAt) {

    public DocumentLifecycleEvent {
        Objects.requireNonNull(eventId, "Event id is required");
        Objects.requireNonNull(type, "Event type is required");
        Objects.requireNonNull(documentId, "Document id is required");
        Objects.requireNonNull(action, "Action is required");
        Objects.requireNonNull(actor, "Actor is required");
        Objects.requireNonNull(occurredAt, "Occurrence time is required");
        if (type.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        if (documentVersion < 1) {
            throw new IllegalArgumentException("Document version must be positive");
        }
    }
}
