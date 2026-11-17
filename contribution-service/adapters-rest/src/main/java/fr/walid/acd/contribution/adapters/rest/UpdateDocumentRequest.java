package fr.walid.acd.contribution.adapters.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateDocumentRequest(
        @NotNull @Positive Long expectedVersion,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 50) List<@NotNull @Valid SectionRequest> sections) {
}
