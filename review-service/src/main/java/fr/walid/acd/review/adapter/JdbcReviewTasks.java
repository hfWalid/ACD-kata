package fr.walid.acd.review.adapter;

import fr.walid.acd.review.application.DocumentLifecycleEvent;
import fr.walid.acd.review.application.ReviewTasks;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL storage of the inbox, the per-document version and the tasks. Each guard is a single
 * statement with {@code ON CONFLICT}, so two consumers cannot apply the same event twice even when
 * they run in parallel.
 */
@Component
public class JdbcReviewTasks implements ReviewTasks {
    private final JdbcTemplate jdbc;

    JdbcReviewTasks(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean remember(DocumentLifecycleEvent event) {
        return jdbc.update("""
                INSERT INTO review.review_inbox (event_id, event_type, schema_version, document_id, document_version, received_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.type(), event.schemaVersion(), event.documentId(), event.documentVersion()) == 1;
    }

    @Override
    public boolean advanceTo(DocumentLifecycleEvent event) {
        return jdbc.update("""
                INSERT INTO review.review_documents (document_id, last_version, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (document_id) DO UPDATE
                   SET last_version = EXCLUDED.last_version, updated_at = EXCLUDED.updated_at
                 WHERE review.review_documents.last_version < EXCLUDED.last_version
                """, event.documentId(), event.documentVersion(), Timestamp.from(event.occurredAt())) == 1;
    }

    @Override
    public void openReview(DocumentLifecycleEvent event) {
        jdbc.update("""
                INSERT INTO review.review_tasks (id, document_id, document_version, state, author, opened_at)
                VALUES (?, ?, ?, 'PENDING', ?, ?)
                ON CONFLICT (document_id, document_version) DO NOTHING
                """, UUID.randomUUID(), event.documentId(), event.documentVersion(), event.actor(),
                Timestamp.from(event.occurredAt()));
    }

    @Override
    public void decide(DocumentLifecycleEvent event, String decision) {
        jdbc.update("""
                UPDATE review.review_tasks SET state = ?, decided_at = ?, decided_by = ?
                 WHERE document_id = ? AND state = 'PENDING'
                """, decision, Timestamp.from(event.occurredAt()), event.actor(), event.documentId());
    }
}
