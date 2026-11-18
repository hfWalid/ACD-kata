# Domaine du kata — S1–S4

## Langage

- **Document** : agrégat de contribution, identifié par un UUID. Il contient une référence de modèle, un titre, des sections ordonnées, un état, une version métier et un audit.
- **Section** : titre et contenu ; le contenu peut être vide pendant la préparation d'un brouillon.
- **Auteur** : identifiant textuel de la personne effectuant une action dans l'exercice.
- **Version métier** : commence à 1 ; augmente exactement une fois par action réussie.
- **Audit** : suite ordonnée des actions réussies, avec auteur, instant et version résultante. Ce n'est pas encore une messagerie événementielle.
- **Version conservée** : valeurs atteintes par le document après une action réussie (titre, statut, sections). L'audit dit qui a fait quoi et quand ; la version conservée dit ce que le document contenait alors.
- **Contribution** : contexte responsable du contenu, de l'état et de leur cohérence. Le contexte de revue séparé reste une extension S8.

## Transitions

| État | Action | État résultant |
|---|---|---|
| Draft | Modifier le contenu | Draft |
| Draft | Soumettre | Submitted |
| Submitted | Commencer la revue | InReview |
| InReview | Approuver | Approved |
| InReview | Rejeter | Rejected |
| Rejected | Corriger le contenu | Rejected |
| Rejected | Soumettre à nouveau | Submitted |
| Approved | Publier | Published |
| Published | Archiver | Archived |

Toute autre combinaison est refusée. Une soumission exige au moins une section et un contenu non blanc pour chacune. Un refus ne modifie ni état, ni version, ni audit.

## Valeurs et temps

Titre de document et de section : 200 caractères au maximum. Référence de modèle et auteur : 100. Un document accepte jusqu'à 50 sections ; chaque contenu accepte 100 000 caractères. Les listes sont copiées défensivement et les objets du domaine sont immuables.

Le domaine reçoit ses instants explicitement et refuse un instant antérieur à la dernière action. Le câblage de l'application utilise une horloge à la microseconde, précision conservée par PostgreSQL. Les tests de domaine utilisent des instants fixes.

## Persistance et concurrence

Le dépôt charge un agrégat, puis sauvegarde une nouvelle version en exigeant sa version antérieure. La comparaison applicative détecte un conflit déjà visible ; le contrôle optimiste de l'adapter protège aussi la course entre deux écritures simultanées. La version technique JPA ne sort pas de l'adapter.

Document, sections, audit et version conservée partagent une transaction. Une action concurrente perdante ne doit jamais laisser une entrée d'audit, des sections ou un instantané supplémentaires.

`document_versions` est en ajout seul : une correction écrit la version suivante et ne réécrit jamais les précédentes. Les documents antérieurs à la conservation des instantanés gardent les valeurs de leur version courante (saisies par la migration) ; leurs versions plus anciennes restent sans valeurs et l'API répond `DOCUMENT_VERSION_NOT_FOUND` plutôt que d'inventer un contenu.

## Limites

Ce modèle est pédagogique. Il ne reproduit pas un circuit de validation EDF établi. Les modèles documentaires ne sont pas administrés dans cette tranche ; leur référence est une valeur validée. Il n'y a pas encore d'authentification, d'autorisation, de publication Kafka, d'outbox ou de contexte de revue distribué.
