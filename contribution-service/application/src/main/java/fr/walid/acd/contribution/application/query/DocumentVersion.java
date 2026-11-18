package fr.walid.acd.contribution.application.query;

import fr.walid.acd.contribution.domain.AuditEntry;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.Section;

import java.util.List;
import java.util.Objects;

/**
 * One stored version of a document: the audit entry that produced it, plus the values the
 * document had once that action succeeded. A document's current values live in the aggregate;
 * this view is what a reader needs to compare versions without rewriting history.
 */
public record DocumentVersion(AuditEntry entry, String title, List<Section> sections, DocumentStatus status) {
    public DocumentVersion {
        Objects.requireNonNull(entry, "Audit entry is required");
        Objects.requireNonNull(title, "Title is required");
        sections = List.copyOf(Objects.requireNonNull(sections, "Sections are required"));
        Objects.requireNonNull(status, "Status is required");
    }

    public long version() {
        return entry.version();
    }
}
