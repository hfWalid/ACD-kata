package fr.walid.acd.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the exit criterion of the phase: replaying an event does not duplicate its effect, an old
 * event cannot reopen work, and a payload this service cannot read lands on the dead-letter topic.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "acd.events.topic=acd.document.lifecycle.review.it",
        "spring.kafka.consumer.group-id=review-it",
        "spring.liquibase.default-schema=review",
        "spring.liquibase.liquibase-schema=review"
})
class ReviewTaskConsumerIT {

    private static final String TOPIC = "acd.document.lifecycle.review.it";
    private static final UUID DOCUMENT_ID = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14-alpine")
            .withDatabaseName("acd_contribution");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?currentSchema=review");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Test
    void opensOneTaskPerSubmissionClosesItOnceAndSurvivesAReplayAndAGarbagePayload() throws Exception {
        var submission = event(UUID.randomUUID(), 1, "SUBMITTED", "2026-11-18T10:00:00Z");
        send(submission);
        await(() -> count("SELECT count(*) FROM review.review_tasks") == 1);
        assertThat(taskState()).isEqualTo("PENDING");

        // Same event delivered twice: the inbox makes the second delivery a no-op.
        send(submission);
        send(submission);
        Thread.sleep(1500);
        assertThat(count("SELECT count(*) FROM review.review_tasks")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM review.review_inbox")).isEqualTo(1);

        // The decision carries a later version and closes the pending task.
        send(event(UUID.randomUUID(), 3, "APPROVED", "2026-11-18T11:00:00Z"));
        await(() -> "APPROVED".equals(taskState()));
        assertThat(jdbc.queryForObject("SELECT decided_by FROM review.review_tasks", String.class))
                .isEqualTo("walid");

        // An old submission cannot reopen a task: the version guard rejects it.
        send(event(UUID.randomUUID(), 2, "SUBMITTED", "2026-11-18T10:30:00Z"));
        Thread.sleep(1500);
        assertThat(count("SELECT count(*) FROM review.review_tasks")).isEqualTo(1);

        // A payload this service cannot read is not retried forever: it goes to the DLT.
        send("{\"schemaVersion\": 2, \"eventId\": \"" + UUID.randomUUID() + "\"}");
        assertThat(consumeDeadLetter(Duration.ofSeconds(30))).isNotBlank();
        assertThat(count("SELECT count(*) FROM review.review_tasks")).isEqualTo(1);
    }

    private void send(String payload) throws Exception {
        kafka.send(TOPIC, DOCUMENT_ID.toString(), payload).get();
    }

    private String event(UUID eventId, long version, String action, String occurredAt) throws Exception {
        return json.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "type", "document.lifecycle.changed",
                "schemaVersion", 1,
                "occurredAt", occurredAt,
                "documentId", DOCUMENT_ID.toString(),
                "documentVersion", version,
                "action", action,
                "status", "Submitted",
                "title", "Contrat d'exploitation",
                "actor", "walid"));
    }

    private String taskState() {
        var states = jdbc.queryForList("SELECT state FROM review.review_tasks", String.class);
        return states.isEmpty() ? "NONE" : states.getFirst();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static void await(BooleanSupplier condition) {
        var deadline = Instant.now().plusSeconds(30);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("Condition not met before the timeout");
        }
    }

    private static String consumeDeadLetter(Duration timeout) {
        var properties = new HashMap<String, Object>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(TOPIC + ".DLT"));
            var records = new ArrayList<ConsumerRecord<String, String>>();
            var deadline = Instant.now().plus(timeout);
            while (records.isEmpty() && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
            return records.isEmpty() ? "" : records.getFirst().value();
        }
    }
}
