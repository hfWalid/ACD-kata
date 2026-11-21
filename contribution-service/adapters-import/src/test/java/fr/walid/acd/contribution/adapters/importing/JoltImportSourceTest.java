package fr.walid.acd.contribution.adapters.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.exception.ImportBatchRejectedException;
import fr.walid.acd.contribution.application.importing.ImportBatch;
import fr.walid.acd.contribution.application.importing.RowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoltImportSourceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path specs;

    @Test
    void mapsTheSampleFileToTheExpectedRecords() {
        var source = new JoltImportSource(contracts(), 100);

        var batch = source.read("annuaire", "sample.csv", bytes(contracts().resolve("annuaire/sample.csv")));

        assertThat(batch.records()).hasSize(2);
        assertThat(batch.rejections()).isEmpty();
        assertThat(asMaps(batch)).isEqualTo(expected("annuaire/expected.json"));
    }

    @Test
    void mapsAJsonFileWithTheSameSpec() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);
        var json = """
                [
                  {"reference": "DOC-1", "titre": "Titre 1", "gabarit": "policy",
                   "sections": [{"titre": "Contexte", "contenu": "Un"}]},
                  {"reference": "DOC-2", "titre": "Titre 2", "gabarit": "plan",
                   "sections": [{"titre": "Contexte", "contenu": "Deux"}]}
                ]
                """;

        var batch = source.read("annuaire", "annuaire.json", json.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).extracting(record -> record.sourceRef().value()).containsExactly("DOC-1", "DOC-2");
        assertThat(batch.records()).extracting(record -> record.line()).containsExactly(1, 2);
        assertThat(batch.records().getFirst().sections()).hasSize(1);
        assertThat(batch.records().getFirst().sections().getFirst().title()).isEqualTo("Contexte");
    }

    @Test
    void refusesAJsonFileThatIsNotAnObjectOrAList() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);

        assertThatThrownBy(() -> source.read("annuaire", "annuaire.json", "42".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ImportBatchRejectedException.class)
                .hasMessage("Un fichier JSON d'import contient un objet ou une liste d'objets");
    }

    @Test
    void reportsAJsonElementThatIsNotAnObject() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);
        var json = """
                [{"reference": "DOC-1", "titre": "Titre", "gabarit": "policy", "sections": []}, 42]
                """;

        var batch = source.read("annuaire", "annuaire.json", json.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).hasSize(1);
        assertThat(batch.rejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.line()).isEqualTo(2);
            assertThat(rejection.code()).isEqualTo("MAPPING_FAILED");
        });
    }

    @Test
    void refusesAnUnreadableJsonFile() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);

        assertThatThrownBy(() -> source.read("annuaire", "annuaire.json", "{ pas du json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ImportBatchRejectedException.class)
                .satisfies(exception -> assertThat(((ImportBatchRejectedException) exception).code())
                        .isEqualTo("UNREADABLE_FILE"));
    }

    @Test
    void reportsARowWithoutReference() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);
        var csv = """
                reference;titre;gabarit;section_titre;section_contenu
                ;Sans référence;policy;Contexte;Contenu
                DOC-2;Avec référence;policy;Contexte;Contenu
                """;

        var batch = source.read("annuaire", "annuaire.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).hasSize(1);
        assertThat(batch.rejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.line()).isEqualTo(2);
            assertThat(rejection.status()).isEqualTo(RowStatus.REJECTED);
            assertThat(rejection.code()).isEqualTo("MISSING_SOURCE_REF");
        });
    }

    @Test
    void reportsADocumentWhoseGabaritTheSpecDoesNotMap() {
        writeSpec("annuaire", """
                [{"operation": "shift", "spec": {"reference": "sourceRef", "titre": "title"}}]
                """.getBytes(StandardCharsets.UTF_8));
        var source = new JoltImportSource(specs, 100);
        var csv = """
                reference;titre
                DOC-1;Titre
                """;

        var batch = source.read("annuaire", "annuaire.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).isEmpty();
        assertThat(batch.rejections()).singleElement()
                .satisfies(rejection -> assertThat(rejection.code()).isEqualTo("MAPPING_FAILED"));
    }

    @Test
    void reportsAReferenceTheDomainWouldRefuse() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);
        var csv = """
                reference;titre;gabarit;section_titre;section_contenu
                %s;Titre;policy;Contexte;Contenu
                """.formatted("R".repeat(201));

        var batch = source.read("annuaire", "annuaire.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).isEmpty();
        assertThat(batch.rejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.code()).isEqualTo("MAPPING_FAILED");
            assertThat(rejection.detail()).isEqualTo("Import reference must not exceed 200 characters");
        });
    }

    @Test
    void stripsTheSpreadsheetByteOrderMark() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);
        var csv = new String(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, StandardCharsets.UTF_8)
                + """
                reference;titre;gabarit;section_titre;section_contenu
                DOC-1;Titre;policy;Contexte;Contenu
                """;

        var batch = source.read("annuaire", "annuaire.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).singleElement().satisfies(record ->
                assertThat(record.sourceRef().value()).isEqualTo("DOC-1"));
    }

    @Test
    void ignoresARowThatFillsNoSectionColumn() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);
        var csv = """
                reference;titre;gabarit;section_titre;section_contenu
                DOC-1;Titre;policy;Contexte;Contenu
                DOC-1;Titre;policy;;
                """;

        var batch = source.read("annuaire", "annuaire.csv", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(batch.records()).singleElement().satisfies(record ->
                assertThat(record.sections()).hasSize(1));
    }

    @Test
    void refusesAnUnknownSource() {
        var source = new JoltImportSource(specs, 100);

        assertThatThrownBy(() -> source.read("inconnue", "fichier.csv", new byte[0]))
                .isInstanceOf(ImportBatchRejectedException.class)
                .satisfies(exception -> assertThat(((ImportBatchRejectedException) exception).code())
                        .isEqualTo("UNKNOWN_SOURCE"));
    }

    @Test
    void refusesAnUnreadableSpec() {
        writeSpec("annuaire", "{ pas un tableau }".getBytes(StandardCharsets.UTF_8));
        var source = new JoltImportSource(specs, 100);

        assertThatThrownBy(() -> source.read("annuaire", "annuaire.csv", new byte[0]))
                .isInstanceOf(ImportBatchRejectedException.class)
                .satisfies(exception -> assertThat(((ImportBatchRejectedException) exception).code())
                        .isEqualTo("INVALID_MAPPING"));
    }

    @Test
    void refusesAnUnsupportedFormat() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 100);

        assertThatThrownBy(() -> source.read("annuaire", "annuaire.xlsx", new byte[0]))
                .isInstanceOf(ImportBatchRejectedException.class)
                .satisfies(exception -> assertThat(((ImportBatchRejectedException) exception).code())
                        .isEqualTo("UNSUPPORTED_FORMAT"));
        assertThatThrownBy(() -> source.read("annuaire", null, new byte[0]))
                .isInstanceOf(ImportBatchRejectedException.class)
                .hasMessage("Format non pris en charge : null");
    }

    @Test
    void refusesMoreDocumentsThanTheLimit() {
        writeSpec("annuaire", ImportFixtures.specBytes());
        var source = new JoltImportSource(specs, 1);
        var csv = """
                reference;titre;gabarit;section_titre;section_contenu
                DOC-1;Un;policy;Contexte;Contenu
                DOC-2;Deux;policy;Contexte;Contenu
                """;

        assertThatThrownBy(() -> source.read("annuaire", "annuaire.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ImportBatchRejectedException.class)
                .hasMessageContaining("au-delà de la limite de 1");
    }

    @Test
    void refusesALimitThatCannotWork() {
        assertThatThrownBy(() -> new JoltImportSource(specs, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Maximum rows must be positive");
    }

    private void writeSpec(String source, byte[] spec) {
        try {
            var directory = specs.resolve(source);
            Files.createDirectories(directory);
            Files.write(directory.resolve("mapping.jolt.json"), spec);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path contracts() {
        return repositoryRoot().resolve("contracts/import");
    }

    private static Path repositoryRoot() {
        var current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("contracts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return current;
    }

    private static byte[] bytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Map<String, Object>> expected(String relativePath) {
        try {
            return JSON.readValue(bytes(contracts().resolve(relativePath)), new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Map<String, Object>> asMaps(ImportBatch batch) {
        return batch.records().stream().map(record -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("line", record.line());
            map.put("sourceRef", record.sourceRef().value());
            map.put("templateRef", record.templateRef().value());
            map.put("title", record.title());
            map.put("sections", ImportFixtures.asMaps(record.sections()));
            return map;
        }).toList();
    }
}
