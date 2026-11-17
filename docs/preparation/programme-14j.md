# Programme J0 → J14 — 8 h/jour

**Créneaux fixes :** 09 h 30 – 12 h 30 · 14 h 00 – 17 h 00 · 21 h 00 – 23 h 00
**Volume :** 15 jours × 8 h = 120 h. **Cours ≈ 45 h · Kata ≈ 60 h · Rituels/doc ≈ 15 h**
**Calendrier réel :** du samedi 7 novembre au samedi 21 novembre. Dimanche 22 = buffer + repos. Lundi 23 = mission.

## Le rythme quotidien

| Créneau | Contenu | Règle |
|---|---|---|
| 09 h 30 – 09 h 50 | **Rituel de rappel** : restituer de mémoire les concepts de la veille, sans notes, puis vérifier | 20 min, jamais plus |
| 09 h 50 – 12 h 30 | **Cours** (2 h 40 de vidéo utile, à 1,25x) | Frappe chaque snippet montré. Aucune prise de note passive |
| 14 h 00 – 17 h 00 | **Kata** : l'incrément qui applique exactement le cours du matin | Le cours n'est « fini » que quand le code tourne |
| 21 h 00 – 23 h 00 | **Soirée** : doc officielle, ADR, tests, ou rattrapage | Contenu *différent* du matin. Pas de nouveau concept lourd |

**Trois règles dures :**
1. **Arrêt net à 17 h 00.** Sinon la soirée devient du travail supplémentaire et le rythme casse au bout de 5 jours.
2. **Aucun cours sans code le jour même.** Si le kata dérape, tu coupes la vidéo, pas le code.
3. **Soirée ≠ rattrapage systématique.** Elle est planifiée avec un contenu propre. Deux soirées seulement (J7, J12) sont des buffers assumés.

Les jours sont volontairement monotones : cours le matin quand le cerveau est frais, production l'après-midi, consolidation le soir. Ne permute pas.

---

## J0 — Samedi 7 nov · Setup & baseline

**Matin — Mesure et outillage**
- Skill IQ **Angular**, puis **Java**, puis **Spring** (~1 h 15 au total). Note les trois scores dans `journal.md` : c'est ta seule mesure objective du progrès.
- Installe : JDK 21 Temurin, Maven 3.9, Node 22 LTS, Angular CLI 21, Docker Desktop.
- VS Code ou IntelliJ + extensions **Angular Language Service**, **SonarLint**, **Tailwind CSS IntelliSense**.

**Après-midi — Squelette du kata**
- Crée `acd-kata` : modules Maven vides (`domain`, `application`, `adapters-rest`, `adapters-persistence`, `adapters-messaging`, `boot`), workspace Angular 21, `.editorconfig`, `.gitignore`.
- `docker-compose.yml` : PostgreSQL 16, Kafka (mode KRaft), SonarQube.
- Crée les playlists Pluralsight « Bloc A / B / C » dans l'ordre du programme.

**Soirée — Mise en jambe**
- Doc Angular : *Zoneless* + vue d'ensemble des *Signals* (40 min).
- `journal.md` : objectifs, scores, risques identifiés, questions ouvertes.

**Fin de journée** — `docker compose up` démarre Postgres + Kafka · `ng version` affiche 21 · trois Skill IQ notés.

---

## J1 — Dimanche 8 nov · Java 21

**Matin — `What's New in Java 21`** (intégral, ~1 h à 1,25x)
Puis rejoue les démos de *pattern matching for switch* et *record patterns* en codant en parallèle (1 h 30).

**Après-midi — S1 : le domaine en Java pur**
`Document`, `DocumentId`, `TemplateRef`, `Section`, `sealed interface DocumentStatus`. Records partout, constructeurs compacts validant les invariants : pas de soumission d'un document vide, pas de publication sans approbation.

**Soirée — Machine à états**
Tests AssertJ couvrant tous les chemins de transition + un `switch` exhaustif avec record patterns. Flashcards : `sealed`, record patterns, virtual threads.

**Fin de journée** — 100 % de branches couvertes sur les transitions · le module `domain` ne contient aucun import Spring.

---

## J2 — Lundi 9 nov · DDD stratégique

**Matin — `Domain-Driven Design Fundamentals`** (Steve Smith & Julie Lerman)
Modules de *strategic design* uniquement : langage ubiquitaire, bounded contexts, context mapping (~2 h 30). **Arrête-toi avant le DDD tactique**, qui est du .NET et que tu vas construire toi-même.

**Après-midi — Appliquer le stratégique**
Rédige `docs/domain-model.md` : glossaire métier, les deux bounded contexts (`contribution`, `review`), la context map, le catalogue d'événements de domaine. Puis refactorise S1 si le langage ubiquitaire impose des renommages. Écris les commandes en records.

**Soirée — Confrontation**
Lis la partie tactique du cours (aggregate, invariants, événements) et confronte-la à ton agrégat. Note les trois écarts dans `journal.md`.

**Fin de journée** — `domain-model.md` avec glossaire + event storming simplifié · les noms du code sont ceux du métier.

---

## J3 — Mardi 10 nov · Ports, use cases, ArchUnit

**Matin — `Spring Boot 3 Playbook`** (2 h 16, à 1,25x)
Insiste sur architecture, configuration, et surtout tests. Saute les modules de découverte de Spring.

**Après-midi — S2**
Ports entrants (un par cas d'usage) et sortants (`DocumentRepository`, `EventPublisher`, `Clock`). Quatre cas d'usage. **Fakes écrits à la main**, pas de Mockito sur les ports. Règles ArchUnit 1 à 5 + un test qui *prouve* que chaque règle rejette une violation.

**Soirée — ADR**
Rédige `docs/adr/0001-hexagonal.md` et `0002-ports-et-fakes-manuels.md`.

**Fin de journée** — ArchUnit échoue réellement si `domain` importe Spring (vérifié en cassant volontairement) · cas d'usage testés sans Spring.

---

## J4 — Mercredi 11 nov · Persistance

**Matin — Data & migrations**
`Spring Boot 3 Playbook` : modules accès aux données et tests (1 h 30). Puis doc **Liquibase 5** : changelogs, changesets, rollback, nouveautés v5 (1 h).

**Après-midi — S3**
Adapter JPA avec entités séparées du domaine + mappers. Changelogs versionnés par feature. IT Testcontainers PostgreSQL.

**Soirée — Données de test**
Vérifie `liquibase rollback` sur chaque changeset. Génère un seed de **10 000 documents** (SQL ou Faker) — indispensable pour J9.

**Fin de journée** — IT vert sous Docker · rollback testé · seed de 10 000 lignes prêt.

---

## J5 — Jeudi 12 nov · REST & contrat de pagination

**Matin — Spec d'abord**
Doc springdoc/OpenAPI (45 min), doc RFC 7807 `ProblemDetail` (30 min), `Spring Boot 3 Playbook` modules Actuator/observabilité (45 min).

**Après-midi — S4**
Contrôleurs, DTO, mappers, OpenAPI généré, `ProblemDetail`, Bean Validation, Actuator + Micrometer. **Et le contrat de pagination AG Grid** : `startRow`/`endRow`/`sort`/`filter` → `{rowCount, rows}`, avec un `count` séparé et les index sur `status` et `updatedAt`.

**Soirée — Tests de contrat**
`@WebMvcTest` sur chaque endpoint + trois tests d'erreur (400, 404, 409). Publie `/v3/api-docs` en artefact.

**Fin de journée** — contrat figé et documenté · Actuator expose `health`, `metrics`, `prometheus` · 0 endpoint non testé.

---

## J6 — Vendredi 13 nov · Angular, première journée

**Matin — `Angular: Getting Started`** (Deborah Kurata)
Modules : environnement, composants, templates, data-binding, DI (~2 h 30). **Arrête-toi avant HTTP et routing.**

**Après-midi — S5 (démarrage)**
Workspace, app **standalone + zoneless**, routing, premier composant liste branché sur un mock.

**Soirée — Traduction mentale**
Prends un composant React que tu as réellement écrit en mission et réécris-le en Angular sur papier. Note les cinq différences structurantes. Flashcards : `signal` vs `useState`, DI vs Context.

**Fin de journée** — app zoneless qui démarre · routing fonctionnel · zéro `NgModule`.

---

## J7 — Samedi 14 nov · Signals

**Matin — Deux cours**
`Angular: Getting Started` : HTTP, services, routing avancé (1 h 30). Puis **`Angular: Signals`** (1 h 21) en entier : `signal`, `computed`, `linkedSignal`, **`resource()`**.

**Après-midi — S5 (fin)**
Chargement des documents via `resource()`, dérivés en `computed`, états loading/error/succès pilotés par signals, service HTTP typé contre l'API Spring.

**Soirée — Buffer assumé**
Si en avance : doc Angular sur le control flow moderne (`@if`, `@for`, `@switch`). Sinon : rattrapage.

**Fin de journée** — données réelles depuis Spring affichées · aucun `subscribe` orphelin · état géré en signals.

---

## J8 — Dimanche 15 nov · Tests front

**Matin — Deux cours**
**`Angular: Unit Testing`** (Vitest + Angular Testing Library, ~1 h 30) — le cours mentionne le *test builder* stable en v21, c'est exactement ton contexte EDF. Puis le reste de `Getting Started` (forms) si nécessaire (1 h).

**Après-midi — Tests**
Service avec `HttpTestingController`, composant avec Angular Testing Library, une directive. Vise > 70 % sur les services.

**Soirée — Le sujet de revue de code**
Lis la doc Angular **« Migrating from Karma to Vitest »** (1 h). C'est ce qui te permettra de tenir une conversation crédible sur la cohabitation Jasmine/Vitest de la codebase ACD. Puis flashcards.

**Fin de journée** — `ng test` vert · couverture services > 70 % · tu sais expliquer la migration Karma → Vitest.

---

## J9 — Lundi 16 nov · AG Grid Enterprise

**Matin — `Workshop: Getting Started with AG Grid`** (ng-conf, 2 h 56 à 1,25x)
Note précisément : `colDef`, `gridOptions`, events, renderers, et la logique de datasource.

**Après-midi — S6**
Active le trial **Enterprise** aujourd'hui seulement (30 jours, ne le grille pas en J0) via variable d'environnement — jamais dans le dépôt. Puis : `rowModelType: 'serverSide'`, `IServerSideDatasource` traduisant `sortModel`/`filterModel` vers ton API, `cellRenderer` pour le badge de statut.

**Soirée — Le complément indispensable**
Doc AG Grid **Server-Side Row Model** (1 h) : c'est le sujet absent du cours et la vraie exigence Enterprise de la mission. Puis 1 h d'implémentation.

**Fin de journée** — scroll fluide sur 10 000 documents · une requête par bloc · tri et filtres exécutés côté serveur.

---

## J10 — Mardi 17 nov · Tailwind & patterns modernes

**Matin — Deux cours**
**`Tailwind CSS Foundations`** (Jon Friskics, 41 min) puis **`Angular Foundations: Modern Patterns and Best Practices`** (Jim Cooper, 2 h 14, mis à jour août 2026) : strict mode, Vite, zoneless, standalone, feature shells.

**Après-midi — S7**
Formulaire réactif de contribution, validations croisées, **Tailwind en version 3 explicitement** (`npm i -D tailwindcss@3` — sinon npm te mettra la 4), états d'erreur, et actions filtrées par statut : le domaine décide ce qui est légal, le front n'affiche que ça.

**Soirée — UX/UI**
La mission demande une sensibilité UX/UI. Traite : états vides, skeletons de chargement, navigation clavier dans la grille, focus visible, contraste.

**Fin de journée** — formulaire complet · seules les transitions légales sont proposées · Tailwind v3 confirmée dans `package.json`.

---

## J11 — Mercredi 18 nov · Event-driven

**Matin — Deux cours**
`Getting Started with Apache Kafka` (producers, consumers, consumer groups, offsets, **at-least-once** — saute Connect et Streams) puis `Designing Event-driven Applications Using Apache Kafka Ecosystem` pour les modules de design. ~2 h 30.

**Après-midi — S8**
Table outbox écrite dans la même transaction que l'agrégat, relais vers Kafka, `review-service` qui consomme `DocumentSubmitted`, crée une `ReviewTask` et publie `ReviewCompleted`. Idempotence : clé `eventId` + table de déduplication.

**Soirée — Contrat & rejeu**
Doc Spring Kafka + pattern outbox (1 h). Écris le contrat d'événement en JSON Schema dans `contracts/`. Teste le rejeu d'un événement : aucun doublon ne doit apparaître.

**Fin de journée** — rejeu sans doublon · IT Testcontainers Kafka vert · contrat versionné.

---

## J12 — Jeudi 19 nov · Cypress

**Matin — `Cypress Foundations`** (Adhithi Ravichandran, ~2 h) + doc Cypress sur l'exécution headless en CI (30 min).

**Après-midi — S9**
Cinq specs : créer, soumettre, approuver, publier, rejeter. Sélecteurs `data-cy` stables, exécution contre `docker compose` complet.

**Soirée — Buffer assumé**
Si en avance : branche Cypress dans la CI et stabilise les tests instables. Sinon : rattrapage.

**Fin de journée** — e2e vert en mode headless contre la stack complète.

---

## J13 — Vendredi 20 nov · Qualité & CI

**Matin — Deux cours**
**`Application Analysis with SonarQube`** (35 min) puis **`DevOps Continuous Integration and Delivery: Setting up a CI/CD Pipeline`** (~1 h 30) — il configure un GitLab auto-hébergé et du GitLab CI, ce qui remplace ton Jenkins.

**Après-midi — S10 + S11**
SonarQube en compose, quality gate sur la couverture, JaCoCo, OWASP dependency-check, checklist Checkmarx (secrets, injection, désérialisation, `bypassSecurityTrust`, dépendances). Dockerfiles multi-stage. Pipeline `build → test → quality → package → deploy-dev` avec cache Maven/npm.

**Soirée — Le lab cloud**
**`LAB : DevOps CI/CD — Setting Up a CI/CD Pipeline`** (2 h, environnement AWS éphémère) : GitLab réel, stages réels, aucun coût, aucun risque. C'est le meilleur retour sur 2 h de tout le programme.

**Fin de journée** — pipeline vert sous 10 min · 0 finding bloquant · lab GitLab terminé.

---

## J14 — Samedi 21 nov · AWS, Terraform, clôture

**Matin — Terraform puis AWS**
**`Terraform: Getting Started`** (Ned Bellavance, 1 h 37, mise à jour août 2025) : `plan`/`apply`, state, modules. Puis `AWS Developer: Getting Started` — uniquement les modules ECS, RDS, S3, CloudFormation (~1 h).

**Après-midi — S12 + observabilité**
Module Terraform : S3 + RDS + ECS Fargate + log group CloudWatch. **`terraform plan` uniquement**, dans un lab cloud éphémère — jamais sur ton compte personnel. Puis dashboard Grafana sur les métriques Micrometer. Module CloudWatch de `Monitoring AWS Cloud Security` (35 min).

**Soirée — Clôture**
- Repasse les **Skill IQ Angular et Java**, compare à J0.
- Finalise le README et les trois ADR.
- Écris la note d'une page **« ce que j'ai appris sur le domaine ACD »** : cycle de vie documentaire, versioning, acquittement, idempotence.
- Prépare **cinq questions** à poser à l'équipe le 23.

**Fin de journée** — `terraform plan` sans erreur · dashboard opérationnel · Skill IQ repassés et comparés · note ACD écrite.

---

## J15 — Dimanche 22 nov · Buffer & repos

Relis uniquement la note ACD, le README et les ADR. Prépare la logistique. **Pas de code, pas de vidéo.** Arriver reposé vaut plus que 8 h de révision supplémentaires.

---

## Ordre de sacrifice si tu prends du retard

1. S12 (Terraform/AWS/Grafana) — l'infra EDF se lit sur place.
2. S10–S11 (Sonar, CI) — tu les pratiqueras en contexte réel.
3. S8 (Kafka) — ton expérience Camel + ACK + idempotence est déjà un proxy crédible.
4. `Clean Architecture` et `Getting Started` en cours du soir — jamais au détriment du kata.

**Ne sacrifie jamais J6 → J10.** C'est 40 % du programme et 100 % de ton risque réel.

## Trois pièges à éviter

- **Le trial AG Grid Enterprise se consomme.** Active-le en J9, pas avant.
- **Tailwind 4 par défaut.** `npm i -D tailwindcss@3`, sinon tu apprends une syntaxe que la mission n'utilise pas.
- **Le syndrome du cours fini.** Un cours à 100 % sans code est du temps perdu. Mieux vaut 60 % du cours et un incrément de kata livré.