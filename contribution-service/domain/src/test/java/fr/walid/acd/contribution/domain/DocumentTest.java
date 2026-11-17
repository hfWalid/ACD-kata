package fr.walid.acd.contribution.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {
    private static final Actor AUTHOR = new Actor("walid");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void createsAnAuditedDraftWithoutRequiringContent() {
        var id = new DocumentId(UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4"));
        var author = new Actor("walid");
        var now = Instant.parse("2026-11-17T10:00:00Z");

        var document = Document.create(id, new TemplateRef("report"), "Report", List.of(), author, now);

        assertThat(document.id()).isEqualTo(id);
        assertThat(document.status()).isEqualTo(new DocumentStatus.Draft());
        assertThat(document.version()).isEqualTo(1);
        assertThat(document.auditTrail()).containsExactly(new AuditEntry(1, DocumentAction.CREATED, author, now));
    }

    @Test
    void advancesVersionAndAuditForEachActionWithoutChangingPreviousSnapshots() {
        var draft = draft();
        var submitted = draft.submit(AUTHOR, NOW.plusSeconds(1));
        var reviewed = submitted.startReview(AUTHOR, NOW.plusSeconds(2));
        var approved = reviewed.approve(AUTHOR, NOW.plusSeconds(3));
        var published = approved.publish(AUTHOR, NOW.plusSeconds(4));
        var archived = published.archive(AUTHOR, NOW.plusSeconds(5));

        assertThat(archived.status()).isEqualTo(new DocumentStatus.Archived());
        assertThat(archived.version()).isEqualTo(6);
        assertThat(archived.createdAt()).isEqualTo(NOW);
        assertThat(archived.updatedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(archived.templateRef()).isEqualTo(new TemplateRef("report"));
        assertThat(archived.auditTrail()).extracting(AuditEntry::action).containsExactly(
                DocumentAction.CREATED, DocumentAction.SUBMITTED, DocumentAction.REVIEW_STARTED,
                DocumentAction.APPROVED, DocumentAction.PUBLISHED, DocumentAction.ARCHIVED);
        assertThat(archived.auditTrail()).extracting(AuditEntry::version).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(archived.auditTrail()).allSatisfy(entry -> assertThat(entry.author()).isEqualTo(AUTHOR));
        assertThat(draft.status()).isEqualTo(new DocumentStatus.Draft());
        assertThat(draft.auditTrail()).hasSize(1);
    }

    @Test
    void allowsCorrectionAndResubmissionAfterRejection() {
        var rejected = draft().submit(AUTHOR, NOW).startReview(AUTHOR, NOW).reject(AUTHOR, NOW);
        var corrected = rejected.updateContent(" Corrected ", List.of(new Section(" Summary ", "New content")), AUTHOR, NOW);
        var resubmitted = corrected.submit(AUTHOR, NOW);

        assertThat(corrected.title()).isEqualTo("Corrected");
        assertThat(corrected.sections()).containsExactly(new Section("Summary", "New content"));
        assertThat(corrected.status()).isEqualTo(new DocumentStatus.Rejected());
        assertThat(corrected.auditTrail().getLast().action()).isEqualTo(DocumentAction.CONTENT_UPDATED);
        assertThat(resubmitted.status()).isEqualTo(new DocumentStatus.Submitted());
        assertThat(resubmitted.version()).isEqualTo(6);
        assertThat(rejected.title()).isEqualTo("Report");
    }

    @Test
    void permitsDraftEditingAndDirectResubmissionOfARejectedDocument() {
        var edited = draft().updateContent("Edited", List.of(new Section("Summary", "")), AUTHOR, NOW);
        assertThat(edited.status()).isEqualTo(new DocumentStatus.Draft());
        assertThat(edited.version()).isEqualTo(2);
        var rejected = draft().submit(AUTHOR, NOW).startReview(AUTHOR, NOW).reject(AUTHOR, NOW);
        assertThat(rejected.submit(AUTHOR, NOW).status()).isEqualTo(new DocumentStatus.Submitted());
    }

    @Test
    void refusesSubmissionOfEmptyOrPartiallyEmptyContent() {
        var empty = Document.create(draft().id(), new TemplateRef("report"), "Report", List.of(), AUTHOR, NOW);
        var incomplete = draft().updateContent("Report", List.of(new Section("First", "Ready"), new Section("Second", " \n")), AUTHOR, NOW);
        for (var document : List.of(empty, incomplete)) {
            assertThatThrownBy(() -> document.submit(AUTHOR, NOW)).isInstanceOfSatisfying(DomainException.class,
                    error -> assertThat(error.code()).isEqualTo("EMPTY_DOCUMENT"));
            assertThat(document.status()).isEqualTo(new DocumentStatus.Draft());
        }
    }

    @ParameterizedTest
    @MethodSource("documentsInEveryState")
    void enforcesEveryTransitionAndLeavesRefusedDocumentsUnchanged(Document document) {
        var commands = Map.<DocumentAction, BiFunction<Actor, Instant, Document>>of(
                DocumentAction.CONTENT_UPDATED, (author, at) -> document.updateContent("Edited", document.sections(), author, at),
                DocumentAction.SUBMITTED, document::submit,
                DocumentAction.REVIEW_STARTED, document::startReview,
                DocumentAction.APPROVED, document::approve,
                DocumentAction.REJECTED, document::reject,
                DocumentAction.PUBLISHED, document::publish,
                DocumentAction.ARCHIVED, document::archive);
        var originalAudit = document.auditTrail();
        for (var action : DocumentAction.values()) {
            String expected = expectedNext(document.status().name(), action);
            if (expected == null) {
                assertThatThrownBy(() -> DocumentTransition.next(document.status(), action))
                        .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("INVALID_TRANSITION"));
                if (commands.containsKey(action)) {
                    assertThatThrownBy(() -> commands.get(action).apply(AUTHOR, NOW)).isInstanceOf(DomainException.class);
                }
            } else {
                var changed = commands.get(action).apply(AUTHOR, NOW);
                assertThat(changed.status().name()).isEqualTo(expected);
                assertThat(changed.version()).isEqualTo(document.version() + 1);
                assertThat(changed.auditTrail().getLast().action()).isEqualTo(action);
            }
            assertThat(document.auditTrail()).isSameAs(originalAudit);
        }
    }

    @Test
    void rejectsAnActionEarlierThanTheLatestChange() {
        var draft = draft();
        assertThatThrownBy(() -> draft.submit(AUTHOR, NOW.minusSeconds(1)))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.code()).isEqualTo("NON_MONOTONIC_TIME"));
        assertThat(draft.version()).isEqualTo(1);
        assertThat(draft.auditTrail()).hasSize(1);
    }

    @Test
    void protectsItsCollectionsAndCopiesRestoredAuditWithoutGeneratingAnEvent() {
        var sections = new ArrayList<>(List.of(new Section("Summary", "Content")));
        var original = Document.create(draft().id(), new TemplateRef("report"), " Report ", sections, AUTHOR, NOW);
        sections.clear();
        var audit = new ArrayList<>(original.auditTrail());
        var restored = Document.restore(original.id(), original.templateRef(), original.title(), original.sections(),
                original.status(), original.version(), original.createdAt(), original.updatedAt(), audit);
        audit.clear();

        assertThat(original.sections()).hasSize(1);
        assertThat(restored.auditTrail()).containsExactlyElementsOf(original.auditTrail());
        assertThat(restored.version()).isEqualTo(1);
        assertThatThrownBy(() -> original.sections().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.auditTrail().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidTitlesSectionsVersionsAndRestorationMetadata() {
        for (String title : new String[]{null, " ", "a".repeat(201)}) {
            assertThatThrownBy(() -> Document.create(draft().id(), new TemplateRef("report"), title, List.of(), AUTHOR, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var tooMany = Stream.generate(() -> new Section("Summary", "Content")).limit(51).toList();
        assertThatThrownBy(() -> Document.create(draft().id(), new TemplateRef("report"), "Report", tooMany, AUTHOR, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(0, NOW, NOW, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(1, NOW, NOW.minusSeconds(1), List.of(new AuditEntry(1, DocumentAction.CREATED, AUTHOR, NOW))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(2, NOW, NOW, draft().auditTrail())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(1, NOW, NOW, List.of(new AuditEntry(1, DocumentAction.SUBMITTED, AUTHOR, NOW))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(1, NOW, NOW, List.of(new AuditEntry(1, DocumentAction.CREATED, AUTHOR, NOW.plusSeconds(1)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(2, NOW, NOW.plusSeconds(1), List.of(new AuditEntry(1, DocumentAction.CREATED, AUTHOR, NOW), new AuditEntry(2, DocumentAction.SUBMITTED, AUTHOR, NOW))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(2, NOW, NOW, List.of(new AuditEntry(1, DocumentAction.CREATED, AUTHOR, NOW), new AuditEntry(3, DocumentAction.SUBMITTED, AUTHOR, NOW))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> restore(3, NOW, NOW, List.of(new AuditEntry(1, DocumentAction.CREATED, AUTHOR, NOW), new AuditEntry(2, DocumentAction.SUBMITTED, AUTHOR, NOW.plusSeconds(1)), new AuditEntry(3, DocumentAction.REVIEW_STARTED, AUTHOR, NOW))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Document restore(long version, Instant created, Instant updated, List<AuditEntry> audit) {
        return Document.restore(draft().id(), new TemplateRef("report"), "Report", List.of(), new DocumentStatus.Draft(), version, created, updated, audit);
    }

    private static String expectedNext(String status, DocumentAction action) {
        return switch (status + ":" + action) {
            case "Draft:CONTENT_UPDATED", "Rejected:CONTENT_UPDATED" -> status;
            case "Draft:SUBMITTED", "Rejected:SUBMITTED" -> "Submitted";
            case "Submitted:REVIEW_STARTED" -> "InReview";
            case "InReview:APPROVED" -> "Approved";
            case "InReview:REJECTED" -> "Rejected";
            case "Approved:PUBLISHED" -> "Published";
            case "Published:ARCHIVED" -> "Archived";
            default -> null;
        };
    }

    static Stream<Document> documentsInEveryState() {
        var draft = draft();
        var submitted = draft.submit(AUTHOR, NOW);
        var inReview = submitted.startReview(AUTHOR, NOW);
        var approved = inReview.approve(AUTHOR, NOW);
        var rejected = inReview.reject(AUTHOR, NOW);
        var published = approved.publish(AUTHOR, NOW);
        return Stream.of(draft, submitted, inReview, approved, rejected, published, published.archive(AUTHOR, NOW));
    }

    private static Document draft() {
        return Document.create(new DocumentId(UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4")),
                new TemplateRef("report"), "Report", List.of(new Section("Summary", "Content")), AUTHOR, NOW);
    }
}
