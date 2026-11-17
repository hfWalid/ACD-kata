# Journal d'apprentissage

Ce journal décrit le travail réellement réalisé. Le programme daté d'origine reste une référence de préparation ; il ne fournit aucun statut d'avancement.

## Point de départ — S0

- Objectif : disposer d'un socle Java / Angular reproductible avant le premier exercice métier.
- Périmètre : modules et bootstrap, shell web, PostgreSQL local et garde-fous initiaux.
- Aucun comportement documentaire ni intégration base / API n'est attendu en S0.
- Scores de diagnostic Angular / Java / Spring : **non renseignés**.
- Pipeline GitLab : **préparée, exécution distante non réalisée**.
- Vérifications locales : **S0 validé**, voir les preuves ci-dessous et `docs/phases/S0-foundation.md`.

## Vérification S0 — 17 novembre 2026

Résultats observés pendant la préparation du socle :

- Maven : phase `verify` réussie pour les huit projets du réacteur, avec deux tests réussis.
- Bootstrap : le jar démarre sans serveur HTTP et termine avec un code de sortie 0.
- Pureté du domaine : l'ajout d'une dépendance Spring de compilation est rejeté dans une copie temporaire ; le code livré n'a pas été modifié pour ce test négatif.
- Angular sous Node 24.19.0 : installation `npm ci`, test Vitest, build et contrôle de format réussis ; un test exécuté.
- Compose : configuration validée. PostgreSQL 16.14 démarre sain et une requête de contrôle réussit dans un projet de vérification isolé ; ses ressources temporaires sont nettoyées.
- GitLab : fichier préparé, aucune exécution distante revendiquée.

Le build Angular a nécessité une autorisation d'exécution adaptée au sandbox macOS ; aucune modification du code n'a été utilisée pour contourner cette contrainte. Ces résultats ne constituent pas une validation des fonctionnalités des phases suivantes.

## S1–S4 — 17 novembre 2026

Tranche explicitement demandée et réalisée dans l'ordre domaine → application → persistance → REST. S5 et les incréments suivants restent à faire.

### Décisions

- Brouillon initial en version 1 ; une action réussie ajoute une version et une entrée d'audit. Un rejet est corrigeable et resoumissible.
- Agrégat immuable, temps explicite et horloge applicative à la microseconde ; détails JPA séparés.
- Version attendue exigée, comparaison applicative puis protection atomique dans le dépôt.
- Contrat REST code-first nommé comme tel, export OpenAPI vérifié ; `X-User` reste un auteur pédagogique local.
- Publication externe et `EventPublisher` différés à S8 ; aucun adapter silencieux ajouté.

### Vérification incrémentale

| Phase | Rouge observé | Vert observé |
|---|---|---|
| S1 | Premier test de domaine avant présence des types | 18 tests purs, machine à états exhaustive |
| S2 | Tests de cas d'usage avant leurs implémentations | 13 tests applicatifs avec faux dépôts ; 7 contrôles d'architecture positifs/négatifs dans le réacteur |
| S3 | Tests du dépôt avant ses entités et son adapter | 12 tests d'intégration PostgreSQL, dont rollback/réapplication et régressions de concurrence |
| S4 | Tests MVC avant contrôleur/advice | 44 tests MVC et 6 tests HTTP complets contre PostgreSQL |

La revue a identifié deux courses entre la lecture de l'entité racine et le chargement différé des collections : agrégat composé de versions différentes et conflit d'écriture mal classé. Les tests déterministes ont d'abord reproduit les défauts. Les lectures utilisent un snapshot cohérent ; l'écriture effectue son contrôle optimiste racine avant les collections, en conservant une seule transaction.

Le premier passage du réacteur complet avait 99 succès sur 100 : Prometheus était désactivé par les valeurs par défaut de `@SpringBootTest`. Le test HTTP active désormais explicitement l'observabilité ; le paramétrage runtime n'a pas été modifié pour contourner ce défaut de fixture.

### Résultat final

Vérification normale du réacteur avec le Maven Wrapper, Java 21.0.12, Maven 3.9.16 et Docker Desktop :

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

L'exécution a utilisé un dépôt Maven local dédié au workspace via `-Dmaven.repo.local` et un `MAVEN_USER_HOME` dédié ; ces paramètres changent le cache, pas la sélection des tests.

**100 tests réussis, 0 échec, 0 erreur, 0 ignoré.** Répartition : domaine 18, application 13, MVC 44, PostgreSQL 12, architecture 7, HTTP 6. Le contrat OpenAPI existant a été comparé sans option de régénération. Le contrôle Prometheus et les contrôles de couverture ont réussi. Couverture JaCoCo : domaine 88/88 branches et 146/146 lignes ; application 22/22 branches et 84/84 lignes.

La syntaxe YAML de la CI et la configuration Compose ont aussi été validées localement. Aucune pipeline GitLab distante, aucun déploiement et aucune modification du dépôt parent ne sont revendiqués. Les migrations de test ont été exécutées uniquement dans les bases jetables Testcontainers.

Prochaine étape : S5, intégration HTTP du shell Angular avec chargement, erreurs et états vides.

## Démarrage local — correction en cours, 17 novembre 2026

Le README proposait une copie inconditionnelle de `.env.example` sur `.env.local`. Cette opération remplaçait la configuration alors que le volume PostgreSQL conservait son rôle et son mot de passe ; le serveur pouvait donc être disponible tout en refusant la connexion du backend.

La procédure distingue désormais création et conservation de la configuration : `make local-env` ne crée qu'un fichier absent, puis `make infra-up` vérifie une connexion réellement authentifiée. Le lancement Spring et Compose utilisent le même fichier. Une connexion locale en mode `trust` et `pg_isready` ne suffisent pas à vérifier le mot de passe.

**Vérification finale du correctif et du redémarrage : réussie.** Voir le relevé final ci-dessous ; le volume et ses données ont été conservés.

## Modèle d'entrée

```text
Date :
Phase / comportement travaillé :
Ce que je savais restituer avant de coder :
Test rouge observé / résultat attendu :
Changement minimal :
Commandes exécutées et résultats :
Ce que je sais expliquer sans notes :
Question ou difficulté restante :
Prochaine petite étape :
```

Pour S0, remplacer la rubrique « test rouge » par l'observation du socle avant / après. Ne pas inventer un cycle TDD rétroactif.

## Vérification finale du redémarrage local — 17 novembre 2026

- Le mot de passe du rôle local et `.env.local` ont été réalignés avec une nouvelle valeur aléatoire, sans affichage du secret. Les comptes de lignes des documents, sections et audit ont été comparés avant/après et conservés. Aucun volume ni document utilisateur n'a été supprimé.
- `make local-env-test` : trois tests réussis, création privée et conservation exacte des fichiers existants vérifiées.
- Projet Compose jetable : bon mot de passe → sain ; changement du mot de passe de configuration avec le même volume → échec de readiness ; restauration de la configuration initiale → sain, ligne témoin conservée. Seules les ressources de cette vérification ont été nettoyées.
- Le parcours du README a été exécuté : `make local-env` conserve la configuration réparée ; `make backend-run` valide PostgreSQL, construit le jar et démarre le serveur sur 8081.
- API locale : santé et Swagger UI accessibles, puis création → soumission → revue → approbation → publication → archivage vérifiés sur un document de contrôle, versions 1 à 6 et audit durable.
- La syntaxe YAML de la CI et de Compose a été vérifiée ; aucune nouvelle exécution distante GitLab n'est revendiquée. Le code métier n'a pas changé pendant cette correction.
