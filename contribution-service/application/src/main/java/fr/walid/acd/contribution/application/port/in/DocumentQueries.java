package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

public interface DocumentQueries {
    Document get(DocumentId id);
    DocumentPage search(DocumentSearch search);
}
