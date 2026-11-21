package fr.walid.acd.contribution.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.service.OutboxRelay;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chain of the phase: a mutation commits its event, the relay publishes it, the broker emits it.
 * The relay is triggered by hand so the test never races the scheduler.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "acd.outbox.relay.enabled=false",
        "acd.events.topic=acd.document.lifecycle.it",
        "acd.events.send-timeout=20s"
})
class OutboxPublicationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14-alpine")
            .withDatabaseName("acd_contribution");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    OutboxRelay relay;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Test
    void publishesOneEventPerCommittedMutationAndNoneForARejectedOne() {
        var created = create("Notice d'exploitation");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var id = created.getBody().path("id").asText();

        // Nothing reached the broker before the relay ran: the event waited for a commit.
        assertThat(pendingEvents()).isEqualTo(1);
        assertThat(consume(2, Duration.ofSeconds(2))).isEmpty();

        assertThat(relay.relay(10)).isEqualTo(1);
        assertThat(pendingEvents()).isZero();

        var submitted = transition(id, "submit", 1);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(relay.relay(10)).isEqualTo(1);

        var events = consume(2, Duration.ofSeconds(30)).stream().map(record -> parse(record.value())).toList();
        assertThat(events).extracting(event -> event.path("action").asText())
                .containsExactly("CREATED", "SUBMITTED");
        assertThat(events).extracting(event -> event.path("documentVersion").asLong()).containsExactly(1L, 2L);
        assertThat(events).extracting(event -> event.path("status").asText()).containsExactly("Draft", "Submitted");
        assertThat(events).allSatisfy(event -> assertThat(event.path("documentId").asText()).isEqualTo(id));
        assertThat(events.getFirst().path("eventId").asText()).isNotEqualTo(events.getLast().path("eventId").asText());

        // A stale client changes nothing, so it produces no event.
        var rejected = transition(id, "start-review", 1);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(2);
    }

    private ResponseEntity<JsonNode> create(String title) {
        var body = Map.of("templateRef", "report", "title", title, "sections",
                List.of(Map.of("title", "Résumé", "content", "Contenu")));
        return http.exchange("/api/documents", HttpMethod.POST, new HttpEntity<>(body, headers()), JsonNode.class);
    }

    private ResponseEntity<JsonNode> transition(String id, String action, long expectedVersion) {
        var body = Map.of("expectedVersion", expectedVersion);
        return http.exchange("/api/documents/" + id + "/" + action, HttpMethod.POST,
                new HttpEntity<>(body, headers()), JsonNode.class);
    }

    private static HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User", "walid");
        return headers;
    }

    private int pendingEvents() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Integer.class);
    }

    private JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<ConsumerRecord<String, String>> consume(int expected, Duration timeout) {
        var properties = new HashMap<String, Object>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of("acd.document.lifecycle.it"));
            var records = new ArrayList<ConsumerRecord<String, String>>();
            var deadline = Instant.now().plus(timeout);
            while (records.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(300)).forEach(records::add);
            }
            return records;
        }
    }
}
