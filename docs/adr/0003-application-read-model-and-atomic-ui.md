# ADR 0003 — Lecture applicative et structure atomique de l'interface

Statut : accepté pour le kata, 18 novembre 2026. Refactoring sans changement de comportement ni de contrat.

## Contexte

Le découpage en modules, en couches et en paquets était déjà en place, mais deux dépendances restaient permises par le compilateur : le contrôleur REST pouvait lire l'agrégat `Document` du domaine, et les adapters pouvaient importer un service applicatif concret plutôt qu'un port. Côté interface, tous les composants vivaient dans un seul paquet `documents/`, si bien qu'un éditeur de section pouvait connaître la page qui l'utilise, et un service HTTP la grille qui l'appelle.

Aucune de ces dépendances n'était exploitée dans le code. Le refactoring les rend impossibles plutôt que de compter sur la discipline.

## Décision

### Un unique modèle de lecture applicative

`DocumentDetail` (paquet `application.query`) est ce que renvoient `DocumentQueries.get` et les huit commandes de `DocumentCommands`. Il porte l'identité, le contenu, le statut, la version, les horodatages et l'audit, et se construit par `DocumentDetail.from(Document)` avec validation des arguments et copie défensive des collections.

Conséquences : l'agrégat ne franchit plus la frontière applicative, l'adapter REST ne peut pas invoquer une méthode métier ni observer un état intermédiaire, et les trois façades (`create`, `updateContent`, `transition`) répondent avec la même forme. Le coût est une projection par appel : elle est immuable, sans I/O, et négligeable devant la transaction de persistance qui la précède.

### Contrôleurs dans un paquet de cas d'usage

`adapters.rest.document` porte le contrôleur et ses DTO, `adapters.rest.problem` la traduction des erreurs. Un paquet par ressource, plutôt qu'un paquet par nature technique, pour que la frontière reste lisible quand une deuxième ressource arrivera.

### Interface atomique

```
core/            modèles et client HTTP
shared/ui/       composants réutilisables hors d'une feature
features/documents/data/          sans Angular : requêtes, datasource, libellés
features/documents/ui/atoms/      état affiché, sans logique
features/documents/ui/molecules/  une interaction
features/documents/ui/organisms/  un assemblage réutilisable
features/documents/pages/         routes ; seules à injecter le client HTTP
```

La cascade est stricte : `data` ← `atoms` ← `molecules` ← `organisms` ← `pages`. Un organisme reçoit ses valeurs par `input()` et remonte ses intentions par `output()`. Seules la coquille et le routeur traversent les features.

## Vérification

Les règles sont exécutables, donc vérifiées :

- ArchUnit côté backend : `adaptersDependOnPortsOnly`, `portsDoNotDependOnImplementations`, `adaptersStayOutsideTheCompositionRoot`, `restAdapterDoesNotExposeTheAggregate`, chacune avec une fixture en contre-exemple qui prouve que la règle mord.
- `scripts/web-structure.test.mjs` côté interface : cascade atomique, indépendance de `core` et `shared`, traversée des features réservée à la coquille, et résolution de chaque import relatif. Exécuté par `make web-structure-test` et par `make verify`.

La non-régression est établie par les mêmes tests qu'avant le refactoring : 116 tests backend, 40 tests web, contrat OpenAPI inchangé, et un parcours navigateur sur les trois pages.

## Alternatives écartées

- **DTO de transport construits depuis l'agrégat dans le contrôleur** : laissait l'adapter REST dépendre du domaine et dupliquait la projection.
- **Un modèle de lecture par cas d'usage** : multiplierait les types pour des différences qui n'existent pas encore.
- **Conventions de nommage seules pour l'interface** (dossiers `atoms`/`molecules` sans règle) : rien n'empêche un import à contresens, le premier serait découvert en revue.
- **ESLint avec `no-restricted-imports`** : aurait ajouté une chaîne d'outils complète pour une règle que trente lignes de Node vérifient déjà sur les sources.
