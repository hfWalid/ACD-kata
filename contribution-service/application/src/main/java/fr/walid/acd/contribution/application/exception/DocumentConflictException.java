package fr.walid.acd.contribution.application.exception;

import fr.walid.acd.contribution.domain.DocumentId;

public final class DocumentConflictException extends RuntimeException {
    private final DocumentId id;

    public DocumentConflictException(DocumentId id) {
        super("Document version conflict: " + id.value());
        this.id = id;
    }

    public DocumentId id() {
        return id;
    }
}
