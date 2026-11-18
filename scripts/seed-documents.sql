-- Optional local seed for the S6 grid exercise: 10 000 documents.
-- It writes directly to the Compose volume, so every seeded row stays a coherent
-- aggregate: business version = audit rows, last audit instant = updated_at.
-- Documents 1, 11, 21… are InReview (version 3); the others are Draft (version 1).
-- Safe to re-run: existing identifiers are skipped.

WITH seed AS (
    SELECT
        n,
        md5('acd-seed-' || n)::uuid AS id,
        now() - (n || ' minutes')::interval AS created_at,
        n % 10 = 0 AS in_review
    FROM generate_series(1, 10000) AS n
)
INSERT INTO documents (id, template_ref, title, status, business_version, lock_version, created_at, updated_at)
SELECT
    id,
    'notice-v1',
    'Document de démonstration ' || lpad(n::text, 5, '0'),
    CASE WHEN in_review THEN 'InReview' ELSE 'Draft' END,
    CASE WHEN in_review THEN 3 ELSE 1 END,
    0,
    created_at,
    CASE WHEN in_review THEN created_at + interval '2 minutes' ELSE created_at END
FROM seed
ON CONFLICT (id) DO NOTHING;

INSERT INTO document_audit (id, document_id, business_version, action, author, occurred_at)
SELECT gen_random_uuid(), d.id, 1, 'CREATED', 'seed', d.created_at
FROM documents d
WHERE d.title LIKE 'Document de démonstration %'
ON CONFLICT (document_id, business_version) DO NOTHING;

INSERT INTO document_audit (id, document_id, business_version, action, author, occurred_at)
SELECT gen_random_uuid(), d.id, 2, 'SUBMITTED', 'seed', d.created_at + interval '1 minute'
FROM documents d
WHERE d.status = 'InReview' AND d.title LIKE 'Document de démonstration %'
ON CONFLICT (document_id, business_version) DO NOTHING;

INSERT INTO document_audit (id, document_id, business_version, action, author, occurred_at)
SELECT gen_random_uuid(), d.id, 3, 'REVIEW_STARTED', 'seed', d.created_at + interval '2 minutes'
FROM documents d
WHERE d.status = 'InReview' AND d.title LIKE 'Document de démonstration %'
ON CONFLICT (document_id, business_version) DO NOTHING;
