package fr.walid.acd.contribution.application.service;

import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentSort;
import fr.walid.acd.contribution.application.query.DocumentSummary;
import fr.walid.acd.contribution.domain.Actor;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import fr.walid.acd.contribution.domain.TemplateRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentQueryServiceTest {
    @Test
    void readsTheStoredAggregateAndReportsMissingDocuments() {
        var repository = new FakeDocumentRepository();
        var service = new DocumentQueryService(repository);
        var id = new DocumentId(UUID.randomUUID());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(DocumentNotFoundException.class);
        var document = Document.create(id, new TemplateRef("report"), "Report", List.of(), new Actor("walid"), Instant.EPOCH);
        repository.add(document);
        assertThat(service.get(id)).isSameAs(document);
    }

    @Test
    void preservesTheRequestedWindowFiltersSortAndFilteredTotal() {
        var repository = new FakeDocumentRepository();
        var search = new DocumentSearch(3, 5, new DocumentSort(DocumentSort.Field.UPDATED_AT, DocumentSort.Direction.DESC),
                Optional.of(new DocumentStatus.InReview()), " report ");
        repository.searchResult = new DocumentPage(42, List.of(new DocumentSummary(new DocumentId(UUID.randomUUID()),
                "Report", new DocumentStatus.InReview(), 3, Instant.EPOCH)));

        var result = new DocumentQueryService(repository).search(search);

        assertThat(repository.lastSearch).isSameAs(search);
        assertThat(search.query()).isEqualTo("report");
        assertThat(result).isSameAs(repository.searchResult);
        assertThat(result.rowCount()).isEqualTo(42);
    }
}
