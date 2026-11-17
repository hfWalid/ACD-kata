# S0 — Socle et garde-fous

## Objectif

Préparer un environnement reproductible pour apprendre par incréments. S0 ne fournit aucun cas d'usage documentaire et ne dépend pas de PostgreSQL pour compiler ou tester Java / Angular.

## Périmètre

- Maven Wrapper 3.9.16, JDK 21 et parent Spring Boot 3.5.16.
- Agrégateur `contribution-service` et ses six modules : `domain`, `application`, `adapters-rest`, `adapters-persistence`, `adapters-messaging`, `boot`.
- Packages déclarés mais vides ; bootstrap Spring avec `spring-boot-starter`, sans pile web.
- Test de contexte du bootstrap ; premier contrôle ArchUnit de l'entrée de l'application ; Maven Enforcer protège les dépendances de production du domaine.
- Angular 21.2.26, TypeScript 5.9.x, strict, standalone, zoneless, Vitest et Node 24.19.0.
- PostgreSQL 16 dans Compose, bind local, mot de passe fourni par `.env.local`.
- Commandes locales, configuration GitLab CI préparée et documentation.

Les règles ArchUnit complètes arrivent en S2. Le premier garde-fou ne constitue pas déjà une validation exhaustive de l'architecture.

## Hors de cette phase

Pas de `Document`, machine à états, port métier, entité JPA, changelog Liquibase, endpoint, connexion à la base, grille, formulaire, Tailwind, Kafka, SonarQube, Dockerfile ou Terraform. `review-service`, `contracts` et `infra` ne contiennent que des explications sur leurs responsabilités futures.

## Checklist de vérification

Ne cocher qu'après observation du résultat. Les validations ci-dessous concernent S0 uniquement.

- [x] JDK 21 et Node 24.19.0 utilisés.
- [x] Installation Angular reproductible via `npm ci` réussie.
- [x] Phase Maven `verify` réussie sur les huit projets du réacteur ; deux tests réussis.
- [x] Le test de contexte et le garde-fou ArchUnit sont exécutés.
- [x] Le jar démarre sans serveur HTTP et termine avec un code de sortie 0.
- [x] Le garde-fou du domaine rejette une dépendance Spring de compilation dans une copie temporaire.
- [x] Test Vitest non interactif, build Angular et contrôle de format réussis ; un test exécuté.
- [x] Vérifications Java / Angular réalisées sans dépendre de PostgreSQL.
- [x] Configuration Compose validée sans exposer de secret.
- [x] PostgreSQL 16.14 sain et requête de contrôle réussie dans un projet de vérification isolé.
- [x] Fichiers du scaffold relus avant remise : aucune fonctionnalité S1+, aucun secret ni dépôt Git imbriqué ; références d'origine conservées à l'identique.

### Relevé des exécutions locales

| Vérification | Date / environnement | Résultat observé |
|---|---|---|
| Installation Angular `npm ci` | 17 novembre 2026, Node 24.19.0 | Réussie |
| Maven `verify` | 17 novembre 2026, JDK 21 | Huit projets réussis, deux tests réussis |
| Jar non-web | 17 novembre 2026, local | Démarrage réussi, sortie 0 |
| Garde-fou négatif du domaine | 17 novembre 2026, copie temporaire | Dépendance Spring de compilation rejetée |
| Vitest / build / format Angular | 17 novembre 2026, Node 24.19.0 | Réussis, un test exécuté |
| Validation Compose | 17 novembre 2026, configuration locale d'exemple | Réussie |
| PostgreSQL | 17 novembre 2026, projet Compose isolé | Image 16.14 saine, requête réussie, ressources de test nettoyées |

Les cibles `make` documentent les commandes reproductibles ; ce relevé ne revendique pas leur exécution sous ce nom lorsqu'une vérification équivalente a été lancée directement. Le build Angular a nécessité une autorisation adaptée au sandbox macOS, sans changement de code.

**GitLab CI : PRÉPARÉE.** L'exécution distante n'est pas réalisée par la création du fichier. Le fichier suppose `acd-kata` à la racine du dépôt GitLab ; adapter ce point si le kata reste dans un dépôt parent. Aucun push n'est nécessaire pour la validation locale.

## Sortie attendue

Le développeur sait expliquer les six modules, lancer leurs vérifications et identifier ce qui n'est pas encore implémenté. Le [brief S1](S1-domain.md) est prêt à être lu ; son code n'appartient pas à S0.
