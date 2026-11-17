package fr.walid.acd.contribution.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Document {
    private final DocumentId id;
    private final TemplateRef templateRef;
    private final String title;
    private final List<Section> sections;
    private final DocumentStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<AuditEntry> auditTrail;

    private Document(DocumentId id, TemplateRef templateRef, String title, List<Section> sections,
                     DocumentStatus status, long version, Instant createdAt, Instant updatedAt,
                     List<AuditEntry> auditTrail) {
        this.id = Objects.requireNonNull(id, "Document id is required");
        this.templateRef = Objects.requireNonNull(templateRef, "Template reference is required");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Document title is required");
        }
        this.title = title.strip();
        if (this.title.length() > 200) {
            throw new IllegalArgumentException("Document title must not exceed 200 characters");
        }
        this.sections = List.copyOf(sections);
        if (this.sections.size() > 50) {
            throw new IllegalArgumentException("A document must not exceed 50 sections");
        }
        this.status = Objects.requireNonNull(status, "Document status is required");
        if (version < 1) {
            throw new IllegalArgumentException("Document version must be positive");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp is required");
        this.auditTrail = List.copyOf(auditTrail);
        validateAudit();
    }

    public static Document create(DocumentId id, TemplateRef templateRef, String title, List<Section> sections,
                                  Actor author, Instant now) {
        return new Document(id, templateRef, title, sections, new DocumentStatus.Draft(), 1, now, now,
                List.of(new AuditEntry(1, DocumentAction.CREATED, author, now)));
    }

    public static Document restore(DocumentId id, TemplateRef templateRef, String title, List<Section> sections,
                                   DocumentStatus status, long version, Instant createdAt, Instant updatedAt,
                                   List<AuditEntry> auditTrail) {
        return new Document(id, templateRef, title, sections, status, version, createdAt, updatedAt, auditTrail);
    }

    public Document updateContent(String title, List<Section> sections, Actor author, Instant now) {
        var nextStatus = DocumentTransition.next(status, DocumentAction.CONTENT_UPDATED);
        return changed(title, sections, nextStatus, DocumentAction.CONTENT_UPDATED, author, now);
    }

    public Document submit(Actor author, Instant now) {
        var nextStatus = DocumentTransition.next(status, DocumentAction.SUBMITTED);
        if (sections.isEmpty() || sections.stream().anyMatch(section -> section.content().isBlank())) {
            throw new DomainException("EMPTY_DOCUMENT", "A submitted document requires non-empty sections");
        }
        return changed(title, sections, nextStatus, DocumentAction.SUBMITTED, author, now);
    }

    public Document startReview(Actor author, Instant now) {
        return transition(DocumentAction.REVIEW_STARTED, author, now);
    }

    public Document approve(Actor author, Instant now) {
        return transition(DocumentAction.APPROVED, author, now);
    }

    public Document reject(Actor author, Instant now) {
        return transition(DocumentAction.REJECTED, author, now);
    }

    public Document publish(Actor author, Instant now) {
        return transition(DocumentAction.PUBLISHED, author, now);
    }

    public Document archive(Actor author, Instant now) {
        return transition(DocumentAction.ARCHIVED, author, now);
    }

    private Document transition(DocumentAction action, Actor author, Instant now) {
        return changed(title, sections, DocumentTransition.next(status, action), action, author, now);
    }

    private Document changed(String nextTitle, List<Section> nextSections, DocumentStatus nextStatus,
                             DocumentAction action, Actor author, Instant now) {
        Objects.requireNonNull(now, "Action timestamp is required");
        if (now.isBefore(updatedAt)) {
            throw new DomainException("NON_MONOTONIC_TIME", "Action timestamp precedes the latest document change");
        }
        long nextVersion = Math.incrementExact(version);
        var audit = new ArrayList<>(auditTrail);
        audit.add(new AuditEntry(nextVersion, action, author, now));
        return new Document(id, templateRef, nextTitle, nextSections, nextStatus, nextVersion, createdAt, now, audit);
    }

    private void validateAudit() {
        if (updatedAt.isBefore(createdAt) || auditTrail.size() != version) {
            throw new IllegalArgumentException("Document timestamps, version and audit are inconsistent");
        }
        if (auditTrail.getFirst().action() != DocumentAction.CREATED
                || !auditTrail.getFirst().occurredAt().equals(createdAt)
                || !auditTrail.getLast().occurredAt().equals(updatedAt)) {
            throw new IllegalArgumentException("Audit must start with creation and match document timestamps");
        }
        Instant previous = createdAt;
        for (int index = 0; index < auditTrail.size(); index++) {
            var entry = auditTrail.get(index);
            if (entry.version() != index + 1L || entry.occurredAt().isBefore(previous)) {
                throw new IllegalArgumentException("Audit versions and timestamps must be ordered");
            }
            previous = entry.occurredAt();
        }
    }

    public DocumentId id() { return id; }
    public TemplateRef templateRef() { return templateRef; }
    public String title() { return title; }
    public List<Section> sections() { return sections; }
    public DocumentStatus status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<AuditEntry> auditTrail() { return auditTrail; }
}
