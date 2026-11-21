package fr.walid.acd.contribution.application.importing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceRefTest {

    @Test
    void requiresASourceName() {
        assertThatThrownBy(() -> new SourceRef(null, "DOC-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Import source is required");
        assertThatThrownBy(() -> new SourceRef("  ", "DOC-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Import source is required");
    }

    @Test
    void requiresAReference() {
        assertThatThrownBy(() -> new SourceRef("annuaire", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Import reference is required");
        assertThatThrownBy(() -> new SourceRef("annuaire", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Import reference is required");
    }

    @Test
    void boundsTheReferenceLength() {
        assertThatThrownBy(() -> new SourceRef("annuaire", "R".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import reference must not exceed 200 characters");
    }

    @Test
    void keepsASourceAndAReference() {
        var reference = new SourceRef("annuaire", "DOC-2026-0001");

        assertThat(reference.source()).isEqualTo("annuaire");
        assertThat(reference.value()).isEqualTo("DOC-2026-0001");
    }
}
