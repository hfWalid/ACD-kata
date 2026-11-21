package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.event.OutboxEntry;
import fr.walid.acd.contribution.application.port.out.EventOutbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FakeEventOutbox implements EventOutbox {
    final List<DocumentLifecycleEvent> appended = new ArrayList<>();
    final List<OutboxEntry> due = new ArrayList<>();
    final List<UUID> published = new ArrayList<>();
    final Map<UUID, String> failures = new LinkedHashMap<>();
    final Map<UUID, Instant> nextAttempts = new HashMap<>();
    int lastLimit;
    Instant lastLease;

    @Override
    public void append(List<DocumentLifecycleEvent> events) {
        appended.addAll(events);
    }

    @Override
    public List<OutboxEntry> claimDue(int limit, Instant leaseUntil) {
        lastLimit = limit;
        lastLease = leaseUntil;
        return List.copyOf(due);
    }

    @Override
    public void markPublished(List<UUID> eventIds, Instant publishedAt) {
        published.addAll(eventIds);
        due.removeIf(entry -> eventIds.contains(entry.id()));
    }

    @Override
    public void markFailed(UUID eventId, String error, Instant nextAttemptAt) {
        failures.put(eventId, error);
        nextAttempts.put(eventId, nextAttemptAt);
        due.removeIf(entry -> entry.id().equals(eventId));
    }
}
