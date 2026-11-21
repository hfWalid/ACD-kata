package fr.walid.acd.contribution.application.event;

import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEntryTest {
    private static final UUID ID = UUID.fromString("2c1f5f0e-8a6c-4b2a-9a3f-0f3c7b1d5e11");

    @Test
    void keepsTheEventWithItsFailureCount() {
        var event = event();

        var entry = new OutboxEntry(ID, event, 2);

        assertThat(entry.event()).isSameAs(event);
        assertThat(entry.attempts()).isEqualTo(2);
    }

    @Test
    void refusesANegativeFailureCount() {
        assertThatThrownBy(() -> new OutboxEntry(ID, event(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    private static DocumentLifecycleEvent event() {
        return new DocumentLifecycleEvent(ID, DocumentLifecycleEvent.TYPE, DocumentLifecycleEvent.SCHEMA_VERSION,
                new DocumentId(ID), 1, DocumentAction.CREATED, new DocumentStatus.Draft(), "Report",
                new Actor("walid"), Instant.EPOCH);
    }
}
