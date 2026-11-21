package fr.walid.acd.contribution.application.port.out;

import fr.walid.acd.contribution.application.importing.SourceRef;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.Optional;

/**
 * Remembers which document an import record produced. {@link #record} must run inside the
 * transaction that creates the document: either both are stored, or neither is.
 */
public interface DocumentSourceIndex {

    Optional<DocumentId> find(SourceRef sourceRef);

    void record(SourceRef sourceRef, DocumentId documentId);
}
