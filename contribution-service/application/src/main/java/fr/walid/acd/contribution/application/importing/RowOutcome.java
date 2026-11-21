package fr.walid.acd.contribution.application.importing;

import fr.walid.acd.contribution.application.query.DocumentDetail;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;

/**
 * The result of one file record. It carries the identifiers a reader needs to find the document
 * again, and the domain code when the record was refused: the import never invents its own
 * vocabulary for a business refusal.
 */
public record RowOutcome(
        int line,
        String reference,
        RowStatus status,
        DocumentId documentId,
        long version,
        String documentStatus,
        String code,
        String detail) {

    public static RowOutcome imported(MappedRecord record, DocumentDetail detail) {
        return new RowOutcome(record.line(), record.sourceRef().value(), RowStatus.IMPORTED, detail.id(),
                detail.version(), detail.status().name(), null, null);
    }

    public static RowOutcome planned(MappedRecord record, DocumentStatus status, long version) {
        return new RowOutcome(record.line(), record.sourceRef().value(), RowStatus.PLANNED, null,
                version, status.name(), null, null);
    }

    public static RowOutcome alreadyImported(MappedRecord record, DocumentId documentId) {
        return new RowOutcome(record.line(), record.sourceRef().value(), RowStatus.ALREADY_IMPORTED, documentId,
                0, null, null, null);
    }

    public static RowOutcome rejected(int line, String reference, String code, String detail) {
        return new RowOutcome(line, reference, RowStatus.REJECTED, null, 0, null, code, detail);
    }

    public static RowOutcome rejected(MappedRecord record, String code, String detail) {
        return rejected(record.line(), record.sourceRef().value(), code, detail);
    }
}
