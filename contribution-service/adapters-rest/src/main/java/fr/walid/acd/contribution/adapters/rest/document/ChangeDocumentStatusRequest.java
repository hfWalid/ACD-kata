package fr.walid.acd.contribution.adapters.rest.document;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChangeDocumentStatusRequest(@NotNull @Positive Long expectedVersion) {
}
