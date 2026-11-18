# Interface web — S5 à S7

Application Angular 21 standalone, stricte et zoneless : liste des documents avec grille AG Grid, création et correction d'un document, actions selon le statut courant.

```sh
npm ci
npm start          # http://127.0.0.1:4200, proxy /api vers 127.0.0.1:8081
npm run test:ci
npm run build
npm run format:check
```

Le backend doit tourner (`make backend-run` à la racine). `proxy.conf.json` relaie `/api` vers le backend local, ce qui évite du CORS : le navigateur ne voit qu'une origine. `make seed-documents` charge 10 000 documents de démonstration pour l'exercice de grille.

## Organisation

| Chemin | Rôle |
|---|---|
| `src/app/core/models/document.model.ts` | Types du contrat REST : document, page, versions, actions |
| `src/app/core/api/document-api.ts` | Client HTTP typé et en-tête `X-User` sur les mutations |
| `src/app/core/api/api-failure.ts` | `ApiFailure` et lecture du `ProblemDetail` (code stable, champs refusés) |
| `src/app/shared/ui/molecules/api-failure-alert.ts` | Bandeau d'erreur commun, réutilisable hors documents |
| `src/app/features/documents/data/` | Sans Angular : traduction du tri, `IDatasource` de l'infinite row model, libellés et actions de statut |
| `src/app/features/documents/ui/atoms/status-badge.ts` | Pastille de statut : une seule définition des libellés et des couleurs |
| `src/app/features/documents/ui/molecules/section-editor.ts` | Édition d'une section, avec ses messages de limite |
| `src/app/features/documents/ui/organisms/document-form/` | Formulaire réactif assemblant les sections |
| `src/app/features/documents/ui/organisms/document-version-panel.ts` | Valeurs conservées d'une version, en lecture seule |
| `src/app/features/documents/pages/` | Pages routées : liste, création, détail ; elles seules parlent au client HTTP |
| `src/app/features/documents/ui/form-error.ts` | Messages d'erreur partagés par les composants de formulaire |

## Structure atomique

`core` ne dépend de rien d'autre que lui-même, `shared` ne dépend pas des features, et à l'intérieur d'une feature la cascade est stricte : `data` ← `atoms` ← `molecules` ← `organisms` ← `pages`. Seules la coquille (`app.ts`) et le routeur (`app.routes.ts`) traversent les features. Ces règles sont vérifiées par `scripts/web-structure.test.mjs`, exécuté par `make web-structure-test` et par `make verify` : un import à contresens fait échouer la vérification, comme les règles ArchUnit côté backend.

Les pages portent le suffixe `-page` et sont les seules à injecter `DocumentApi` : un organisme reçoit ses valeurs par `input()` et remonte ses intentions par `output()`.

## Choix de la grille

AG Grid **Community 36.3.0** avec `rowModelType: 'infinite'`. Le *Server-Side Row Model* proposé par la fiche d'origine est une fonctionnalité Enterprise qui suppose une licence ; le contrat de l'API (fenêtre `[startRow, endRow[`, `rowCount`, tri, filtres) correspond exactement à l'infinite row model. Détail et limites dans [la fiche S6](../../../docs/phases/S6-grid.md).

Le thème vient de la *Theming API* (`themeQuartz`), injectée par AG Grid : aucun fichier CSS de thème n'est importé. Les styles applicatifs passent par Tailwind 3 (`.postcssrc.json`, `tailwind.config.js`) ; les classes de statut sont écrites littéralement pour rester détectées.

La pagination est celle d'AG Grid, alimentée par le `rowCount` du serveur (25, 50 ou 100 lignes par page). La recherche est découplée de la frappe : la requête part 300 ms après la dernière touche, et tout changement de filtre repart de la première page.

## Tests

Vitest avec `TestBed` et `HttpTestingController` : 40 tests dans 9 fichiers. Le rendu AG Grid n'est pas testé en jsdom (mise en page réelle) ; le datasource, la traduction de requête, les états du composant et les composants de formulaire le sont.
