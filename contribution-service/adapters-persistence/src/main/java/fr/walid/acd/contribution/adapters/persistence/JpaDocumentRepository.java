package fr.walid.acd.contribution.adapters.persistence;

import fr.walid.acd.contribution.application.exception.DocumentConflictException;
import fr.walid.acd.contribution.application.exception.DocumentNotFoundException;
import fr.walid.acd.contribution.application.port.out.DocumentRepository;
import fr.walid.acd.contribution.application.query.DocumentPage;
import fr.walid.acd.contribution.application.query.DocumentSearch;
import fr.walid.acd.contribution.application.query.DocumentSort;
import fr.walid.acd.contribution.application.query.DocumentSummary;
import fr.walid.acd.contribution.application.query.DocumentVersion;
import fr.walid.acd.contribution.domain.Document;
import fr.walid.acd.contribution.domain.DocumentId;
import fr.walid.acd.contribution.domain.DocumentStatus;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class JpaDocumentRepository implements DocumentRepository {

    private final EntityManager entityManager;

    public JpaDocumentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Document> findById(DocumentId id) {
        Objects.requireNonNull(id, "Document id is required");
        return Optional.ofNullable(entityManager.find(DocumentEntity.class, id.value()))
                .map(DocumentEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(DocumentId id) {
        Objects.requireNonNull(id, "Document id is required");
        return entityManager.find(DocumentEntity.class, id.value()) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentVersion> version(DocumentId id, long version) {
        Objects.requireNonNull(id, "Document id is required");
        var entry = entityManager.createQuery("""
                        select entry from DocumentAuditEntity entry
                        where entry.document.id = :documentId and entry.businessVersion = :version
                        """, DocumentAuditEntity.class)
                .setParameter("documentId", id.value())
                .setParameter("version", version)
                .getResultStream()
                .findFirst();
        return entry.flatMap(audit -> entityManager.createQuery("""
                        select stored from DocumentVersionEntity stored
                        where stored.documentId = :documentId and stored.businessVersion = :version
                        """, DocumentVersionEntity.class)
                .setParameter("documentId", id.value())
                .setParameter("version", version)
                .getResultStream()
                .findFirst()
                .map(snapshot -> snapshot.toDomain(audit.toDomain())));
    }

    @Override
    public void add(Document document) {
        Objects.requireNonNull(document, "Document is required");
        if (document.version() != 1) {
            throw new IllegalArgumentException("Only a new document can be added");
        }
        try {
            entityManager.persist(DocumentEntity.from(document));
            entityManager.flush();
            entityManager.persist(DocumentVersionEntity.from(document));
        } catch (PersistenceException failure) {
            throwConflictOrRethrow(document.id(), failure);
        }
    }

    @Override
    public void save(Document document, long expectedVersion) {
        Objects.requireNonNull(document, "Document is required");
        if (expectedVersion < 1 || document.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("A saved document must advance the expected version by one");
        }
        var entity = entityManager.find(DocumentEntity.class, document.id().value());
        if (entity == null) {
            throw new DocumentNotFoundException(document.id());
        }
        if (entity.businessVersion() != expectedVersion) {
            throw new DocumentConflictException(document.id());
        }
        try {
            entity.advanceHeader(document);
            // Acquire the root's optimistic compare-and-set before loading its
            // lazy children. A concurrent commit must be a version conflict,
            // not a mix of the old root and the newly committed audit/content.
            entityManager.flush();
            entity.applyContentAndAudit(document);
            // Both flushes are inside one transaction; any failure rolls back
            // the document, its sections and its audit together.
            entityManager.flush();
            entityManager.persist(DocumentVersionEntity.from(document));
        } catch (PersistenceException failure) {
            throwConflictOrRethrow(document.id(), failure);
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DocumentPage search(DocumentSearch search) {
        Objects.requireNonNull(search, "Document search is required");
        var builder = entityManager.getCriteriaBuilder();
        var count = builder.createQuery(Long.class);
        var countedDocument = count.from(DocumentEntity.class);
        count.select(builder.count(countedDocument)).where(predicates(builder, countedDocument, search));
        long total = bindFilters(entityManager.createQuery(count), search).getSingleResult();

        var rows = builder.createTupleQuery();
        var document = rows.from(DocumentEntity.class);
        rows.multiselect(document.get("id").alias("id"), document.get("title").alias("title"),
                document.get("status").alias("status"), document.get("businessVersion").alias("version"),
                document.get("updatedAt").alias("updatedAt"));
        rows.where(predicates(builder, document, search));
        Path<?> sortedField = switch (search.sort().field()) {
            case TITLE -> document.get("title");
            case UPDATED_AT -> document.get("updatedAt");
            case STATUS -> document.get("status");
            case VERSION -> document.get("businessVersion");
        };
        var primaryOrder = search.sort().direction() == DocumentSort.Direction.ASC
                ? builder.asc(sortedField) : builder.desc(sortedField);
        rows.orderBy(primaryOrder, builder.asc(document.get("id")));
        var query = bindFilters(entityManager.createQuery(rows), search)
                .setFirstResult(search.startRow())
                .setMaxResults(search.endRow() - search.startRow());
        return new DocumentPage(total, query.getResultList().stream().map(this::summary).toList());
    }

    private Predicate[] predicates(CriteriaBuilder builder, Root<DocumentEntity> document, DocumentSearch search) {
        var filters = new ArrayList<Predicate>();
        if (search.statusFilter().isPresent()) {
            filters.add(builder.equal(document.get("status"), builder.parameter(String.class, "status")));
        }
        if (!search.query().isEmpty()) {
            filters.add(builder.like(builder.lower(document.get("title")),
                    builder.parameter(String.class, "query"), '\\'));
        }
        return filters.toArray(Predicate[]::new);
    }

    private <T> TypedQuery<T> bindFilters(TypedQuery<T> query, DocumentSearch search) {
        search.statusFilter().ifPresent(status -> query.setParameter("status", status.name()));
        if (!search.query().isEmpty()) {
            String literal = search.query().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            query.setParameter("query", "%" + literal + "%");
        }
        return query;
    }

    private DocumentSummary summary(Tuple row) {
        return new DocumentSummary(new DocumentId(row.get("id", UUID.class)), row.get("title", String.class),
                DocumentStatus.fromName(row.get("status", String.class)), row.get("version", Long.class),
                row.get("updatedAt", Instant.class));
    }

    private void throwConflictOrRethrow(DocumentId id, PersistenceException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof EntityExistsException
                    || cause instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())) {
                throw new DocumentConflictException(id);
            }
        }
        throw failure;
    }
}
