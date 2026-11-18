# Progression par phases

La progression dépend des comportements démontrés, pas des dates du programme d'origine. **S0–S4 sont implémentés et validés localement.** Les résultats de vérification figurent dans le journal : 101 tests backend réussis pour S1–S4. S5 et les phases suivantes attendent une nouvelle demande.

Les références de préparation et leurs limites sont dans [preparation/](preparation/reading-notes.md). Les décisions locales font autorité pour ce kata ; elles ne prétendent pas définir le produit EDF.

| Phase | Résultat recherché | Frontière de sortie |
|---|---|---|
| [S0 — Socle](phases/S0-foundation.md) | Réacteur Maven, bootstrap non-web, shell Angular, PostgreSQL local et vérification préparée | Builds et tests du socle vérifiés ; aucun métier |
| [S1 — Domaine](phases/S1-domain.md) | Premier modèle documentaire Java pur, construit en TDD | Invariants et transitions testés sans framework |
| [S2 — Application](phases/S2-application.md) | Cas d'usage, ports nécessaires et fakes ; règles ArchUnit complètes | Orchestration testée sans Spring ; violations d'architecture effectivement détectées |
| [S3 — Persistance](phases/S3-persistence.md) | PostgreSQL, mapping JPA hors du domaine, migrations Liquibase et tests d'intégration | Sauvegarde / relecture et concurrence vérifiées ; stratégie de migration documentée |
| [S4 — REST](phases/S4-rest.md) | Contrat code-first, DTO, validation, erreurs, pagination et observabilité initiale | Contrat, erreurs et parcours HTTP testés contre PostgreSQL |
| S5 — Shell Angular intégré | Navigation, accès HTTP et états d'affichage | Composant et client HTTP testés ; données réelles consommées |
| S6 — Grille | AG Grid et exercice de tri / filtre / pagination serveur | Contrat de requête et chargement par blocs démontrés ; licence et choix du row model explicités |
| S7 — Contribution | Formulaire réactif, Tailwind 3 et actions selon le statut | Validations, erreurs et accessibilité du parcours vérifiées |
| S8 — Événements, optionnel | Outbox, Kafka, contexte review et déduplication | Rejeu sans effet dupliqué ; responsabilités et contrats versionnés |
| S9 — Parcours e2e, optionnel | Cypress sur parcours de contribution et décision | Parcours métier indépendants, répétables et lisibles |
| S10 — Qualité, optionnel | SonarQube, couverture et contrôles de dépendances / sécurité | Résultats observés et limites des outils documentés |
| S11 — Livraison, optionnel | Packaging Docker et pipeline GitLab complet | Pipeline réellement exécutée ; aucun déploiement implicite |
| S12 — Infrastructure, optionnel | Terraform et observabilité cloud en laboratoire autorisé | Plan et métriques vérifiés dans un environnement explicitement choisi |

## Séquence

Suivre **S0 → S1 → S2 → S3 → S4 → S5 → S6 → S7** pour construire une tranche cohérente. Les phases S8 et suivantes enrichissent ensuite cette tranche. Elles ne sont ni commencées ni requises pour valider S0.

S9 dépend des parcours fonctionnels disponibles ; S8 ajoute une frontière distribuée qui devra être clarifiée avant implémentation. Un incrément ne devient donc pas indépendant du simple fait qu'il porte un numéro.

Le shell créé en S0 ne réalise pas S5 : S5 ajoute les comportements et l'intégration. De même, les modules adapters vides n'implémentent pas S3, S4 ou S8.

## Règle d'avancement

Pour chaque phase : lire le brief, convenir d'un comportement observable, écrire le test pertinent, implémenter le minimum, vérifier et expliquer le résultat dans le journal. Réduire une phase trop grande avant de coder plutôt que préremplir les suivantes.
