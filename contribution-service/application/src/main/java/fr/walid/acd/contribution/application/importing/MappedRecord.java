package fr.walid.acd.contribution.application.importing;

import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;

import java.util.List;
import java.util.Objects;

/**
 * One file record once mapped, before any business rule is applied. The values are passed through
 * as the file gives them: deciding whether a title is acceptable is the domain's job, not the
 * mapping's.
 */
public record MappedRecord(
        int line,
        SourceRef sourceRef,
        TemplateRef templateRef,
        String title,
        List<Section> sections) {

    public MappedRecord {
        if (line < 1) {
            throw new IllegalArgumentException("Line must be positive");
        }
        Objects.requireNonNull(sourceRef, "Source reference is required");
        Objects.requireNonNull(templateRef, "Template reference is required");
        Objects.requireNonNull(title, "Title is required");
        sections = List.copyOf(Objects.requireNonNull(sections, "Sections are required"));
    }
}
