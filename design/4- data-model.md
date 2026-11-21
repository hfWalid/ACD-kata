# Modèle de données

Neuf tables : six pour le document et son outbox, trois pour le contexte review. Les schémas sont créés par Liquibase ; Hibernate valide seulement.

```mermaid
erDiagram
  documents ||--o{ document_sections : "ordonne"
  documents ||--o{ document_audit : "trace"
  documents ||--o{ document_versions : "instantané"
  document_versions ||--o{ document_version_sections : "fige"

  documents {
    uuid id PK
    varchar template_ref
    varchar title
    varchar status
    bigint business_version
    bigint lock_version
    timestamptz created_at
    timestamptz updated_at
  }

  document_sections {
    uuid id PK
    uuid document_id FK
    int section_position
    varchar title
    text content
  }

  document_audit {
    uuid id PK
    uuid document_id FK
    bigint business_version
    varchar action
    varchar author
    timestamptz occurred_at
  }

  document_versions {
    uuid id PK
    uuid document_id FK
    bigint business_version
    varchar title
    varchar status
  }

  document_version_sections {
    uuid id PK
    uuid version_id FK
    int section_position
    varchar title
    text content
  }

  outbox_events {
    uuid id PK
    varchar event_type
    integer schema_version
    uuid document_id
    bigint business_version
    varchar action
    varchar status
    varchar title
    varchar actor
    timestamptz occurred_at
    integer attempts
    timestamptz next_attempt_at
    timestamptz published_at
    varchar last_error
  }
```

`outbox_events` n'a pas de clé étrangère vers `documents` : un événement publié doit survivre à la suppression du document qu'il décrit.

## Trois lectures d'un même document

| Table | Répond à | Écrit quand |
|---|---|---|
| `documents` + `document_sections` | l'état courant | à chaque action |
| `document_audit` | qui a fait quoi et quand | à chaque action, dans l'agrégat |
| `document_versions` + `document_version_sections` | ce à quoi ressemblait le document une fois l'action réussie | à chaque action, par la persistance |
| `outbox_events` | quel événement doit partir au broker | à chaque action, dans la même transaction |

L'audit est du domaine ; l'instantané est une projection de persistance, ce qui dispense de rejouer l'historique pour lire une version.

## Contexte review

Schéma PostgreSQL `review`, même instance locale, propriété du second service.

```mermaid
erDiagram
  review_inbox {
    uuid event_id PK
    varchar event_type
    integer schema_version
    uuid document_id
    bigint document_version
    timestamptz received_at
  }

  review_documents {
    uuid document_id PK
    bigint last_version
    timestamptz updated_at
  }

  review_tasks {
    uuid id PK
    uuid document_id
    bigint document_version
    varchar state
    varchar author
    timestamptz opened_at
    timestamptz decided_at
    varchar decided_by
  }
```

| Table | Répond à | Garde |
|---|---|---|
| `review_inbox` | cet événement a-t-il déjà été traité ? | clé primaire sur `event_id` |
| `review_documents` | quelle version a déjà été appliquée ? | `ON CONFLICT DO UPDATE … WHERE last_version < EXCLUDED` |
| `review_tasks` | quelles revues sont ouvertes ? | `UNIQUE (document_id, document_version)` |

Aucune clé étrangère entre les deux schémas : les deux services ne partagent pas de données, seulement des événements.

## Correspondance domaine ↔ tables

| Domaine | Table | Remarque |
|---|---|---|
| `Document` | `documents` | `business_version` = version métier |
| `Document.sections` | `document_sections` | ordre par `section_position` |
| `Document.auditTrail` | `document_audit` | ordre par `business_version` |
| `DocumentStatus` | `status` varchar | `CHECK` sur les 7 valeurs, pas un type Postgres |
| `DocumentAction` | `action` varchar | `CHECK` sur les 8 valeurs |
| `Actor` | `author` varchar(100) | auteur pédagogique, sans authentification |

`lock_version` est technique : il ne sort jamais de l'adapter.

## Contraintes

| Contrainte | But |
|---|---|
| FK `document_id` → `documents(id)` `ON DELETE CASCADE` | pas d'enfant orphelin |
| FK `version_id` → `document_versions(id)` `ON DELETE CASCADE` | pas de section d'instantané orpheline |
| `UNIQUE (document_id, section_position)` | ordre sans doublon |
| `UNIQUE (document_id, business_version)` sur `document_audit` | un audit par version |
| `UNIQUE (document_id, business_version)` sur `document_versions` | un instantané par version |
| `UNIQUE (version_id, section_position)` | ordre figé sans doublon |
| `CHECK business_version >= 1` | version positive |
| `CHECK updated_at >= created_at` | temps cohérent |
| `CHECK length(content) <= 100000` | limite du domaine en base |
| Index `(status, updated_at DESC, id)` | filtre statut + tri par défaut |
| Index `(updated_at DESC, id)` | tri par défaut sans filtre |

Les documents antérieurs à l'instantané gardent l'image de leur version courante (changeset 002). Leurs versions précédentes ne sont pas reconstruites : `GET /versions/{n}` répond `404` pour elles.

## Écriture et concurrence

```mermaid
sequenceDiagram
  autonumber
  participant S as DocumentCommandService
  participant R as JpaDocumentRepository
  participant DB as PostgreSQL

  S->>R: findById(id)  [REPEATABLE_READ]
  R->>DB: SELECT document + sections + audit
  R-->>S: agrégat (version 3)
  Note over S: version attendue comparée<br/>puis action du domaine
  S->>R: save(document, expectedVersion = 3)
  R->>DB: UPDATE documents SET business_version = 4<br/>WHERE id = ? AND lock_version = ?
  R->>DB: flush
  alt lock_version modifié depuis la lecture
    DB-->>R: 0 ligne
    R-->>S: DocumentConflictException → 409
  else version technique libre
    R->>DB: INSERT document_audit, upsert sections
    R->>DB: INSERT document_versions + document_version_sections
    R->>DB: COMMIT (document, sections, audit, instantané)
    R-->>S: agrégat version 4
  end
```

Trois protections complémentaires :

- applicative : `document.version() != expectedVersion` → conflit avant toute écriture ;
- base : `lock_version` en compare-and-set, qui couvre la course entre deux écritures simultanées ;
- transaction : état courant, audit et instantané validés ensemble ou pas du tout.

Le `UPDATE` de la racine est envoyé **avant** le chargement des collections : un écrivain concurrent produit un conflit, jamais un agrégat composé de deux versions.

## Règles du relais

| Règle | Effet |
|---|---|
| `ORDER BY occurred_at, id LIMIT n` avec `FOR UPDATE SKIP LOCKED` | deux relais prennent des lots disjoints |
| Claim = `next_attempt_at = now() + bail` | un relais mort libère son lot à l'expiration |
| Échec = `attempts + 1`, `last_error`, `next_attempt_at = now() + backoff` | reprise différée, plafonnée à 5 minutes |
| Succès = `published_at` renseigné | la ligne ne sera plus réclamée |
| Purge = `DELETE … WHERE published_at < now() - rétention` | la table reste bornée ; un non-publié n'est jamais supprimé |

Les événements publiés restent 7 jours par défaut (configurable), puis sont supprimés : assez pour enquêter après incident, borné pour ne pas transformer une file en archive.

## Règles de lecture

| Opération | Isolation | Charge |
|---|---|---|
| `get` | `REPEATABLE_READ` | racine + sections + audit |
| `version` | `REPEATABLE_READ` | une ligne d'audit + un instantané |
| `search` | `REPEATABLE_READ` | projection seulement, aucune collection |

Le total et les lignes d'une recherche partagent les mêmes prédicats : statut, `q` littéral sur le titre (insensible à la casse, `%` et `_` échappés), fenêtre `[startRow, endRow[` de 1 à 500 lignes.
