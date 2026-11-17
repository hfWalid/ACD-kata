package fr.walid.acd.contribution.application.query;

import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;

import java.time.Instant;
import java.util.Objects;

public record DocumentSummary(DocumentId id, String title, DocumentStatus status, long version, Instant updatedAt) {
    public DocumentSummary {
        Objects.requireNonNull(id, "Document id is required");
        Objects.requireNonNull(title, "Title is required");
        Objects.requireNonNull(status, "Status is required");
        if (version < 1) {
            throw new IllegalArgumentException("Document version must be positive");
        }
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
    }
}
