package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.AuditEntry;
import fr.walid.acd.contribution.domain.DocumentAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_audit")
class DocumentAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "business_version", nullable = false)
    private long businessVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentAction action;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected DocumentAuditEntity() {
    }

    DocumentAuditEntity(DocumentEntity document, AuditEntry entry) {
        this.document = document;
        businessVersion = entry.version();
        action = entry.action();
        author = entry.author().value();
        occurredAt = entry.occurredAt();
    }

    AuditEntry toDomain() {
        return new AuditEntry(businessVersion, action, new Actor(author), occurredAt);
    }
}
