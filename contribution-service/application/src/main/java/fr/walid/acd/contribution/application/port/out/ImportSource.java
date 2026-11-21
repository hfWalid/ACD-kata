package fr.walid.acd.contribution.application.port.out;

import fr.walid.acd.contribution.application.importing.ImportBatch;

/**
 * Reads a file into records the application can command. The mapping itself (columns, transform
 * spec) belongs to the adapter; the application only sees mapped records and row refusals.
 */
public interface ImportSource {

    /**
     * @param source   the import source name, which selects the mapping
     * @param fileName the original file name, which selects the format
     * @param content  the whole file
     */
    ImportBatch read(String source, String fileName, byte[] content);
}
