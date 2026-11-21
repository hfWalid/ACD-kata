package fr.walid.acd.contribution.application.importing;

/** What happened to one file record. */
public enum RowStatus {
    /** The document was created, and moved through the workflow when the mode asks for it. */
    IMPORTED,
    /** A dry run: nothing was written, the document would have been created. */
    PLANNED,
    /** The source reference is already stored, so the record was skipped. */
    ALREADY_IMPORTED,
    /** The record was refused; the code says why and nothing was written for it. */
    REJECTED
}
