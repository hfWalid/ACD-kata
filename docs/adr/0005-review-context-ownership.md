# ADR 0005 — Propriété du contexte review

Statut : accepté pour le kata, 21 novembre 2026. S8.

## Contexte

La fiche de phase demande « outbox, Kafka, contexte review et déduplication ». Le dossier `review-service` réservait cette responsabilité en S0 en demandant de préciser, avant de distribuer le comportement, qui possède la décision, comment elle modifie le document et comment sont traités rejeu, concurrence et indisponibilité.

## Décision

### Une seule source de vérité

`contribution-service` possède l'état du document et la décision (soumettre, approuver, rejeter). `review-service` possède la tâche de revue : ouverte à la soumission, close à la décision. Aucun des deux n'écrit dans les données de l'autre.

Cette séparation évite la question la plus coûteuse d'un découpage distribué : deux services qui prétendent décider du même statut. Une décision distribuée exigerait une saga, une compensation et un arbitrage des conflits ; le kata n'en a pas besoin pour démontrer une chaîne événementielle.

### Idempotence et ordre

Le consommateur traite chaque événement au plus une fois par deux gardes indépendantes :

- `review_inbox` (clé `event_id`) : un rejeu est reconnu et ignoré ;
- `review_documents.last_version` : un événement plus ancien que la dernière version appliquée est ignoré, donc un rejeu hors ordre ne rouvre pas une revue close.

Les deux gardes sont des instructions SQL uniques avec `ON CONFLICT`, donc la garantie tient même avec plusieurs instances du consommateur.

### Indisponibilité

Sans broker, `contribution-service` continue d'accepter les mutations : les événements s'accumulent dans l'outbox et partent au retour du broker. Sans base, `review-service` ne consomme pas et l'offset ne progresse pas : Kafka rejoue à la reprise, et l'idempotence rend le rejeu inoffensif.

### Contrat

Le consommateur relit le même fichier de contrat que le producteur et refuse toute version de schéma inconnue. Un message illisible va en `DLT` après deux tentatives (`EventContractException` n'est pas réessayée) : il ne bloque pas la partition.

## Vérification

- `ReviewServiceTest` : soumission, approbation, rejet, doublon, événement périmé, action sans effet.
- `ReviewTaskConsumerIT` (Kafka + PostgreSQL réels) : une tâche par soumission, clôture unique, rejeu sans effet, `DLT` pour un `schemaVersion` inconnu.
- Parcours local : quatre événements produits, deux rejeux du même identifiant, toujours une seule tâche et quatre lignes d'inbox.

## Alternatives écartées

- **Un seul service qui consomme ses propres événements** : ne démontre pas de frontière distribuée.
- **Décision distribuée (le service de revue écrit le statut)** : demande une saga et un arbitrage de conflits hors périmètre.
- **Déduplication en mémoire** : perdue au redémarrage, donc inutile.
- **Un topic par type d'événement** : multiplierait les ordonnancements à garantir pour un seul agrégat.
