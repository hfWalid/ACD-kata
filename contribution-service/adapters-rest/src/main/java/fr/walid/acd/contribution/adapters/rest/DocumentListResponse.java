package fr.walid.acd.contribution.adapters.rest;

import fr.walid.acd.contribution.application.query.DocumentPage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentListResponse(long rowCount, List<DocumentSummaryResponse> rows) {
    static DocumentListResponse from(DocumentPage page) {
        return new DocumentListResponse(page.rowCount(), page.rows().stream()
                .map(row -> new DocumentSummaryResponse(row.id().value(), row.title(), row.status().name(),
                        row.version(), row.updatedAt())).toList());
    }

    public record DocumentSummaryResponse(UUID id, String title, String status, long version, Instant updatedAt) {
    }
}
