package fr.walid.acd.contribution.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentValuesTest {
    @Test
    void normalizesLabelsWithoutChangingSectionContent() {
        assertThat(new Actor(" walid ").value()).isEqualTo("walid");
        assertThat(new TemplateRef(" report ").value()).isEqualTo("report");
        assertThat(new Section(" Summary ", " \n").title()).isEqualTo("Summary");
        assertThat(new Section("Summary", " \n").content()).isEqualTo(" \n");
        assertThat(new Actor("a".repeat(100)).value()).hasSize(100);
        assertThat(new TemplateRef("a".repeat(100)).value()).hasSize(100);
        assertThat(new Section("a".repeat(200), "a".repeat(100_000)).content()).hasSize(100_000);
    }

    @Test
    void rejectsMissingOrOversizedLabelsAndContent() {
        for (String value : new String[]{null, " ", "a".repeat(101)}) {
            assertThatThrownBy(() -> new Actor(value)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new TemplateRef(value)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String value : new String[]{null, " ", "a".repeat(201)}) {
            assertThatThrownBy(() -> new Section(value, "Content")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Section("Summary", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Section("Summary", "a".repeat(100_001))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEntry(0, DocumentAction.CREATED, new Actor("walid"), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEntry(1, null, new Actor("walid"), Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEntry(1, DocumentAction.CREATED, null, Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEntry(1, DocumentAction.CREATED, new Actor("walid"), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void roundTripsOnlyKnownStatusNames() {
        var statuses = List.of(new DocumentStatus.Draft(), new DocumentStatus.Submitted(), new DocumentStatus.InReview(),
                new DocumentStatus.Approved(), new DocumentStatus.Rejected(), new DocumentStatus.Published(), new DocumentStatus.Archived());
        statuses.forEach(status -> assertThat(DocumentStatus.fromName(status.name())).isEqualTo(status));
        assertThatThrownBy(() -> DocumentStatus.fromName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentStatus.fromName("draft")).isInstanceOf(IllegalArgumentException.class);
    }
}
