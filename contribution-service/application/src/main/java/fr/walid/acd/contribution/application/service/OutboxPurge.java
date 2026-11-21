package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.port.out.EventOutbox;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Drops published events once they are older than the retention window. The outbox is a queue, not
 * an archive: keeping everything forever turns a transport table into a growing liability. A
 * retention of several days leaves room to investigate an incident before the trace disappears.
 */
public final class OutboxPurge {
    private final EventOutbox outbox;
    private final Clock clock;
    private final Duration retention;

    public OutboxPurge(EventOutbox outbox, Clock clock, Duration retention) {
        this.outbox = Objects.requireNonNull(outbox, "Outbox is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.retention = Objects.requireNonNull(retention, "Retention is required");
    }

    /** @return the number of published events removed. */
    public int purge() {
        return outbox.purgePublishedBefore(clock.instant().minus(retention));
    }
}
