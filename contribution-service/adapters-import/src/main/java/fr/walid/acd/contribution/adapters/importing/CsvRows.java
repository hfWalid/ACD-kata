package fr.walid.acd.contribution.adapters.importing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fr.walid.acd.contribution.application.exception.ImportBatchRejectedException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Reads a semicolon separated file with a header line into one map per data row. The map keeps the
 * raw text: deciding what a column means is the mapping's job, not the reader's.
 */
final class CsvRows {
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final CsvMapper MAPPER = new CsvMapper();
    private static final CsvSchema SCHEMA = CsvSchema.emptySchema().withColumnSeparator(';').withHeader();

    List<Map<String, String>> read(byte[] content) {
        try (MappingIterator<Map<String, String>> rows = MAPPER
                .readerFor(new TypeReference<Map<String, String>>() {})
                .with(SCHEMA)
                .readValues(withoutBom(content))) {
            return rows.readAll();
        } catch (IOException e) {
            throw new ImportBatchRejectedException("UNREADABLE_FILE", "CSV illisible : " + e.getMessage());
        }
    }

    /** A spreadsheet export usually starts with a byte order mark, which would corrupt the first header. */
    private static byte[] withoutBom(byte[] content) {
        if (content.length >= UTF8_BOM.length
                && content[0] == UTF8_BOM[0] && content[1] == UTF8_BOM[1] && content[2] == UTF8_BOM[2]) {
            return java.util.Arrays.copyOfRange(content, UTF8_BOM.length, content.length);
        }
        return content;
    }
}
