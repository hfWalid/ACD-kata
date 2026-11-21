package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.event.DocumentLifecycleEvent;
import fr.walid.acd.contribution.application.event.OutboxEntry;
import fr.walid.acd.contribution.application.port.out.EventOutbox;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.DocumentAction;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL implementation of the outbox. Claiming uses {@code FOR UPDATE SKIP LOCKED} so several
 * relays take disjoint batches instead of waiting on each other, and the lease pushed into
 * {@code next_attempt_at} releases a batch whose relay died.
 */
@Component
public class JdbcEventOutbox implements EventOutbox {
    private static final String APPEND = """
            INSERT INTO outbox_events (id, event_type, schema_version, document_id, business_version,
                                       action, status, title, actor, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Claims a batch and returns it in the order the events must be published.
     *
     * <p>Two details matter. {@code ORDER BY} inside an {@code UPDATE ... RETURNING} does not order
     * the result, so the claim is wrapped in a CTE and ordered again on the way out; without that,
     * the four events of one imported document came back as 3,4,1,2 and the review service ignored
     * the submission. The order is by timestamp, then by document version, because several
     * commands of one document can share a microsecond: without the version, a tie would be broken
     * by a random identifier.
     */
    private static final String CLAIM = """
            WITH claimed AS (
                UPDATE outbox_events SET next_attempt_at = ?
                WHERE id IN (SELECT id FROM outbox_events
                             WHERE published_at IS NULL
                               AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                             ORDER BY occurred_at, business_version, id
                             LIMIT ?
                             FOR UPDATE SKIP LOCKED)
                RETURNING id, event_type, schema_version, document_id, business_version, action, status,
                          title, actor, occurred_at, attempts
            )
            SELECT * FROM claimed ORDER BY occurred_at, business_version, id
            """;

    private static final String MARK_PUBLISHED =
            "UPDATE outbox_events SET published_at = ?, last_error = NULL WHERE id = ? AND published_at IS NULL";

    private static final String MARK_FAILED =
            "UPDATE outbox_events SET attempts = attempts + 1, last_error = ?, next_attempt_at = ? WHERE id = ?";

    private static final String COUNT_PENDING =
            "SELECT count(*) FROM outbox_events WHERE published_at IS NULL";

    private static final String COUNT_FAILED =
            "SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND attempts > 0";

    private static final String PURGE_PUBLISHED =
            "DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < ?";

    private static final RowMapper<OutboxEntry> ENTRY = JdbcEventOutbox::read;

    private final JdbcTemplate jdbc;

    JdbcEventOutbox(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(List<DocumentLifecycleEvent> events) {
        jdbc.batchUpdate(APPEND, events, events.size(), (statement, event) -> {
            statement.setObject(1, event.eventId());
            statement.setString(2, event.type());
            statement.setInt(3, event.schemaVersion());
            statement.setObject(4, event.documentId().value());
            statement.setLong(5, event.documentVersion());
            statement.setString(6, event.action().name());
            statement.setString(7, event.status().name());
            statement.setString(8, event.title());
            statement.setString(9, event.actor().value());
            statement.setTimestamp(10, Timestamp.from(event.occurredAt()));
        });
    }

    @Override
    public List<OutboxEntry> claimDue(int limit, Instant leaseUntil) {
        return jdbc.query(CLAIM, ENTRY, Timestamp.from(leaseUntil), limit);
    }

    @Override
    public void markPublished(List<UUID> eventIds, Instant publishedAt) {
        var published = Timestamp.from(publishedAt);
        jdbc.batchUpdate(MARK_PUBLISHED, eventIds, eventIds.size(), (statement, id) -> {
            statement.setTimestamp(1, published);
            statement.setObject(2, id);
        });
    }

    @Override
    public void markFailed(UUID eventId, String error, Instant nextAttemptAt) {
        jdbc.update(MARK_FAILED, error, Timestamp.from(nextAttemptAt), eventId);
    }

    @Override
    public long countPending() {
        return count(COUNT_PENDING);
    }

    @Override
    public long countFailed() {
        return count(COUNT_FAILED);
    }

    @Override
    public int purgePublishedBefore(Instant threshold) {
        return jdbc.update(PURGE_PUBLISHED, Timestamp.from(threshold));
    }

    private long count(String sql) {
        var counted = jdbc.queryForObject(sql, Long.class);
        return counted == null ? 0L : counted;
    }

    private static OutboxEntry read(ResultSet row, int index) throws SQLException {
        var event = new DocumentLifecycleEvent(
                row.getObject("id", UUID.class),
                row.getString("event_type"),
                row.getInt("schema_version"),
                new DocumentId(row.getObject("document_id", UUID.class)),
                row.getLong("business_version"),
                DocumentAction.valueOf(row.getString("action")),
                DocumentStatus.fromName(row.getString("status")),
                row.getString("title"),
                new Actor(row.getString("actor")),
                row.getTimestamp("occurred_at").toInstant());
        return new OutboxEntry(event.eventId(), event, row.getInt("attempts"));
    }
}
