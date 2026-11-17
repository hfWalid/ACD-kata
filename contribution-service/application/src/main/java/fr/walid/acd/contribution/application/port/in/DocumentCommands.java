package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.domain.Document;

public interface DocumentCommands {
    Document create(CreateDocumentCommand command);
    Document update(UpdateDocumentCommand command);
    Document submit(ChangeDocumentStatusCommand command);
    Document startReview(ChangeDocumentStatusCommand command);
    Document approve(ChangeDocumentStatusCommand command);
    Document reject(ChangeDocumentStatusCommand command);
    Document publish(ChangeDocumentStatusCommand command);
    Document archive(ChangeDocumentStatusCommand command);
}
