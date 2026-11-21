package fr.walid.acd.contribution.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Relay cadence and retry policy. The lease must stay well above the time a batch needs to publish,
 * otherwise a slow broker makes a second relay re-read events still in flight.
 */
@ConfigurationProperties(prefix = "acd.outbox.relay")
record OutboxRelayProperties(boolean enabled, int batchSize, Duration interval, Duration initialDelay,
                             Duration lease, Duration baseBackoff, Duration maxBackoff) {

    OutboxRelayProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Relay batch size must be positive");
        }
        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("Relay lease must be positive");
        }
        if (baseBackoff.isNegative() || baseBackoff.isZero()) {
            throw new IllegalArgumentException("Relay base backoff must be positive");
        }
        if (maxBackoff.compareTo(baseBackoff) < 0) {
            throw new IllegalArgumentException("Relay max backoff must not be shorter than the base backoff");
        }
    }
}
