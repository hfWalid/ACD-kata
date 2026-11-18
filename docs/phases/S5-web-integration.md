# S5 — Shell Angular intégré

**Statut : implémenté et vérifié dans le navigateur le 18 novembre 2026.** Le shell S0 devient une application qui consomme l'API réelle.

## Ce qui est construit

- Routes `/documents`, `/documents/new` et `/documents/:id`, chargées en *lazy* pour garder le bundle initial à 268 kB ; `/` redirige vers la liste.
- `DocumentApi` : un client typé par opération (`list`, `get`, `create`, `updateContent`, `transition`) sur `/api`, avec l'en-tête `X-User` sur les mutations uniquement.
- États explicites en signals : `loading`, `failure`, `total`, et `empty` dérivé. Chaque état est rendu par un texte accessible (`role="status"`, `role="alert"`).
- Shell : en-tête, navigation avec lien actif, `<router-outlet>`.

## Décisions

- **`HttpClient` + signals explicites, pas `resource()`.** Le chargement de la liste doit devenir un `IDatasource` AG Grid en S6 ; une primitive de chargement déclarative aurait été remplacée. Les tests utilisent `HttpTestingController`, ce que les notes de lecture réservent au choix retenu.
- **Pas d'Angular Testing Library.** `TestBed` et `HttpTestingController` suffisent pour les comportements testés ; ajouter une dépendance n'aurait rien démontré de plus.
- **Proxy de développement plutôt que CORS.** `web/proxy.conf.json` relaie `/api` vers `127.0.0.1:8081`. Aucune configuration CORS n'est ajoutée au serveur : le navigateur ne fait qu'une origine.
- **Échecs typés.** `ApiFailure` conserve `status`, `code` et `detail` du `ProblemDetail` ; les composants affichent le message du serveur et n'inventent pas de règle.

## Vérification

```sh
npm --prefix web run test:ci     # 30 tests Vitest
npm --prefix web run format:check
npm --prefix web run build
```

Tests : `core/api/document-api.spec.ts` (fenêtre, filtres, en-têtes), `core/api/api-failure.spec.ts` (code stable, champs refusés, réseau), `features/documents/data/document-status.spec.ts` (table des actions), `app.spec.ts` (navigation), `pages/document-detail-page.spec.ts` et `ui/organisms/document-form/document-form.spec.ts` (rendu, validation, erreurs, transitions). Les chemins suivent la structure atomique décrite dans [l'ADR 0003](../../adr/0003-application-read-model-and-atomic-ui.md).

Parcours exécuté dans le navigateur avec PostgreSQL et le bac à sable local : liste chargée depuis l'API, création, soumission, refus `EMPTY_DOCUMENT` affiché, audit visible.
