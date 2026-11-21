# S8 — Événements, outbox et contexte review

**Statut : implémenté et vérifié localement (Kafka et PostgreSQL réels) le 21 novembre 2026.**

## Ce qui est construit

- **Outbox transactionnel.** Chaque mutation réussie écrit une ligne `outbox_events` dans la même transaction que le document, ses sections, son audit et l'instantané de version. Un échec d'écriture de l'événement annule la mutation.
- **Contrat versionné.** `document.lifecycle.changed` v1 : `eventId`, `type`, `schemaVersion`, `occurredAt`, `documentId`, `documentVersion`, `action`, `status`, `title`, `actor`. Instantané dans [`contracts/events/document-lifecycle-changed-v1.json`](../../contracts/events/document-lifecycle-changed-v1.json), comparé champ par champ par un test.
- **Relais.** `OutboxRelay` réclame des lots avec `FOR UPDATE SKIP LOCKED` et un bail, publie, puis marque publié ou échoué. Backoff exponentiel plafonné (1 s → 5 min), `attempts` et `last_error` en base. Ordonnanceur du contexte `boot`, compteur Micrometer `acd.outbox.published`.
- **Producteur Kafka.** Clé = identifiant du document (ordre par document), `acks=all`, idempotence producteur, en-têtes `eventId`, `eventType`, `schemaVersion`, attente de l'accusé du broker avant de rendre la main.
- **Contexte review.** `review-service` consomme le topic, ouvre une tâche à la soumission, la clôt à l'approbation ou au rejet, et expose `GET /api/review-tasks?state=PENDING`. Base : `review_inbox`, `review_documents`, `review_tasks`.
- **Déduplication.** Deux gardes SQL indépendantes : identifiant d'événement déjà traité, et version inférieure à la dernière appliquée. Un rejeu, y compris hors ordre, ne change rien.
- **Erreurs.** Deux tentatives puis `<topic>.DLT` sur la même partition ; un message dont le contrat n'est pas lisible va directement en `DLT`.
- **Observation.** Jauges `acd.outbox.pending` et `acd.outbox.failed` sur `/actuator/prometheus`, compteurs `acd.outbox.published` et `acd.outbox.purged`. Un retard de broker ou un relais arrêté deviennent visibles sans requête SQL.
- **Rétention.** Purge horaire des événements publiés au-delà de 7 jours (`acd.outbox.purge.*`) ; un événement non publié n'est jamais supprimé.
- **Contrat piloté par le consommateur.** `contracts/events/expectations/` contient les attentes du consommateur (charge utile + effet attendu) ; le consommateur les joue, le producteur les rejoue à travers son domaine et son sérialiseur (`EventContractCompatibilityTest`). Renommer un champ casse la vérification côté producteur.
- **Relais concurrents.** Deux relais sur la même base publient vingt événements sans aucun doublon (`twoRelaysNeverPublishTheSameEventTwice`).
- **Infra locale.** Kafka 3.9.1 (KRaft, un nœud) dans `docker-compose.yml`, `make infra-up` attend sa santé, `make review-run` démarre le consommateur.

## Décisions

- **Outbox plutôt que publication directe** : publier depuis le service applicatif perd un événement si le broker tombe après le commit, ou publie une mutation annulée si le commit échoue. Voir [ADR 0004](../../docs/adr/0004-transactional-outbox-and-event-contract.md).
- **At-least-once assumé** : le relais peut republier après un crash (bail expiré). La déduplication est portée par le consommateur, jamais promise par le producteur.
- **`Transactions` comme port** : l'atomicité est une décision applicative ; seul le mécanisme est délégué à l'adapter. Le service applicatif reste sans framework.
- **La décision reste dans `contribution-service`** : le contexte review possède la tâche, pas le statut du document. Pas de saga, pas d'écriture croisée. Voir [ADR 0005](../../docs/adr/0005-review-context-ownership.md).
- **Un seul topic versionné** plutôt qu'un topic par action : l'ordre par document est plus simple à garantir et l'agrégat reste l'unité.
- **Schéma PostgreSQL `review`** dans l'instance partagée du kata, plutôt qu'une seconde base : la séparation est visible sans ajouter un service d'infrastructure ; une topologie réelle donnerait une base par service.

## Limites assumées

- Kafka est **optionnel** : sans broker, `contribution-service` fonctionne et accumule les événements ; le retard est désormais lisible sur la jauge `acd.outbox.pending` et les refus sur `acd.outbox.failed`.
- Un seul ordonnanceur par instance et un topic à partitions multiples : le débit reste celui d'un relais par instance. Le `SKIP LOCKED` rend plusieurs instances correctes, mais rien ne mesure la répartition.
- Pas de compaction ni de rétention configurée : les topics utilisent les réglages par défaut du broker de test.
- Le service de revue ne décide pas et n'écrit pas dans le document : approuver reste une action de `contribution-service`.
- Le relais publie séquentiellement dans un lot : un broker lent allonge le lot au lieu d'être parallélisé.
- La purge supprime les traces après 7 jours : au-delà, il ne reste que les logs et Kafka pour reconstituer ce qui est parti.
- Pas de broker Pact ni de registre de schémas centralisé : les attentes sont des fichiers versionnés dans le dépôt, vérifiés des deux côtés. Un troisième consommateur demanderait de généraliser l'outillage.
- Pas de tests Cypress de bout en bout (S9).

## Vérification

```sh
./mvnw verify          # 171 tests backend, dont Kafka et PostgreSQL via Testcontainers
make infra-up          # PostgreSQL + Kafka
make backend-run       # 127.0.0.1:8081
make review-run        # 127.0.0.1:8082
```

| Niveau | Test | Ce qu'il prouve |
|---|---|---|
| Application | `OutboxRelayTest` (9) | publication par lot, bail, backoff plafonné, message d'erreur tronqué |
| Application | `OutboxPurgeTest` | seuil de purge calculé depuis la rétention |
| Application | `DocumentCommandServiceTest` | un événement par mutation, aucun si l'écriture échoue ou si le domaine refuse |
| Persistance | `JdbcEventOutboxIT` (9) | atomicité réelle, unicité par version, claim unique puis reprise après expiration du bail, `SKIP LOCKED`, compteurs et purge, deux relais concurrents |
| Messagerie | `EventContractCompatibilityTest` | le producteur émet tout ce que le consommateur déclare attendre |
| Consommateur | `ConsumerContractTest` (4) | chaque attente publiée produit l'effet annoncé |
| Messagerie | `DocumentLifecycleEventJsonTest` | format identique au contrat versionné, échappement du texte libre |
| Messagerie | `KafkaEventPublisherIT` (2) | livraison réelle, clé, en-têtes, ordre par document et par partition |
| Bout en bout | `OutboxPublicationIT` | HTTP → outbox → relais → Kafka, aucun événement pour une mutation refusée, jauges `pending`/`failed` cohérentes |
| Consommateur | `ReviewServiceTest` (6) | soumission, décision, doublon, événement périmé, action sans effet |
| Consommateur | `ReviewTaskConsumerIT` | une tâche par soumission, clôture unique, rejeu sans effet, `DLT` |

Parcours local exécuté : création, soumission, début de revue, approbation d'un document, puis deux rejeux du même identifiant d'événement. Résultat observé : 6 messages sur le topic, 4 lignes d'inbox, une seule tâche, passée de `PENDING` à `APPROVED` avec `decidedBy = relecteur`.

Observation et rétention vérifiées sur l'application réelle, avec `acd.outbox.purge.retention=1s` : `acd_outbox_pending 1.0` immédiatement après la mutation, puis `acd_outbox_pending 0.0`, `acd_outbox_purged_total 5.0` et `outbox_events` revenue à 0 ligne. En configuration par défaut, la purge passe toutes les heures avec une rétention de 7 jours.
