package fr.walid.acd.contribution.application.importing;

import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappedRecordTest {
    private static final SourceRef REF = new SourceRef("annuaire", "DOC-1");
    private static final TemplateRef TEMPLATE = new TemplateRef("policy");

    @Test
    void requiresAPositiveLine() {
        assertThatThrownBy(() -> new MappedRecord(0, REF, TEMPLATE, "Titre", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Line must be positive");
    }

    @Test
    void requiresItsParts() {
        assertThatThrownBy(() -> new MappedRecord(1, null, TEMPLATE, "Titre", List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Source reference is required");
        assertThatThrownBy(() -> new MappedRecord(1, REF, null, "Titre", List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Template reference is required");
        assertThatThrownBy(() -> new MappedRecord(1, REF, TEMPLATE, null, List.of()))
                .isInstanceOf(NullPointerException.class).hasMessage("Title is required");
        assertThatThrownBy(() -> new MappedRecord(1, REF, TEMPLATE, "Titre", null))
                .isInstanceOf(NullPointerException.class).hasMessage("Sections are required");
    }

    @Test
    void copiesSectionsAndKeepsTheFileValuesUntouched() {
        var sections = new ArrayList<>(List.of(new Section("Contexte", "Contenu")));

        var record = new MappedRecord(4, REF, TEMPLATE, "  ", sections);
        sections.clear();

        assertThat(record.sections()).hasSize(1);
        // A blank title reaches the domain: the mapping never decides what is acceptable.
        assertThat(record.title()).isEqualTo("  ");
        assertThat(record.line()).isEqualTo(4);
    }
}
