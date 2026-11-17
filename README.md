# ACD Kata

Projet d'entraînement Java / Angular inspiré de la mission ACD. Le domaine détaillé et l'architecture sont des choix pédagogiques ; ils ne décrivent pas l'architecture interne d'EDF.

**Tranche courante : S1–S4 — domaine, application, PostgreSQL et REST.** Les résultats de vérification sont consignés dans le journal. Le shell Angular reste au stade S0 ; S5 et les incréments suivants ne sont pas commencés.

Validation locale : **100 tests backend réussis, aucun ignoré**, contrat OpenAPI comparé et couverture des branches du domaine et de l'application à 100 %.

## Ce qui est construit

- Java 21, Spring Boot 3.5.16 et Maven Wrapper 3.9.16.
- Domaine immuable : brouillon, soumission, revue, approbation ou rejet, publication et archivage ; correction et resoumission après rejet.
- Versions et audit déterministes, cas d'usage sans framework, faux dépôts de test et règles ArchUnit avec contre-exemples.
- Adapter JPA séparé, PostgreSQL 16.14, Liquibase 5.0.4, transactions et contrôle optimiste des écritures concurrentes.
- API REST, validation, `ProblemDetail`, pagination par fenêtre, contrat OpenAPI exporté et endpoints Actuator/Micrometer.
- Tests unitaires, MVC et d'intégration avec PostgreSQL Testcontainers.

Le domaine n'a aucune dépendance de production externe au JDK. L'application dépend du domaine ; les adapters adaptent les frontières et `boot` assure le câblage. Kafka, outbox, service de revue distribué, AG Grid et formulaires Angular appartiennent aux phases suivantes.

## Vérifier

Prérequis : JDK 21, Node **24.19.0** (`.nvmrc`), npm, Docker avec Compose v2 et `make`. Le wrapper fournit Maven. Docker doit fonctionner pour la vérification complète du backend.

```sh
nvm use
make web-install
make verify
```

Commandes ciblées :

```sh
make backend-unit-test
make backend-verify
make web-verify
```

`backend-unit-test` exécute les tests unitaires/MVC et les contrôles d'architecture sans base. `backend-verify` ajoute les tests `*IT` via Failsafe, sur des bases PostgreSQL jetables créées automatiquement par Testcontainers. Il ne se connecte pas à la base Compose locale. Docker indisponible fait échouer les tests d'intégration.

La couverture est disponible dans `contribution-service/domain/target/site/jacoco/` et `contribution-service/application/target/site/jacoco/`. Le build exige 100 % des branches de `DocumentTransition` couvertes.

## Démarrer le backend local

Première utilisation ou redémarrage :

```sh
cd /Users/hefhafwalid/Developer/KATAs/EDF/acd-kata
make local-env
make infra-up
make backend-run
```

`make local-env` crée `.env.local` avec un mot de passe aléatoire et des permissions `0600` **uniquement si le fichier est absent**. S'il existe, son contenu est conservé à l'identique. Le fichier est ignoré par Git ; ne pas le remplacer par `.env.example` lors d'un redémarrage et ne pas partager son contenu.

`make infra-up` attend une connexion PostgreSQL authentifiée avant de réussir. `make backend-run` dépend également de ce contrôle et utilise le même fichier d'environnement que Compose. Un `ENV_FILE` personnalisé est transmis explicitement à Spring.

PostgreSQL écoute sur `127.0.0.1:55432`, base `acd_contribution`, utilisateur `acd`. Liquibase applique le schéma initial au démarrage ; Hibernate le valide.

**Si l'authentification échoue :** conserver `.env.local` et le volume, puis faire vérifier la concordance des identifiants avec le rôle PostgreSQL existant. Modifier `POSTGRES_PASSWORD` dans un fichier n'effectue pas une rotation du mot de passe : l'image l'utilise pour initialiser une base vide et laisse une base existante intacte. [Documentation de l'image PostgreSQL](https://hub.docker.com/_/postgres).

L'API écoute par défaut sur **http://127.0.0.1:8081**. `SERVER_PORT` permet de choisir un autre port. Préférer `make backend-run` pour conserver le contrôle de connexion et le même fichier de configuration entre Compose et Spring.

Arrêter le backend avec Ctrl-C. `make infra-down` arrête PostgreSQL en conservant le volume. Les tests de rollback utilisent uniquement une base jetable : ne pas les transposer au volume utilisateur.

## Essayer l'API

Interface interactive : **http://127.0.0.1:8081/swagger-ui/index.html** une fois le backend démarré. Ouvrir une opération, choisir **Try it out**, renseigner les paramètres puis **Execute**. Le [parcours Swagger](docs/swagger-walkthrough.md) détaille les scénarios et versions attendues.

```sh
curl --fail-with-body http://127.0.0.1:8081/actuator/health
curl --fail-with-body http://127.0.0.1:8081/api/documents
curl --fail-with-body -X POST http://127.0.0.1:8081/api/documents   -H 'Content-Type: application/json'   -H 'X-User: contributor-1'   -d '{"templateRef":"notice-v1","title":"Notice technique","sections":[{"title":"Objet","content":"Première contribution"}]}'
```

La création retourne `201`, `Location` et le document en version 1. Pour une transition, envoyer `POST /api/documents/{id}/submit` avec `X-User` et le JSON `{"expectedVersion":1}`. Les autres actions sont `start-review`, `approve`, `reject`, `publish`, `archive`. Pour corriger le contenu, utiliser `PUT /api/documents/{id}/content` avec `expectedVersion`, `title` et `sections`.

Une version périmée ou une transition interdite retourne `409`. Une requête invalide retourne `400`, un document absent `404`. Les réponses d'erreur utilisent `application/problem+json` avec un code stable. La version attendue doit être un entier JSON positif ; les chaînes et nombres décimaux ne sont pas convertis silencieusement.

**`X-User` est un auteur pédagogique, sans authentification ni autorisation réelle.** Le serveur reste local par défaut.

Recherche pour l'exercice AG Grid :

```http
GET /api/documents?startRow=0&endRow=100&sort=updatedAt:desc&filter.status=InReview&q=contrat
```

La réponse contient `rowCount` et `rows`. La fenêtre est exclusive à droite, de 1 à 500 lignes ; un offset arbitraire est accepté. Les tris autorisés sont `title`, `updatedAt`, `status` et `version`. Le total exact et les lignes partagent les mêmes filtres ; la recherche textuelle est littérale et insensible à la casse.

## Contrat et métriques

- `/swagger-ui/index.html` : interface Swagger pour exécuter les requêtes.
- `/v3/api-docs` : spécification **code-first** générée par springdoc.
- `contracts/openapi.json` : export versionné comparé au contrat réel par le test HTTP.
- `/actuator/health` : santé sans détails internes.
- `/actuator/metrics` et `/actuator/prometheus` : métriques applicatives.

Pour mettre à jour volontairement l'export après une évolution revue du contrat :

```sh
./mvnw verify -DupdateOpenApiSnapshot=true
```

La vérification normale compare le contrat ; elle ne réécrit pas l'export.

## Progression et CI

Lire la [roadmap](docs/roadmap.md), le [modèle de domaine](docs/domain-model.md), les [fiches de phase](docs/phases/S1-domain.md), les [ADR](docs/adr/0002-document-lifecycle-and-boundaries.md) et le [journal](journal.md). Les références d'origine sont conservées à l'identique dans `docs/preparation/` ; leurs limites sont expliquées dans les notes de lecture.

La CI GitLab est **préparée, sans exécution distante observée**. Elle suppose `acd-kata` à la racine du dépôt GitLab et un runner Docker configuré pour le service Docker-in-Docker privilégié. Le dépôt parent KATAs, ses fichiers et ses réglages ne sont pas modifiés. Aucun push, déploiement ou provisionnement cloud n'est effectué.
