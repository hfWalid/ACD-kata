package fr.walid.acd.contribution.application.query;

import java.util.List;

public record DocumentPage(long rowCount, List<DocumentSummary> rows) {
    public DocumentPage {
        rows = List.copyOf(rows);
        if (rowCount < rows.size()) {
            throw new IllegalArgumentException("Row count cannot be negative or smaller than the returned rows");
        }
    }
}
