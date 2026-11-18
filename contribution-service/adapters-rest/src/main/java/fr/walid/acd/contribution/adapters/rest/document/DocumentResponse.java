package fr.walid.acd.contribution.adapters.rest.document;

import fr.walid.acd.contribution.application.query.DocumentDetail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String templateRef,
        String title,
        List<SectionResponse> sections,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<AuditEntryResponse> auditTrail) {

    static DocumentResponse from(DocumentDetail document) {
        return new DocumentResponse(document.id().value(), document.templateRef().value(), document.title(),
                document.sections().stream()
                        .map(section -> new SectionResponse(section.title(), section.content())).toList(),
                document.status().name(), document.version(), document.createdAt(), document.updatedAt(),
                document.auditTrail().stream()
                        .map(entry -> new AuditEntryResponse(entry.version(), entry.action().name(),
                                entry.author().value(), entry.occurredAt())).toList());
    }

    public record SectionResponse(String title, String content) {
    }

    public record AuditEntryResponse(long version, String action, String author, Instant occurredAt) {
    }
}
