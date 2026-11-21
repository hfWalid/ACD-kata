package fr.walid.acd.contribution.application.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPurgeTest {
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(7);

    @Test
    void removesWhatWasPublishedBeforeTheRetentionWindow() {
        var outbox = new FakeEventOutbox();
        outbox.purged = 4;
        var purge = new OutboxPurge(outbox, Clock.fixed(NOW, ZoneOffset.UTC), RETENTION);

        var removed = purge.purge();

        assertThat(removed).isEqualTo(4);
        assertThat(outbox.purgeThresholds).containsExactly(NOW.minus(RETENTION));
        assertThat(outbox.published).isEmpty();
    }
}
