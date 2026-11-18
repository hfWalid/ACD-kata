package fr.walid.acd.contribution.application.query;

import fr.walid.acd.contribution.domain.AuditEntry;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Read model returned by the detail query. It exposes what a reader needs and nothing that could
 * change the document: the aggregate, with its invariants and its transitions, stays inside the
 * application and persistence layers.
 */
public record DocumentDetail(
        DocumentId id,
        TemplateRef templateRef,
        String title,
        List<Section> sections,
        DocumentStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<AuditEntry> auditTrail) {

    public DocumentDetail {
        Objects.requireNonNull(id, "Document id is required");
        Objects.requireNonNull(templateRef, "Template reference is required");
        Objects.requireNonNull(title, "Title is required");
        sections = List.copyOf(Objects.requireNonNull(sections, "Sections are required"));
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(createdAt, "Creation timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        auditTrail = List.copyOf(Objects.requireNonNull(auditTrail, "Audit trail is required"));
    }

    public static DocumentDetail from(Document document) {
        return new DocumentDetail(document.id(), document.templateRef(), document.title(), document.sections(),
                document.status(), document.version(), document.createdAt(), document.updatedAt(),
                document.auditTrail());
    }
}
