package fr.walid.acd.review.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view of the review tasks. The context owns this projection; the contribution service
 * remains the owner of the document state.
 */
@RestController
@RequestMapping("/api/review-tasks")
class ReviewTaskController {
    private final JdbcTemplate jdbc;

    ReviewTaskController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    List<ReviewTaskResponse> tasks(@RequestParam(required = false) String state) {
        return jdbc.query("""
                SELECT id, document_id, document_version, state, author, opened_at, decided_at, decided_by
                  FROM review.review_tasks
                 WHERE (?::text IS NULL OR state = ?::text)
                 ORDER BY opened_at DESC, id
                 LIMIT 200
                """, (row, index) -> new ReviewTaskResponse(
                row.getObject("id", UUID.class),
                row.getObject("document_id", UUID.class),
                row.getLong("document_version"),
                row.getString("state"),
                row.getString("author"),
                instant(row.getTimestamp("opened_at")),
                instant(row.getTimestamp("decided_at")),
                row.getString("decided_by")), state, state);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    record ReviewTaskResponse(UUID id, UUID documentId, long documentVersion, String state, String author,
                              Instant openedAt, Instant decidedAt, String decidedBy) {
    }
}
