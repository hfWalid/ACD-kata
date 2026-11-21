package fr.walid.acd.review.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeJsonTest {
    private static final Path CONTRACT = Path.of("../contracts/events/document-lifecycle-changed-v1.json");
    private final EventEnvelopeJson json = new EventEnvelopeJson(new ObjectMapper());

    @Test
    void readsTheSharedContractFile() {
        var event = json.read(contract());

        assertThat(event.eventId()).isEqualTo(UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11"));
        assertThat(event.type()).isEqualTo("document.lifecycle.changed");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.documentId()).isEqualTo(UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4"));
        assertThat(event.documentVersion()).isEqualTo(2);
        assertThat(event.action()).isEqualTo("SUBMITTED");
        assertThat(event.actor()).isEqualTo("walid");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-18T10:00:00Z"));
    }

    @Test
    void acceptsExtraFieldsSoTheProducerCanAddThemWithinTheSameVersion() {
        var payload = contract().replaceFirst("\\{", "{\"newField\":42,");

        assertThat(json.read(payload).documentVersion()).isEqualTo(2);
    }

    @Test
    void refusesAnUnknownSchemaVersion() {
        assertThatThrownBy(() -> json.read(contract().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")))
                .isInstanceOf(EventContractException.class)
                .hasMessageContaining("Unsupported schemaVersion 2");
    }

    @Test
    void refusesAMissingField() {
        assertThatThrownBy(() -> json.read(contract().replace("\"actor\": \"walid\"", "\"actor\": \"\"")))
                .isInstanceOf(EventContractException.class)
                .hasMessageContaining("'actor'");
    }

    @Test
    void refusesAPayloadThatIsNotJson() {
        assertThatThrownBy(() -> json.read("not json"))
                .isInstanceOf(EventContractException.class)
                .hasMessageContaining("not valid JSON");
    }

    private static String contract() {
        try {
            return Files.readString(CONTRACT);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
