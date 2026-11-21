package fr.walid.acd.contribution.application.importing;

import java.util.List;
import java.util.Objects;

/**
 * What the file reader extracted: the records it could map, and the rows it could not, already
 * refused with a code. A structural problem on one row must not cost the whole file.
 */
public record ImportBatch(String source, List<MappedRecord> records, List<RowOutcome> rejections) {

    public ImportBatch {
        Objects.requireNonNull(source, "Import source is required");
        records = List.copyOf(Objects.requireNonNull(records, "Records are required"));
        rejections = List.copyOf(Objects.requireNonNull(rejections, "Rejections are required"));
    }
}
