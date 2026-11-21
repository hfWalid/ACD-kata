package fr.walid.acd.contribution.adapters.rest.importing;

import fr.walid.acd.contribution.application.importing.ImportReport;
import fr.walid.acd.contribution.application.importing.RowStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** What one file import produced: one row per file record, plus the counts a client displays. */
@Schema(description = "Import report, one row per file record")
public record ImportReportResponse(
        String source,
        String mode,
        boolean dryRun,
        long imported,
        long planned,
        long alreadyImported,
        long rejected,
        List<ImportRowResponse> rows) {

    static ImportReportResponse from(ImportReport report) {
        return new ImportReportResponse(report.source(), report.mode().name(), report.dryRun(),
                report.count(RowStatus.IMPORTED), report.count(RowStatus.PLANNED),
                report.count(RowStatus.ALREADY_IMPORTED), report.count(RowStatus.REJECTED),
                report.rows().stream().map(ImportRowResponse::from).toList());
    }
}
