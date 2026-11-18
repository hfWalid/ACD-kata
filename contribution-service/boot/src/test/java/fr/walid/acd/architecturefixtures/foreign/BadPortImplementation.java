package fr.walid.acd.architecturefixtures.foreign;

import fr.walid.acd.contribution.application.port.in.DocumentQueries;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentVersion;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

/** A port implementation deliberately located outside the permitted boundaries. */
public final class BadPortImplementation implements DocumentQueries {
    @Override
    public Document get(DocumentId id) {
        throw new UnsupportedOperationException("Architecture fixture only");
    }

    @Override
    public DocumentPage search(DocumentSearch search) {
        throw new UnsupportedOperationException("Architecture fixture only");
    }

    @Override
    public DocumentVersion version(DocumentId id, long version) {
        throw new UnsupportedOperationException("Architecture fixture only");
    }
}
