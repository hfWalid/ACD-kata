package fr.walid.acd.contribution.application.importing;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportTypesTest {
    private static final SourceRef REF = new SourceRef("annuaire", "DOC-1");
    private static final MappedRecord RECORD = new MappedRecord(2, REF, new TemplateRef("policy"), "Titre",
            List.of(new Section("Contexte", "Contenu")));

    @Test
    void aBatchRequiresItsSourceAndItsRows() {
        assertThatThrownBy(() -> new ImportBatch(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Import source is required");
        assertThatThrownBy(() -> new ImportBatch("annuaire", null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Records are required");
        assertThatThrownBy(() -> new ImportBatch("annuaire", List.of(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("Rejections are required");

        assertThat(new ImportBatch("annuaire", List.of(RECORD), List.of()).records()).containsExactly(RECORD);
    }

    @Test
    void optionsRequireAnActorAnApproverAndAMode() {
        var actor = new Actor("importateur");

        assertThatThrownBy(() -> new ImportOptions(null, actor, ImportMode.DRAFT, false))
                .isInstanceOf(NullPointerException.class).hasMessage("Import actor is required");
        assertThatThrownBy(() -> new ImportOptions(actor, null, ImportMode.DRAFT, false))
                .isInstanceOf(NullPointerException.class).hasMessage("Import approver is required");
        assertThatThrownBy(() -> new ImportOptions(actor, actor, null, false))
                .isInstanceOf(NullPointerException.class).hasMessage("Import mode is required");

        assertThat(new ImportOptions(actor, actor, ImportMode.DRAFT, true).dryRun()).isTrue();
    }

    @Test
    void aReportRequiresASourceAModeAndRows() {
        assertThatThrownBy(() -> new ImportReport(null, ImportMode.DRAFT, false, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Import source is required");
        assertThatThrownBy(() -> new ImportReport("annuaire", null, false, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Import mode is required");
        assertThatThrownBy(() -> new ImportReport("annuaire", ImportMode.DRAFT, false, null))
                .isInstanceOf(NullPointerException.class).hasMessage("Rows are required");
    }

    @Test
    void aReportIsOrderedByFileLine() {
        var rejectedFirst = RowOutcome.rejected(1, null, "MISSING_SOURCE_REF", "absent");
        var importedSecond = RowOutcome.imported(RECORD,
                new fr.walid.acd.contribution.application.query.DocumentDetail(new DocumentId(UUID.randomUUID()),
                        new TemplateRef("policy"), "Titre", List.of(new Section("Contexte", "Contenu")),
                        new fr.walid.acd.contribution.domain.DocumentStatus.Draft(), 1,
                        java.time.Instant.EPOCH, java.time.Instant.EPOCH, List.of()));

        var report = new ImportReport("annuaire", ImportMode.DRAFT, false, List.of(importedSecond, rejectedFirst));

        assertThat(report.rows()).extracting(RowOutcome::line).containsExactly(1, 2);
        assertThat(report.count(RowStatus.REJECTED)).isEqualTo(1);
        assertThat(report.hasRejections()).isTrue();
    }

    @Test
    void outcomesDescribeWhatHappened() {
        var rejected = RowOutcome.rejected(3, "DOC-3", "INVALID_DOCUMENT", "titre vide");
        var alreadyImported = RowOutcome.alreadyImported(RECORD, new DocumentId(UUID.randomUUID()));

        assertThat(rejected.reference()).isEqualTo("DOC-3");
        assertThat(rejected.code()).isEqualTo("INVALID_DOCUMENT");
        assertThat(alreadyImported.status()).isEqualTo(RowStatus.ALREADY_IMPORTED);
        assertThat(alreadyImported.documentId()).isNotNull();
        assertThat(RowOutcome.rejected(RECORD, "EMPTY_DOCUMENT", "vide").line()).isEqualTo(2);
    }
}
