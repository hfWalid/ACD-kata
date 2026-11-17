package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeDocumentRepository implements DocumentRepository {
    private final Map<DocumentId, Document> documents = new HashMap<>();
    DocumentSearch lastSearch;
    DocumentPage searchResult = new DocumentPage(0, List.of());
    boolean rejectNextSave;
    int saves;

    @Override
    public Optional<Document> findById(DocumentId id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public void add(Document document) {
        if (documents.putIfAbsent(document.id(), document) != null) {
            throw new DocumentConflictException(document.id());
        }
    }

    @Override
    public void save(Document document, long expectedVersion) {
        if (rejectNextSave) {
            throw new DocumentConflictException(document.id());
        }
        var current = findById(document.id()).orElseThrow(() -> new DocumentNotFoundException(document.id()));
        if (current.version() != expectedVersion) {
            throw new DocumentConflictException(document.id());
        }
        documents.put(document.id(), document);
        saves++;
    }

    @Override
    public DocumentPage search(DocumentSearch search) {
        lastSearch = search;
        return searchResult;
    }
}
