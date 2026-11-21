# Cas d'usage

Un seul humain dans le kata, plusieurs rôles fonctionnels. Aucune authentification : `X-User` est un auteur pédagogique, et la machine à états est la seule barrière.

```mermaid
flowchart LR
  C(("Contributeur"))
  R(("Relecteur"))
  P(("Publication"))
  V(("Consultation"))

  subgraph System["API contribution"]
    UC1["Créer un brouillon"]
    UC2["Corriger le contenu"]
    UC3["Soumettre"]
    UC4["Commencer la revue"]
    UC5["Approuver"]
    UC6["Rejeter"]
    UC7["Publier"]
    UC8["Archiver"]
    UC9["Lister et rechercher"]
    UC10["Consulter un document"]
    UC11["Consulter une version enregistrée"]
  end

  C --> UC1
  C --> UC2
  C --> UC3
  R --> UC4
  R --> UC5
  R --> UC6
  P --> UC7
  P --> UC8
  V --> UC9
  V --> UC10
  V --> UC11
  UC3 -.->|"si Rejected"| UC2
  UC10 -.->|"depuis le journal d'audit"| UC11
```

## Détail

| Cas d'usage | Écran | API | Condition |
|---|---|---|---|
| Créer un brouillon | `/documents/new` | `POST /api/documents` | titre et modèle non vides |
| Corriger le contenu | `/documents/{id}` | `PUT /api/documents/{id}/content` | statut `Draft` ou `Rejected` |
| Soumettre | `/documents/{id}` | `POST .../submit` | `Draft` ou `Rejected`, ≥ 1 section non vide |
| Commencer la revue | `/documents/{id}` | `POST .../start-review` | `Submitted` |
| Approuver | `/documents/{id}` | `POST .../approve` | `InReview` |
| Rejeter | `/documents/{id}` | `POST .../reject` | `InReview` |
| Publier | `/documents/{id}` | `POST .../publish` | `Approved` |
| Archiver | `/documents/{id}` | `POST .../archive` | `Published` |
| Lister et rechercher | `/documents` | `GET /api/documents` | — |
| Consulter un document | `/documents/{id}` | `GET /api/documents/{id}` | — |
| Consulter une version enregistrée | `/documents/{id}` | `GET /api/documents/{id}/versions/{version}` | version écrite après la migration 002 |

Toute mutation d'un document existant exige `expectedVersion` : la version métier lue avant l'action.

## Ce que le front fait et ne fait pas

| Le front fait | Le front ne fait pas |
|---|---|
| Masque les actions illégales du statut | Décider qu'une soumission est valide |
| Valide les formats (longueurs, champs vides) | Remplacer la validation du serveur |
| Affiche le `code` et le `detail` du `ProblemDetail` | Traduire un 409 en règle inventée |
| Recharge sur `VERSION_CONFLICT` | Résoudre le conflit à la place de l'utilisateur |
| Affiche une version stockée à la demande | Rejouer l'historique côté client |

## Hors périmètre S0–S7

Authentification, autorisation par rôle, publication externe, revue distribuée, notifications. Voir [roadmap](../docs/roadmap.md) S8+.
