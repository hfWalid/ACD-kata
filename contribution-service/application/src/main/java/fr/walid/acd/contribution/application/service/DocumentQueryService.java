package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.port.in.DocumentQueries;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.Objects;

public final class DocumentQueryService implements DocumentQueries {
    private final DocumentRepository repository;

    public DocumentQueryService(DocumentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository is required");
    }

    @Override
    public Document get(DocumentId id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Override
    public DocumentPage search(DocumentSearch search) {
        return repository.search(search);
    }
}
