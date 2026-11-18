package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.domain.Section;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "document_version_sections")
class DocumentVersionSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersionEntity version;

    @Column(name = "section_position", nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected DocumentVersionSectionEntity() {
    }

    DocumentVersionSectionEntity(DocumentVersionEntity version, int position, Section section) {
        this.version = version;
        this.position = position;
        this.title = section.title();
        this.content = section.content();
    }

    Section toDomain() {
        return new Section(title, content);
    }
}
