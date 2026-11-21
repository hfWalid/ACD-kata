package fr.walid.acd.review.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.walid.acd.review.application.DocumentLifecycleEvent;
import fr.walid.acd.review.application.ReviewService;
import fr.walid.acd.review.application.ReviewTasks;
import fr.walid.acd.review.application.Transactions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consumer side of the contract. The expectations live in {@code contracts/events/expectations} and
 * are verified by the producer too, so a message the consumer relies on cannot silently change shape.
 */
class ConsumerContractTest {
    private static final Path EXPECTATIONS = Path.of("../contracts/events/expectations");

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventEnvelopeJson json = new EventEnvelopeJson(mapper);

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectations")
    void behavesAsTheContractSays(String file, JsonNode expectation) {
        var payload = expectation.path("payload").toString();
        var outcome = expectation.path("outcome").asText();

        if ("REJECT".equals(outcome)) {
            assertThatThrownBy(() -> json.read(payload)).isInstanceOf(EventContractException.class);
            return;
        }

        var tasks = new InMemoryReviewTasks(expectation.path("currentVersion").asLong());
        var service = new ReviewService(tasks, transactions());
        var applied = service.handle(json.read(payload));

        switch (outcome) {
            case "OPEN_TASK" -> {
                assertThat(applied).isTrue();
                assertThat(tasks.opened).isEqualTo(1);
                assertThat(tasks.decided).isZero();
            }
            case "DECIDE" -> {
                assertThat(applied).isTrue();
                assertThat(tasks.decided).isEqualTo(1);
            }
            case "IGNORE" -> assertThat(applied).isFalse();
            default -> throw new AssertionError("Unknown expectation outcome: " + outcome);
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> expectations() {
        try (var files = Files.list(EXPECTATIONS)) {
            var mapper = new ObjectMapper();
            return files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .map(path -> {
                        try {
                            return org.junit.jupiter.params.provider.Arguments.of(
                                    path.getFileName().toString(), mapper.readTree(Files.readString(path)));
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    })
                    .toList()
                    .stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Transactions transactions() {
        return new Transactions() {
            @Override
            public <T> T inTransaction(Supplier<T> work) {
                return work.get();
            }
        };
    }

    private static final class InMemoryReviewTasks implements ReviewTasks {
        private final List<UUID> seen = new ArrayList<>();
        private final long currentVersion;
        int opened;
        int decided;

        InMemoryReviewTasks(long currentVersion) {
            this.currentVersion = currentVersion;
        }

        @Override
        public boolean remember(DocumentLifecycleEvent event) {
            return seen.add(event.eventId());
        }

        @Override
        public boolean advanceTo(DocumentLifecycleEvent event) {
            return event.documentVersion() > currentVersion;
        }

        @Override
        public void openReview(DocumentLifecycleEvent event) {
            opened++;
        }

        @Override
        public void decide(DocumentLifecycleEvent event, String decision) {
            decided++;
        }
    }
}
