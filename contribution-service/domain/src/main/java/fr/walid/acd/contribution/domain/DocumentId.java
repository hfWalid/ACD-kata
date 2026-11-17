package fr.walid.acd.contribution.domain;

import java.util.Objects;
import java.util.UUID;

public record DocumentId(UUID value) {
    public DocumentId {
        Objects.requireNonNull(value, "Document id is required");
    }
}
