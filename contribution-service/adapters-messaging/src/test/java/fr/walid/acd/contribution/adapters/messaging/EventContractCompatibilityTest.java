package fr.walid.acd.contribution.adapters.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Producer side of the contract. Each expectation published by the consumer is replayed through the
 * producer's own domain and serializer: the message it would emit must carry every field the
 * consumer declared, with the same value. Renaming a field, changing a status name or shifting the
 * version numbering breaks this test, not the consumer.
 */
class EventContractCompatibilityTest {
    private static final Path EXPECTATIONS = Path.of("../../contracts/events/expectations");

    private final ObjectMapper mapper = new ObjectMapper();
    private final DocumentLifecycleEventJson json = new DocumentLifecycleEventJson(mapper);

    @ParameterizedTest(name = "{0}")
    @MethodSource("verifiableExpectations")
    void emitsEveryFieldTheConsumerReliesOn(String file, JsonNode expectation) {
        var payload = expectation.path("payload");
        var scenario = expectation.path("scenario");
        var actor = new Actor(scenario.path("actor").asText());
        var steps = actions(scenario.path("steps"));
        var lastChange = Instant.parse(payload.path("occurredAt").asText());

        var document = apply(steps, draft(payload, scenario, actor, lastChange.minusSeconds(steps.size())), actor,
                lastChange.minusSeconds(steps.size()));
        var event = DocumentLifecycleEvent.of(document, UUID.fromString(payload.path("eventId").asText()));
        var produced = read(json.write(event));

        assertThat(event.documentVersion()).isEqualTo(payload.path("documentVersion").asLong());
        payload.fieldNames().forEachRemaining(field ->
                assertThat(produced.path(field)).as("%s in %s", field, file).isEqualTo(payload.path(field)));
    }

    private static Document apply(List<String> steps, Document document, Actor actor, Instant createdAt) {
        var current = document;
        var time = createdAt;
        for (var step : steps) {
            time = time.plusSeconds(1);
            current = switch (step) {
                case "SUBMITTED" -> current.submit(actor, time);
                case "REVIEW_STARTED" -> current.startReview(actor, time);
                case "APPROVED" -> current.approve(actor, time);
                case "REJECTED" -> current.reject(actor, time);
                case "PUBLISHED" -> current.publish(actor, time);
                case "ARCHIVED" -> current.archive(actor, time);
                default -> throw new IllegalArgumentException("Unsupported contract step: " + step);
            };
        }
        return current;
    }

    private static Document draft(JsonNode payload, JsonNode scenario, Actor actor, Instant createdAt) {
        return Document.create(
                new DocumentId(UUID.fromString(payload.path("documentId").asText())),
                new TemplateRef(scenario.path("templateRef").asText()),
                scenario.path("title").asText(),
                List.of(new Section(scenario.path("sectionTitle").asText(), scenario.path("sectionContent").asText())),
                actor,
                createdAt);
    }

    private static List<String> actions(JsonNode steps) {
        var names = new ArrayList<String>();
        steps.forEach(step -> names.add(step.asText()));
        return names;
    }

    private static Stream<Arguments> verifiableExpectations() {
        try (var files = Files.list(EXPECTATIONS)) {
            return files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .map(path -> {
                        try {
                            var expectation = new ObjectMapper().readTree(Files.readString(path));
                            return expectation.has("scenario")
                                    ? Arguments.of(path.getFileName().toString(), expectation)
                                    : null;
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList()
                    .stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
