# Événements

Un événement par mutation, écrit avec elle, publié après elle, consommé au moins une fois.

## Chaîne complète

```mermaid
flowchart LR
  M["Mutation<br/>commande"] --> T["Transaction<br/>document + audit + instantané"]
  T --> O[("outbox_events")]
  O --> R["Relais<br/>claim par lot"]
  R --> K[["Kafka<br/>acd.document.lifecycle.v1"]]
  K --> C["review-service<br/>consommateur"]
  C --> I[("review_inbox<br/>review_tasks")]
  R -->|"échec"| B["attempts + backoff"]
  B --> O
  C -->|"contrat illisible"| D[["...v1.DLT"]]
```

## Contrat v1

Fichier de référence : [`contracts/events/document-lifecycle-changed-v1.json`](../contracts/events/document-lifecycle-changed-v1.json).

| Champ | Type | Rôle |
|---|---|---|
| `eventId` | uuid | identité de l'événement, base de la déduplication |
| `type` | string | `document.lifecycle.changed` |
| `schemaVersion` | int | version du contrat, 1 aujourd'hui |
| `occurredAt` | instant ISO | horodatage de la mutation, pas de la publication |
| `documentId` | uuid | clé Kafka : ordre par document |
| `documentVersion` | long | version métier après la mutation |
| `action` | enum | `CREATED`, `CONTENT_UPDATED`, `SUBMITTED`, … |
| `status` | enum | statut atteint |
| `title` | string | titre courant |
| `actor` | string | auteur pédagogique |

Ajouter un champ, en renommer un ou en retirer un change la version et le topic. Un test compare le JSON produit au fichier : la modification silencieuse casse le build.

## Garanties

| Garantie | Comment | Vérifié par |
|---|---|---|
| Pas d'événement sans mutation validée | écriture dans la même transaction | `rollsBackTheDocumentChangeWhenTheOutboxRejectsTheEvent` |
| Pas de mutation sans événement | l'append échoue, la transaction annule | même test |
| Pas de doublon en base | `UNIQUE (document_id, business_version)` | `refusesTwoEventsForTheSameDocumentVersion` |
| Ordre par document | clé = `documentId`, une partition par clé | `keepsTheEventsOfOneDocumentInVersionOrderOnOnePartition` |
| Reprise après crash du relais | bail dans `next_attempt_at`, `FOR UPDATE SKIP LOCKED` | `claimsAnEventOnceThenAgainOnlyAfterTheLeaseExpires` |
| Deux relais sans recouvrement | claim `SKIP LOCKED` | `aSecondRelaySkipsEventsBeingClaimedByAnother` |
| Livraison au moins une fois | échec → `attempts`, backoff plafonné | `OutboxRelayTest` |
| Rejeu sans effet | inbox + garde de version | `ReviewTaskConsumerIT` |
| Message illisible non bloquant | `DLT` immédiate pour un contrat non supporté | `ReviewTaskConsumerIT` |

**Ce qui n'est pas garanti** : exactly-once de bout en bout. Un événement peut être publié deux fois ; c'est le consommateur qui rend la répétition inoffensive.

## Déduplication

```mermaid
flowchart TD
  A["Message reçu"] --> B{"event_id dans review_inbox ?"}
  B -->|oui| Z["Ignoré, offset commité"]
  B -->|non| C{"version > last_version ?"}
  C -->|non| Z
  C -->|oui| E["Effet appliqué<br/>+ inbox + version, une transaction"]
```

Les deux gardes sont des instructions SQL uniques (`ON CONFLICT`), donc valables même avec plusieurs consommateurs en parallèle.

## États d'un événement

```mermaid
stateDiagram-v2
  [*] --> EnAttente : append avec la mutation
  EnAttente --> Reclame : claim par lot
  Reclame --> Publie : accusé du broker
  Reclame --> EnAttente : échec, attempts + 1, backoff
  Reclame --> Reclame : crash du relais, bail expiré
  Publie --> [*]
```

## Pannes

| Panne | Effet | Reprise |
|---|---|---|
| Broker indisponible | les mutations continuent, l'outbox grossit | le relais reprend au retour du broker |
| Relais arrêté au milieu d'un lot | événements réclamés, non publiés | à l'expiration du bail, un autre relais les reprend |
| Base du consommateur indisponible | offset non commité | Kafka rejoue, la déduplication protège |
| Message au contrat inconnu | `DLT` après deux essais | relecture manuelle, la partition continue |
| Redémarrage du consommateur | rejeu possible du dernier lot | inhérent à l'at-least-once, sans effet |

## Hors périmètre S8

Exactly-once, compaction, rétention configurée, registre de schémas, tests de contrat entre services, saga. Les raisons sont dans [la fiche S8](../docs/phases/S8-events.md).
