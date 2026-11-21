# E1 — Import de fichiers CSV et JSON

**Résultat recherché.** Charger un fichier, le mapper vers la structure documentaire, et amener
chaque document jusqu'au brouillon, à la soumission ou à l'approbation, **sans ajouter de workflow**.

**Frontière de sortie.** Un fichier de huit documents approuvés produit huit documents avec quatre
entrées d'audit chacun, huit tâches de revue ouvertes puis décidées, et un rejeu du même fichier ne
crée rien.

Extension, pas phase : elle ne renumérote ni S9 ni les suivantes. Décisions dans
[ADR 0006](../adr/0006-import-as-a-command-script.md), schémas dans
[le design](../../design/9-%20import.md).

## Ce qui est construit

- **Une porte d'entrée, pas une capacité.** `DocumentImport` reçoit des enregistrements déjà mappés
  et exécute les commandes existantes. `APPROVED` = `create`, `submit`, `startReview`, `approve`.
  Aucune arête ajoutée à `DocumentTransition`.
- **Le mapping est une donnée.** `contracts/import/<source>/` contient la spécification Jolt,
  l'échantillon et la sortie attendue ; le test rejoue l'échantillon et compare champ par champ.
  La spécification est lue à chaque import depuis `acd.import.specs-directory`.
- **CSV normalisé en JSON.** Jolt ne lit que du JSON. Une ligne par section, regroupement par
  référence avant le mapping, parce que Jolt ne regroupe pas.
- **Le mapping ne juge pas.** Les règles restent dans le domaine ; ses codes remontent tels quels
  dans le rapport. L'adaptateur n'a que des refus structurels.
- **Idempotence par `(source, référence)`.** Table `document_sources`, contrainte unique, écriture
  dans la transaction du document. Rejeu → `ALREADY_IMPORTED` ; deux imports concurrents → la
  contrainte arbitre. Une ligne sans référence est refusée.
- **Un enregistrement, une transaction.** Création, référence et transitions ensemble : un refus à
  la troisième étape ne laisse pas de brouillon orphelin. Le lot, lui, n'est pas atomique.
- **Rapport par ligne** : `line`, `reference`, `status`, `documentId`, `version`, `documentStatus`,
  `code`, `detail`, plus les totaux. Fichier JSON en ligne de commande, corps JSON en HTTP.
- **Mode simulation.** Le workflow est rejoué dans l'agrégat, qui est pur : validation et statut
  final calculés, aucune écriture.
- **Deux transports, un cas d'usage.** `make import-documents` (jar, `web-application-type=none`,
  attend le drain de l'outbox, code de sortie 0/2) et `POST /api/imports` (multipart, `X-User`,
  `X-Approver` optionnel).

## Fichiers

| Où | Quoi |
|---|---|
| `application/.../importing/` | `SourceRef`, `MappedRecord`, `ImportBatch`, `ImportOptions`, `ImportReport`, `RowOutcome` |
| `application/.../service/DocumentImportService` | script des commandes, idempotence, simulation, rapport |
| `application/.../port/` | `DocumentImport` (entrant), `ImportSource` et `DocumentSourceIndex` (sortants) |
| `adapters-import/` (nouveau module) | `CsvRows`, `RecordGrouper`, `JoltMapping`, `JoltImportSource`, `ImportReportJson` |
| `adapters-persistence/` | `JdbcDocumentSourceIndex`, changeset `004-add-document-sources` |
| `adapters-rest/.../importing/` | `ImportController`, `ImportReportResponse`, `ImportRowResponse` |
| `boot/` | `ImportProperties`, `ImportConfiguration`, `ImportRunner` |
| `contracts/import/annuaire/` | `mapping.jolt.json`, `sample.csv`, `expected.json` |
| `scripts/import/annuaire-2026.csv` | jeu de démonstration, huit documents |

## Vérification

```sh
make verify                                        # tout le réacteur, 228 tests backend
./mvnw -pl contribution-service/boot verify -Dit.test=DocumentImportIT
make import-documents IMPORT_DRY_RUN=true          # simulation, aucune écriture
make import-documents IMPORT_MODE=approved         # import réel
make review-run                                    # les tâches de revue correspondantes
```

| Niveau | Test | Ce qu'il prouve |
|---|---|---|
| Mapping | `JoltImportSourceTest` (15) | échantillon → `expected.json`, CSV et JSON, BOM, référence absente, gabarit non mappé, source inconnue, spec illisible, format inconnu, limite de lignes |
| Application | `DocumentImportServiceTest` (12) | une commande par mode, ordre create → submit → startReview → approve, approbateur déclaré, doublon, refus du domaine, conflit, doublon concurrent, simulation, ordre du rapport |
| Valeurs | `SourceRefTest`, `MappedRecordTest`, `ImportTypesTest`, `ImportExceptionsTest` | invariants et rapports |
| Persistance | `JdbcDocumentSourceIndexIT` (4) | référence stockée et retrouvée, doublon refusé, même référence dans une autre source, cascade |
| HTTP | `ImportControllerTest` (5) | rapport, approbateur par défaut, mode inconnu, source inconnue, auteur obligatoire |
| Bout en bout | `DocumentImportIT` (4) | huit documents approuvés en quatre étapes, rejeu sans doublon, refus isolé, simulation sans écriture, `UNKNOWN_SOURCE` en `ProblemDetail` |
| Ordre | `claimsTheEventsOfOneDocumentInVersionOrderEvenWhenTheyShareATimestamp` | les versions d'un document partent dans l'ordre |

Parcours local exécuté : `make import-documents IMPORT_DRY_RUN=true` → `0 importé, 8 planifiés`, base
inchangée ; `make import-documents IMPORT_MODE=approved` → `8 importés`, puis en base huit
documents `Approved`, quatre entrées d'audit chacun, et après démarrage du consommateur **huit
tâches `APPROVED`** avec `decidedBy = relecteur`. Rejeu immédiat → `0 importé, 8 déjà présents`.

## Défaut trouvé en exécutant

Le premier import réel a produit huit documents approuvés mais **deux tâches de revue**. Cause :
`ORDER BY` dans un `UPDATE … RETURNING` ne trie pas le résultat, donc les quatre événements d'un
document partaient dans un ordre arbitraire (`3,4,1,2`) et le consommateur ignorait la soumission
par son garde de version. Correction : réclamation dans une CTE, tri de sortie par
`occurred_at, business_version, id`. Le test de non-régression échoue avec l'ancienne requête, ce qui
a été vérifié en la restaurant temporairement.

## Limites

- Import synchrone : le débit est celui d'une commande par étape. Au-delà de quelques milliers de
  lignes, il faut une file (job + worker) et une reprise après incident ; ce serait E2, pas E1.
- Une ligne = un document = une transaction : pas de rollback de fichier. Le rapport dit ce qui est
  passé, y compris en cas d'arrêt au milieu.
- Une seule forme de CSV (séparateur `;`, en-tête, une ligne par section) et une spécification par
  source. Un export sans référence est refusé : la rejouabilité est exigée, pas optionnelle.
- Pas d'envoi de fichier depuis l'interface Angular, pas d'Excel, pas de spécification téléversée.
- La spécification est un fichier du dépôt lu au moment de l'import : en production, il faudrait la
  livrer avec le déploiement (config map) plutôt que dépendre du répertoire courant.
- La limite de lignes (`acd.import.max-rows`, 10 000 par défaut) protège la mémoire, pas la durée.
