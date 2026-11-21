# Séquences

Dix parcours qui expliquent le comportement réel.

## 1. Créer un brouillon depuis l'interface

```mermaid
sequenceDiagram
  autonumber
  actor U as Utilisateur
  participant P as DocumentCreatePage
  participant F as DocumentForm
  participant API as DocumentApi
  participant R as DocumentController
  participant S as DocumentCommandService
  participant D as Document
  participant P2 as JpaDocumentRepository
  participant DB as PostgreSQL

  U->>F: modèle, titre, sections
  F->>F: validation (vide, longueurs)
  F-->>P: saved(valeurs normalisées)
  P->>API: create(...)
  API->>R: POST /api/documents + X-User
  R->>R: @Valid sur le DTO
  R->>S: CreateDocumentCommand
  S->>D: Document.create(id, ..., maintenant)
  D-->>S: Draft v1 + audit CREATED
  S->>P2: add(document)
  P2->>DB: INSERT document, sections, audit, instantané v1
  P2-->>S: ok
  S-->>R: Document
  R->>R: DocumentDetail.from(document)
  R-->>API: 201 + Location + corps
  API-->>P: DocumentDetail
  P->>U: navigation /documents/{id}
```

## 2. Soumettre, avec la règle métier

```mermaid
sequenceDiagram
  autonumber
  actor U as Utilisateur
  participant P as DocumentDetailPage
  participant API as DocumentApi
  participant R as DocumentController
  participant S as DocumentCommandService
  participant D as Document
  participant P2 as JpaDocumentRepository

  U->>P: clic Soumettre
  P->>API: transition(id, submit, version)
  API->>R: POST /{id}/submit {expectedVersion}
  R->>S: ChangeDocumentStatusCommand
  S->>P2: findById(id)
  S->>S: version attendue == version lue ?
  S->>D: submit(author, maintenant)
  alt aucune section valide
    D-->>S: DomainException EMPTY_DOCUMENT
    S-->>R: exception
    R-->>API: 409 problem+json code=EMPTY_DOCUMENT
    API-->>P: ApiFailure
    P->>U: alerte avec le detail du serveur
  else sections valides
    D-->>S: Submitted v+1 + audit SUBMITTED
    S->>P2: save(document, version attendue)
    P2->>P2: racine, sections, audit, instantané
    P2-->>S: ok (même transaction)
    S-->>R: Document
    R-->>API: 200 DocumentDetail
    API-->>P: DocumentDetail
    P->>U: badge Soumis, action suivante
  end
```

## 3. Deux écrivains concurrents

```mermaid
sequenceDiagram
  autonumber
  participant A as Client A
  participant B as Client B
  participant S as Service
  participant DB as PostgreSQL

  A->>S: approve(id, expectedVersion=3)
  B->>S: reject(id, expectedVersion=3)
  S->>DB: A lit la version 3
  S->>DB: B lit la version 3
  S->>DB: A : UPDATE documents ... WHERE lock_version = n
  DB-->>S: 1 ligne, COMMIT (version 4)
  S->>DB: B : UPDATE documents ... WHERE lock_version = n
  DB-->>S: 0 ligne
  S-->>B: DocumentConflictException vers 409
  Note over A,B: un seul gagnant, aucune écriture partielle
```

Le perdant ne laisse ni section, ni audit, ni instantané : les quatre écritures sont dans une transaction.

## 4. Charger un bloc de grille

```mermaid
sequenceDiagram
  autonumber
  participant G as AG Grid (infinite)
  participant DS as document-datasource
  participant API as DocumentApi
  participant R as DocumentController
  participant P as JpaDocumentRepository

  G->>DS: getRows(startRow 0, endRow 25, sortModel)
  DS->>DS: toQuery vers sort=updatedAt:desc
  DS->>API: list(query)
  API->>R: GET /api/documents?startRow=0&endRow=25&sort=...
  R->>R: validation de la fenêtre et du tri
  R->>P: search(DocumentSearch)
  P->>P: count(filtres) puis rows(fenêtre)
  P-->>R: DocumentPage(rowCount, rows)
  R-->>API: 200 {rowCount, rows}
  API-->>DS: page
  DS->>G: successCallback(rows, rowCount)
  Note over G: bloc suivant seulement au défilement
```

Un tri d'en-tête relance ce cycle une fois. Un changement de filtre de la barre d'outils purge les blocs avant de relancer.

## 5. Lire une version enregistrée

```mermaid
sequenceDiagram
  autonumber
  actor U as Utilisateur
  participant P as DocumentDetailPage
  participant API as DocumentApi
  participant R as DocumentController
  participant Q as DocumentQueryService
  participant Rep as JpaDocumentRepository
  participant DB as PostgreSQL

  U->>P: clic sur une entrée d'audit (version 1)
  P->>API: version(id, 1)
  API->>R: GET /api/documents/{id}/versions/1
  R->>R: @Min(1) sur le paramètre version
  R->>Q: version(id, 1)
  Q->>Rep: version(id, 1)
  Rep->>DB: SELECT audit + document_versions + sections figées
  alt instantané absent
    DB-->>Rep: vide
    Q->>Rep: exists(id)
    Rep-->>Q: document présent
    Q-->>R: DocumentVersionNotFoundException
    R-->>API: 404 code=DOCUMENT_VERSION_NOT_FOUND
  else instantané présent
    DB-->>Rep: titre, sections et statut de l'époque
    Rep-->>Q: DocumentVersion
    Q-->>R: DocumentVersion
    R-->>API: 200 DocumentVersionResponse
    API-->>P: DocumentVersion
    P->>U: panneau en lecture seule, marqué « version 1 »
  end
```

La version courante reste affichée en haut : le panneau montre des valeurs figées, pas un formulaire.

## 6. Publier un événement par le relais

```mermaid
sequenceDiagram
  autonumber
  participant S as Planificateur
  participant L as OutboxRelay
  participant O as EventOutbox
  participant DB as PostgreSQL
  participant P as KafkaEventPublisher
  participant K as Kafka

  S->>L: relay(batchSize)
  L->>O: claimDue(50, maintenant + bail)
  O->>DB: UPDATE ... FOR UPDATE SKIP LOCKED<br/>RETURNING id, colonnes, attempts
  DB-->>O: lignes réclamées
  O-->>L: OutboxEntry[]
  loop chaque événement
    L->>P: publish(event)
    P->>K: send(clé = documentId, JSON + en-têtes)
    K-->>P: accusé
    alt accusé reçu
      Note over L: ajouté à la liste des publiés
    else échec ou délai dépassé
      L->>O: markFailed(id, erreur, maintenant + backoff)
      O->>DB: attempts + 1, last_error, next_attempt_at
    end
  end
  L->>O: markPublished(ids, maintenant)
  L-->>S: nombre publié
```

Un relais interrompu avant la publication ne perd rien : le bail expire et le lot est repris. C'est la raison de la déduplication côté consommateur.

## 7. Consommer un événement sans effet dupliqué

```mermaid
sequenceDiagram
  autonumber
  participant K as Kafka
  participant Li as DocumentLifecycleListener
  participant J as EventEnvelopeJson
  participant R as ReviewService
  participant DB as PostgreSQL

  K->>Li: message (clé = documentId)
  Li->>J: read(payload)
  alt schéma inconnu ou champ manquant
    J-->>Li: EventContractException
    Li-->>K: erreur non réessayable
    Note over K: publication sur <topic>.DLT
  else contrat v1
    J-->>Li: DocumentLifecycleEvent
    Li->>R: handle(event)
    R->>DB: INSERT review_inbox ... ON CONFLICT DO NOTHING
    alt déjà traité
      DB-->>R: 0 ligne
      R-->>Li: false
      Note over Li: ignoré, offset commité
    else nouveau
      R->>DB: avance last_version si version > dernière
      alt version périmée
        DB-->>R: 0 ligne
        R-->>Li: false
      else version courante
        R->>DB: ouvre ou clôt la tâche
        R-->>Li: true
      end
    end
  end
```

## 8. Purger les événements publiés

```mermaid
sequenceDiagram
  autonumber
  participant S as Planificateur
  participant P as OutboxPurge
  participant O as EventOutbox
  participant DB as PostgreSQL
  participant M as Micrometer

  S->>P: purge()
  P->>P: seuil = maintenant - rétention
  P->>O: purgePublishedBefore(seuil)
  O->>DB: DELETE ... WHERE published_at IS NOT NULL AND published_at < seuil
  DB-->>O: nombre de lignes
  O-->>P: supprimées
  P-->>S: supprimées
  S->>M: acd.outbox.purged += supprimées
  Note over S: un événement non publié<br/>n'est jamais supprimé
```

## 9. Importer un fichier

```mermaid
sequenceDiagram
  autonumber
  actor U as Importateur
  participant CLI as make import-documents
  participant S as JoltImportSource
  participant M as Jolt
  participant I as DocumentImport
  participant C as DocumentCommands
  participant DB as PostgreSQL
  participant R as Relais

  U->>CLI: import-documents MODE=approved
  CLI->>S: read(source, fichier)
  S->>S: CSV → JSON → regroupement
  S->>M: spec Jolt
  M-->>S: document mappé
  S-->>CLI: lot (enregistrements + refus structurels)
  CLI->>I: importDocuments(lot, options)
  loop chaque ligne du fichier
    I->>DB: référence déjà importée ?
    alt déjà présente
      I-->>CLI: ALREADY_IMPORTED
    else nouvelle
      I->>C: create, submit, startReview, approve
      C->>DB: document + audit + versions + outbox + référence (une transaction)
      I-->>CLI: IMPORTED v4 Approved
    end
  end
  CLI->>R: attendre le drain de l'outbox
  R-->>CLI: pending = 0
  CLI->>U: rapport + code de sortie 0 ou 2
```

## 10. Traduire une erreur en problème HTTP

```mermaid
sequenceDiagram
  participant C as Contrôleur
  participant H as ApiExceptionHandler
  actor U as Client

  C->>H: DocumentNotFoundException
  H-->>U: 404 code=DOCUMENT_NOT_FOUND
  C->>H: DocumentVersionNotFoundException
  H-->>U: 404 code=DOCUMENT_VERSION_NOT_FOUND
  C->>H: DocumentConflictException
  H-->>U: 409 code=VERSION_CONFLICT
  C->>H: DomainException
  H-->>U: 409 code=INVALID_TRANSITION / EMPTY_DOCUMENT / NON_MONOTONIC_TIME
  C->>H: IllegalArgumentException, DTO invalide
  H-->>U: 400 code=INVALID_REQUEST avec errors
  C->>H: Exception inattendue
  H-->>U: 500 code=INTERNAL_ERROR
  Note over H: détail masqué et journalisé côté serveur
```

Toutes les réponses d'erreur sont en `application/problem+json` avec un `code` stable, utilisé par l'interface.
