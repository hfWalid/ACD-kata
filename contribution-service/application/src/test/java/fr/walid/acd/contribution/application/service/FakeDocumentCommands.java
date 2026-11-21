package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.UpdateDocumentCommand;
import fr.walid.acd.contribution.application.query.DocumentDetail;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Records the commands an import runs, and applies them to the real aggregate. */
final class FakeDocumentCommands implements DocumentCommands {
    final List<String> calls = new ArrayList<>();
    final List<Actor> actors = new ArrayList<>();
    final Map<String, RuntimeException> failures = new LinkedHashMap<>();
    private final Map<DocumentId, Document> stored = new LinkedHashMap<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC);
    private int identifiers;

    @Override
    public DocumentDetail create(CreateDocumentCommand command) {
        calls.add("create");
        actors.add(command.author());
        fail("create");
        var document = Document.create(new DocumentId(UUID.fromString("00000000-0000-0000-0000-%012d".formatted(++identifiers))),
                command.templateRef(), command.title(), command.sections(), command.author(), clock.instant());
        stored.put(document.id(), document);
        return DocumentDetail.from(document);
    }

    @Override
    public DocumentDetail update(UpdateDocumentCommand command) {
        throw new UnsupportedOperationException("update is not part of an import");
    }

    @Override
    public DocumentDetail submit(ChangeDocumentStatusCommand command) {
        return transition("submit", command, document -> document.submit(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail startReview(ChangeDocumentStatusCommand command) {
        return transition("startReview", command, document -> document.startReview(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail approve(ChangeDocumentStatusCommand command) {
        return transition("approve", command, document -> document.approve(command.author(), clock.instant()));
    }

    @Override
    public DocumentDetail reject(ChangeDocumentStatusCommand command) {
        throw new UnsupportedOperationException("reject is not part of an import");
    }

    @Override
    public DocumentDetail publish(ChangeDocumentStatusCommand command) {
        throw new UnsupportedOperationException("publish is not part of an import");
    }

    @Override
    public DocumentDetail archive(ChangeDocumentStatusCommand command) {
        throw new UnsupportedOperationException("archive is not part of an import");
    }

    private DocumentDetail transition(String call, ChangeDocumentStatusCommand command, UnaryOperator<Document> action) {
        calls.add(call);
        actors.add(command.author());
        fail(call);
        var current = stored.get(command.id());
        if (current.version() != command.expectedVersion()) {
            throw new DocumentConflictException(command.id());
        }
        var updated = action.apply(current);
        stored.put(updated.id(), updated);
        return DocumentDetail.from(updated);
    }

    private void fail(String call) {
        var failure = failures.get(call);
        if (failure != null) {
            throw failure;
        }
    }
}
