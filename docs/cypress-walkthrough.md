# Apprendre Cypress sur ce kata (S9)

Ce document explique la suite `web/cypress/` **pour quelqu'un qui n'a jamais écrit de test
Cypress**. Il décrit un modèle mental, le code réel du dépôt, les pièges rencontrés pour de vrai, et
une méthode de débogage. La fiche de phase ([S9](phases/S9-e2e.md)) contient les décisions et les
preuves ; ce parcours contient l'apprentissage.

## 1. Le modèle mental

Cypress n'est pas un pilote de navigateur piloté depuis l'extérieur. C'est une application qui
**s'exécute dans le navigateur**, dans la même page que l'application testée :

```
terminal ──► Cypress (processus Node) ──► navigateur ──► votre application + le code du spec
                    │                                        │
                    └── relaie les ordres, collecte les résultats, écrit les captures
```

Trois conséquences pratiques :

1. **Pas de `await`, pas de `sleep`.** Les commandes (`cy.get`, `cy.click`, …) sont mises en file et
   exécutées dans l'ordre. `cy.get('#title').type('x')` ne s'exécute pas au moment où vous l'écrivez.
2. **La notion centrale est la répétition (*retry-ability*).** Une assertion dans `.should()` est
   rejouée jusqu'à `defaultCommandTimeout` (8 s ici). C'est pour cela qu'on n'écrit pas d'attente :
   on écrit une assertion qui doit finir par être vraie.
3. **Le test voit ce que l'utilisateur voit.** S'il faut modifier l'application pour rendre un
   élément trouvable, c'est généralement un signe d'accessibilité à corriger côté application.

Ce que Cypress **n'est pas** : un outil de charge (une seule page, un utilisateur à la fois), ni un
outil de test d'API (utilisez `cy.request`, mais pas pour mesurer des performances), ni un
remplacement des tests unitaires. Ici il couvre huit tests qui traversent navigateur, proxy, REST,
domaine et PostgreSQL — l'étage le plus haut de la pyramide, le plus lent et le plus fragile.

## 2. L'anatomie de la suite

Quatre fichiers, chacun avec un rôle unique :

| Fichier | Rôle | À retenir |
| --- | --- | --- |
| `web/cypress.config.ts` | réglages du projet | `baseUrl`, timeouts, `retries`, captures d'écran |
| `web/cypress/tsconfig.json` | typage des specs | `types: ["cypress"]` donne `cy.*` et `Cypress.*` |
| `web/cypress/support/api.ts` | vocabulaire partagé | types du contrat REST + isolation des données |
| `web/cypress/support/commands.ts` | commandes réutilisables | `cy.createDocument`, `cy.transitionDocument`, `cy.titles` |
| `web/cypress/support/e2e.ts` | point d'entrée du support | importe les commandes, une seule fois par run |

```ts
// cypress.config.ts — les seuls réglages qui comptent au démarrage
export default defineConfig({
  e2e: {
    baseUrl: 'http://127.0.0.1:4200', // cy.visit('/documents') devient cette URL
    specPattern: 'cypress/e2e/**/*.cy.ts', // où sont les specs
    supportFile: 'cypress/support/e2e.ts', // chargé avant chaque spec
    retries: { runMode: 1, openMode: 0 }, // 1 seconde chance en CI, aucune en local
    viewportWidth: 1280,
    viewportHeight: 900,
  },
});
```

`baseUrl` est ce qui relie Cypress au serveur de développement Angular. Le dev server utilise
`web/proxy.conf.json` pour rediriger `/api/**` vers `127.0.0.1:8081` : **le navigateur ne fait donc
jamais d'appel cross-origin**, et il n'y a pas de CORS à configurer sur le backend. Les specs
appellent `/api/documents` en relatif pour cette raison.

## 3. Lire un spec ligne par ligne

Extrait réel de `cypress/e2e/contribution.cy.ts` :

```ts
it('crée un brouillon, le soumet et laisse une trace auditable', () => {
  const title = titledWith(uniqueToken(), 'notice');

  cy.visit('/documents/new');            // navigation, attend le chargement
  cy.get('#templateRef').type('notice-v1');
  cy.get('#title').type(title);
  cy.get('#section-title-0').type('Objet');
  cy.get('#section-content-0').type('Contenu saisi par le parcours end-to-end.');
  cy.contains('button', 'Créer le brouillon').click();

  cy.location('pathname').should('match', /^\/documents\/[0-9a-f-]{36}$/);
  cy.contains('h1', title).should('be.visible');
  cy.get('app-status-badge').should('contain', 'Brouillon');

  cy.contains('button', 'Soumettre').click();

  cy.get('app-status-badge').should('contain', 'Soumis');
  cy.get('tbody tr').should('have.length', 2);
});
```

Ce qui mérite une explication :

- **`cy.get('#title')` plutôt qu'une classe CSS.** L'identifiant est stable, unique et visible dans
  le HTML. `#section-title-0` suit l'index de la ligne du `FormArray` : c'est le contrat implicite du
  formulaire (S7), et le spec le documente.
- **`cy.contains('button', 'Soumettre')`** cible un bouton **par son texte**, donc par ce que
  l'utilisateur lit. Si le libellé change, le test casse : c'est voulu, c'est un changement de
  parcours.
- **`app-status-badge`** au lieu du texte seul : le mot « Brouillon » apparaît aussi dans une phrase
  d'aide de la page. Un `cy.contains('Brouillon')` serait ambigu et trouverait le mauvais élément.
  Quand deux éléments se ressemblent, on précise le composant.
- **`cy.location('pathname').should('match', …)`** : à la création, l'identifiant est inconnu, donc on
  vérifie la *forme* de l'URL au lieu d'une valeur. `should` rejoue l'assertion le temps que la
  navigation se termine.
- **`should` partout, aucune attente explicite.** Le `have.length, 2` est la preuve que la deuxième
  commande a produit exactement une entrée d'audit — pas « au moins deux ».

## 4. La règle d'or : préparer par l'API, agir par l'interface

```ts
// support/commands.ts
Cypress.Commands.add('createDocument', (input) =>
  cy
    .request<DocumentView>({
      method: 'POST',
      url: '/api/documents',
      headers: { 'X-User': API_AUTHOR },
      body: { templateRef: 'notice-v1', title: input.title, sections: input.sections },
    })
    .then((response) => response.body),
);
```

Le parcours de décision (`decision.cy.ts`) doit approuver un document **soumis**. Le préparer en
cliquant dans l'interface coûterait plusieurs secondes et rendrait le spec dépendant du formulaire :
une régression du formulaire ferait échouer aussi les tests de décision, et on ne saurait plus où
est la faute. On le prépare donc par l'API, et **on n'exerce par l'interface que le comportement
testé**. C'est la recommandation « server communication » de la documentation Cypress.

Corollaire important : un spec qui prépare par l'API échoue avec un message clair (`cy.request` casse
sur un statut inattendu) si le contrat REST est cassé. La panne reste localisée.

## 5. Isoler les données dans une base de 10 000 documents

La base locale contient 10 000 documents de démonstration (`make seed-documents`). Assertions du type
« la première ligne » ou « 3 lignes » sont donc fausses par construction. Deux fonctions suffisent :

```ts
// support/api.ts
export function uniqueToken(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`;
}

export function titledWith(token: string, name: string): string {
  return `E2E ${token} ${name}`; // trié et recherchable : « E2E 3f2k1a notice »
}
```

Chaque exécution crée ses documents avec un titre qui commence par le jeton, puis filtre la grille
dessus (`cy.get('#document-search').type(token)`). Conséquences :

- la suite est **rejouable** sans nettoyer la base ;
- deux exécutions en parallèle ne se marchent pas dessus ;
- le jeton est **triable** (horodatage en base 36), ce qui rend l'assertion de tri déterministe.

Aucun spec ne supprime ni ne tronque quoi que ce soit : un test qui détruit des données ne peut pas
tourner sur une base partagée, et c'est précisément la situation ici.

## 6. Les quatre pièges rencontrés pour de vrai

**1. `bad option: --no-sandbox` au premier lancement.** Cause : l'environnement exporte
`ELECTRON_RUN_AS_NODE=1`, ce qui fait tourner le binaire Electron comme un simple Node ; il ne
comprend alors plus les options de Cypress. Solutions : `env -u ELECTRON_RUN_AS_NODE npx cypress run`
(c'est ce que fait `make e2e-run`) ou `unset ELECTRON_RUN_AS_NODE`.

**2. Les classes CSS d'AG Grid ne sont pas un contrat.** Le premier spec cherchait
`.ag-center-cols-container .ag-row`. Ce conteneur n'existe plus dans AG Grid 36, qui rend les lignes
dans `.ag-grid-scrolling-container`. La règle : viser les **rôles ARIA** que la grille émet, parce que
ce sont eux que lit un lecteur d'écran et qu'ils sont stables :

```ts
cy.get('[role="row"].ag-row');
cy.get('[role="gridcell"][col-id="title"]');
```

**3. Une grille virtualisée n'affiche pas toutes les données.** Après « Effacer les filtres », on ne
peut pas asserter `have.length, 3` : la grille ne rend que les lignes visibles (~20 selon la hauteur).
L'assertion porte alors sur ce qui est réellement observable : les valeurs des filtres et le libellé
de synthèse. Tester un modèle de rendu fenêtré, c'est tester ce qui est à l'écran.

**4. `cy.contains(/^\d+ document\(s\)$/)` ne trouve rien.** Le texte du `<p role="status">` contient
des retours à la ligne et des espaces autour du compteur, donc les ancres `^` et `$` échouent. On
asserte sur l'élément : `should('contain.text', 'document(s)')` et
`should('not.contain.text', 'pour ces filtres')`.

## 7. Déboguer

```sh
npm --prefix web run e2e                     # mode interactif : navigateur + time travel
npm --prefix web run e2e:ci -- --spec cypress/e2e/grid.cy.ts
make e2e-run                                 # les 4 specs, avec vérification des deux serveurs
```

- **Mode interactif** : chaque commande est listée à gauche ; cliquer dessus rejoue l'état du DOM à
  cet instant. C'est l'outil nº 1 : on voit la page telle qu'elle était, pas telle qu'elle est.
- **`cy.pause()`** arrête l'exécution et rend la main dans le navigateur (à retirer avant de
  commiter), **`cy.debug()`** met un point d'arrêt sur la commande.
- **Captures** : un échec écrit `web/cypress/screenshots/` (ignoré par Git). Sur ce dépôt, la capture
  du timeout AG Grid a montré que les lignes existaient — le problème était le sélecteur, pas
  l'attente. Lire la capture évite des heures de tâtonnement.
- **Les retries** (`runMode: 1`) servent à absorber une instabilité réseau, pas à masquer un test
  faux. Un spec qui ne passe qu'à la deuxième tentative est un bug à corriger : le rapport le signale
  comme *flaky*.

## 8. Règles d'hygiène retenues ici

1. **Un spec = un parcours métier lisible**, pas un enchaînement de clics. Les noms sont en français
   et décrivent l'intention (« laisse une trace auditable »).
2. **Aucune attente arbitraire** (`cy.wait(2000)`). Si une assertion ne suffit pas, c'est que
   l'application n'expose pas d'état observable — à corriger dans l'application.
3. **Aucun état partagé entre specs.** Cypress exécute les fichiers dans l'ordre alphabétique et
   chaque fichier peut tourner seul ; un spec qui dépend d'un autre est un piège à CI.
4. **Les specs restent boîte noire.** Une règle de `scripts/web-structure.test.mjs` interdit d'importer
   `src/app` depuis `cypress/e2e/` : un spec qui importe du code applicatif casse au premier
   refactoring et ne prouve plus que la page fonctionne seule.
5. **Ne jamais tester deux fois la même règle.** `EMPTY_DOCUMENT` est vérifié par un test de domaine,
   un test MVC et un parcours navigateur ; le parcours vérifie seulement que le message du serveur
   **remonte tel quel** dans l'interface.

## 9. Ce que la suite ne prouve pas

- Pas de vérification multi-onglets, ni de parcours en plusieurs sessions.
- Pas de test de charge ni de performance.
- Pas de couverture : Cypress n'instrumente pas l'application ici. La couverture vient de Vitest.
- Les specs supposent un état de départ : le backend migré et au moins un utilisateur `web-kata` dans
  l'audit (créé par le premier parcours). Elles ne vérifient pas les migrations, ni Kafka, ni le
  service de revue : ces étages ont leurs propres tests.

Suite logique : [S10 — qualité](sonarqube-walkthrough.md) pour mesurer ce que ces tests couvrent, et
[la fiche S9](phases/S9-e2e.md) pour les décisions et les preuves.
