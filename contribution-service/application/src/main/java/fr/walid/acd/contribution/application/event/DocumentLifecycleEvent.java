package fr.walid.acd.contribution.application.event;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Integration event published after a document mutation. It is a contract read by other services:
 * field names, and the pair ({@link #TYPE}, {@code schemaVersion}), change only together with a new
 * version of the event. The outbox stores the version each row was recorded with, so a row written
 * before an upgrade is still published as the version it belongs to.
 */
public record DocumentLifecycleEvent(
        UUID eventId,
        String type,
        int schemaVersion,
        DocumentId documentId,
        long documentVersion,
        DocumentAction action,
        DocumentStatus status,
        String title,
        Actor actor,
        Instant occurredAt) {

    public static final String TYPE = "document.lifecycle.changed";
    public static final int SCHEMA_VERSION = 1;

    public DocumentLifecycleEvent {
        Objects.requireNonNull(eventId, "Event id is required");
        Objects.requireNonNull(type, "Event type is required");
        Objects.requireNonNull(documentId, "Document id is required");
        Objects.requireNonNull(action, "Action is required");
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(title, "Title is required");
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

    /** Builds the event of the mutation that produced this aggregate state. */
    public static DocumentLifecycleEvent of(Document document, UUID eventId) {
        var applied = document.auditTrail().getLast();
        return new DocumentLifecycleEvent(eventId, TYPE, SCHEMA_VERSION, document.id(), document.version(),
                applied.action(), document.status(), document.title(), applied.author(), applied.occurredAt());
    }

    /** Partition key: all events of one document stay ordered. */
    public String partitionKey() {
        return documentId.value().toString();
    }
}
