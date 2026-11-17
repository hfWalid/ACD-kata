package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;

import java.util.List;
import java.util.Objects;

public record CreateDocumentCommand(TemplateRef templateRef, String title, List<Section> sections, Actor author) {
    public CreateDocumentCommand {
        Objects.requireNonNull(templateRef, "Template reference is required");
        Objects.requireNonNull(title, "Title is required");
        sections = List.copyOf(sections);
        Objects.requireNonNull(author, "Author is required");
    }
}
