package fr.walid.acd.contribution.adapters.importing;

import com.bazaarvoice.jolt.exception.SpecException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.exception.ImportBatchRejectedException;
import fr.walid.acd.contribution.application.importing.ImportBatch;
import fr.walid.acd.contribution.application.importing.MappedRecord;
import fr.walid.acd.contribution.application.importing.RowOutcome;
import fr.walid.acd.contribution.application.importing.SourceRef;
import fr.walid.acd.contribution.application.port.out.ImportSource;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a CSV or JSON file and maps it with the Jolt spec stored for its source.
 *
 * <p>The mapping refuses nothing on business grounds: a blank title, an empty document or a
 * forbidden transition is the domain's call, and its code comes back in the report untouched. What
 * this class refuses are structural problems: no spec for the source, an unknown format, a row
 * that cannot be a document at all.
 */
public final class JoltImportSource implements ImportSource {
    private static final String SPEC_FILE = "mapping.jolt.json";

    private final Path specsDirectory;
    private final int maxRows;
    private final ObjectMapper json = new ObjectMapper();
    private final CsvRows csvRows = new CsvRows();
    private final RecordGrouper grouper = new RecordGrouper();

    public JoltImportSource(Path specsDirectory, int maxRows) {
        if (maxRows < 1) {
            throw new IllegalArgumentException("Maximum rows must be positive");
        }
        this.specsDirectory = specsDirectory;
        this.maxRows = maxRows;
    }

    @Override
    public ImportBatch read(String source, String fileName, byte[] content) {
        var mapping = mappingFor(source);
        var grouped = switch (formatOf(fileName)) {
            case CSV -> grouper.group(csvRows.read(content));
            case JSON -> jsonDocuments(content);
        };
        if (grouped.documents().size() > maxRows) {
            throw new ImportBatchRejectedException("ROW_LIMIT_EXCEEDED",
                    "Le fichier contient " + grouped.documents().size() + " documents, au-delà de la limite de "
                            + maxRows);
        }
        var records = new ArrayList<MappedRecord>();
        var rejections = new ArrayList<>(grouped.rejections());
        grouped.documents().forEach(document -> map(document, source, mapping, records, rejections));
        return new ImportBatch(source, records, rejections);
    }

    private JoltMapping mappingFor(String source) {
        var specFile = specsDirectory.resolve(source).resolve(SPEC_FILE);
        if (!Files.isRegularFile(specFile)) {
            throw new ImportBatchRejectedException("UNKNOWN_SOURCE", "Source d'import inconnue : " + source);
        }
        try {
            return JoltMapping.from(json.readValue(specFile.toFile(), Object.class));
        } catch (IOException | SpecException e) {
            throw new ImportBatchRejectedException("INVALID_MAPPING",
                    "Spécification de mapping illisible pour " + source + " : " + e.getMessage());
        }
    }

    private static Format formatOf(String fileName) {
        var name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return Format.CSV;
        }
        if (name.endsWith(".json")) {
            return Format.JSON;
        }
        throw new ImportBatchRejectedException("UNSUPPORTED_FORMAT", "Format non pris en charge : " + fileName);
    }

    private GroupedDocuments jsonDocuments(byte[] content) {
        Object parsed;
        try {
            parsed = json.readValue(content, Object.class);
        } catch (IOException e) {
            throw new ImportBatchRejectedException("UNREADABLE_FILE", "JSON illisible : " + e.getMessage());
        }
        if (parsed instanceof List<?> elements) {
            return asDocuments(elements);
        }
        if (parsed instanceof Map<?, ?> object) {
            return asDocuments(List.of(object));
        }
        throw new ImportBatchRejectedException("UNSUPPORTED_FORMAT",
                "Un fichier JSON d'import contient un objet ou une liste d'objets");
    }

    private static GroupedDocuments asDocuments(List<?> elements) {
        var documents = new ArrayList<GroupedDocument>();
        var rejections = new ArrayList<RowOutcome>();
        for (var index = 0; index < elements.size(); index++) {
            var line = index + 1;
            if (elements.get(index) instanceof Map<?, ?> object) {
                var copy = new LinkedHashMap<String, Object>();
                object.forEach((key, value) -> copy.put(String.valueOf(key), value));
                documents.add(new GroupedDocument(line, copy));
            } else {
                rejections.add(RowOutcome.rejected(line, null, "MAPPING_FAILED",
                        "Objet JSON attendu à la ligne " + line));
            }
        }
        return new GroupedDocuments(documents, rejections);
    }

    private static void map(GroupedDocument grouped, String source, JoltMapping mapping,
                            List<MappedRecord> records, List<RowOutcome> rejections) {
        var mapped = mapping.apply(grouped.document());
        if (!(mapped instanceof Map<?, ?> values)) {
            rejections.add(RowOutcome.rejected(grouped.line(), null, "MAPPING_FAILED",
                    "Le mapping n'a pas produit d'objet pour la ligne " + grouped.line()));
            return;
        }
        var reference = text(values.get("sourceRef"));
        var templateRef = text(values.get("templateRef"));
        if (reference.isBlank()) {
            rejections.add(RowOutcome.rejected(grouped.line(), null, "MISSING_SOURCE_REF",
                    "Ligne " + grouped.line() + " sans référence"));
            return;
        }
        if (templateRef.isBlank()) {
            rejections.add(RowOutcome.rejected(grouped.line(), reference, "MAPPING_FAILED",
                    "Gabarit absent pour la ligne " + grouped.line()));
            return;
        }
        try {
            records.add(new MappedRecord(grouped.line(), new SourceRef(source, reference),
                    new TemplateRef(templateRef), text(values.get("title")), sections(values.get("sections"))));
        } catch (IllegalArgumentException e) {
            rejections.add(RowOutcome.rejected(grouped.line(), reference, "MAPPING_FAILED", e.getMessage()));
        }
    }

    private static List<Section> sections(Object value) {
        if (!(value instanceof List<?> elements)) {
            return List.of();
        }
        var sections = new ArrayList<Section>();
        for (var element : elements) {
            if (element instanceof Map<?, ?> section) {
                sections.add(new Section(text(section.get("title")), text(section.get("content"))));
            }
        }
        return sections;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private enum Format {
        CSV,
        JSON
    }
}
