package fr.walid.acd.contribution.application.port.out;

import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

import java.util.Optional;

public interface DocumentRepository {
    Optional<Document> findById(DocumentId id);
    void add(Document document);

    /** Saves the aggregate and its audit atomically, rejecting a stale expected version. */
    void save(Document document, long expectedVersion);

    DocumentPage search(DocumentSearch search);
}
