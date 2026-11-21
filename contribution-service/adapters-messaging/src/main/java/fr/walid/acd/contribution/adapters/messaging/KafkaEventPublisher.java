package fr.walid.acd.contribution.adapters.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.port.out.EventPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes the event on Kafka and waits for the broker acknowledgement: a returned call is a
 * publication the relay may mark as done. The document id is the key, so all events of one document
 * land on the same partition and stay ordered.
 */
@Component
final class KafkaEventPublisher implements EventPublisher {
    private static final String HEADER_EVENT_ID = "eventId";
    private static final String HEADER_EVENT_TYPE = "eventType";
    private static final String HEADER_SCHEMA_VERSION = "schemaVersion";

    private final KafkaTemplate<String, String> kafka;
    private final DocumentLifecycleEventJson json;
    private final MessagingProperties properties;

    KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, MessagingProperties properties) {
        this.kafka = Objects.requireNonNull(kafka);
        this.json = new DocumentLifecycleEventJson(Objects.requireNonNull(mapper));
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void publish(DocumentLifecycleEvent event) {
        var record = new ProducerRecord<>(properties.topic(), event.partitionKey(), json.write(event));
        addHeader(record, HEADER_EVENT_ID, event.eventId().toString());
        addHeader(record, HEADER_EVENT_TYPE, event.type());
        addHeader(record, HEADER_SCHEMA_VERSION, Integer.toString(event.schemaVersion()));
        try {
            kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Event " + event.eventId() + " was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new EventPublishingException("Event " + event.eventId() + " was not acknowledged", exception);
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
