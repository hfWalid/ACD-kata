package fr.walid.acd.contribution.application.exception;

import fr.walid.acd.contribution.application.importing.SourceRef;

/**
 * Raised when two imports race on the same source reference. The database unique constraint is the
 * arbiter, so the losing import can report the record as already imported instead of failing.
 */
public final class SourceRefAlreadyUsedException extends RuntimeException {

    private final transient SourceRef sourceRef;

    public SourceRefAlreadyUsedException(SourceRef sourceRef) {
        super("Source reference already imported: " + sourceRef.source() + "/" + sourceRef.value());
        this.sourceRef = sourceRef;
    }

    public SourceRef sourceRef() {
        return sourceRef;
    }
}
