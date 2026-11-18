package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.query.DocumentVersion;
import fr.walid.acd.contribution.domain.AuditEntry;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Values reached by one version. Rows are append-only: an update never rewrites the snapshots of
 * earlier versions, which is what makes the history readable.
 */
@Entity
@Table(name = "document_versions")
class DocumentVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "business_version", nullable = false)
    private long businessVersion;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @OneToMany(mappedBy = "version", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<DocumentVersionSectionEntity> sections = new ArrayList<>();

    protected DocumentVersionEntity() {
    }

    static DocumentVersionEntity from(Document document) {
        var entity = new DocumentVersionEntity();
        entity.documentId = document.id().value();
        entity.businessVersion = document.version();
        entity.title = document.title();
        entity.status = document.status().name();
        for (int index = 0; index < document.sections().size(); index++) {
            entity.sections.add(new DocumentVersionSectionEntity(entity, index, document.sections().get(index)));
        }
        return entity;
    }

    DocumentVersion toDomain(AuditEntry entry) {
        return new DocumentVersion(entry, title,
                sections.stream().map(DocumentVersionSectionEntity::toDomain).toList(),
                DocumentStatus.fromName(status));
    }
}
