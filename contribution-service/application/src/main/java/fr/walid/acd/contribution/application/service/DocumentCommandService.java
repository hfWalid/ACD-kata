package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.UpdateDocumentCommand;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.application.port.out.Transactions;
import fr.walid.acd.contribution.application.query.DocumentDetail;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class DocumentCommandService implements DocumentCommands {
    private final DocumentRepository repository;
    private final EventOutbox outbox;
    private final Transactions transactions;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public DocumentCommandService(DocumentRepository repository, EventOutbox outbox, Transactions transactions,
                                  Clock clock, Supplier<UUID> idGenerator) {
        this.repository = Objects.requireNonNull(repository, "Repository is required");
        this.outbox = Objects.requireNonNull(outbox, "Outbox is required");
        this.transactions = Objects.requireNonNull(transactions, "Transactions are required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.idGenerator = Objects.requireNonNull(idGenerator, "Id generator is required");
    }

    @Override
    public DocumentDetail create(CreateDocumentCommand command) {
        var document = Document.create(new DocumentId(idGenerator.get()), command.templateRef(), command.title(),
                command.sections(), command.author(), clock.instant());
        // State and event commit together: no event without a document, no document without an event.
        return transactions.inTransaction(() -> {
            repository.add(document);
            outbox.append(List.of(DocumentLifecycleEvent.of(document, idGenerator.get())));
            return DocumentDetail.from(document);
        });
    }

    @Override
    public DocumentDetail update(UpdateDocumentCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.updateContent(
                command.title(), command.sections(), command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail submit(ChangeDocumentStatusCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.submit(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail startReview(ChangeDocumentStatusCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.startReview(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail approve(ChangeDocumentStatusCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.approve(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail reject(ChangeDocumentStatusCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.reject(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail publish(ChangeDocumentStatusCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.publish(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail archive(ChangeDocumentStatusCommand command) {
        return changed(command.id(), command.expectedVersion(), document -> document.archive(command.author(), clock.instant()));
    }

    private DocumentDetail changed(DocumentId id, long expectedVersion, UnaryOperator<Document> action) {
        // The read keeps its own repeatable-read transaction; only the write below is grouped with
        // the outbox append.
        var current = repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        if (current.version() != expectedVersion) {
            throw new DocumentConflictException(id);
        }
        var updated = action.apply(current);
        return transactions.inTransaction(() -> {
            repository.save(updated, expectedVersion);
            outbox.append(List.of(DocumentLifecycleEvent.of(updated, idGenerator.get())));
            return DocumentDetail.from(updated);
        });
    }
}
