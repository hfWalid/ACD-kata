package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.SourceRefAlreadyUsedException;
import fr.walid.acd.contribution.application.importing.SourceRef;
import fr.walid.acd.contribution.application.port.out.DocumentSourceIndex;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeDocumentSourceIndex implements DocumentSourceIndex {
    final Map<SourceRef, DocumentId> recorded = new LinkedHashMap<>();
    final List<SourceRef> lookups = new ArrayList<>();
    /** When true, storing a reference collides with a concurrent import of the same reference. */
    boolean conflictOnRecord;
    /** What the concurrent winner stored, or null when the collision left no trace. */
    DocumentId concurrentWinner;
    private boolean recordAttempted;

    @Override
    public Optional<DocumentId> find(SourceRef sourceRef) {
        lookups.add(sourceRef);
        var stored = recorded.get(sourceRef);
        if (stored != null) {
            return Optional.of(stored);
        }
        if (recordAttempted && concurrentWinner != null) {
            return Optional.of(concurrentWinner);
        }
        return Optional.empty();
    }

    @Override
    public void record(SourceRef sourceRef, DocumentId documentId) {
        recordAttempted = true;
        if (conflictOnRecord) {
            throw new SourceRefAlreadyUsedException(sourceRef);
        }
        recorded.put(sourceRef, documentId);
    }
}
