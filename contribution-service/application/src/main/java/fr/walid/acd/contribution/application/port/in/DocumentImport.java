package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.application.importing.ImportBatch;
import fr.walid.acd.contribution.application.importing.ImportOptions;
import fr.walid.acd.contribution.application.importing.ImportReport;

/**
 * Import use case: run already mapped file records through the existing commands, one record at a
 * time, and report what each of them became.
 */
public interface DocumentImport {
    ImportReport importDocuments(ImportBatch batch, ImportOptions options);
}
