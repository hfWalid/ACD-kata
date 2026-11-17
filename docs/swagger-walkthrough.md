# Essayer S1–S4 avec Swagger UI

Ce parcours exerce le domaine, les cas d'usage, PostgreSQL et l'API via HTTP. Les tests automatisés restent la preuve des règles d'architecture, des écritures concurrentes et des migrations.

## Démarrer et ouvrir Swagger

JDK 21 et Docker doivent être disponibles. Si `.env.local` existe déjà, le conserver ; sinon suivre la configuration locale du [README](../README.md). Ne pas partager ce fichier ni son mot de passe.

```sh
cd /Users/hefhafwalid/Developer/KATAs/EDF/acd-kata
make infra-up
make backend-run
```

Après le démarrage réussi du backend, ouvrir [Swagger UI](http://127.0.0.1:8081/swagger-ui/index.html). Laisser le terminal du backend ouvert pendant les essais.

Pour chaque opération : déplier la route, cliquer sur **Try it out**, remplir les paramètres et le corps JSON, puis **Execute**. Lire le **Server response**, pas seulement les exemples de réponses affichés par Swagger.

Pour toutes les créations, modifications et transitions, renseigner **`X-User` : `walid`**. C'est l'auteur pédagogique enregistré dans l'audit ; aucun compte ni bouton **Authorize** n'est nécessaire. Les lectures n'exigent pas ce header.

## 1. Créer un document complet

Exécuter **POST `/api/documents`** avec `X-User: walid` et ce corps :

```json
{
  "templateRef": "rapport-v1",
  "title": "Rapport de contribution",
  "sections": [
    {
      "title": "Objet",
      "content": "Première contribution documentaire pour le kata."
    }
  ]
}
```

Attendre **201**. Copier l'`id` retourné : il remplacera `{id}` dans les routes suivantes. Vérifier `status: "Draft"`, `version: 1` et une seule entrée d'audit `CREATED`, avec l'auteur `walid` et son horodatage. Le header `Location` désigne le document créé.

## 2. Soumettre, relire, approuver et publier

Utiliser le même `id` et `X-User: walid` à chaque étape. Dans le corps, placer **la version reçue juste avant l'action**, pas la version souhaitée :

```json
{
  "expectedVersion": 1
}
```

| Ordre | Opération POST | `expectedVersion` à envoyer | Réponse attendue |
|---|---|---|---|
| 1 | `/api/documents/{id}/submit` | `1` | 200, `Submitted`, version `2` |
| 2 | `/api/documents/{id}/start-review` | `2` | 200, `InReview`, version `3` |
| 3 | `/api/documents/{id}/approve` | `3` | 200, `Approved`, version `4` |
| 4 | `/api/documents/{id}/publish` | `4` | 200, `Published`, version `5` |
| 5 | `/api/documents/{id}/archive` | `5` | 200, `Archived`, version `6` |

Cette numérotation suppose qu'aucune autre modification n'a eu lieu. Si une réponse retourne une autre version, utiliser cette valeur pour l'action suivante. Ne pas exécuter deux fois une action pour « rafraîchir » l'écran.

## 3. Relire et rechercher

Exécuter **GET `/api/documents/{id}`**. Après le parcours précédent : `Archived`, version `6`, six entrées d'audit ordonnées. Les actions sont `CREATED`, `SUBMITTED`, `REVIEW_STARTED`, `APPROVED`, `PUBLISHED`, `ARCHIVED`.

Exécuter **GET `/api/documents`** avec :

| Paramètre | Valeur |
|---|---|
| `startRow` | `0` |
| `endRow` | `100` |
| `sort` | `updatedAt:desc` |
| `filter.status` | `Archived` |
| `q` | `contribution` |

Attendre **200** avec `rowCount` et `rows`. `rowCount` est le total filtré ; `rows` contient la fenêtre demandée. `endRow` est exclusif ; la fenêtre autorise 1 à 500 lignes, avec un début entre 0 et 1 000 000.

Les tris acceptent `title`, `updatedAt`, `status`, `version`, suivis de `:asc` ou `:desc`. Les statuts sont sensibles à la casse : `Draft`, `Submitted`, `InReview`, `Approved`, `Rejected`, `Published`, `Archived`. Essayer `filter.status=InReview` pour voir les documents encore en revue ; une liste vide est normale si aucun ne correspond. Pour retirer le filtre, laisser le paramètre non renseigné et ne pas demander l'envoi d'une valeur vide.

## 4. Variante : rejeter, corriger, soumettre à nouveau

**Créer un autre document** et refaire seulement la soumission et le début de revue : il est alors `InReview`, version `3`. Ne pas appliquer cette variante au document déjà archivé.

Exécuter **POST `/api/documents/{id}/reject`**, `X-User: walid`, corps `{"expectedVersion":3}` : attendre **200**, `Rejected`, version `4`.

Exécuter **PUT `/api/documents/{id}/content`**, avec le même header :

```json
{
  "expectedVersion": 4,
  "title": "Rapport de contribution corrigé",
  "sections": [
    {
      "title": "Objet",
      "content": "Contribution corrigée après la première revue."
    }
  ]
}
```

Attendre **200**, toujours `Rejected`, version `5`, avec une entrée `CONTENT_UPDATED`. Puis exécuter **POST `/api/documents/{id}/submit`** avec `{"expectedVersion":5}` : attendre `Submitted`, version `6`. Le document pourra reprendre le parcours par `start-review` avec cette nouvelle version.

## 5. Vérifier les refus

Pour chaque erreur, vérifier le code HTTP et le corps `ProblemDetail`. Une mutation refusée ne doit ajouter ni version ni entrée d'audit : le confirmer avec un GET.

| Essai | Manipulation | Résultat attendu |
|---|---|---|
| Document vide | Créer un nouveau document avec `sections: []`, puis appeler `submit` avec sa version `1` | **409**, document toujours `Draft`, version `1` |
| Version périmée | Sur un document déjà passé en version `2` ou plus, envoyer une transition avec `expectedVersion: 1` | **409**, état et audit inchangés |
| Publication prématurée | Sur un nouveau brouillon complet en version `1`, appeler `publish` avec `expectedVersion: 1` | **409**, approbation préalable nécessaire |
| Requête invalide | Envoyer un titre vide lors de la création, ou `{"expectedVersion":"1"}` à une transition | **400** ; la version doit être un entier JSON positif |
| Auteur absent | Omettre `X-User` lors d'une création ou transition | **400** |
| Document absent | Faire un GET avec un UUID valide et inexistant, par exemple `a10a773d-09e7-4736-8a23-6d88785daa24` s'il n'a pas été créé | **404** |

## Arrêter

Dans le terminal du backend : **Ctrl-C**. Puis, depuis la racine du kata :

```sh
make infra-down
```

L'arrêt conserve le volume PostgreSQL et les documents créés. Ce parcours ne nécessite aucune suppression de données.
