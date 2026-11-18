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
| `src/app/core/document-api.ts` | Client HTTP typé, en-tête `X-User` sur les mutations, `ApiFailure` à partir du `ProblemDetail` |
| `src/app/documents/document-grid-query.ts` | Traduction du modèle de tri AG Grid vers `sort=champ:direction` |
| `src/app/documents/document-datasource.ts` | `IDatasource` de l'*infinite row model* : une requête par bloc |
| `src/app/documents/document-list/` | Grille, barre d'outils (statut, recherche), états vide et erreur |
| `src/app/documents/document-detail/` | Document, audit, actions du statut, formulaire si modifiable |
| `src/app/documents/document-form/` | Formulaire réactif et éditeur de section |
| `src/app/documents/document-status.ts` | Libellés, couleurs et table des actions autorisées |

## Choix de la grille

AG Grid **Community 36.3.0** avec `rowModelType: 'infinite'`. Le *Server-Side Row Model* proposé par la fiche d'origine est une fonctionnalité Enterprise qui suppose une licence ; le contrat de l'API (fenêtre `[startRow, endRow[`, `rowCount`, tri, filtres) correspond exactement à l'infinite row model. Détail et limites dans [la fiche S6](../../../docs/phases/S6-grid.md).

Le thème vient de la *Theming API* (`themeQuartz`), injectée par AG Grid : aucun fichier CSS de thème n'est importé. Les styles applicatifs passent par Tailwind 3 (`.postcssrc.json`, `tailwind.config.js`) ; les classes de statut sont écrites littéralement pour rester détectées.

## Tests

Vitest avec `TestBed` et `HttpTestingController` : 30 tests. Le rendu AG Grid n'est pas testé en jsdom (mise en page réelle) ; le datasource, la traduction de requête, les états du composant et les composants de formulaire le sont.
