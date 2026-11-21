# Progression par phases

La progression dépend des comportements démontrés, pas des dates du programme d'origine. **S0–S10 sont implémentés et validés localement.** Les résultats de vérification figurent dans le journal : 228 tests backend, 40 tests web, 9 tests de structure, 8 parcours Cypress sur la pile complète, un parcours Kafka réel pour S8, un import réel pour E1 et une analyse SonarQube sans défaut ouvert. S11 et les phases suivantes attendent une nouvelle demande.

Les références de préparation et leurs limites sont dans [preparation/](preparation/reading-notes.md). Les décisions locales font autorité pour ce kata ; elles ne prétendent pas définir le produit EDF.

| Phase | Résultat recherché | Frontière de sortie |
|---|---|---|
| [S0 — Socle](phases/S0-foundation.md) | Réacteur Maven, bootstrap non-web, shell Angular, PostgreSQL local et vérification préparée | Builds et tests du socle vérifiés ; aucun métier |
| [S1 — Domaine](phases/S1-domain.md) | Premier modèle documentaire Java pur, construit en TDD | Invariants et transitions testés sans framework |
| [S2 — Application](phases/S2-application.md) | Cas d'usage, ports nécessaires et fakes ; règles ArchUnit complètes | Orchestration testée sans Spring ; violations d'architecture effectivement détectées |
| [S3 — Persistance](phases/S3-persistence.md) | PostgreSQL, mapping JPA hors du domaine, migrations Liquibase et tests d'intégration | Sauvegarde / relecture et concurrence vérifiées ; stratégie de migration documentée |
| [S4 — REST](phases/S4-rest.md) | Contrat code-first, DTO, validation, erreurs, pagination et observabilité initiale | Contrat, erreurs et parcours HTTP testés contre PostgreSQL |
| S5 — Shell Angular intégré | Navigation, accès HTTP et états d'affichage | Composant et client HTTP testés ; données réelles consommées |
| [S6 — Grille](phases/S6-grid.md) | AG Grid et exercice de tri / filtre / pagination serveur | Contrat de requête et chargement par blocs démontrés ; licence et choix du row model explicités |
| [S7 — Contribution](phases/S7-contribution.md) | Formulaire réactif, Tailwind 3 et actions selon le statut | Validations, erreurs et accessibilité du parcours vérifiées |
| [S8 — Événements](phases/S8-events.md) | Outbox transactionnel, Kafka, contexte review et déduplication | Rejeu sans effet dupliqué ; responsabilités et contrats versionnés |
| [E1 — Import](phases/E1-import.md) | Lecture CSV/JSON, mapping Jolt versionné et import en brouillon, soumis ou approuvé par les commandes existantes | Rejeu sans doublon ; workflow traversé et non contourné ; rapport par ligne |
| [S9 — Parcours e2e](phases/S9-e2e.md) | Cypress sur parcours de contribution et décision | Parcours métier indépendants, répétables et lisibles |
| [S10 — Qualité](phases/S10-quality.md) | SonarQube, couverture et contrôles de dépendances / sécurité | Résultats observés et limites des outils documentés |
| S11 — Livraison, optionnel | Packaging Docker et pipeline GitLab complet | Pipeline réellement exécutée ; aucun déploiement implicite |
| S12 — Infrastructure, optionnel | Terraform et observabilité cloud en laboratoire autorisé | Plan et métriques vérifiés dans un environnement explicitement choisi |

## Séquence

L'extension **E1** s'insère avant S9 sans renuméroter les phases : elle ajoute une porte d'entrée
(fichier) aux deux existantes (HTTP, interface), pas un nouvel état du document.

La tranche **S0 → S10** est complète : le domaine, l'application, PostgreSQL, l'API, l'interface web, l'outbox, Kafka, le contexte review, l'import, les parcours navigateur et l'analyse statique sont vérifiés localement. Les phases S11 et suivantes enrichissent cette tranche ; elles ne sont ni commencées ni requises pour la valider.

S8 a clarifié la frontière distribuée avant de l'implémenter (ADR 0004 et 0005) : un seul service décide du statut d'un document, l'autre possède la tâche de revue. S9 dépend des parcours fonctionnels disponibles ; S10 mesure ce que ces parcours et les tests couvrent. Un incrément ne devient pas indépendant du simple fait qu'il porte un numéro.

S9 reste volontairement **hors du pipeline** : un parcours Cypress en CI demande Java, Node et un navigateur dans le même job, plus une pile PostgreSQL/Kafka et des attentes de santé — c'est le sujet du packaging de S11. La couture existe (`make e2e-run`, `npm run e2e:ci`). S10, lui, ajoute un job conditionnel : il ne s'active que si un serveur SonarQube est configuré, et sa porte de qualité est bloquante.

Le shell créé en S0 ne réalisait pas S5 : S5 a ajouté les comportements et l'intégration. De même, le module `adapters-messaging` vide ne réalisait pas S8 : l'outbox, le relais et le contrat d'événement l'ont fait.

## Règle d'avancement

Pour chaque phase : lire le brief, convenir d'un comportement observable, écrire le test pertinent, implémenter le minimum, vérifier et expliquer le résultat dans le journal. Réduire une phase trop grande avant de coder plutôt que préremplir les suivantes.
