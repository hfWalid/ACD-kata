# S10 — Qualité : SonarQube, couverture et dépendances

**Statut : implémenté et vérifié localement (serveur SonarQube réel, analyse réelle) le 21 novembre 2026.**
Le parcours d'apprentissage est dans [docs/sonarqube-walkthrough.md](../sonarqube-walkthrough.md).

## Ce qui est construit

| Élément | Rôle |
| --- | --- |
| `docker-compose.yml` service `sonarqube` (profil `quality`) | serveur d'analyse 26.11.0.134175-community, port `SONAR_PORT`, volumes de données et de journaux |
| `sonar-project.properties` | sources, tests, binaires Java, rapports de couverture et d'exécution, exclusions |
| `scripts/sonar-token.mjs` + `make quality-token` | attente du démarrage, changement du mot de passe par défaut, création du jeton dans `.env.local` |
| `make quality-scan` | scanner CLI 12.4 en conteneur, `-Dsonar.qualitygate.wait` côté CI |
| `make quality-deps` | `mvn dependency:analyze`, `npm audit --audit-level=high`, `npm outdated` |
| `web/package.json` script `coverage` | Vitest + `lcov` pour que SonarQube lise une couverture front |
| job CI `sonar-analysis` | conditionnel (`SONAR_HOST_URL` et `SONAR_TOKEN`), dépend des artefacts backend et web |
| `contribution-service/*`, `review-service` | rapports JaCoCo XML et JUnit XML déjà produits par `mvn verify`, désormais importés |

Mesures relevées après analyse du dépôt entier :

```
ncloc 4552 · couverture 87,2 % (lignes 89,2 %, branches 82,1 %) · duplication 0,0 %
bugs 0 · vulnérabilités 0 · hotspots 0 · code smells 0 · 195 tests importés, 0 échec
porte « Sonar way » : new_coverage 100,0 % ≥ 80 · duplication 0,0 % ≤ 3 · nouvelles violations 0 → OK
```

## Décisions

- **Serveur en profil Compose.** SonarQube ne démarre qu'avec `make quality-up` : ni le démarrage
  local ni `make verify` n'en dépendent. La base est celle embarquée de l'édition communautaire —
  acceptable pour apprendre, explicitement inutilisable en équipe.
- **Scanner CLI en conteneur plutôt que plugin Maven.** Un seul point d'entrée analyse le Java **et**
  Angular ; le plugin Maven laisserait la partie web de côté. Conséquence assumée : les jars des
  bibliothèques ne sont pas fournis à l'analyseur (`sonar.java.libraries`), ce qui rend quelques règles
  moins précises — voir les avertissements expliqués dans le parcours.
- **La couverture est importée, jamais mesurée.** JaCoCo et Vitest produisent les rapports, le scanner
  les lit. SonarQube n'exécute aucun test : c'est une propriété utile à connaître, et une source de
  « 0 % » très fréquente quand un chemin de rapport est faux.
- **Résultats d'exécution importés pour le backend seulement.** Surefire et Failsafe écrivent déjà du
  JUnit XML ; le front n'importerait ses résultats qu'au format générique, qui demanderait un
  convertisseur pour Vitest. Sa couverture suffit pour cette phase.
- **Un jeton, jamais un mot de passe.** `make quality-token` fait les trois appels d'API qui
  transforment une instance neuve en instance scannable, et écrit le jeton dans `.env.local`
  (ignoré par Git, permissions 0600).
- **Le contrôle corrige, il ne masque pas.** Le seul défaut *Blocker* signalé
  (`java:S1845`, champ `TYPE` à côté de `type` dans `DocumentLifecycleEvent`) a été corrigé en
  `EVENT_TYPE`, avec un commentaire. Aucune règle désactivée, aucun `@SuppressWarnings`.
- **Mise à jour de sécurité prise, changement majeur refusé.** Six avis graves dans la chaîne de build
  ont été supprimés par des mises à jour de correctif (`@angular/cli` et `@angular/build` 21.2.26 →
  21.2.30, `postcss` 8.5.6 → 8.5.33). Seize avis modérés subsistent, tous dans le client de registre
  npm d'`@angular/cli`, corrigeables seulement par Angular 22 : décision écrite plutôt que mise à
  niveau majeure non planifiée.
- **`dependency:analyze` lu, pas obéi.** Le rapport signale des dépendances « utilisées non déclarées »
  (`spring-jdbc`, `kafka-clients`, `assertj-core`…) qui arrivent par les starters et sont versionnées
  par le BOM Spring Boot. Les déclarer n'ajouterait aucune version ; la convention est documentée.
- **CI conditionnelle.** Le job ne s'exécute que si `SONAR_HOST_URL` et `SONAR_TOKEN` existent, pour
  qu'un dépôt sans serveur garde un pipeline vert. `sonar.qualitygate.wait=true` transforme le scan en
  contrôle bloquant, au lieu d'un rapport que personne ne lit.

## Vérification

```sh
./mvnw verify                 # rapports JaCoCo XML + JUnit XML
npm --prefix web run coverage # lcov front
make quality-up
make quality-token
make quality-scan
```

| Observation | Résultat |
| --- | --- |
| Démarrage | conteneur `acd-kata-sonarqube-1` sain, `/api/system/status` → `UP` sur `127.0.0.1:9100` |
| Jeton | créé par l'API après changement du mot de passe par défaut, écrit dans `.env.local` |
| Analyse | `ANALYSIS SUCCESSFUL`, tâche de calcul `SUCCESS`, 2 min 45 s au premier passage |
| Couverture | 87,2 % global, cohérente avec les rapports importés (Jacoco + lcov) |
| Tests importés | 195 (Surefire + Failsafe), densité de succès 100 % |
| Défauts après correction | 0 bug, 0 vulnérabilité, 0 hotspot, 0 code smell, 0 duplication |
| Porte de qualité | `OK` sur les trois conditions « Sonar way » |
| Dépendances | `npm audit --audit-level=high` : 0 avis grave (22 avant mise à jour), 16 modérés documentés |
| Non-régression | 40 tests web, 9 tests de structure, `format:check` propre, build 269,03 kB, 8 parcours Cypress verts |

## Limites assumées

- Le job `sonar-analysis` est **préparé, jamais exécuté en distant** : aucun serveur SonarQube partagé
  n'existe pour ce kata, et aucune revendication d'exécution distante n'est faite.
- Instance locale unique, base embarquée, pas d'analyse de branches ni de décoration de *merge
  request*.
- `sonar.java.libraries` absent : quelques règles dépendant des types de bibliothèques sont moins
  précises. Les deux correctifs possibles sont décrits dans le parcours.
- L'édition communautaire n'inclut pas l'analyse des dépendances (SCA) : `make quality-deps` la
  remplace partiellement, sans base de vulnérabilités dédiée.
- Le front n'importe pas ses résultats d'exécution (seulement sa couverture) : le tableau de bord
  compte les tests backend.
- Les avis `npm audit` ne mesurent pas l'atteignabilité : aucun des paquets concernés n'entre dans le
  bundle livré, ce qui est une raison de ne pas paniquer, pas une preuve d'innocuité.
- Aucun seuil de couverture par module dans SonarQube : la garantie 100 % de `domain` et
  `application` reste celle du build Maven (règle JaCoCo), pas celle du serveur.
