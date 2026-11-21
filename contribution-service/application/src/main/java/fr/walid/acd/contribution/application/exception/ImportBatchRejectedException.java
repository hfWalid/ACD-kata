package fr.walid.acd.contribution.application.exception;

/**
 * A file the import cannot even start from: unknown source, unsupported format, too many rows.
 * Row-level refusals are reported in the import report instead; this one stops the request.
 */
public final class ImportBatchRejectedException extends RuntimeException {

    private final String code;

    public ImportBatchRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
