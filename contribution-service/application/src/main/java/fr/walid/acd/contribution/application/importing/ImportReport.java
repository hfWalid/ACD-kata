package fr.walid.acd.contribution.application.importing;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One file's outcome, ordered by file line: what was written, what was already there, what was
 * refused and why.
 */
public record ImportReport(String source, ImportMode mode, boolean dryRun, List<RowOutcome> rows) {

    public ImportReport {
        Objects.requireNonNull(source, "Import source is required");
        Objects.requireNonNull(mode, "Import mode is required");
        rows = List.copyOf(Objects.requireNonNull(rows, "Rows are required")).stream()
                .sorted(Comparator.comparingInt(RowOutcome::line))
                .toList();
    }

    public long count(RowStatus status) {
        return rows.stream().filter(row -> row.status() == status).count();
    }

    public boolean hasRejections() {
        return count(RowStatus.REJECTED) > 0;
    }
}
