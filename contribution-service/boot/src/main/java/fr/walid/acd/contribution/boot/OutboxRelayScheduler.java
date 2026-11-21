package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.application.service.OutboxRelay;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the outbox on a fixed delay. One scheduler is enough for the kata; the claim in the
 * outbox already makes several instances safe, which is why the lease exists.
 */
@Component
@ConditionalOnProperty(prefix = "acd.outbox.relay", name = "enabled", havingValue = "true",
        matchIfMissing = true)
final class OutboxRelayScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;
    private final int batchSize;
    private final Counter published;

    OutboxRelayScheduler(OutboxRelay relay, OutboxRelayProperties properties, MeterRegistry registry) {
        this.relay = relay;
        this.batchSize = properties.batchSize();
        this.published = Counter.builder("acd.outbox.published")
                .description("Lifecycle events acknowledged by the broker")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${acd.outbox.relay.interval:2s}",
            initialDelayString = "${acd.outbox.relay.initial-delay:2s}")
    void relayDueEvents() {
        try {
            var accepted = relay.relay(batchSize);
            if (accepted > 0) {
                published.increment(accepted);
            }
        } catch (RuntimeException failure) {
            // A relay failure must never stop the scheduler: the outbox row still holds the event.
            LOGGER.warn("Outbox relay failed: {}", failure.getMessage(), failure);
        }
    }
}
