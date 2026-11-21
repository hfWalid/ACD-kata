package fr.walid.acd.contribution.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Retention of published events. The window is deliberately longer than an incident review: the
 * outbox is the only trace of what the broker received.
 */
@ConfigurationProperties(prefix = "acd.outbox.purge")
record OutboxPurgeProperties(boolean enabled, Duration interval, Duration initialDelay, Duration retention) {

    OutboxPurgeProperties {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Purge retention must be positive");
        }
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Purge interval must be positive");
        }
    }
}
