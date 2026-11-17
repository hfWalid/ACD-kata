package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.Objects;

public record ChangeDocumentStatusCommand(DocumentId id, long expectedVersion, Actor author) {
    public ChangeDocumentStatusCommand {
        Objects.requireNonNull(id, "Document id is required");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("Expected version must be positive");
        }
        Objects.requireNonNull(author, "Author is required");
    }
}
