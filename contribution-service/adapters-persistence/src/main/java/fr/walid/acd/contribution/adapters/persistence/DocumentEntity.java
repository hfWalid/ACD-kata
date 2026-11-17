package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.TemplateRef;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents")
class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "template_ref", nullable = false, length = 100)
    private String templateRef;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "business_version", nullable = false)
    private long businessVersion;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<DocumentSectionEntity> sections = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.PERSIST)
    @OrderBy("businessVersion ASC")
    private List<DocumentAuditEntity> audit = new ArrayList<>();

    protected DocumentEntity() {
    }

    static DocumentEntity from(Document document) {
        var entity = new DocumentEntity();
        entity.id = document.id().value();
        entity.templateRef = document.templateRef().value();
        entity.createdAt = document.createdAt();
        entity.advanceHeader(document);
        entity.applyContentAndAudit(document);
        return entity;
    }

    Document toDomain() {
        return Document.restore(new DocumentId(id), new TemplateRef(templateRef), title,
                sections.stream().map(DocumentSectionEntity::toDomain).toList(),
                DocumentStatus.fromName(status), businessVersion, createdAt, updatedAt,
                audit.stream().map(DocumentAuditEntity::toDomain).toList());
    }

    long businessVersion() {
        return businessVersion;
    }

    void advanceHeader(Document document) {
        if (!id.equals(document.id().value()) || !templateRef.equals(document.templateRef().value())
                || !createdAt.equals(document.createdAt())) {
            throw new IllegalArgumentException("Document identity, template and creation timestamp cannot change");
        }
        title = document.title();
        status = document.status().name();
        businessVersion = document.version();
        updatedAt = document.updatedAt();
    }

    void applyContentAndAudit(Document document) {
        if (audit.size() + 1 != document.auditTrail().size()) {
            throw new IllegalArgumentException("An aggregate update must append its audit entry");
        }
        for (int index = 0; index < audit.size(); index++) {
            if (!audit.get(index).toDomain().equals(document.auditTrail().get(index))) {
                throw new IllegalArgumentException("Existing audit entries cannot be modified");
            }
        }
        // Keep existing row identities and positions: delete/reinsert can violate
        // the unique document/position constraint before orphan removal flushes.
        int retained = Math.min(sections.size(), document.sections().size());
        for (int index = 0; index < retained; index++) {
            sections.get(index).update(document.sections().get(index));
        }
        if (sections.size() > document.sections().size()) {
            sections.subList(document.sections().size(), sections.size()).clear();
        }
        for (int index = retained; index < document.sections().size(); index++) {
            sections.add(new DocumentSectionEntity(this, index, document.sections().get(index)));
        }
        for (int index = audit.size(); index < document.auditTrail().size(); index++) {
            audit.add(new DocumentAuditEntity(this, document.auditTrail().get(index)));
        }
    }
}
