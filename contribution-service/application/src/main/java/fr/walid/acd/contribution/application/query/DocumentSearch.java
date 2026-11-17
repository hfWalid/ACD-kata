package fr.walid.acd.contribution.application.query;

import fr.walid.acd.contribution.domain.DocumentStatus;

import java.util.Objects;
import java.util.Optional;

public record DocumentSearch(int startRow, int endRow, DocumentSort sort, Optional<DocumentStatus> statusFilter, String query) {
    public DocumentSearch {
        if (startRow < 0 || startRow > 1_000_000 || endRow <= startRow || endRow - startRow > 500) {
            throw new IllegalArgumentException("Row range requires 0 <= startRow <= 1000000 and a window of 1 to 500 rows");
        }
        Objects.requireNonNull(sort, "Sort is required");
        Objects.requireNonNull(statusFilter, "Status filter is required");
        query = query == null ? "" : query.strip();
        if (query.length() > 200) {
            throw new IllegalArgumentException("Search query must not exceed 200 characters");
        }
    }
}
