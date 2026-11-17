package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.Section;

import java.util.List;
import java.util.Objects;

public record UpdateDocumentCommand(DocumentId id, long expectedVersion, String title, List<Section> sections, Actor author) {
    public UpdateDocumentCommand {
        Objects.requireNonNull(id, "Document id is required");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("Expected version must be positive");
        }
        Objects.requireNonNull(title, "Title is required");
        sections = List.copyOf(sections);
        Objects.requireNonNull(author, "Author is required");
    }
}
