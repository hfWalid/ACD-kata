# Kata ACD — spécification

Projet d'entraînement unique qui couvre **toute** la stack de la mission. Un seul dépôt, douze incréments, chacun aligné sur un cours Pluralsight du parcours.
Nom du dépôt : `acd-kata`.

## Domaine

Assistant de Contribution Documentaire minimal. Des organisations produisent des documents à partir de **modèles** ; des **contributeurs** remplissent des **sections** ; un document est **soumis**, **relu**, **approuvé**, **publié**, puis **archivé**. Chaque action est versionnée et auditée.

Le choix du domaine n'est pas décoratif : il reproduit le vocabulaire de ta mission AXXES (génération documentaire, acquittement asynchrone, machine à états, idempotence, archivage). Tu pourras en parler avec la même grille de lecture que l'équipe ACD dès le premier jour.

### Modèle tactique (`common` → coeur)

- **Document** (agrégat racine) : `DocumentId`, `TemplateRef`, `title`, `sections[]`, `version`, `status`.
- **Cycle de vie** en `sealed interface DocumentStatus` : `Draft`, `Submitted`, `InReview`, `Approved`, `Rejected`, `Published`, `Archived`.
- **Transitions** : `DocumentTransition` — `switch` exhaustif sur `(status, command)` (record patterns).
- **Invariants** : pas de publication sans approbation ; pas de soumission d'un document vide ; version incrémentée à chaque transition significative ; horodatage + auteur obligatoires sur chaque événement.
- **ReviewTask** (contexte `review`) : créé à la réception de `DocumentSubmitted`, clos par `ReviewCompleted`.

### Événements de domaine

```
DocumentCreated → DocumentSubmitted → ReviewTaskOpened
              → ReviewCompleted(approved | rejected)
              → DocumentPublished → DocumentArchived
```

---

## Architecture

Deux services, deux bounded contexts, pour justifier la maille micro-service sans faire du distribué pour le plaisir :

```
acd-kata/
├── contribution-service/        # contexte coeur : documents, versions, cycle de vie
├── review-service/              # contexte support : tâches de revue, décisions
├── web/                         # Angular 21
└── infra/                       # docker, compose, gitlab-ci, terraform
```

Découpage interne du `contribution-service` (hexagonale, contrôlée par ArchUnit) :

```
contribution-service/
├── domain/            # Java pur. Aucun import Spring, JPA, Jackson.
├── application/       # cas d'usage + ports (in/out). Dépend de domain uniquement.
├── adapters/rest/     # Spring MVC, OpenAPI, DTO, mappers
├── adapters/persistence/  # JPA/Spring Data, Liquibase, entités
├── adapters/messaging/    # producer/consumer Kafka, outbox
└── boot/              # @SpringBootApplication, câblage
```

**Règles ArchUnit** (elles doivent échouer si on les viole — c'est le livrable du sprint S2) :

1. Aucune classe de `domain` ne dépend d'un package `org.springframework..`, `jakarta.persistence..`, `com.fasterxml.jackson..`.
2. `domain` ne dépend de rien d'autre que `java..`.
3. `application` ne dépend que de `domain` et de ses propres ports.
4. `adapters` ne dépendent jamais d'un autre `adapters`.
5. Les ports ne sont implémentés que dans `adapters` ou dans `application` (fakes de test).

---

## Choix techniques et pourquoi

| Sujet | Choix | Justification (à défendre en entretien technique) |
|---|---|---|
| Persistance | Spring Data **JPA** + PostgreSQL 16 | Va au plus vite, reste standard EDF. Le coût de l'hexagone est déjà payé par les ports ; mapper en JPA dans l'adapter est acceptable |
| Migrations | **Liquibase 5**, changelogs XML versionnés par feature | Déjà pratiqué en mission ; s'approprier les changements v5 |
| API | **OpenAPI-first** (`springdoc`) | La mission demande de la spec ; générer le contrat avant le code est le réflexe attendu |
| Erreurs | RFC 7807 `ProblemDetail` | Contrat d'erreur uniforme, exploitable par le front |
| Messagerie | **Kafka + pattern outbox** | L'outbox est ce qui te permet de parler d'exactly-once / at-least-once sans bluffer : tu l'auras codé |
| Tests back | JUnit 5 + AssertJ + **Testcontainers** (Postgres, Kafka) | Les IT contre une vraie base, pas H2 |
| Front | Angular 21 **standalone + zoneless + signals** | Le standard 2026 ; le mode zoneless est explicitement cité dans la mission via Angular 21 |
| Grille | **AG Grid Enterprise** (trial) + **Server-Side Row Model** | Le point le plus discriminant : les gros volumes documentaires imposent le SSR row model |
| CSS | **Tailwind 3** (explicitement `tailwindcss@3`) | La mission dit 3, pas 4 — ne laisse pas npm te mettre la 4 par défaut |
| Tests front | **Vitest** + Angular Testing Library ; **Cypress** en e2e | Vitest est le runner par défaut d'Angular 21 ; Jasmine est le legacy à reconnaître, pas à apprendre |
| Qualité | SonarQube (compose local) + JaCoCo + OWASP dependency-check | Les deux gates qui bloquent chez EDF |
| CI | GitLab CI, runners Docker | La mission est GitLab CI, pas Jenkins |
| Packaging | Dockerfile multi-stage, `docker compose` pour le local | Doit démarrer en une commande |
| Observabilité | Actuator + Micrometer → Prometheus → Grafana | Reproduit la chaîne CloudWatch/Grafana de la mission |

Le trial AG Grid Enterprise s'active avec une clé de licence gratuite (30 j) via variable d'environnement — ne la commite pas, c'est déjà une leçon Checkmarx.

---

## Contrat clé : pagination pour AG Grid SSR

C'est le contrat qui relie le back et le front et que la plupart des candidats ratent.

```http
GET /api/documents?startRow=0&endRow=100&sort=updatedAt:desc&filter.status=InReview&q=contrat
→ 200
{
  "rowCount": 12480,          // total filtré, pour la scrollbar virtuelle
  "rows": [ { "id": "...", "title": "...", "status": "InReview", "version": 3, "updatedAt": "..." } ]
}
```

Côté Angular : `rowModelType: 'serverSide'`, un `IServerSideDatasource` qui traduit `getRows({startRow, endRow, sortModel, filterModel})` vers ce contrat. Côté Spring : un `Pageable` dérivé, un `count` séparé pour éviter le count coûteux, index sur `status` et `updatedAt`.

---

## Les douze incréments

Chaque incrément est indépendamment démontrable. Un incrément non fini ne bloque pas le suivant, sauf mention contraire.

### S0 — Scaffold & garde-fous
Maven multi-module, `.editorconfig`, ArchUnit branché (règle triviale au départ), workspace Angular 21, `.gitlab-ci.yml` stub, `docker-compose.yml` (Postgres).
**DoD** : `mvn verify` et `ng test` verts, pipeline GitLab vert (même trivial).

### S1 — Coeur du domaine en Java 21 pur
Agrégat `Document`, `sealed interface DocumentStatus`, `record` pour les identifiants et commandes, `switch` exhaustif avec record patterns pour les transitions. Aucun framework.
**DoD** : 100 % de branches couvertes sur la machine à états ; une transition illégale lève une `DomainException` typée ; le module `domain` ne compile pas si on y ajoute Spring.

### S2 — Ports & cas d'usage
`SubmitDocument`, `ApproveDocument`, `RejectDocument`, `PublishDocument`. Ports sortants : `DocumentRepository`, `EventPublisher`, `Clock`. Fakes écrits à la main (pas de Mockito pour les ports — c'est plus lisible et ça force à concevoir l'interface).
**DoD** : règles ArchUnit 1 à 5 actives et testées en négatif (un test qui *prouve* que la règle rejette une violation).

### S3 — Persistance
Adapter JPA, entités séparées du modèle de domaine + mappers, changelogs Liquibase, IT Testcontainers PostgreSQL.
**DoD** : `liquibase rollback` testé ; IT vert en CI avec Docker ; aucun `@Entity` dans le package `domain`.

### S4 — REST
Contrôleurs, OpenAPI généré, `ProblemDetail`, validation Bean Validation, contrat de pagination AG Grid (voir ci-dessus), Actuator + Micrometer.
**DoD** : `/v3/api-docs` exploitable ; test de contrat sur les erreurs 400/404/409 ; `@WebMvcTest` sur chaque endpoint.

### S5 — Shell Angular
App standalone, zoneless, routing, `resource()` pour charger les documents, état en signals (`computed` pour les dérivés), design system minimal.
**DoD** : `ng test` avec Vitest ; tests sur le service (HttpTestingController) et sur un composant (ATL) ; aucun `NgModule`.

### S6 — AG Grid Enterprise SSR
Liste des documents en `serverSide` row model, tri, filtres serveur, `cellRenderer` custom pour le badge de statut, sélection multiple.
**DoD** : seed de 10 000 documents ; scroll fluide ; une requête par bloc, pas de chargement global.

### S7 — Formulaire de contribution
Reactive forms, validation croisée (section obligatoire), Tailwind 3, états loading/erreur/succès, UX des transitions disponibles selon le statut.
**DoD** : le formulaire n'expose que les actions légales pour le statut courant (le domaine décide, le front affiche).

### S8 — Event-driven avec outbox
Table outbox écrite dans la même transaction que l'agrégat ; relais vers Kafka ; `review-service` consomme `DocumentSubmitted`, crée une `ReviewTask`, publie `ReviewCompleted` ; consumer idempotent (clé = `eventId` + table de déduplication).
**DoD** : IT Testcontainers Kafka ; rejeu d'un événement ne crée pas de doublon ; contrat d'événement en JSON Schema versionné dans `contracts/`.

### S9 — Cypress e2e
Parcours complet : créer → soumettre → approuver → publier, plus un parcours de rejet.
**DoD** : e2e vert contre `docker compose` en CI, plus de 5 specs, sélecteurs stables (`data-cy`).

### S10 — Quality gates
SonarQube en compose, quality gate sur la couverture, OWASP dependency-check, revue Checkmarx-style (checklist : secrets, injection, désérialisation, XSS/`bypassSecurityTrust`, dépendances).
**DoD** : 0 vulnérabilité bloquante, couverture > 70 % sur `domain` + `application`.

### S11 — CI/CD complet
Dockerfiles multi-stage, pipeline `build → test → quality → package → deploy-dev`, cache Maven/npm, artefacts.
**DoD** : pipeline complet sous 10 min ; une PR cassée est bloquée avant `package`.

### S12 — Infra & observabilité *(stretch, après le 23 si nécessaire)*
Module Terraform (S3 + RDS + ECS Fargate + log group CloudWatch), `terraform plan` validé dans un lab cloud éphémère, dashboard Grafana sur les métriques Micrometer.
**DoD** : `plan` sans erreur, dashboard qui montre latence p95 et taux d'erreur.

---

## Bonus à faible coût, fort rendement

- **3 ADR** dans `docs/adr/` : « pourquoi l'outbox plutôt que des events dans la même transaction », « pourquoi JPA dans l'adapter et pas Spring Data JDBC », « pourquoi le SSR row model plutôt que le client-side ». Ce sont des artefacts que tu montreras à l'équipe ACD.
- **Une note de 1 page** : « ce que j'ai appris sur le domaine ACD ». Reprend le vocabulaire cycle de vie, versioning, acquittement. C'est ta carte de visite le 23.
- **`docs/` versionné** : la mission demande explicitement de la documentation technique et du transfert de connaissances. Un kata documenté vaut deux katas codés.

## Ce que ce kata ne fera pas

- Pas de Kubernetes (hors mission).
- Pas de NgRx (les signals suffisent, et l'équipe ACD ne va pas t'imposer un store dès le jour 1).
- Pas de Kafka Streams ni Kafka Connect.
- Pas de multi-tenancy ni d'authentification complète : un `X-User` header suffit, sauf si tu as le temps d'ajouter Keycloak (que tu connais déjà).