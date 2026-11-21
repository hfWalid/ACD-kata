# Modèle de données

Cinq tables, un agrégat. Le schéma est créé par Liquibase ; Hibernate le valide seulement.

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
```

## Trois lectures d'un même document

| Table | Répond à | Écrit quand |
|---|---|---|
| `documents` + `document_sections` | l'état courant | à chaque action |
| `document_audit` | qui a fait quoi et quand | à chaque action, dans l'agrégat |
| `document_versions` + `document_version_sections` | ce à quoi ressemblait le document une fois l'action réussie | à chaque action, par la persistance |

L'audit est du domaine ; l'instantané est une projection de persistance, ce qui dispense de rejouer l'historique pour lire une version.

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

## Règles de lecture

| Opération | Isolation | Charge |
|---|---|---|
| `get` | `REPEATABLE_READ` | racine + sections + audit |
| `version` | `REPEATABLE_READ` | une ligne d'audit + un instantané |
| `search` | `REPEATABLE_READ` | projection seulement, aucune collection |

Le total et les lignes d'une recherche partagent les mêmes prédicats : statut, `q` littéral sur le titre (insensible à la casse, `%` et `_` échappés), fenêtre `[startRow, endRow[` de 1 à 500 lignes.
