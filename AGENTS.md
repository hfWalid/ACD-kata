# Règles de travail — ACD Kata

## Progression incrémentale

1. Lire `1- README.md`, `docs/roadmap.md`, la fiche de phase concernée et les ADR avant toute modification.
2. Implémenter **uniquement les incréments explicitement demandés**. Après S0, la tranche S1–S4 a été autorisée. Ne pas commencer S5 ou les phases suivantes sans nouvelle demande.
3. Lire le code et les tests présents avant de proposer une structure. Préserver le travail des autres contributeurs et les changements sans rapport.
4. Pour S1 et les phases fonctionnelles, avancer par petites boucles TDD : comportement précis, test rouge observé, minimum de code, test vert, refactoring.
5. Faire évoluer la documentation avec le code. Ne jamais marquer une phase terminée sur la seule présence de fichiers.

## Frontières

- `domain` : Java pur, règles et langage métier. Aucune dépendance externe de production, annotation Spring/JPA/Jackson ou opération d'I/O.
- `application` : orchestration des cas d'usage et ports nécessaires. Dépend du domaine et du JDK, sans framework.
- `adapters-rest`, `adapters-persistence`, `adapters-messaging`, `adapters-import` : adaptations de leurs frontières respectives. Les détails techniques ne remontent pas vers le cœur.
- `boot` : composition et démarrage. Depuis S4, serveur REST local, PostgreSQL et observabilité ; le bootstrap S0 non-web est historique.
- `web` : Angular strict, standalone et zoneless. Ne pas anticiper la grille ou les formulaires.

Ne créer ni abstraction générique, ni classe vide annonçant une fonctionnalité future. Les déclarations de package S0 rendent seulement les frontières visibles. Ne pas introduire un service distribué, une dépendance ou un outil avant son besoin dans la phase courante.

## Sources et décisions

`docs/preparation/kata-acd.md`, `learning-path.md` et `programme-14j.md` sont des copies des références fournies. Les conserver inchangées ; consigner les corrections dans `docs/preparation/reading-notes.md`. Le calendrier historique n'est pas un relevé d'avancement.

Le domaine du kata est pédagogique. Ne pas affirmer que son workflow, Kafka, son découpage ou ses conventions reflètent les choix réels d'EDF. Les versions, cours et capacités d'outils doivent être vérifiés avant de servir de justification technique.

## Vérification et sécurité

- Utiliser le Maven Wrapper, Node indiqué par `.nvmrc` et le lockfile npm.
- Lancer les vérifications proportionnées à la modification, puis inspecter le diff.
- Rapporter les commandes réellement exécutées, leurs résultats et les limitations. Distinguer compilation, tests, démarrage local et exécution GitLab.
- Ne pas publier de secret. Garder `.env.local` hors du contrôle de version ; `.env.example` contient uniquement des placeholders.
- Ne pas supprimer de données, modifier le dépôt parent, créer de dépôt Git imbriqué, pousser, déployer ou provisionner du cloud sans demande explicite.
- Le scaffold CI reste **préparé** tant qu'une exécution distante n'a pas été observée.
