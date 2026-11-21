package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.importing.ImportBatch;
import fr.walid.acd.contribution.application.importing.ImportMode;
import fr.walid.acd.contribution.application.importing.ImportOptions;
import fr.walid.acd.contribution.application.importing.MappedRecord;
import fr.walid.acd.contribution.application.importing.RowOutcome;
import fr.walid.acd.contribution.application.importing.RowStatus;
import fr.walid.acd.contribution.application.importing.SourceRef;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentImportServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC);
    private static final Actor AUTHOR = new Actor("importateur");

    private final FakeDocumentCommands commands = new FakeDocumentCommands();
    private final FakeDocumentSourceIndex sourceIndex = new FakeDocumentSourceIndex();
    private final FakeTransactions transactions = new FakeTransactions();
    private DocumentImportService service;

    @BeforeEach
    void setUp() {
        var identifiers = new java.util.concurrent.atomic.AtomicInteger();
        service = new DocumentImportService(commands, sourceIndex, transactions, CLOCK,
                () -> UUID.fromString("11111111-0000-0000-0000-%012d".formatted(identifiers.incrementAndGet())));
    }

    @Test
    void createsADraftAndRemembersWhereItCameFrom() {
        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.DRAFT, false));

        assertThat(commands.calls).containsExactly("create");
        assertThat(transactions.calls).isEqualTo(1);
        assertThat(sourceIndex.recorded).containsKey(new SourceRef("annuaire", "DOC-1"));
        var row = report.rows().getFirst();
        assertThat(row.status()).isEqualTo(RowStatus.IMPORTED);
        assertThat(row.documentStatus()).isEqualTo("Draft");
        assertThat(row.version()).isEqualTo(1);
        assertThat(row.documentId()).isEqualTo(sourceIndex.recorded.get(new SourceRef("annuaire", "DOC-1")));
        assertThat(report.count(RowStatus.IMPORTED)).isEqualTo(1);
        assertThat(report.hasRejections()).isFalse();
    }

    @Test
    void submitsTheDocumentWhenTheModeAsksForIt() {
        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.SUBMITTED, false));

        assertThat(commands.calls).containsExactly("create", "submit");
        assertThat(report.rows().getFirst().documentStatus()).isEqualTo("Submitted");
        assertThat(report.rows().getFirst().version()).isEqualTo(2);
    }

    @Test
    void approvesThroughEveryExistingStepWithTheDeclaredApprover() {
        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.APPROVED, false));

        assertThat(commands.calls).containsExactly("create", "submit", "startReview", "approve");
        assertThat(commands.actors).containsExactly(AUTHOR, AUTHOR, AUTHOR, new Actor("relecteur"));
        assertThat(report.rows().getFirst().documentStatus()).isEqualTo("Approved");
        assertThat(report.rows().getFirst().version()).isEqualTo(4);
        // The whole record is one unit of work, so a late refusal cannot leave a half-imported document.
        assertThat(transactions.calls).isEqualTo(1);
    }

    @Test
    void skipsARecordWhoseReferenceIsAlreadyImported() {
        var alreadyStored = new DocumentId(UUID.randomUUID());
        sourceIndex.recorded.put(new SourceRef("annuaire", "DOC-1"), alreadyStored);

        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.APPROVED, false));

        assertThat(commands.calls).isEmpty();
        assertThat(report.rows().getFirst().status()).isEqualTo(RowStatus.ALREADY_IMPORTED);
        assertThat(report.rows().getFirst().documentId()).isEqualTo(alreadyStored);
    }

    @Test
    void reportsADomainRefusalOnOneRowAndKeepsGoing() {
        var invalidTitle = new MappedRecord(1, new SourceRef("annuaire", "DOC-1"), new TemplateRef("policy"),
                "   ", List.of(new Section("Contexte", "Contenu")));

        var report = service.importDocuments(new ImportBatch("annuaire", List.of(invalidTitle, record(2, "DOC-2")),
                List.of()), options(ImportMode.DRAFT, false));

        assertThat(report.rows().getFirst().status()).isEqualTo(RowStatus.REJECTED);
        assertThat(report.rows().getFirst().code()).isEqualTo("INVALID_DOCUMENT");
        assertThat(report.rows().get(1).status()).isEqualTo(RowStatus.IMPORTED);
        assertThat(sourceIndex.recorded).containsOnlyKeys(new SourceRef("annuaire", "DOC-2"));
    }

    @Test
    void reportsTheDomainCodeWhenTheWorkflowRefusesTheRecord() {
        var withoutSections = new MappedRecord(1, new SourceRef("annuaire", "DOC-1"), new TemplateRef("policy"),
                "Titre", List.of());

        var report = service.importDocuments(batch(withoutSections), options(ImportMode.SUBMITTED, false));

        var row = report.rows().getFirst();
        assertThat(row.status()).isEqualTo(RowStatus.REJECTED);
        assertThat(row.code()).isEqualTo("EMPTY_DOCUMENT");
        assertThat(row.detail()).isEqualTo("A submitted document requires non-empty sections");
    }

    @Test
    void reportsAConcurrentModificationAsAVersionConflict() {
        commands.failures.put("submit", new DocumentConflictException(new DocumentId(UUID.randomUUID())));

        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.SUBMITTED, false));

        assertThat(report.rows().getFirst().status()).isEqualTo(RowStatus.REJECTED);
        assertThat(report.rows().getFirst().code()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void reportsAConcurrentDuplicateAsAlreadyImported() {
        var winner = new DocumentId(UUID.randomUUID());
        sourceIndex.conflictOnRecord = true;
        sourceIndex.concurrentWinner = winner;

        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.DRAFT, false));

        assertThat(report.rows().getFirst().status()).isEqualTo(RowStatus.ALREADY_IMPORTED);
        assertThat(report.rows().getFirst().documentId()).isEqualTo(winner);
    }

    @Test
    void reportsAConcurrentDuplicateThatLeftNoTrace() {
        sourceIndex.conflictOnRecord = true;

        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.DRAFT, false));

        assertThat(report.rows().getFirst().status()).isEqualTo(RowStatus.REJECTED);
        assertThat(report.rows().getFirst().code()).isEqualTo("SOURCE_REF_CONFLICT");
    }

    @Test
    void dryRunPlansTheWholeWorkflowWithoutWritingAnything() {
        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.APPROVED, true));

        assertThat(commands.calls).isEmpty();
        assertThat(transactions.calls).isZero();
        assertThat(sourceIndex.recorded).isEmpty();
        var row = report.rows().getFirst();
        assertThat(row.status()).isEqualTo(RowStatus.PLANNED);
        assertThat(row.documentStatus()).isEqualTo("Approved");
        assertThat(row.version()).isEqualTo(4);
        assertThat(report.dryRun()).isTrue();
        assertThat(report.count(RowStatus.PLANNED)).isEqualTo(1);
    }

    @Test
    void dryRunPlansADraftWithoutTouchingTheWorkflow() {
        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.DRAFT, true));

        assertThat(commands.calls).isEmpty();
        assertThat(report.rows().getFirst().documentStatus()).isEqualTo("Draft");
        assertThat(report.rows().getFirst().version()).isEqualTo(1);
    }

    @Test
    void dryRunPlansASubmission() {
        var report = service.importDocuments(batch(record(1, "DOC-1")), options(ImportMode.SUBMITTED, true));

        assertThat(commands.calls).isEmpty();
        assertThat(report.rows().getFirst().documentStatus()).isEqualTo("Submitted");
        assertThat(report.rows().getFirst().version()).isEqualTo(2);
    }

    @Test
    void dryRunStillReportsWhatTheDomainWouldRefuse() {
        var withoutSections = new MappedRecord(1, new SourceRef("annuaire", "DOC-1"), new TemplateRef("policy"),
                "Titre", List.of());

        var report = service.importDocuments(batch(withoutSections), options(ImportMode.SUBMITTED, true));

        assertThat(report.rows().getFirst().status()).isEqualTo(RowStatus.REJECTED);
        assertThat(report.rows().getFirst().code()).isEqualTo("EMPTY_DOCUMENT");
    }

    @Test
    void keepsTheRefusalsTheReaderReportedAndOrdersEverythingByLine() {
        var readerRejection = RowOutcome.rejected(1, null, "MISSING_SOURCE_REF", "Ligne 1 sans référence");

        var report = service.importDocuments(new ImportBatch("annuaire", List.of(record(3, "DOC-3")),
                List.of(readerRejection)), options(ImportMode.DRAFT, true));

        assertThat(report.rows()).extracting(RowOutcome::line).containsExactly(1, 3);
        assertThat(report.count(RowStatus.REJECTED)).isEqualTo(1);
        assertThat(report.count(RowStatus.PLANNED)).isEqualTo(1);
        assertThat(report.hasRejections()).isTrue();
        assertThat(report.source()).isEqualTo("annuaire");
    }

    private static ImportOptions options(ImportMode mode, boolean dryRun) {
        return new ImportOptions(AUTHOR, new Actor("relecteur"), mode, dryRun);
    }

    private static ImportBatch batch(MappedRecord... records) {
        return new ImportBatch("annuaire", List.of(records), List.of());
    }

    private static MappedRecord record(int line, String reference) {
        return new MappedRecord(line, new SourceRef("annuaire", reference), new TemplateRef("policy"),
                "Titre " + reference, List.of(new Section("Contexte", "Contenu " + reference)));
    }
}
