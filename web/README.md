# Interface web — phase S0

Socle Angular 21 standalone, strict et zoneless, avec le routeur configuré et un test Vitest. La page d’accueil indique la phase du kata ; elle ne contient aucune fonctionnalité documentaire.

Depuis ce répertoire, après activation de la version Node indiquée par le `.nvmrc` à la racine :

```sh
npm ci
npm start
npm run test:ci
npm run build
npm run format:check
```

Le serveur de développement écoute sur `http://127.0.0.1:4200`.

Les modules Angular, le CLI et le builder sont verrouillés sur le même correctif 21.2.26. TypeScript reste en 5.9. Le fichier `package-lock.json` fixe les dépendances transitives ; utiliser `npm ci` pour les reproduire.

L’intégration HTTP, les composants métier et les tests Angular Testing Library arrivent en S5. AG Grid Enterprise arrive en S6 ; Tailwind **3** et les formulaires arrivent en S7. Aucune licence Enterprise n’est nécessaire pour S0.
