package fr.walid.acd.contribution.adapters.rest.importing;

import io.swagger.v3.oas.annotations.media.Schema;
import fr.walid.acd.contribution.application.importing.RowOutcome;

/** One line of an import report, as the client reads it back. */
@Schema(description = "Outcome of one file record")
public record ImportRowResponse(
        int line,
        String reference,
        String status,
        String documentId,
        long version,
        String documentStatus,
        String code,
        String detail) {

    static ImportRowResponse from(RowOutcome row) {
        return new ImportRowResponse(row.line(), row.reference(), row.status().name(),
                row.documentId() == null ? null : row.documentId().value().toString(),
                row.version(), row.documentStatus(), row.code(), row.detail());
    }
}
