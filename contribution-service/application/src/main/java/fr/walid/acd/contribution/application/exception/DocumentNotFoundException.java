package fr.walid.acd.contribution.application.exception;

import fr.walid.acd.contribution.domain.DocumentId;

public final class DocumentNotFoundException extends RuntimeException {
    private final DocumentId id;

    public DocumentNotFoundException(DocumentId id) {
        super("Document not found: " + id.value());
        this.id = id;
    }

    public DocumentId id() {
        return id;
    }
}
