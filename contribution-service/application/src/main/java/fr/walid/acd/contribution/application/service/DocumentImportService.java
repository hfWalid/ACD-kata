package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.SourceRefAlreadyUsedException;
import fr.walid.acd.contribution.application.importing.ImportBatch;
import fr.walid.acd.contribution.application.importing.ImportMode;
import fr.walid.acd.contribution.application.importing.ImportOptions;
import fr.walid.acd.contribution.application.importing.ImportReport;
import fr.walid.acd.contribution.application.importing.MappedRecord;
import fr.walid.acd.contribution.application.importing.RowOutcome;
import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.DocumentCommands;
import fr.walid.acd.contribution.application.port.in.DocumentImport;
import fr.walid.acd.contribution.application.port.out.DocumentSourceIndex;
import fr.walid.acd.contribution.application.port.out.Transactions;
import fr.walid.acd.contribution.application.query.DocumentDetail;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DomainException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs one file record through the commands the UI uses: create, then submit, review and approve
 * when the mode asks for it. Nothing here invents a status or a shortcut — an approved import is
 * four real commands, so it leaves four audit entries, four versions and four events behind.
 *
 * <p>A record is its own unit of work: a refusal on line 7 does not undo lines 1 to 6, and the
 * report says exactly what happened to each line.
 */
public final class DocumentImportService implements DocumentImport {
    private final DocumentCommands commands;
    private final DocumentSourceIndex sourceIndex;
    private final Transactions transactions;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public DocumentImportService(DocumentCommands commands, DocumentSourceIndex sourceIndex, Transactions transactions,
                                 Clock clock, Supplier<UUID> idGenerator) {
        this.commands = Objects.requireNonNull(commands, "Commands are required");
        this.sourceIndex = Objects.requireNonNull(sourceIndex, "Source index is required");
        this.transactions = Objects.requireNonNull(transactions, "Transactions are required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.idGenerator = Objects.requireNonNull(idGenerator, "Id generator is required");
    }

    @Override
    public ImportReport importDocuments(ImportBatch batch, ImportOptions options) {
        var outcomes = new ArrayList<>(batch.rejections());
        batch.records().forEach(record -> outcomes.add(importOne(record, options)));
        return new ImportReport(batch.source(), options.mode(), options.dryRun(), outcomes);
    }

    private RowOutcome importOne(MappedRecord record, ImportOptions options) {
        var existing = sourceIndex.find(record.sourceRef());
        if (existing.isPresent()) {
            return RowOutcome.alreadyImported(record, existing.get());
        }
        try {
            return options.dryRun() ? plan(record, options) : execute(record, options);
        } catch (SourceRefAlreadyUsedException e) {
            // The unique constraint caught a concurrent import of the same reference: the losing
            // transaction is rolled back, so the record is simply already there.
            return sourceIndex.find(record.sourceRef())
                    .map(documentId -> RowOutcome.alreadyImported(record, documentId))
                    .orElseGet(() -> RowOutcome.rejected(record, "SOURCE_REF_CONFLICT",
                            "Concurrent import of the same reference could not be resolved"));
        } catch (DocumentConflictException e) {
            return RowOutcome.rejected(record, "VERSION_CONFLICT", e.getMessage());
        } catch (DomainException e) {
            return RowOutcome.rejected(record, e.code(), e.getMessage());
        } catch (IllegalArgumentException e) {
            return RowOutcome.rejected(record, "INVALID_DOCUMENT", e.getMessage());
        }
    }

    /** Dry run: the aggregate is pure, so the workflow runs in memory and leaves nothing behind. */
    private RowOutcome plan(MappedRecord record, ImportOptions options) {
        var document = Document.create(new DocumentId(idGenerator.get()), record.templateRef(), record.title(),
                record.sections(), options.actor(), clock.instant());
        var reached = simulate(document, options);
        return RowOutcome.planned(record, reached.status(), reached.version());
    }

    private Document simulate(Document document, ImportOptions options) {
        return switch (options.mode()) {
            case DRAFT -> document;
            case SUBMITTED -> document.submit(options.actor(), clock.instant());
            case APPROVED -> document.submit(options.actor(), clock.instant())
                    .startReview(options.actor(), clock.instant())
                    .approve(options.approver(), clock.instant());
        };
    }

    /**
     * One record, one unit of work: the document, its source note and every transition it goes
     * through commit together. A refusal on the fourth step therefore leaves no half-imported
     * document behind, and the row is reported as refused without touching the rows around it.
     */
    private RowOutcome execute(MappedRecord record, ImportOptions options) {
        return transactions.inTransaction(() -> {
            var created = commands.create(new CreateDocumentCommand(record.templateRef(), record.title(),
                    record.sections(), options.actor()));
            sourceIndex.record(record.sourceRef(), created.id());
            return RowOutcome.imported(record, advance(created, options));
        });
    }

    private DocumentDetail advance(DocumentDetail document, ImportOptions options) {
        if (options.mode() == ImportMode.DRAFT) {
            return document;
        }
        var submitted = commands.submit(statusChange(document, options.actor()));
        if (options.mode() == ImportMode.SUBMITTED) {
            return submitted;
        }
        var inReview = commands.startReview(statusChange(submitted, options.actor()));
        return commands.approve(statusChange(inReview, options.approver()));
    }

    private static ChangeDocumentStatusCommand statusChange(DocumentDetail document, Actor actor) {
        return new ChangeDocumentStatusCommand(document.id(), document.version(), actor);
    }
}
