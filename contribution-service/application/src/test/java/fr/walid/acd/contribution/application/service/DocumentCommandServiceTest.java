package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.port.in.CreateDocumentCommand;
import fr.walid.acd.contribution.application.port.in.ChangeDocumentStatusCommand;
import fr.walid.acd.contribution.application.port.in.UpdateDocumentCommand;
import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.DomainException;
import fr.walid.acd.contribution.domain.Section;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCommandServiceTest {
    private static final UUID ID = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Actor AUTHOR = new Actor("walid");
    @Test
    void createsAndStoresAnAuditedDraftWithInjectedTimeAndIdentity() {
        var repository = new FakeDocumentRepository();
        var id = UUID.fromString("5f476a52-0145-4e1e-9366-d61b8e529ff4");
        var now = Instant.parse("2026-11-17T10:00:00Z");
        var service = new DocumentCommandService(repository, Clock.fixed(now, ZoneOffset.UTC), () -> id);

        var document = service.create(new CreateDocumentCommand(new TemplateRef("report"), "Report", List.of(), new Actor("walid")));

        assertThat(document.id().value()).isEqualTo(id);
        assertThat(document.status()).isEqualTo(new DocumentStatus.Draft());
        assertThat(document.createdAt()).isEqualTo(now);
        assertThat(repository.findById(document.id())).contains(document);
    }

    @Test
    void editsSubmitsReviewsRejectsCorrectsApprovesPublishesAndArchives() {
        var repository = new FakeDocumentRepository();
        var service = service(repository);
        var document = create(service);
        document = service.update(new UpdateDocumentCommand(document.id(), document.version(), "Updated", List.of(new Section("Summary", "Content")), AUTHOR));
        document = service.submit(change(document));
        document = service.startReview(change(document));
        document = service.reject(change(document));
        document = service.update(new UpdateDocumentCommand(document.id(), document.version(), "Corrected", document.sections(), AUTHOR));
        document = service.submit(change(document));
        document = service.startReview(change(document));
        document = service.approve(change(document));
        document = service.publish(change(document));
        document = service.archive(change(document));

        assertThat(document.status()).isEqualTo(new DocumentStatus.Archived());
        assertThat(document.title()).isEqualTo("Corrected");
        assertThat(document.version()).isEqualTo(11);
        assertThat(document.auditTrail()).hasSize(11);
        assertThat(repository.saves).isEqualTo(10);
        assertThat(repository.findById(document.id())).contains(document);
    }

    @Test
    void rejectsAStaleClientBeforeChangingOrSavingTheDocument() {
        var repository = new FakeDocumentRepository();
        var service = service(repository);
        var original = create(service);
        var current = service.submit(change(original));

        assertThatThrownBy(() -> service.startReview(change(original)))
                .isInstanceOfSatisfying(DocumentConflictException.class, error -> assertThat(error.id()).isEqualTo(original.id()));
        assertThat(repository.findById(original.id())).contains(current);
        assertThat(repository.saves).isEqualTo(1);
    }

    @Test
    void propagatesAnAtomicRepositoryConflictWithoutMutatingTheLoadedSnapshot() {
        var repository = new FakeDocumentRepository();
        var service = service(repository);
        var original = create(service);
        repository.rejectNextSave = true;

        assertThatThrownBy(() -> service.submit(change(original))).isInstanceOf(DocumentConflictException.class);
        assertThat(repository.findById(original.id())).contains(original);
        assertThat(original.version()).isEqualTo(1);
        assertThat(original.auditTrail()).hasSize(1);
        assertThat(repository.saves).isZero();
    }

    @Test
    void doesNotSaveAnIllegalDomainTransition() {
        var repository = new FakeDocumentRepository();
        var service = service(repository);
        var document = create(service);

        assertThatThrownBy(() -> service.publish(change(document))).isInstanceOf(DomainException.class);
        assertThat(repository.findById(document.id())).contains(document);
        assertThat(repository.saves).isZero();
    }

    @Test
    void reportsAMissingDocumentAndDoesNotCreateItAccidentally() {
        var repository = new FakeDocumentRepository();
        var id = new DocumentId(ID);
        assertThatThrownBy(() -> service(repository).submit(new ChangeDocumentStatusCommand(id, 1, AUTHOR)))
                .isInstanceOfSatisfying(DocumentNotFoundException.class, error -> assertThat(error.id()).isEqualTo(id));
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.saves).isZero();
    }

    private static DocumentCommandService service(FakeDocumentRepository repository) {
        return new DocumentCommandService(repository, Clock.fixed(NOW, ZoneOffset.UTC), () -> ID);
    }

    private static Document create(DocumentCommandService service) {
        return service.create(new CreateDocumentCommand(new TemplateRef("report"), "Report", List.of(new Section("Summary", "Content")), AUTHOR));
    }

    private static ChangeDocumentStatusCommand change(Document document) {
        return new ChangeDocumentStatusCommand(document.id(), document.version(), AUTHOR);
    }
}
