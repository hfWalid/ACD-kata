# ADR 0006 — Importer un fichier sans créer un second workflow

## Contexte

Le kata doit accepter un fichier CSV ou JSON, le transformer vers la structure documentaire, puis
amener chaque document jusqu'à `Submitted` ou `Approved`. La tentation est d'écrire une méthode
`Document.importApproved()` qui saute les états intermédiaires : une ligne de code, et un second
workflow à maintenir, un audit sans revue, une garantie (« aucun document approuvé sans revue »)
qui devient fausse.

## Décision

**1. L'import est une porte d'entrée, pas une capacité métier.** Il produit les huit commandes
existantes. Un import approuvé, c'est `create` → `submit` → `startReview` → `approve` : quatre
commandes réelles, donc quatre entrées d'audit, quatre versions, quatre événements et une tâche de
revue ouverte puis fermée. Aucune arête n'est ajoutée à `DocumentTransition`.

**2. Le mapping est une donnée versionnée.** Chaque source d'import possède un dossier
`contracts/import/<source>/` avec sa spécification Jolt, un échantillon et la sortie attendue,
comparés par un test. Changer un mapping, c'est changer un fichier relu, pas du Java. Jolt ne lit
que du JSON : le CSV est d'abord normalisé, et comme Jolt ne regroupe pas de lignes, le
regroupement par référence se fait avant lui, en Java, dans l'adaptateur.

**3. Le mapping ne juge pas.** Un titre vide, un document sans section, une transition interdite
sont des refus du domaine, et son code (`INVALID_DOCUMENT`, `EMPTY_DOCUMENT`, …) remonte tel quel
dans le rapport. L'import n'a pas son propre vocabulaire d'erreur métier ; il n'a que des codes
structurels (`UNKNOWN_SOURCE`, `MISSING_SOURCE_REF`, `MAPPING_FAILED`, `UNSUPPORTED_FORMAT`,
`ROW_LIMIT_EXCEEDED`).

**4. L'idempotence est une paire (source, référence) stockée avec le document.** La table
`document_sources` porte une contrainte unique, écrite dans la transaction qui crée le document :
un fichier rejoué rapporte `ALREADY_IMPORTED` avec l'identifiant existant, et deux imports
concurrents se départagent sur la contrainte, pas sur une lecture préalable. Une ligne sans
référence est refusée : mieux vaut un refus visible qu'un doublon silencieux.

**5. Un enregistrement = une unité de travail.** Création, référence source et transitions
partagent une seule transaction : un refus à la troisième étape ne laisse pas un brouillon
orphelin. Le lot, lui, n'est pas atomique — un refus ligne 7 ne défait pas les lignes 1 à 6, et le
rapport dit exactement ce qui est passé. Le mode simulation (`dry-run`) rejoue le workflow dans
l'agrégat, qui est pur : il valide et planifie sans écrire.

**6. Le relais publie dans l'ordre des versions.** `UPDATE … RETURNING` ne conserve pas l'ordre de
sa sous-requête. Le lot réclamé est donc réordonné en sortie de requête, par horodatage puis par
version métier. Sans cela, les quatre événements d'un document importé partaient `3,4,1,2` et le
consommateur ignorait la soumission : huit documents approuvés, deux tâches de revue. Trouvé en
exécutant l'import, pas en relisant le code.

## Conséquences

- Un import de huit documents approuvés produit trente-deux événements et huit tâches de revue :
  c'est le prix de l'honnêteté du workflow, et cela se voit dans les métriques.
- Le débit est celui d'une commande par étape : quelques millisecondes par document et par étape.
  Au-delà de quelques milliers de lignes, la réponse est une file (job + worker), pas un import
  synchrone plus rapide.
- Une nouvelle frontière d'adaptateur (`adapters-import`) apparaît ; elle ne dépend que des ports,
  et ArchUnit le vérifie.
- Le rapport est un fichier JSON en ligne de commande, un corps JSON en HTTP : même structure,
  deux transports.
- Voir [la fiche E1](../phases/E1-import.md) pour les preuves et [le design](../../design/9-%20import.md)
  pour les schémas.
