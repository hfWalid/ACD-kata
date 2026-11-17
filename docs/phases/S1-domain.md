# S1 — Premier domaine, exercice TDD

**Statut : implémenté après S0 ; domaine vérifié indépendamment avant S2.** La progression TDD ci-dessous reste un support d'apprentissage. Les règles retenues sont décrites dans le modèle de domaine et l'ADR 0002.

## Intention

Construire un domaine documentaire Java pur, puis savoir expliquer les règles qu'il protège. Le workflow du kata est un exercice ; il reste à confronter au domaine réel lors de l'arrivée en mission.

Avant de coder, relire la [roadmap](../roadmap.md), l'[ADR de structure](../adr/0001-incremental-foundation.md) et les [notes de lecture](../preparation/reading-notes.md). S1 fait partie de la tranche S1–S4 explicitement demandée.

## Première petite tâche

Définir ce que signifie « document vide » pour l'exercice et formuler un seul exemple de soumission refusée. Écrire un test comportemental dans `domain`, observer son échec, puis écrire le minimum pour le faire passer. Ne pas générer d'emblée toutes les classes de la spécification.

Consigner dans le journal la règle choisie et le résultat du test rouge. Le nom du test doit exprimer l'intention métier, pas un accesseur.

## Progression proposée

1. Définir le vocabulaire minimal : document, section, identifiant et état initial.
2. Protéger un premier invariant, puis son cas autorisé.
3. Ajouter les transitions demandées une par une, avec leur règle et leur refus explicite.
4. Introduire des types dédiés lorsque leur invariant ou leur usage le justifie.
5. Après les tests verts, revoir les noms, responsabilités et duplication.

La spécification d'origine suggère `Document`, `DocumentId`, `TemplateRef`, `Section` et une hiérarchie fermée `DocumentStatus`. Ce sont des pistes de travail, pas une solution à recopier avant d'avoir défini les comportements.

## Questions à décider avant les tests correspondants

- Quel contenu rend une soumission recevable : titre, sections, contenu de chaque section ?
- Quelles transitions sont autorisées depuis chaque état, notamment après un rejet ?
- Qu'est-ce qui fait évoluer une version : modification du contenu, changement d'état, les deux ?
- Quels faits nécessitent auteur et horodatage, et comment fournir ces valeurs sans dépendance cachée à l'environnement ?
- Une commande refusée doit-elle laisser exactement le même état et la même version ?

Décider une règle lorsqu'elle devient nécessaire au test. Les cas d'usage, la persistance et la messagerie sont hors de S1.

## Critères de sortie à confirmer au démarrage de S1

- Les règles convenues ont des exemples de réussite et de refus testés.
- Les transitions retenues sont exhaustives ; une transition illégale produit une erreur métier explicite.
- Une action refusée ne modifie pas le document.
- Les choix de version et de temps sont déterministes et expliqués.
- `domain` compile et ses tests passent sans Spring, JPA ou Jackson.
- Les branches de la machine à états retenue sont couvertes ; un pourcentage ne remplace pas la qualité des assertions.
- `./mvnw verify` reste vert et le journal contient les commandes réellement exécutées.

## Contraintes Java 21

Les records conviennent aux valeurs et commandes immuables ; ils ne sont pas imposés à tous les objets. Une hiérarchie `sealed` peut rendre les états explicites. Le `switch` porte sur un seul sélecteur : la notation `(status, command)` des références n'est pas directement une syntaxe de sélection Java 21. Choisir une représentation valide sans fonctionnalités preview.

## À expliquer sans notes en fin de phase

Prendre une transition autorisée et une transition refusée, expliquer la règle protégée, puis montrer le test qui prouve chacune. Expliquer pourquoi ni une base de données ni Spring ne sont nécessaires pour vérifier cette règle.
