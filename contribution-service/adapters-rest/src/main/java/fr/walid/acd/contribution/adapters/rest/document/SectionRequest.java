package fr.walid.acd.contribution.adapters.rest.document;

import fr.walid.acd.contribution.domain.Section;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SectionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 100_000) String content) {

    Section toDomain() {
        return new Section(title, content);
    }
}
