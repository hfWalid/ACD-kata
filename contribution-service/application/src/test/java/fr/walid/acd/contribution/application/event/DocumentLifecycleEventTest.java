package fr.walid.acd.contribution.application.event;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentLifecycleEventTest {
    private static final UUID DOCUMENT_ID = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");
    private static final UUID EVENT_ID = UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11");
    private static final Instant CREATED_AT = Instant.parse("2026-11-18T09:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-18T10:00:00Z");
    private static final Actor AUTHOR = new Actor("walid");

    @Test
    void describesTheMutationThatProducedTheAggregateState() {
        var document = submittedDocument();

        var event = DocumentLifecycleEvent.of(document, EVENT_ID);

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.type()).isEqualTo(DocumentLifecycleEvent.TYPE);
        assertThat(event.schemaVersion()).isEqualTo(DocumentLifecycleEvent.SCHEMA_VERSION);
        assertThat(event.documentId()).isEqualTo(new DocumentId(DOCUMENT_ID));
        assertThat(event.documentVersion()).isEqualTo(2);
        assertThat(event.action()).isEqualTo(DocumentAction.SUBMITTED);
        assertThat(event.status()).isEqualTo(new DocumentStatus.Submitted());
        assertThat(event.title()).isEqualTo("Report");
        assertThat(event.actor()).isEqualTo(AUTHOR);
        assertThat(event.occurredAt()).isEqualTo(SUBMITTED_AT);
        assertThat(DocumentLifecycleEvent.TYPE).isEqualTo("document.lifecycle.changed");
        assertThat(DocumentLifecycleEvent.SCHEMA_VERSION).isEqualTo(1);
    }

    @Test
    void usesTheDocumentAsPartitionKeySoItsEventsStayOrdered() {
        var event = DocumentLifecycleEvent.of(submittedDocument(), EVENT_ID);

        assertThat(event.partitionKey()).isEqualTo(DOCUMENT_ID.toString());
    }

    @Test
    void rejectsAVersionBelowTheFirstOne() {
        assertThatThrownBy(() -> new DocumentLifecycleEvent(EVENT_ID, DocumentLifecycleEvent.TYPE,
                DocumentLifecycleEvent.SCHEMA_VERSION, new DocumentId(DOCUMENT_ID), 0,
                DocumentAction.CREATED, new DocumentStatus.Draft(), "Report", AUTHOR, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsASchemaVersionBelowTheFirstOne() {
        assertThatThrownBy(() -> new DocumentLifecycleEvent(EVENT_ID, DocumentLifecycleEvent.TYPE, 0,
                new DocumentId(DOCUMENT_ID), 1, DocumentAction.CREATED, new DocumentStatus.Draft(), "Report",
                AUTHOR, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schema version");
    }

    @Test
    void rejectsABlankType() {
        assertThatThrownBy(() -> new DocumentLifecycleEvent(EVENT_ID, " ", DocumentLifecycleEvent.SCHEMA_VERSION,
                new DocumentId(DOCUMENT_ID), 1, DocumentAction.CREATED, new DocumentStatus.Draft(), "Report",
                AUTHOR, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    private static Document submittedDocument() {
        var draft = Document.create(new DocumentId(DOCUMENT_ID), new TemplateRef("report"), "Report",
                List.of(new Section("Summary", "Content")), AUTHOR, CREATED_AT);
        return draft.submit(AUTHOR, SUBMITTED_AT);
    }
}
