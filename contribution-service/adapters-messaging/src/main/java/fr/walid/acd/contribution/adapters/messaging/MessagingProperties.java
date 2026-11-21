package fr.walid.acd.contribution.adapters.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Topic and acknowledgement budget of the lifecycle events.
 */
@ConfigurationProperties(prefix = "acd.events")
public record MessagingProperties(String topic, Duration sendTimeout) {

    public MessagingProperties {
        Objects.requireNonNull(topic, "Event topic is required");
        Objects.requireNonNull(sendTimeout, "Send timeout is required");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Event topic must not be blank");
        }
        if (sendTimeout.isNegative() || sendTimeout.isZero()) {
            throw new IllegalArgumentException("Send timeout must be positive");
        }
    }
}
