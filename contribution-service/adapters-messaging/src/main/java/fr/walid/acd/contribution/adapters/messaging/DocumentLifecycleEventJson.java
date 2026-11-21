package fr.walid.acd.contribution.adapters.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Writes the wire format of the lifecycle event. The field names and their order are the contract
 * snapshotted in {@code contracts/events/document-lifecycle-changed-v1.json}; changing either is a
 * new schema version, never a silent edit.
 */
final class DocumentLifecycleEventJson {
    private final ObjectMapper mapper;

    DocumentLifecycleEventJson(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    String write(DocumentLifecycleEvent event) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("eventId", event.eventId().toString());
        fields.put("type", event.type());
        fields.put("schemaVersion", event.schemaVersion());
        fields.put("occurredAt", DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()));
        fields.put("documentId", event.documentId().value().toString());
        fields.put("documentVersion", event.documentVersion());
        fields.put("action", event.action().name());
        fields.put("status", event.status().name());
        fields.put("title", event.title());
        fields.put("actor", event.actor().value());
        try {
            return mapper.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw new EventPublishingException("Event " + event.eventId() + " could not be serialized", exception);
        }
    }
}
