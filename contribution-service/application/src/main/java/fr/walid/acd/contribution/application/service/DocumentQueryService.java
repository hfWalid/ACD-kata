package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.exception.DocumentVersionNotFoundException;
import fr.walid.acd.contribution.application.port.in.DocumentQueries;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.query.DocumentDetail;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentVersion;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.Objects;

public final class DocumentQueryService implements DocumentQueries {
    private final DocumentRepository repository;

    public DocumentQueryService(DocumentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Repository is required");
    }

    @Override
    public DocumentDetail get(DocumentId id) {
        return repository.findById(id)
                .map(DocumentDetail::from)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Override
    public DocumentPage search(DocumentSearch search) {
        return repository.search(search);
    }

    @Override
    public DocumentVersion version(DocumentId id, long version) {
        return repository.version(id, version).orElseThrow(() -> {
            if (!repository.exists(id)) {
                throw new DocumentNotFoundException(id);
            }
            throw new DocumentVersionNotFoundException(id, version);
        });
    }
}
