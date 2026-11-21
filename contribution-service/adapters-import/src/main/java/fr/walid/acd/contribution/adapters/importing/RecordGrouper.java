package fr.walid.acd.contribution.adapters.importing;

import fr.walid.acd.contribution.application.importing.RowOutcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A CSV row carries one section, so several rows describe one document. Jolt shapes a single
 * object and cannot group rows, so the grouping happens here, before the mapping: it is the only
 * step of the chain that needs to know the file layout rather than the target shape.
 */
final class RecordGrouper {
    private static final int FIRST_DATA_LINE = 2;

    GroupedDocuments group(List<Map<String, String>> rows) {
        var documents = new LinkedHashMap<String, DocumentBuilder>();
        var rejections = new ArrayList<RowOutcome>();
        var line = FIRST_DATA_LINE;
        for (var row : rows) {
            var currentLine = line;
            var reference = value(row, "reference");
            if (reference.isBlank()) {
                rejections.add(RowOutcome.rejected(currentLine, null, "MISSING_SOURCE_REF",
                        "Ligne " + currentLine + " sans référence"));
            } else {
                var document = documents.computeIfAbsent(reference,
                        key -> new DocumentBuilder(currentLine, key, value(row, "titre"), value(row, "gabarit")));
                document.addSection(value(row, "section_titre"), value(row, "section_contenu"));
            }
            line++;
        }
        return new GroupedDocuments(documents.values().stream().map(DocumentBuilder::toDocument).toList(), rejections);
    }

    private static String value(Map<String, String> row, String column) {
        var value = row.get(column);
        return value == null ? "" : value;
    }

    private static final class DocumentBuilder {
        private final int line;
        private final String reference;
        private final String title;
        private final String templateRef;
        private final List<Map<String, Object>> sections = new ArrayList<>();

        private DocumentBuilder(int line, String reference, String title, String templateRef) {
            this.line = line;
            this.reference = reference;
            this.title = title;
            this.templateRef = templateRef;
        }

        private void addSection(String sectionTitle, String sectionContent) {
            // A row that fills neither section column adds nothing: it is line noise, not an empty section.
            if (sectionTitle.isBlank() && sectionContent.isBlank()) {
                return;
            }
            var section = new LinkedHashMap<String, Object>();
            section.put("titre", sectionTitle);
            section.put("contenu", sectionContent);
            sections.add(section);
        }

        private GroupedDocument toDocument() {
            var document = new LinkedHashMap<String, Object>();
            document.put("reference", reference);
            document.put("titre", title);
            document.put("gabarit", templateRef);
            document.put("sections", sections);
            return new GroupedDocument(line, document);
        }
    }
}
