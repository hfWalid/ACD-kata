package fr.walid.acd.contribution.boot;

import fr.walid.acd.contribution.application.service.OutboxPurge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes published events once they fall out of the retention window. Failures never stop the
 * scheduler: the next run simply tries again.
 */
@Component
@ConditionalOnProperty(prefix = "acd.outbox.purge", name = "enabled", havingValue = "true",
        matchIfMissing = true)
final class OutboxPurgeScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPurgeScheduler.class);

    private final OutboxPurge purge;
    private final Counter removed;

    OutboxPurgeScheduler(OutboxPurge purge, MeterRegistry registry) {
        this.purge = purge;
        this.removed = Counter.builder("acd.outbox.purged")
                .description("Published events removed after the retention window")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${acd.outbox.purge.interval:1h}",
            initialDelayString = "${acd.outbox.purge.initial-delay:5m}")
    void purgeExpiredEvents() {
        try {
            var deleted = purge.purge();
            if (deleted > 0) {
                removed.increment(deleted);
                LOGGER.info("Removed {} published events past the retention window", deleted);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Outbox purge failed: {}", failure.getMessage(), failure);
        }
    }
}
