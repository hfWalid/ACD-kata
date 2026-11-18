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

## Revue de la tranche S1–S4 et durcissement — 18 novembre 2026

Revue complète du code, des tests et des documents, puis vérification par ré-exécution du réacteur. Cinq écarts corrigés ; aucun comportement métier modifié.

- **Contrat OpenAPI incomplet.** L'export ne décrivait aucune réponse de succès pour 9 opérations sur 10 : les `@ApiResponses` de classe (400/404/409) empêchaient l'inférence de la réponse déduite du type de retour, et `DocumentListResponse` était absent des schémas. Chaque opération déclare désormais sa réponse `200`/`201` ; l'export a été régénéré et le test HTTP vérifie qu'aucune opération n'est exportée sans réponse de succès et que le schéma de liste expose `rowCount`. Le test précédent ne pouvait pas voir ce défaut : il ne contrôlait que le nombre de chemins et la réponse `201` de création.
- **Portée de la couverture.** La règle JaCoCo ne visait que `DocumentTransition` : les 100 % annoncés n'étaient donc pas protégés. Elle exige maintenant 100 % des branches et des lignes de `domain` et `application`. Couverture observée : domaine 88/88 branches et 146/146 lignes, application 22/22 et 84/84.
- **Affirmation S3 non testée.** « La projection de liste ne charge pas les collections » n'était vérifié que par lecture du code. Un test active les statistiques Hibernate et exige zéro chargement de collection sur `search`, puis vérifie que le même compteur réagit sur `findById` : le contrôle n'est pas vide.
- **S5 : accès HTTP depuis Angular.** Aucune configuration CORS n'est ajoutée : le serveur de développement relaie `/api` vers `127.0.0.1:8081` via `web/proxy.conf.json`. Vérifié en lançant `npm start` : `/` sert l'application, `/api/documents` passe par le proxy et échoue en 500 tant que le backend est arrêté.
- **Dépôt distant.** `master` local et `origin/master` portent S1–S4. `origin/main` est un historique distinct, sans ancêtre commun, qui contient un scaffold et un `.gitlab-ci.yml` généré par GitLab (`include:` SAST et Secret Detection). Avant toute merge request : choisir la branche cible et conserver ces `include:` si le projet distant les utilise, car le pipeline local `verify` les remplace. Aucun push, merge ou réécriture d'historique n'a été effectué.

Vérification : `./mvnw --batch-mode --no-transfer-progress verify` → **101 tests réussis, 0 échec, 0 ignoré** (domaine 18, application 13, MVC 44, PostgreSQL 13, architecture 7, HTTP 6), contrat comparé sans régénération, contrôles de couverture réussis. `npm --prefix web run format:check`, `test:ci` et `build` réussis. La CI GitLab reste **préparée** ; aucune exécution distante n'est revendiquée.

## S5–S7 — interface Angular, grille et formulaire, 18 novembre 2026

Tranche S5–S7 explicitement demandée, réalisée dans l'ordre intégration → grille → formulaire. Le domaine et l'API n'ont pas changé ; aucun test backend n'a été touché.

### Décisions

- **AG Grid Community 36.3.0 en *infinite row model*** plutôt qu'Enterprise/SSRM. Le SSRM suppose une licence ou un *trial* ; le contrat local (fenêtre `[startRow, endRow[`, `rowCount`, tri, filtres) est celui de l'infinite row model et la démonstration attendue — une requête par bloc, aucun chargement global — est obtenue sans clé. Limite consignée dans les notes de lecture et la fiche S6.
- **`HttpClient` et signals explicites** pour le chargement : la liste doit devenir un `IDatasource` en S6 et les tests s'appuient sur `HttpTestingController`. `resource()` n'apportait rien une fois la grille en place.
- **Proxy de développement** (`web/proxy.conf.json`) au lieu de CORS : le navigateur ne voit qu'une origine.
- **Le front n'invente aucune règle métier** : la table des actions ne masque que les boutons illégaux ; un document vide reste soumettable et le `409 EMPTY_DOCUMENT` du serveur est affiché avec son code. Un test épingle la table des actions sur `docs/domain-model.md`.
- **`X-User` fixé à `web-kata`**, auteur pédagogique, comme dans le parcours Swagger ; ce n'est pas une authentification.

### Incréments et observations

| Phase | Premier échec observé | Résultat |
|---|---|---|
| S5 | Types et services absents ; tests écrits avant le client | Client HTTP typé, routes paresseuses, états chargement / erreur / vide ; 8 tests client et navigation |
| S6 | Grille absente ; le datasource est testé avant le composant | Une requête par bloc de 25, tri et filtres serveur ; 6 tests de traduction et de datasource |
| S7 | Formulaires absents ; tests de validation avant le gabarit | Formulaire réactif, actions par statut, audit ; 16 tests de formulaire et de détail |

Deux défauts d'outillage rencontrés et corrigés, sans contournement du comportement :

- `@for` sur un `FormArray` muté en place produisait `NG0100` à l'ajout d'une ligne (reproduit avec un composant minimal hors du projet). Les lignes passent par une liste en signal et un `SectionEditor` lié par `[formGroup]`.
- Le gestionnaire `sortChanged` doublait la requête de bloc : l'infinite row model recharge déjà les blocs au tri. Seuls les filtres de la barre d'outils relancent la requête.

### Vérification exécutée

- `npm --prefix web run test:ci` : **30 tests réussis** (7 fichiers). `format:check` et `build` réussis ; bundle initial 268 kB, AG Grid dans un bloc paresseux de 1,23 MB.
- Parcours navigateur avec PostgreSQL et le backend local : liste sur 10 003 documents (`startRow=0&endRow=25&sort=updatedAt:desc`), tri par en-tête (`sort=version:asc`, une requête), filtre `filter.status=InReview` (1 000 lignes), ouverture d'un document, création via le formulaire, soumission (version 2, action suivante), puis refus `EMPTY_DOCUMENT` affiché pour un brouillon sans section. Aucune erreur console en dehors du 409 attendu.
- Le volume local contient désormais les 10 000 documents de démonstration créés par `make seed-documents` et trois documents créés pendant le parcours. Aucune donnée préexistante n'a été supprimée ; les tests automatisés utilisent toujours des bases Testcontainers jetables.

## Pagination et recherche réactive — 18 novembre 2026

Incrément demandé après la tranche S5–S7 : pagination dans la grille, filtres cohérents avec le contrat et recherche au fil de la frappe. Le domaine, l'API et les tests backend n'ont pas changé ; seuls le composant de liste, son gabarit, ses tests et la documentation ont évolué.

### Décisions

- **Pagination native d'AG Grid alimentée par `rowCount`**, pas un compteur local : `pagination`, `paginationPageSize` 25, `paginationPageSizeSelector` 25/50/100. Le nombre de pages est donc celui du serveur, et non celui des lignes déjà chargées.
- **`cacheBlockSize` maintenu à 25** quelle que soit la taille de page. Une page de 50 lignes demande deux blocs, donc deux requêtes, et seuls les blocs visibles sont chargés : la fenêtre reste celle du contrat, la pagination ne change pas la stratégie de chargement.
- **Rechargement par réaffectation du datasource** (`setGridOption('datasource', …)`) plutôt que `purgeInfiniteCache()` : reconstruire le row model repart de la première page et vide les blocs obsolètes en une seule requête. L'ancienne purge conservait la page courante, ce qui laissait l'utilisateur sur une page inexistante après un filtre restrictif.
- **Découplage saisie / requête** : `searchInput` suit chaque frappe, `search` n'est appliqué qu'après 300 ms d'inactivité, avec annulation du minuteur à l'effacement et à la destruction. Un terme identique après `trim()` ne requête pas.
- **Aucun filtre de colonne AG Grid activé** : le serveur n'exécute que `filter.status` et `q`. Mieux vaut une barre d'outils honnête qu'un filtre client qui ne filtrerait que les blocs déjà chargés.

### Vérification exécutée

- `npm --prefix web run test:ci` : **33 tests réussis** (7 fichiers), dont la temporisation de la recherche, l'absence de requête pour un terme inchangé, la réinitialisation en un seul rechargement et les options de pagination.
- Navigateur, backend et PostgreSQL locaux : page suivante → `startRow=50&endRow=75` (bloc suivant) ; dernière page → `startRow=10000&endRow=10025` pour 10 006 lignes et « 10 001 à 10 006 sur 10 006 » ; taille 50 → 201 pages ; `filter.status=InReview` depuis la page 201 → retour page 1 et `startRow=0`.
- Recherche : frappe de `00042` en cinq touches → **une seule** requête `q=00042`, 248 ms après la dernière touche, aucun appel pour les termes intermédiaires ; « Effacer les filtres » → une requête sans `q` ni `filter.status`.
- Frappe rapide combinée à un changement de taille de page et à un saut de page : **aucun message console**, ni avertissement AG Grid sur les blocs obsolètes, ni `NG0100`.
