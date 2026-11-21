# ADR 0004 — Outbox transactionnel et contrat d'événement versionné

Statut : accepté pour le kata, 21 novembre 2026. S8.

## Contexte

Les mutations d'un document sont déjà transactionnelles et versionnées, mais un autre contexte veut réagir à ces mutations. Publier vers un broker depuis le contrôleur ou le service applicatif crée deux problèmes connus : un échec réseau après le commit perd l'événement, et un échec avant le commit publie un événement pour une écriture annulée. Aucun des deux n'est acceptable pour une reprise sur incident.

## Décision

### Écrire l'événement avec la mutation

`outbox_events` reçoit une ligne par mutation réussie, dans la même transaction que l'agrégat, les sections, l'audit et l'instantané de version. Le port `EventOutbox` n'a que quatre opérations (`append`, `claimDue`, `markPublished`, `markFailed`) et la frontière transactionnelle est un port explicite (`Transactions.inTransaction`) : l'application reste sans framework, mais le caractère atomique est écrit noir sur blanc dans le cas d'usage.

La contrainte `UNIQUE (document_id, business_version)` rend un doublon impossible même en cas de rejeu du code appelant.

### Un relais qui assume l'at-least-once

`OutboxRelay` (application) publie par lots et enregistre le résultat. Le claim utilise `FOR UPDATE SKIP LOCKED` et pousse l'échéance à `now() + lease` : deux relais prennent des lots disjoints, et un relais qui meurt avant publication libère son lot à l'expiration du bail. Un échec incrémente `attempts`, stocke `last_error` et applique un backoff exponentiel plafonné.

Conséquence assumée : un événement peut être publié deux fois. La déduplication est une obligation du consommateur, pas une promesse du producteur.

### Un contrat explicite et testé

`contracts/events/document-lifecycle-changed-v1.json` est l'instantané du format sur le fil. Un test compare le JSON produit champ par champ à ce fichier : ajouter, renommer ou retirer un champ casse le test et impose une nouvelle version (`schemaVersion`). Le thème Kafka est versionné (`acd.document.lifecycle.v1`) et la clé est l'identifiant du document, donc les événements d'un même document restent ordonnés dans une partition.

La version est stockée dans la table d'outbox : une ligne écrite avant une montée de version est publiée avec la version à laquelle elle appartient.

## Vérification

- `./mvnw verify` : 171 tests backend, dont 9 tests d'outbox en base réelle, 2 tests Kafka et un test de bout en bout HTTP → outbox → relais → broker.
- Le test `rollsBackTheDocumentChangeWhenTheOutboxRejectsTheEvent` prouve qu'un échec d'écriture d'événement annule la mutation.
- `aSecondRelaySkipsEventsBeingClaimedByAnother` prouve le `SKIP LOCKED`, `twoRelaysNeverPublishTheSameEventTwice` prouve le résultat avec deux relais concurrents sur vingt événements.
- Parcours local réel : création, soumission, revue, approbation observées dans Kafka et dans le service de revue ; deux rejeux du même événement n'ajoutent ni ligne d'inbox ni tâche.

## Alternatives écartées

- **Publier depuis le service applicatif** : perd des événements sur panne réseau ou publie des mutations annulées.
- **Debezium / capture de journal** : ajoute une plateforme à exploiter pour un kata qui doit rester lisible.
- **Écrire l'événement après le commit** : fenêtre de perte entre les deux transactions.
- **Exiger un ordre parfait sans claim ni bail** : faux dès qu'un relais redémarre au milieu d'un lot.
