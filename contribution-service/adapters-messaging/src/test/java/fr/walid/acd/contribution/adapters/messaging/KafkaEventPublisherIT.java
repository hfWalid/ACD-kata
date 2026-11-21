package fr.walid.acd.contribution.adapters.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class KafkaEventPublisherIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    private static final UUID DOCUMENT_ID = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");

    @Test
    void deliversTheEventOnTheAgreedTopicKeyedByDocument() {
        var topic = "acd.document.lifecycle." + UUID.randomUUID();

        publisher(topic).publish(event(topic, 1, DocumentAction.SUBMITTED, new DocumentStatus.Submitted()));

        var records = consume(topic, 1);
        assertThat(records).hasSize(1);
        var record = records.getFirst();
        assertThat(record.key()).isEqualTo(DOCUMENT_ID.toString());
        assertThat(record.value()).contains("\"documentVersion\":1")
                .contains("\"action\":\"SUBMITTED\"")
                .contains("\"status\":\"Submitted\"");
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("document.lifecycle.changed");
    }

    @Test
    void keepsTheEventsOfOneDocumentInVersionOrderOnOnePartition() {
        var topic = "acd.document.lifecycle." + UUID.randomUUID();
        var publisher = publisher(topic);

        publisher.publish(event(topic, 1, DocumentAction.SUBMITTED, new DocumentStatus.Submitted()));
        publisher.publish(event(topic, 2, DocumentAction.REVIEW_STARTED, new DocumentStatus.InReview()));

        var records = consume(topic, 2);
        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records).extracting(ConsumerRecord::value)
                .allSatisfy(value -> assertThat(value).startsWith("{\"eventId\""));
        assertThat(records).extracting(record -> record.value().contains("\"documentVersion\":1")
                ? 1 : 2).containsExactly(1, 2);
    }

    private KafkaEventPublisher publisher(String topic) {
        var properties = new HashMap<String, Object>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        var kafka = new KafkaTemplate<String, String>(new DefaultKafkaProducerFactory<>(properties));
        return new KafkaEventPublisher(kafka, new ObjectMapper(),
                new MessagingProperties(topic, Duration.ofSeconds(20)));
    }

    private static List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        var properties = new HashMap<String, Object>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(topic));
            var records = new ArrayList<ConsumerRecord<String, String>>();
            var deadline = Instant.now().plusSeconds(30);
            while (records.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
            return records;
        }
    }

    private static DocumentLifecycleEvent event(String topic, long version, DocumentAction action,
                                                DocumentStatus status) {
        return new DocumentLifecycleEvent(UUID.randomUUID(), "document.lifecycle.changed", 1,
                new DocumentId(DOCUMENT_ID), version, action, status, "Contrat d'exploitation",
                new Actor("walid"), Instant.parse("2026-11-18T10:00:00Z").plusSeconds(version));
    }
}
