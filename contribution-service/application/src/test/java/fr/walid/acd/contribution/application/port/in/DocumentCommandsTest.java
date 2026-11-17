package fr.walid.acd.contribution.application.port.in;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCommandsTest {
    @Test
    void requiresAPositiveExpectedVersionAndExplicitAuthor() {
        var id = new DocumentId(UUID.randomUUID());
        var author = new Actor("walid");
        assertThatThrownBy(() -> new ChangeDocumentStatusCommand(id, 0, author)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UpdateDocumentCommand(id, 0, "Report", List.of(), author)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeDocumentStatusCommand(id, 1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UpdateDocumentCommand(id, 1, "Report", List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateDocumentCommand(new TemplateRef("report"), "Report", List.of(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void copiesCommandContentBeforeItCrossesTheApplicationBoundary() {
        var sections = new ArrayList<>(List.of(new Section("Summary", "Content")));
        var author = new Actor("walid");
        var create = new CreateDocumentCommand(new TemplateRef("report"), "Report", sections, author);
        var update = new UpdateDocumentCommand(new DocumentId(UUID.randomUUID()), 1, "Report", sections, author);
        sections.clear();
        assertThat(create.sections()).hasSize(1);
        assertThat(update.sections()).hasSize(1);
        assertThatThrownBy(() -> create.sections().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> update.sections().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
