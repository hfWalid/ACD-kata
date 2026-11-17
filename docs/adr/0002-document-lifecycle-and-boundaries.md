# ADR 0002 — Cycle documentaire et frontières S1–S4

Statut : accepté pour le kata, 17 novembre 2026. Les règles ci-dessous sont pédagogiques et ne décrivent pas les règles internes d'EDF.

## Cycle et contenu

Un document est créé en `Draft`, version 1. Un brouillon possède un titre et une référence de modèle non vides ; il peut ne contenir aucune section. La soumission exige au moins une section, dont chaque contenu est non vide.

Transitions : `Draft/Rejected → Submitted → InReview → Approved → Published → Archived`, et `InReview → Rejected`. Le démarrage de revue est explicite. Seuls `Draft` et `Rejected` sont modifiables. La correction puis resoumission d'un rejet est retenue plutôt qu'un rejet terminal pour pouvoir pratiquer un parcours de correction réel.

Le modèle est immuable. Chaque création, modification ou transition réussie ajoute exactement une entrée d'audit avec version résultante, action, auteur et instant ; une action refusée ne change rien. Le temps vient de l'appelant et ne doit pas reculer. Les valeurs et collections sont validées et copiées défensivement. Aucun événement n'est encore publié vers un système externe.

## Cas d'usage et transactions

Les ports entrants regroupent les commandes d'un document et les requêtes. Cela évite un couple interface/implémentation par méthode tout en laissant le transport dépendre de contrats applicatifs. Les implémentations restent sans Spring. Le dépôt est le port sortant de cette tranche ; `Clock` du JDK et un fournisseur d'UUID rendent les tests déterministes.

Une mutation exige la version métier attendue. L'application détecte une version déjà périmée ; le dépôt garantit aussi atomiquement l'absence de modification concurrente entre lecture et écriture. Le document, ses sections et son audit sont écrits dans la même transaction. Une version technique JPA reste dans l'adapter. Les entités JPA ne sont jamais des objets du domaine.

La transaction ne couvre aucune publication externe. Un `EventPublisher` sans consommateur réel est différé à S8, où l'outbox et la livraison au moins une fois auront une responsabilité concrète.

## Recherche et contrat HTTP

La pagination utilise une fenêtre `[startRow, endRow[`, de 1 à 500 lignes et un offset maximal de 1 000 000. Un offset quelconque doit fonctionner. Le tri accepte `title`, `updatedAt`, `status` ou `version`, avec un identifiant comme second critère stable. Les lignes et le total exact partagent les mêmes filtres. La recherche est littérale, insensible à la casse et limitée au titre ; `%` et `_` ne deviennent pas des jokers utilisateur.

Choix **code-first explicite** : springdoc expose `/v3/api-docs` à partir des contrôleurs et DTO ; un export JSON versionné est vérifié par un test d'intégration. Ce mécanisme n'est pas appelé contract-first. Les tests HTTP couvrent les entrées, réponses et erreurs observables.

Création : `201` et `Location`. Lecture, modification et transitions : `200`. Erreurs au format `ProblemDetail` : `400` pour une requête invalide, `404` pour un document absent, `409` pour une version périmée ou une règle métier empêchant l'action.

`X-User` identifie l'auteur uniquement dans cet exercice local ; il ne constitue pas une authentification. Le serveur écoute par défaut sur la boucle locale. Aucun mécanisme d'autorisation réel n'est simulé.

## Migration initiale

La base applicative n'existait pas en S0 : il n'y a pas de données historiques à transformer. Liquibase 5 crée un schéma explicite pour documents, sections ordonnées et audit. Hibernate valide le schéma et ne le crée pas.

La réversibilité du schéma initial est testée uniquement dans une base Testcontainers jetable, suivie d'une réapplication. Le rollback supprime les tables et leurs données : il n'est ni exécuté sur le Compose utilisateur, ni présenté comme stratégie de restauration en production.

## Vérification

S1 : tests du domaine et de la matrice de transitions. S2 : tests avec faux dépôt et règles ArchUnit positives/négatives. S3 : PostgreSQL Testcontainers, round-trip, concurrence, atomicité et rollback/réapplication. S4 : tests MVC, parcours HTTP avec PostgreSQL, contrat OpenAPI et disponibilité des métriques. Les résultats sont consignés après exécution, phase par phase.
