# Parcours Pluralsight — 14 jours vers EDF/ACD

Démarrage mission : 23 novembre. Fenêtre utile : ~2 semaines.
Profil de départ : senior Java/Spring (Java 21, Spring Boot 3 en prod, PostgreSQL, Liquibase, GitLab, Docker, TDD, hexagonale), **zéro Angular**.

## Règles d'engagement

1. **Budget vidéo : 1 h 15 max par jour.** Le reste en kata. À 1,25–1,5x, 1 h 15 de vidéo = ~50 min réelles.
2. **Sélection au module, pas au cours.** Un cours Pluralsight de 5 h contient souvent 1 h 30 utile pour toi. Les tableaux ci-dessous indiquent quoi sauter.
3. **Aucun cours sans incrément de kata le même jour.** Si tu regardes sans coder, tu perds la journée.
4. **Diagnostic avant/après** : passe les *Skill IQ* Angular et Java le jour 0 (20 min chacun), refais-les au jour 14. C'est ta seule mesure objective du progrès et ça coûte 40 min au total.

## Décision structurante : Angular d'abord, AWS plus tard

Tu as 14 jours et deux semaines de Java derrière toi très récentes. Le risque réel n'est pas Spring — c'est **Angular + AG Grid**, sur lesquels tu seras en revue de code dès la semaine 1. Terraform/AWS/Grafana se rattrapent en autonomie sur la mission (l'infra est celle d'EDF, tu la liras sur place), Angular ne se rattrape pas en réunion.

Donc : **Bloc A + B = 10 jours pleins, Bloc C = 4 jours, AWS/Terraform = veille légère (2 h, hors kata).**

---

## Bloc A — Back & domaine (J1 → J5)

| Cours | Auteur | Durée | Statut |
|---|---|---|---|
| **What's New in Java 21** | Pluralsight | ~1 h 15 | Obligatoire — records, *pattern matching for switch*, *record patterns*, virtual threads |
| **Spring Boot 3 Playbook** | Pluralsight | 2 h 16 | Obligatoire — mais tu connais déjà Spring : garde *architecture*, *configuration*, *tests*, *Actuator* ; saute les modules de découverte |
| **Domain-Driven Design Fundamentals** | Steve Smith & Julie Lerman | 4 h 46 | **Partiel** — uniquement les modules de *strategic design* (bounded contexts, ubiquitous language, context mapping). ~1 h. Le reste est du DDD tactique .NET que tu vas construire toi-même |

**À sauter** : Clean Architecture (Matthew Renze). Tu pratiques déjà l'hexagonale ; ArchUnit dans le kata t'apportera plus que 3 h de théorie.

## Bloc B — Front (J6 → J10) — le cœur de l'effort

| Cours | Auteur | Durée | Statut |
|---|---|---|---|
| **Angular: Signals** | Deborah Kurata | 1 h 21 | Obligatoire. `signal`, `computed`, `linkedSignal`, **`resource()`** pour le data fetching. C'est le pont direct depuis tes `useState`/Redux |
| **Angular: Unit Testing** | Pluralsight | ~1 h 30 | Obligatoire. **Vitest + Angular Testing Library**, et le cours mentionne explicitement le *test builder* stable en v21. Ton Jest se transfère quasi à l'identique |
| **Angular Foundations: Modern Patterns and Best Practices** | Jim Cooper | 2 h 14 | Obligatoire. Mis à jour août 2026 : *strict mode*, Vite, **zoneless**, standalone, nouveau control flow, feature shells, monorepo. C'est le cours qui te fait passer pour un Angulariste moderne en entretien technique |
| **Angular: Getting Started** | Deborah Kurata | 5 h 52 | **Partiel** — uniquement DI, routing/navigation, HTTP. ~1 h 15. Saute tout le début « composants et data-binding » : tu l'as déjà en React |
| **Workshop: Getting Started with AG Grid** | ng-conf | 2 h 56 | Obligatoire. Le seul contenu AG Grid du catalogue, et il est Angular. Voir note ci-dessous |
| **Tailwind CSS Foundations** | Jon Friskics | 41 min | Obligatoire. Court, suffisant. Le reste s'apprend en écrivant des classes |

**Note AG Grid** : le workshop couvre le *client-side row model*. La mission parle d'**AG Grid Enterprise**, donc du **Server-Side Row Model** — absent du cours. Prévois 1 h de doc officielle (`ag-grid.com/angular-data-grid/server-side-model/`) en complément, et implémente-le dans le kata (sprint S6). C'est exactement le trou que le cours laisse, et exactement ce qui te distinguera.

## Bloc C — EDA, qualité, CI (J11 → J14)

| Cours | Auteur | Durée | Statut |
|---|---|---|---|
| **Getting Started with Apache Kafka** | Pluralsight | ~2 h | Obligatoire, **ciblé** : producers, consumers, consumer groups, offsets, livraison *at-least-once*. Saute Kafka Connect et Kafka Streams |
| **Cypress Foundations** | Adhithi Ravichandran | ~2 h (à vérifier) | Obligatoire. Sorti juillet 2025. Alternative si indisponible : *Cypress: End-to-end JavaScript Testing* du même auteur |
| **Application Analysis with SonarQube** | Pluralsight | ~35 min | Obligatoire. Court, et couvre exactement ce que fera EDF : quality gate, hotspots, remédiation |
| **DevOps CI/CD: Setting up a CI/CD Pipeline** | Pluralsight | ~1 h 30 (à vérifier) | Utile — configure un **GitLab auto-hébergé + GitLab CI** (que tu connais via Jenkins) |
| **LAB : DevOps CI/CD — Setting Up a CI/CD Pipeline** | Pluralsight Labs | 2 h | **Fortement recommandé** — lab avec environnement AWS éphémère : GitLab réel, stages build/test/deploy. Fait une fois, ça vaut trois cours |

## Veille légère (2 h, hors kata)

| Cours | Auteur | Durée | Pourquoi |
|---|---|---|---|
| **Terraform: Getting Started** | Ned Bellavance | 1 h 37 | Mis à jour août 2025. `plan`/`apply`, state, modules. Juste pour lire un module EDF sans être perdu |
| **Monitoring AWS Cloud Security** (module CloudWatch) | Pluralsight | 35 min | CloudWatch metrics/alarms — le vocabulaire d'observabilité de la mission |
| *(optionnel)* **API Testing with Burp Suite** | Pluralsight | ~1 h | Substitute Checkmarx : même réflexe de **gate sécurité dans GitLab CI** qui casse le build. Checkmarx n'existe pas au catalogue |

## Ce que Pluralsight n'a pas (et qu'il faut traiter autrement)

- **Testcontainers** → docs officielles + kata S3. Aucun cours Pluralsight correct.
- **Liquibase 5** → doc officielle + kata S3. Tu connais déjà Liquibase, il s'agit surtout des changements de comportement v5.
- **Server-Side Row Model AG Grid** → doc AG Grid + kata S6.
- **Checkmarx** → laissé à EDF ; le cours Burp ci-dessus donne le bon réflexe.
- **Jasmine** → **ne l'apprends pas**. Le poste cumule Jasmine et Vitest parce que la codebase est **en cours de migration Karma/Jasmine → Vitest**. Savoir expliquer cette migration vaut mieux que savoir écrire du Jasmine.

---

## Calendrier jour par jour

| Jour | Pluralsight (≤1 h 15) | Incrément kata | Definition of Done |
|---|---|---|---|
| **J0** | — | — | Skill IQ Angular + Java + Spring passés, scores notés |
| **J1** | Java 21 — records, sealed, pattern matching | S0 scaffold + S1 domaine | `mvn verify` vert ; module `domain` sans aucune dépendance Spring |
| **J2** | Java 21 — record patterns, virtual threads | S1 domaine (fin) | Machine à états exhaustive (`sealed` + `switch`), 100 % de branches couvertes |
| **J3** | Spring Boot 3 Playbook (archi + tests) | S2 ports & use cases + ArchUnit | ArchUnit **échoue** si `domain` importe Spring ; cas d'usage testés avec des faux écrits à la main |
| **J4** | — (doc Liquibase + Postgres) | S3 persistance | Changelog Liquibase réversible vérifié, IT Testcontainers PostgreSQL vert |
| **J5** | Spring Boot 3 Playbook (Actuator, obs.) | S4 REST | OpenAPI généré, erreurs RFC 7807, contrat de pagination figé pour AG Grid |
| **J6** | Angular: Signals (1/2) | S5 shell Angular | App standalone + zoneless qui consomme l'API via `resource()` |
| **J7** | Angular: Signals (2/2) + Angular: Unit Testing | S5 (fin) | Tests Vitest sur service + composant, `ng test` vert |
| **J8** | AG Grid workshop (1/2) | S6 grille | Liste des documents en `serverSide` row model, tri/filtre serveur |
| **J9** | AG Grid workshop (2/2) + doc SSR | S6 (fin) | Cell renderer statut, pagination serveur validée sur 10 k lignes seedées |
| **J10** | Tailwind CSS Foundations + Modern Angular (1/2) | S7 formulaire | Formulaire réactif de contribution, validations, UI Tailwind, états d'erreur |
| **J11** | Modern Angular (2/2) | S8 outbox + producer Kafka | `DocumentSubmitted` publié de façon transactionnelle (pattern outbox) |
| **J12** | Kafka — consumers & livraison | S8 (fin) | `review-service` consomme en idempotence, contrat d'événement versionné |
| **J13** | Cypress Foundations + SonarQube | S9 e2e + S10 qualité | Parcours soumettre → valider → publier sous Cypress ; SonarQube local, 0 finding bloquant |
| **J14** | LAB GitLab CI (2 h) | S11 CI + docs | Pipeline complet vert ; README + 3 ADR ; Skill IQ repassés |

**Marge** : le plan est tendu. Si tu prends du retard, sacrifie dans cet ordre : S10 (Sonar, tu le feras chez EDF) → S11 (CI, idem) → S8 (Kafka, tu as déjà Camel en proxy). **Ne sacrifie jamais le Bloc B.**

## Vérification des titres

Relevés sur pluralsight.com le 7 novembre 2026. Les durées marquées « à vérifier » sont estimées : le catalogue change souvent, et plusieurs cours sont régulièrement remplacés par une nouvelle édition. Avant de commencer, cherche le titre exact et prends la **dernière mise à jour** — pour Angular en particulier, une édition antérieure à 2025 ne couvrira pas les signals ni le control flow moderne.