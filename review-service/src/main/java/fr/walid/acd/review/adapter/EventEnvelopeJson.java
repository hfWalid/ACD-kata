package fr.walid.acd.review.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.review.application.DocumentLifecycleEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads version 1 of the lifecycle contract. Unknown fields are ignored so the producer can add
 * fields inside the same version, while a missing field or an unknown version is rejected loudly.
 */
@Component
final class EventEnvelopeJson {
    static final String SUPPORTED_TYPE = "document.lifecycle.changed";
    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper mapper;

    EventEnvelopeJson(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    DocumentLifecycleEvent read(String payload) {
        var root = parse(payload);
        var schemaVersion = root.path("schemaVersion").asInt(0);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new EventContractException("Unsupported schemaVersion " + schemaVersion);
        }
        return new DocumentLifecycleEvent(
                uuid(root, "eventId"),
                text(root, "type"),
                schemaVersion,
                uuid(root, "documentId"),
                root.path("documentVersion").asLong(0),
                text(root, "action"),
                text(root, "actor"),
                instant(root, "occurredAt"));
    }

    private JsonNode parse(String payload) {
        try {
            return mapper.readTree(payload);
        } catch (IOException exception) {
            throw new EventContractException("Payload is not valid JSON", exception);
        }
    }

    private static String text(JsonNode root, String field) {
        var value = root.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new EventContractException("Field '" + field + "' is missing or blank");
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode root, String field) {
        try {
            return UUID.fromString(text(root, field));
        } catch (IllegalArgumentException exception) {
            throw new EventContractException("Field '" + field + "' is not a uuid", exception);
        }
    }

    private static Instant instant(JsonNode root, String field) {
        try {
            return Instant.parse(text(root, field));
        } catch (RuntimeException exception) {
            throw new EventContractException("Field '" + field + "' is not an instant", exception);
        }
    }
}
