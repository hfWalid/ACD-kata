package fr.walid.acd.contribution.application.query;

import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentQueryTest {
    private static final DocumentSort SORT = new DocumentSort(DocumentSort.Field.UPDATED_AT, DocumentSort.Direction.DESC);

    @Test
    void acceptsArbitraryOffsetsAndBothBoundaryWindowSizes() {
        assertThat(search(3, 4, null).query()).isEmpty();
        assertThat(search(1_000_000, 1_000_500, " \n").query()).isEmpty();
        assertThat(search(0, 500, "a".repeat(200)).query()).hasSize(200);
    }

    @Test
    void rejectsInvalidRangesAndOversizedSearchQueries() {
        for (int[] range : new int[][]{{-1, 10}, {1, 1}, {4, 3}, {0, 501}, {1_000_001, 1_000_002}, {0, Integer.MAX_VALUE}}) {
            assertThatThrownBy(() -> search(range[0], range[1], "")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> search(0, 1, "a".repeat(201))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentSearch(0, 1, null, Optional.empty(), "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DocumentSearch(0, 1, SORT, null, "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DocumentSort(null, DocumentSort.Direction.ASC)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DocumentSort(DocumentSort.Field.TITLE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void keepsPageRowsImmutableAndRejectsAnInconsistentTotal() {
        var row = new DocumentSummary(new DocumentId(UUID.randomUUID()), "Report", new DocumentStatus.Draft(), 1, Instant.EPOCH);
        var rows = new ArrayList<>(List.of(row));
        var page = new DocumentPage(10, rows);
        rows.clear();
        assertThat(page.rows()).containsExactly(row);
        assertThatThrownBy(() -> page.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new DocumentPage(-1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentPage(0, List.of(row))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentSummary(row.id(), row.title(), row.status(), 0, row.updatedAt()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DocumentSearch search(int start, int end, String query) {
        return new DocumentSearch(start, end, SORT, Optional.empty(), query);
    }
}
