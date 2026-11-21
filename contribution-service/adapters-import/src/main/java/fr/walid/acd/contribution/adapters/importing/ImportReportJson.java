package fr.walid.acd.contribution.adapters.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.walid.acd.contribution.application.importing.ImportReport;

/** Writes the import report as JSON, so a command line run leaves an auditable trace on disk. */
public final class ImportReportJson {
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String write(ImportReport report) {
        try {
            return json.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Import report cannot be serialized", e);
        }
    }
}
