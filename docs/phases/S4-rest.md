# S4 — REST, erreurs et observabilité

L'API est **code-first** : les contrôleurs et DTO alimentent springdoc, exposé à `/v3/api-docs`. Le contrat JSON exporté dans `contracts/openapi.json` est versionné et vérifié par le test d'intégration HTTP.

## Opérations

| Méthode | Chemin | Résultat |
|---|---|---|
| POST | `/api/documents` | Création, `201` et `Location` |
| GET | `/api/documents/{id}` | Document et audit, `200` |
| GET | `/api/documents` | Total filtré et lignes, `200` |
| PUT | `/api/documents/{id}/content` | Correction de contenu, `200` |
| POST | `/api/documents/{id}/submit` | Soumission |
| POST | `/api/documents/{id}/start-review` | Début de revue |
| POST | `/api/documents/{id}/approve` | Approbation |
| POST | `/api/documents/{id}/reject` | Rejet |
| POST | `/api/documents/{id}/publish` | Publication |
| POST | `/api/documents/{id}/archive` | Archivage |

Toutes les mutations portent `X-User`, auteur pédagogique uniquement. Les mutations d'un document existant portent aussi `expectedVersion` dans le JSON. Il n'y a pas d'authentification ou d'autorisation réelle ; l'application est destinée au local et écoute par défaut sur `127.0.0.1:8081`.

Chaque opération déclare sa réponse de succès dans le contrat exporté : `201` et `Location` pour la création, `200` avec `DocumentResponse` pour la lecture, la correction et les transitions, `200` avec `DocumentListResponse` pour la liste. Le test HTTP exige qu'aucune opération ne soit exportée sans réponse de succès et que le schéma de liste expose `rowCount`.

## Liste

```http
GET /api/documents?startRow=0&endRow=100&sort=updatedAt:desc&filter.status=InReview&q=contrat
```

`endRow` est exclusif ; le bloc contient au maximum 500 lignes, à partir d'un offset quelconque compris entre 0 et 1 000 000. Les tris autorisés sont `title`, `updatedAt`, `status`, `version`, en `asc` ou `desc`. L'identifiant assure un ordre stable en cas d'égalité. `q` recherche littéralement dans le titre, sans tenir compte de la casse ; il est limité à 200 caractères.

Réponse : `{ "rowCount": 42, "rows": [...] }`. Le total est exact et applique les mêmes filtres que les lignes. Un comptage séparé reste un coût de requête réel.

## Erreurs et métriques

Les erreurs sont des `application/problem+json`, avec `status`, `title`, `detail` et un `code` stable. Requête invalide : `400`. Document absent : `404`. Version périmée ou action interdite : `409`. Les détails SQL et les traces ne sont pas envoyés au client.

Actuator expose `/actuator/health`, `/actuator/metrics` et `/actuator/prometheus`. Les détails internes de santé restent masqués. Le test HTTP complet démarre le vrai serveur avec PostgreSQL Testcontainers et vérifie les parcours, le contrat et ces endpoints.

```sh
./mvnw verify
```

La pipeline GitLab est préparée pour un runner Docker avec Docker-in-Docker privilégié, configuration à fournir par l'opérateur du runner. Aucune exécution distante ou configuration du runner n'est effectuée par cette tranche.
