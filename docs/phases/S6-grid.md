# S6 — Grille, tri et filtres serveur

**Statut : implémenté et vérifié dans le navigateur le 18 novembre 2026.**

## Choix du row model et de la licence

La fiche d'origine propose AG Grid **Enterprise** avec le *Server-Side Row Model*. Ce choix n'est pas retenu ici :

- Le SSRM est une fonctionnalité **Enterprise** : l'utiliser suppose une licence ou un *trial* de 30 jours. Le kata n'active ni compte ni clé, et ne commite aucun secret.
- Le contrat exposé par l'API — `[startRow, endRow[`, `rowCount` exact, `sort` et filtres — est exactement celui de l'**Infinite Row Model**, disponible dans la version **Community**.

`ag-grid-community` et `ag-grid-angular` **36.3.0** sont donc utilisés avec `rowModelType: 'infinite'` : une requête par bloc de 25 lignes, aucun chargement global, total filtré pour la barre de défilement. Le comportement recherché — « une requête par bloc, pas de chargement global » — est démontré ; seule la classe de licence diffère, et la limite est consignée dans les notes de lecture.

## Ce qui est construit

- `document-grid-query.ts` : `toSort` et `toQuery` traduisent le modèle de tri AG Grid vers les quatre champs acceptés (`title`, `updatedAt`, `status`, `version`). Une colonne non acceptée retombe sur `updatedAt:desc` au lieu de produire un `400`.
- `document-datasource.ts` : `IDatasource.getRows` appelle `GET /api/documents` avec la fenêtre, le tri et les filtres courants ; succès → `successCallback(rows, rowCount)`, échec → `failCallback()` et message.
- `DocumentList` : colonnes (titre, statut en badge, version, date), tri par en-tête, filtre de statut et recherche `q` dans une barre d'outils, états vide et erreur, clic sur une ligne vers le détail.
- Badge de statut rendu par `valueFormatter` + `cellClass` (classes Tailwind littérales) plutôt qu'un `cellRenderer` : moins de DOM non testable, même résultat visuel.
- `scripts/seed-documents.sql` et `make seed-documents` : 10 000 documents cohérents (version = nombre d'entrées d'audit), dont 1 000 `InReview`, écrits directement dans le volume Compose.

## Limites assumées

- Le contrat HTTP n'offre que `filter.status` et `q` (titre). Les autres filtres de colonne AG Grid ne sont pas transmis : la barre d'outils porte donc les deux filtres que le serveur sait exécuter.
- `sort=status` trie le libellé technique alphabétiquement, pas dans l'ordre du cycle de vie.
- Le rendu AG Grid n'est pas testé en jsdom (mise en page réelle) : le datasource, la traduction de requête et l'état du composant le sont ; le rendu a été vérifié dans le navigateur.

## Vérification

```sh
make seed-documents
npm --prefix web run test:ci
```

Navigateur : `startRow=0&endRow=25&sort=updatedAt:desc` au chargement, une seule requête par tri (`sort=version:asc`), `filter.status=InReview` → `rowCount` 1000 et lignes filtrées.
