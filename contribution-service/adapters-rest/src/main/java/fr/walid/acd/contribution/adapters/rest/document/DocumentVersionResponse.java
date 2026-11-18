package fr.walid.acd.contribution.adapters.rest.document;

import fr.walid.acd.contribution.application.query.DocumentVersion;

import java.time.Instant;
import java.util.List;

public record DocumentVersionResponse(
        long version,
        String action,
        String author,
        Instant occurredAt,
        String title,
        String status,
        List<DocumentResponse.SectionResponse> sections) {

    static DocumentVersionResponse from(DocumentVersion version) {
        return new DocumentVersionResponse(version.version(), version.entry().action().name(),
                version.entry().author().value(), version.entry().occurredAt(), version.title(),
                version.status().name(),
                version.sections().stream()
                        .map(section -> new DocumentResponse.SectionResponse(section.title(), section.content()))
                        .toList());
    }
}
