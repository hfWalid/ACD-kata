# S7 — Formulaire de contribution

**Statut : implémenté et vérifié dans le navigateur le 18 novembre 2026.**

## Ce qui est construit

- `DocumentForm` réactif : référence de modèle (création seulement, en lecture seule ensuite), titre, et `FormArray` de sections avec ajout et suppression. Limites alignées sur le contrat : 200 caractères pour les titres, 100 000 pour un contenu, 50 sections.
- Validation utile : `trimmedRequired` refuse une valeur blanche, ce que `Validators.required` accepte ; les longueurs maximales sont vérifiées des deux côtés du formulaire, titre de section et contenu compris, et l'ajout de section s'arrête à 50. Les erreurs sont liées au champ (`aria-invalid`, `aria-describedby`) et n'apparaissent qu'après interaction.
- Les refus du serveur restent visibles : `ApiFailure` transporte le tableau `errors` du `problem+json` et `ApiFailureAlert` liste chaque champ refusé avec le message de l'API. Le formulaire bloque les cas qu'il connaît ; l'API reste l'autorité pour ceux qu'il ne connaît pas.
- Création (`POST /api/documents`) puis redirection vers le document ; enregistrement du contenu (`PUT .../content`) avec la version lue sur le serveur, jamais une version devinée.
- Barre d'actions dérivée du statut (`document-status.ts`, miroir de `docs/domain-model.md`) : `Draft` → soumettre, `InReview` → approuver ou rejeter, `Approved` → publier, `Published` → archiver, `Archived` → aucune. **Le domaine reste l'autorité** : un désaccord produit `409 INVALID_TRANSITION`, affiché tel quel.
- Le contenu n'est modifiable qu'en `Draft` ou `Rejected` ; les autres statuts affichent les sections en lecture seule.
- Vue des versions : version, action, auteur et date de chaque entrée, plus un bouton par ligne qui charge les valeurs conservées (`GET /api/documents/{id}/versions/{version}`) et les affiche dans un panneau refermable : titre, statut et sections telles qu'elles étaient. La version courante est marquée « actuelle » ; une version sans valeurs conservées affiche la réponse `404 DOCUMENT_VERSION_NOT_FOUND` au lieu d'un contenu reconstitué.
- Tailwind 3.4.19 (`@tailwindcss` via PostCSS), sans plugin ni configuration personnalisée.

## Décisions

- **Le front n'invente aucune règle métier.** Soumettre un document vide n'est pas bloqué côté client : le serveur répond `409 EMPTY_DOCUMENT` et le message du serveur est affiché. La table des actions ne fait que masquer les boutons illégaux ; elle est épinglée par un test.
- **Conflit de version** (`409 VERSION_CONFLICT`) : rechargement du document et message d'information ; la saisie en cours est conservée pour éviter de la perdre.
- **Composant `SectionEditor`** : un groupe de formulaire lié par `[formGroup]` plutôt que `formGroupName` dans `@for`. Cette décomposition évite une erreur de changement de détection (`NG0100`) lors de l'ajout d'une ligne, et garde le gabarit principal lisible.

## Limites assumées

- Après un rechargement pour conflit, le formulaire conserve les valeurs saisies alors que la version affichée est celle du serveur : un nouvel enregistrement écrasera la version concurrente. C'est volontaire (ne pas perdre la saisie) et signalé à l'utilisateur.
- Pas de brouillon local, pas d'optimistic UI, pas de gestion de focus avancée au-delà des libellés, `aria-live` et `aria-invalid`.

## Vérification

```sh
npm --prefix web run test:ci
```

`ui/organisms/document-form/document-form.spec.ts` : création avec une section vide, référence en lecture seule, titre blanc refusé avec `aria-invalid`, limites dépassées (titre 201, contenu 100 001) refusées avec le message du champ, charge utile normalisée, ajout et suppression de sections. `pages/document-detail-page.spec.ts` : actions par statut, enregistrement avec `expectedVersion`, transition, règle métier affichée, champs refusés par le serveur listés, valeurs d'une version affichées puis refermées, version sans instantané signalée, rechargement sur conflit, 404.
