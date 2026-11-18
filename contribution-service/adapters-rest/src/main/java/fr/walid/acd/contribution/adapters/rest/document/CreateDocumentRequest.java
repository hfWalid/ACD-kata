package fr.walid.acd.contribution.adapters.rest.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 100) String templateRef,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 50) List<@NotNull @Valid SectionRequest> sections) {
}
