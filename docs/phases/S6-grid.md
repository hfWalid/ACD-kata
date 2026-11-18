# S6 — Grille, tri et filtres serveur

**Statut : implémenté et vérifié dans le navigateur le 18 novembre 2026.**

## Choix du row model et de la licence

La fiche d'origine propose AG Grid **Enterprise** avec le *Server-Side Row Model*. Ce choix n'est pas retenu ici :

- Le SSRM est une fonctionnalité **Enterprise** : l'utiliser suppose une licence ou un *trial* de 30 jours. Le kata n'active ni compte ni clé, et ne commite aucun secret.
- Le contrat exposé par l'API — `[startRow, endRow[`, `rowCount` exact, `sort` et filtres — est exactement celui de l'**Infinite Row Model**, disponible dans la version **Community**.

`ag-grid-community` et `ag-grid-angular` **36.3.0** sont donc utilisés avec `rowModelType: 'infinite'` : une requête par bloc de 25 lignes, aucun chargement global, total filtré pour la barre de défilement. Le comportement recherché — « une requête par bloc, pas de chargement global » — est démontré ; seule la classe de licence diffère, et la limite est consignée dans les notes de lecture.

## Ce qui est construit

- `features/documents/data/document-grid-query.ts` : `toSort` et `toQuery` traduisent le modèle de tri AG Grid vers les quatre champs acceptés (`title`, `updatedAt`, `status`, `version`). Une colonne non acceptée retombe sur `updatedAt:desc` au lieu de produire un `400`.
- `features/documents/data/document-datasource.ts` : `IDatasource.getRows` appelle `GET /api/documents` avec la fenêtre, le tri et les filtres courants ; succès → `successCallback(rows, rowCount)`, échec → `failCallback()` et message.
- `DocumentList` : colonnes (titre, statut en badge, version, date), tri par en-tête, filtre de statut et recherche `q` dans une barre d'outils, états vide et erreur, clic sur une ligne vers le détail.
- Badge de statut rendu par `valueFormatter` + `cellClass` (classes Tailwind littérales) plutôt qu'un `cellRenderer` : moins de DOM non testable, même résultat visuel.
- `scripts/seed-documents.sql` et `make seed-documents` : 10 000 documents cohérents (version = nombre d'entrées d'audit), dont 1 000 `InReview`, écrits directement dans le volume Compose.

## Pagination

La pagination s'appuie sur le `rowCount` renvoyé par le serveur : AG Grid affiche « Page X sur Y », les boutons première/précédente/suivante/dernière et un sélecteur de 25, 50 ou 100 lignes par page (`pagination`, `paginationPageSize`, `paginationPageSizeSelector`). Le `cacheBlockSize` reste fixé à 25 : une page de 50 lignes demande deux blocs, donc deux requêtes, et AG Grid ne charge que les blocs correspondant aux lignes visibles. Le total et le nombre de pages viennent tous deux du serveur, jamais d'un comptage local.

Changer un filtre repart de la première page : le datasource est réaffecté (`setGridOption('datasource', …)`), ce qui reconstruit le row model, vide les blocs obsolètes et relance une seule requête. Sans cela, un filtre réduisant le jeu de résultats laisserait l'utilisateur sur une page qui n'existe plus.

## Recherche réactive

La saisie est découplée de la requête : `searchInput` suit chaque frappe pour l'affichage, `search` n'est mis à jour qu'après 300 ms sans frappe (`SEARCH_DEBOUNCE_MS`), puis déclenche le rechargement. Un terme inchangé après `trim()` ne requête pas de nouveau, et le minuteur est annulé au changement de filtre, à l'effacement et à la destruction du composant — sans quoi la frappe produirait une requête par caractère.

La barre d'outils affiche le total filtré (« N document(s) pour ces filtres »), un bouton « Effacer les filtres » qui remet statut et recherche à zéro en un seul rechargement, et rappelle que `q` porte sur le titre, littéralement et sans tenir compte de la casse. Le libellé du sélecteur de statut suit l'ordre du cycle de vie (`DOCUMENT_STATUSES`), pas l'alphabet.

## Limites assumées

- Le contrat HTTP n'offre que `filter.status` et `q` (titre). Les autres filtres de colonne AG Grid ne sont pas transmis : la barre d'outils porte donc les deux filtres que le serveur sait exécuter, et aucun filtre de colonne n'est activé pour ne pas laisser croire à un filtrage serveur.
- `sort=status` trie le libellé technique alphabétiquement, pas dans l'ordre du cycle de vie.
- Le rendu AG Grid n'est pas testé en jsdom (mise en page réelle) : le datasource, la traduction de requête et l'état du composant le sont ; le rendu a été vérifié dans le navigateur.

## Vérification

```sh
make seed-documents
npm --prefix web run test:ci
```

Navigateur : `startRow=0&endRow=25&sort=updatedAt:desc` au chargement, une seule requête par tri (`sort=version:asc`), `filter.status=InReview` → `rowCount` 1000 et lignes filtrées. Page suivante → `startRow=50&endRow=75` (bloc suivant) ; dernière page → `startRow=10000&endRow=10025` pour 10 006 lignes ; frappe de `00042` → une seule requête `q=00042`, 248 ms après la dernière touche, sans requête intermédiaire ; « Effacer les filtres » → une requête sans `q` ni `filter.status`. Aucun message console pendant une frappe rapide combinée à un changement de taille de page.
