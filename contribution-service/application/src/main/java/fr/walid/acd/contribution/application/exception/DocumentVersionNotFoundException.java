package fr.walid.acd.contribution.application.exception;

import fr.walid.acd.contribution.domain.DocumentId;

public final class DocumentVersionNotFoundException extends RuntimeException {
    private final DocumentId id;
    private final long version;

    public DocumentVersionNotFoundException(DocumentId id, long version) {
        super("Document " + id.value() + " has no stored values for version " + version);
        this.id = id;
        this.version = version;
    }

    public DocumentId id() {
        return id;
    }

    public long version() {
        return version;
    }
}
