# S7 — Formulaire de contribution

**Statut : implémenté et vérifié dans le navigateur le 18 novembre 2026.**

## Ce qui est construit

- `DocumentForm` réactif : référence de modèle (création seulement, en lecture seule ensuite), titre, et `FormArray` de sections avec ajout et suppression. Limites alignées sur le contrat : 200 caractères pour les titres, 100 000 pour un contenu, 50 sections.
- Validation utile : `trimmedRequired` refuse une valeur blanche, ce que `Validators.required` accepte. Les erreurs sont liées au champ (`aria-invalid`, `aria-describedby`) et n'apparaissent qu'après interaction.
- Création (`POST /api/documents`) puis redirection vers le document ; enregistrement du contenu (`PUT .../content`) avec la version lue sur le serveur, jamais une version devinée.
- Barre d'actions dérivée du statut (`document-status.ts`, miroir de `docs/domain-model.md`) : `Draft` → soumettre, `InReview` → approuver ou rejeter, `Approved` → publier, `Published` → archiver, `Archived` → aucune. **Le domaine reste l'autorité** : un désaccord produit `409 INVALID_TRANSITION`, affiché tel quel.
- Le contenu n'est modifiable qu'en `Draft` ou `Rejected` ; les autres statuts affichent les sections en lecture seule.
- Vue d'audit : version, action, auteur et date de chaque entrée.
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

`document-form.spec.ts` : création avec une section vide, référence en lecture seule, titre blanc refusé avec `aria-invalid`, charge utile normalisée, ajout et suppression de sections. `document-detail.spec.ts` : actions par statut, enregistrement avec `expectedVersion`, transition, règle métier affichée, rechargement sur conflit, 404.
