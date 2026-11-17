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
@Table(name = "document_sections")
class DocumentSectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "section_position", nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected DocumentSectionEntity() {
    }

    DocumentSectionEntity(DocumentEntity document, int position, Section section) {
        this.document = document;
        this.position = position;
        update(section);
    }

    void update(Section section) {
        title = section.title();
        content = section.content();
    }

    Section toDomain() {
        return new Section(title, content);
    }
}
