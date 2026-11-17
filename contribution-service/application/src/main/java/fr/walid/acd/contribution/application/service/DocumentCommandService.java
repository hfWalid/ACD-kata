package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.UpdateDocumentCommand;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class DocumentCommandService implements DocumentCommands {
    private final DocumentRepository repository;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public DocumentCommandService(DocumentRepository repository, Clock clock, Supplier<UUID> idGenerator) {
        this.repository = Objects.requireNonNull(repository, "Repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.idGenerator = Objects.requireNonNull(idGenerator, "Id generator is required");
    }

    @Override
    public Document create(CreateDocumentCommand command) {
        var document = Document.create(new DocumentId(idGenerator.get()), command.templateRef(), command.title(),
                command.sections(), command.author(), clock.instant());
        repository.add(document);
        return document;
    }

    @Override
    public Document update(UpdateDocumentCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.updateContent(
                command.title(), command.sections(), command.author(), clock.instant()));
    }

    @Override
    public Document submit(ChangeDocumentStatusCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.submit(command.author(), clock.instant()));
    }

    @Override
    public Document startReview(ChangeDocumentStatusCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.startReview(command.author(), clock.instant()));
    }

    @Override
    public Document approve(ChangeDocumentStatusCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.approve(command.author(), clock.instant()));
    }

    @Override
    public Document reject(ChangeDocumentStatusCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.reject(command.author(), clock.instant()));
    }

    @Override
    public Document publish(ChangeDocumentStatusCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.publish(command.author(), clock.instant()));
    }

    @Override
    public Document archive(ChangeDocumentStatusCommand command) {
        return change(command.id(), command.expectedVersion(), document -> document.archive(command.author(), clock.instant()));
    }

    private Document change(DocumentId id, long expectedVersion, UnaryOperator<Document> action) {
        var document = repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        if (document.version() != expectedVersion) {
            throw new DocumentConflictException(id);
        }
        var changed = action.apply(document);
        repository.save(changed, expectedVersion);
        return changed;
    }
}
