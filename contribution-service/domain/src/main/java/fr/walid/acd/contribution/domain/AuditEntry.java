package fr.walid.acd.contribution.domain;

import java.time.Instant;
import java.util.Objects;

public record AuditEntry(long version, DocumentAction action, Actor author, Instant occurredAt) {
    public AuditEntry {
        if (version < 1) {
            throw new IllegalArgumentException("Audit version must be positive");
        }
        Objects.requireNonNull(action, "Audit action is required");
        Objects.requireNonNull(author, "Audit author is required");
        Objects.requireNonNull(occurredAt, "Audit timestamp is required");
    }
}
