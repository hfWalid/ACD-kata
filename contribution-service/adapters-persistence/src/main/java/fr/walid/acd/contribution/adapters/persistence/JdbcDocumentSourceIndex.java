package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.exception.SourceRefAlreadyUsedException;
import fr.walid.acd.contribution.application.importing.SourceRef;
import fr.walid.acd.contribution.application.port.out.DocumentSourceIndex;
import fr.walid.acd.contribution.domain.DocumentId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of the import's idempotence key. It uses the ambient transaction, so
 * the reference is stored inside the transaction that creates the document.
 */
@Component
public class JdbcDocumentSourceIndex implements DocumentSourceIndex {
    private static final String FIND = """
            SELECT document_id FROM document_sources WHERE source_name = ? AND source_reference = ?
            """;
    private static final String INSERT = """
            INSERT INTO document_sources (document_id, source_name, source_reference)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcDocumentSourceIndex(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DocumentId> find(SourceRef sourceRef) {
        return jdbc.query(FIND, resultSet -> resultSet.next()
                        ? Optional.of(new DocumentId(resultSet.getObject(1, UUID.class)))
                        : Optional.empty(),
                sourceRef.source(), sourceRef.value());
    }

    /**
     * The unique constraint is the arbiter: two imports racing on the same reference both pass
     * {@link #find}, and the loser sees this exception instead of a duplicate document.
     */
    @Override
    public void record(SourceRef sourceRef, DocumentId documentId) {
        try {
            jdbc.update(INSERT, documentId.value(), sourceRef.source(), sourceRef.value());
        } catch (DuplicateKeyException e) {
            throw new SourceRefAlreadyUsedException(sourceRef);
        }
    }
}
