package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.application.query.DocumentDetail;

/**
 * Write use cases. Each one returns the state reached by the document as a read model, so a
 * transport adapter never manipulates the aggregate or its transitions.
 */
public interface DocumentCommands {
    DocumentDetail create(CreateDocumentCommand command);
    DocumentDetail update(UpdateDocumentCommand command);
    DocumentDetail submit(ChangeDocumentStatusCommand command);
    DocumentDetail startReview(ChangeDocumentStatusCommand command);
    DocumentDetail approve(ChangeDocumentStatusCommand command);
    DocumentDetail reject(ChangeDocumentStatusCommand command);
    DocumentDetail publish(ChangeDocumentStatusCommand command);
    DocumentDetail archive(ChangeDocumentStatusCommand command);
}
