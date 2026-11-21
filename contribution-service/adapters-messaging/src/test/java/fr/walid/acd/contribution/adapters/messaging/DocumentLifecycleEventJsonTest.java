package fr.walid.acd.contribution.adapters.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentLifecycleEventJsonTest {
    private static final Path CONTRACT = Path.of("../../contracts/events/document-lifecycle-changed-v1.json");
    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentLifecycleEventJson json = new DocumentLifecycleEventJson(mapper);

    @Test
    void writesExactlyTheFieldsOfTheVersionedContract() throws IOException {
        var event = new DocumentLifecycleEvent(
                UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11"),
                DocumentLifecycleEvent.TYPE,
                DocumentLifecycleEvent.SCHEMA_VERSION,
                new DocumentId(UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4")),
                2,
                DocumentAction.SUBMITTED,
                new DocumentStatus.Submitted(),
                "Contrat d'exploitation",
                new Actor("walid"),
                Instant.parse("2026-11-18T10:00:00Z"));

        var written = mapper.readTree(json.write(event));
        var contract = mapper.readTree(Files.readString(CONTRACT));

        assertThat(written).isEqualTo(contract);
        assertThat(written.fieldNames()).toIterable().containsExactly(
                "eventId", "type", "schemaVersion", "occurredAt", "documentId", "documentVersion",
                "action", "status", "title", "actor");
    }

    @Test
    void escapesFreeTextSoAConsumerCanAlwaysParseIt() {
        var event = new DocumentLifecycleEvent(
                UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11"),
                DocumentLifecycleEvent.TYPE,
                DocumentLifecycleEvent.SCHEMA_VERSION,
                new DocumentId(UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4")),
                1,
                DocumentAction.CREATED,
                new DocumentStatus.Draft(),
                "Guillemet \" et barre \\ oblique / accent é",
                new Actor("walid"),
                Instant.parse("2026-11-18T10:00:00Z"));

        var written = parse(json.write(event));

        assertThat(written.get("title").asText()).isEqualTo("Guillemet \" et barre \\ oblique / accent é");
    }

    private com.fasterxml.jackson.databind.JsonNode parse(String value) {
        try {
            return mapper.readTree(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
