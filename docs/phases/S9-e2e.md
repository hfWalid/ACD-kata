# S9 — Parcours de bout en bout avec Cypress

**Statut : implémenté et vérifié localement (navigateur réel, backend et base réels) le 21 novembre 2026.**
Le parcours d'apprentissage est dans [docs/cypress-walkthrough.md](../cypress-walkthrough.md).

## Ce qui est construit

Huit tests, quatre fichiers, exécutés en 5 secondes environ contre la pile complète.

| Fichier | Tests | Parcours |
| --- | --- | --- |
| `web/cypress/e2e/contribution.cy.ts` | 2 | créer un brouillon par le formulaire, le soumettre, lire la piste d'audit ; refus `EMPTY_DOCUMENT` affiché tel quel |
| `web/cypress/e2e/decision.cy.ts` | 2 | soumettre → commencer la revue → approuver (4 entrées d'audit) ; rejeter → corriger → resoumettre |
| `web/cypress/e2e/grid.cy.ts` | 3 | filtre de titre côté serveur avec total exact, tri par en-tête (croissant puis décroissant), filtre de statut puis effacement |
| `web/cypress/e2e/conflict.cy.ts` | 1 | page périmée face à une transition concurrente : bandeau d'information puis état rechargé |

Le harnais :

- `web/cypress.config.ts` — `baseUrl` 127.0.0.1:4200, `specPattern cypress/e2e/**/*.cy.ts`,
  `supportFile`, viewport 1280×900, `defaultCommandTimeout` 8 s, `responseTimeout` 15 s,
  `retries { runMode: 1, openMode: 0 }`, `video: false`, captures sur échec.
- `web/cypress/support/api.ts` — vocabulaire partagé : `uniqueToken()`, `titledWith()`, auteurs
  `web-kata` / `e2e-runner`, types du contrat REST.
- `web/cypress/support/commands.ts` — `cy.createDocument`, `cy.transitionDocument`, `cy.titles`.
- `web/cypress/support/e2e.ts` — point d'entrée du support.
- `web/cypress/tsconfig.json` — typage des specs (`types: ["cypress"]`).
- `Makefile` — `make e2e-run` vérifie que le backend et le serveur de développement répondent, puis
  lance Cypress en tête-à-tête. Les scripts `e2e`, `e2e:ci` et `coverage` sont dans `web/package.json`.
- `scripts/web-structure.test.mjs` — nouvelle règle : un spec ne peut rien importer de `src/app`.

## Décisions

- **Cypress plutôt que Playwright.** Le parcours est un parcours *dans* le navigateur, pas un test de
  protocole : le mode interactif (voyage dans le temps sur chaque commande) est ce qui rend
  l'apprentissage autoportant. Playwright gagne sur le multi-onglets et le parallélisme, dont ce kata
  n'a pas besoin.
- **Préparer par l'API, agir par l'interface.** Une commande `cy.request` prépare un document soumis
  en quelques millisecondes, puis un seul geste passe par l'interface. Sans cela, une régression du
  formulaire ferait échouer aussi les tests de décision, et on ne saurait plus localiser la faute.
- **Isoler par jeton plutôt que nettoyer.** Chaque exécution crée ses documents avec un titre
  `E2E <jeton> …` et filtre dessus. La base garde ses 10 000 documents de démonstration ; la suite
  est rejouable et deux exécutions ne se marchent pas dessus. Aucun spec ne supprime de données.
- **Sélecteurs par rôle et par texte, pas par classe CSS.** `[role="row"]`, `[role="gridcell"][col-id]`,
  `#title`, `cy.contains('button', 'Soumettre')` : ce sont les repères qu'un utilisateur et un lecteur
  d'écran perçoivent, et ils survivent aux renommages internes d'AG Grid.
- **Aucune attente arbitraire.** Toutes les synchronisations passent par une assertion (`should`).
  Si une assertion ne suffit pas, c'est l'application qui n'expose pas d'état observable.
- **Retries seulement en mode `run`.** Une seconde chance en CI absorbe une instabilité réseau, sans
  masquer un test faux en développement ; le rapport signale de toute façon un test *flaky*.
- **Le spec ne fait pas partie de la pyramide applicative.** Une règle de structure interdit
  d'importer `src/app` : un parcours doit prouver que la page fonctionne seule, pas se coupler au code
  qu'elle assemble.

## Quatre pièges rencontrés, et leur cause

| Symptôme | Cause | Correctif |
| --- | --- | --- |
| `bad option: --no-sandbox` | l'environnement exporte `ELECTRON_RUN_AS_NODE=1`, Cypress tourne alors comme un Node nu | `env -u ELECTRON_RUN_AS_NODE` (ce que fait `make e2e-run`) |
| `.ag-center-cols-container .ag-row` introuvable | conteneur renommé dans AG Grid 36 (`.ag-grid-scrolling-container`) | sélecteurs ARIA `[role="row"]`, `[role="gridcell"]` |
| `have.length 3` échoue avec 20 lignes | grille virtualisée : seules les lignes visibles sont rendues | asserter sur l'état observable (valeurs des filtres, libellé de synthèse) |
| `cy.contains(/^…$/)` introuvable | le texte du `<p role="status">` contient des espaces et retours à la ligne | `should('contain.text', …)` / `should('not.contain.text', …)` |

## Vérification

```sh
make backend-run        # terminal 1 : 127.0.0.1:8081
npm --prefix web start  # terminal 2 : 127.0.0.1:4200
make e2e-run            # terminal 3
```

| Observation | Résultat |
| --- | --- |
| Exécution complète | 4 fichiers, 8 tests, 8 réussis, 0 ignoré |
| Durée | 5 s (navigateur déjà installé) |
| Répétabilité | 5 exécutions consécutives vertes, 0 test *flaky* |
| Isolation | exécution sur une base contenant 10 002 documents de démonstration + import E1 |
| Vérifications du dépôt | `format:check` propre, 40 tests web, 9 tests de structure, build 269,03 kB initial |

Les tests Cypress ne sont pas exécutés par `make verify` : ils exigent deux serveurs et une base
locale. `make verify` reste la vérification hors-ligne ; `make e2e-run` est la vérification de
parcours.

## Limites assumées

- **Pas de job CI** : un parcours Cypress en CI demande Java, Node et un navigateur dans le même job,
  plus une pile PostgreSQL/Kafka (dinD) et des attentes de santé. Cela relève de S11 (« packaging
  Docker et pipeline GitLab complète ») ; la couture est déjà là (`make e2e-run`, `npm run e2e:ci`).
  Aucune revendication d'exécution distante.
- Pas de couverture issue de Cypress : la couverture vient de Vitest (S10).
- Pas de multi-onglets, ni de sessions parallèles, ni de test de charge.
- Les specs supposent le backend migré et le serveur de développement lancé ; elles ne testent ni les
  migrations, ni Kafka, ni le service de revue, qui ont leurs propres tests.
- Aucun parcours d'archivage, ni de pagination au-delà du premier bloc de 25 lignes.
