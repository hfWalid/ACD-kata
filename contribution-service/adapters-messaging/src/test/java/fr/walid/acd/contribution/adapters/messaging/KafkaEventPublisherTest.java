package fr.walid.acd.contribution.adapters.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaEventPublisherTest {
    private static final String TOPIC = "acd.document.lifecycle.v1";
    private static final UUID EVENT_ID = UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11");
    private static final UUID DOCUMENT_ID = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    @Test
    void publishesTheDocumentKeyTheContractValueAndDiscoveryHeaders() {
        var record = capturePublishedRecord(CompletableFuture.completedFuture(success()));

        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo(DOCUMENT_ID.toString());
        assertThat(record.value()).contains("\"documentId\":\"" + DOCUMENT_ID + "\"")
                .contains("\"action\":\"SUBMITTED\"")
                .contains("\"schemaVersion\":1");
        assertThat(header(record, "eventId")).isEqualTo(EVENT_ID.toString());
        assertThat(header(record, "eventType")).isEqualTo("document.lifecycle.changed");
        assertThat(header(record, "schemaVersion")).isEqualTo("1");
    }

    @Test
    void reportsAFailureTheOutboxCanRetryWhenTheBrokerRejectsTheEvent() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(() -> publisher().publish(event()))
                .isInstanceOf(EventPublishingException.class)
                .hasMessageContaining(EVENT_ID.toString())
                .hasRootCauseMessage("broker down");
    }

    @Test
    void refusesAnEmptyTopicOrANonPositiveTimeout() {
        assertThatThrownBy(() -> new MessagingProperties(" ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MessagingProperties(TOPIC, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, String> capturePublishedRecord(CompletableFuture<SendResult<String, String>> result) {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(result);

        publisher().publish(event());

        var captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafka).send(captor.capture());
        return (ProducerRecord<String, String>) captor.getValue();
    }

    private KafkaEventPublisher publisher() {
        return new KafkaEventPublisher(kafka, new ObjectMapper(), new MessagingProperties(TOPIC, Duration.ofSeconds(5)));
    }

    private static SendResult<String, String> success() {
        return new SendResult<>(null, new RecordMetadata(new org.apache.kafka.common.TopicPartition(TOPIC, 0),
                0, 0, 0, 0, 0));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static DocumentLifecycleEvent event() {
        return new DocumentLifecycleEvent(EVENT_ID, DocumentLifecycleEvent.EVENT_TYPE,
                DocumentLifecycleEvent.SCHEMA_VERSION, new DocumentId(DOCUMENT_ID), 2, DocumentAction.SUBMITTED,
                new DocumentStatus.Submitted(), "Contrat d'exploitation", new Actor("walid"),
                Instant.parse("2026-11-18T10:00:00Z"));
    }
}
