# Apprendre SonarQube sur ce kata (S10)

Ce document explique l'analyse statique mise en place pour S10 **pour quelqu'un qui n'a jamais
utilisé SonarQube** : les pièces du montage, ce que le scanner lit réellement, comment lire le
tableau de bord, ce que la première analyse a trouvé ici, et ce qu'il faut ignorer volontairement.
Les preuves et décisions sont dans la [fiche S10](phases/S10-quality.md).

## 1. Les trois pièces, et pourquoi il y en a trois

```
   ┌──────────────┐   1. rapports produits par le build    ┌────────────────────┐
   │ build        │──── jacoco.xml, lcov.info, JUnit XML ──►│                    │
   │ mvn verify   │                                        │  SonarQube         │
   │ ng test      │                                        │  (serveur, Docker) │
   └──────────────┘                                        │  analyse + stockage│
                                                           │  règles + seuils   │
   ┌──────────────┐   2. sources + rapports (HTTP)         │                    │
   │ scanner CLI  │───────────────────────────────────────►│                    │
   │ (Docker)     │◄────────── 3. résultats ───────────────│                    │
   └──────────────┘                                        └────────────────────┘
```

- **Le serveur** stocke les résultats, fournit les règles et applique la porte de qualité. Il ne
  compile rien et ne lance aucun test.
- **Le scanner** est un client en ligne de commande. Il lit les sources, les envoie au serveur avec
  les rapports déjà produits, et attend la réponse. C'est un conteneur ici
  (`sonarsource/sonar-scanner-cli:12.4`), pour ne rien installer sur la machine.
- **Les rapports** viennent du build, pas de SonarQube. C'est le point que les débutants ratent :
  SonarQube n'exécute jamais les tests ; sans `jacoco.xml` ni `lcov.info`, il affiche 0 % de
  couverture sur du code testé.

Conséquence pratique de l'ordre : `./mvnw verify`, puis `npm --prefix web run coverage`, puis le
scan. `make quality-scan` suppose que ces deux commandes ont tourné.

## 2. Démarrer l'instance locale

```sh
make quality-up      # docker compose --profile quality up -d sonarqube
make quality-token   # attend le démarrage, crée le jeton dans .env.local
make quality-scan    # lance le scanner et attend la porte de qualité
```

Puis ouvrir <http://127.0.0.1:9100> (le port est `SONAR_PORT` ; ici 9100, parce que 9000 est déjà
pris par un autre projet local). Identifiants : `admin` et le mot de passe de
`SONAR_ADMIN_PASSWORD` dans `.env.local`.

`make quality-token` fait trois appels d'API, dans cet ordre, et c'est tout ce que fait
`scripts/sonar-token.mjs` :

1. `GET /api/system/status` jusqu'à `{"status":"UP"}` — Elasticsearch démarre avant l'API, comptez
   une minute ;
2. `POST /api/users/change_password` — une instance neuve refuse tout le reste avant que le mot de
   passe `admin`/`admin` soit changé ; l'appel s'authentifie avec **l'ancien** mot de passe ;
3. `POST /api/user_tokens/generate` — le jeton qui servira aux scans.

Un jeton, pas un mot de passe : un mot de passe peut tout faire, dont administrer le serveur ; un
jeton ne peut que publier une analyse, et il se révoque sans changer les identifiants. Le jeton n'est
affiché qu'une fois par SonarQube, d'où son écriture dans `.env.local` (ignoré par Git, permissions
0600).

## 3. Ce que le scanner lit : `sonar-project.properties` décodé

```properties
sonar.projectKey=acd-kata          # identité du projet côté serveur
sonar.sources=…/src/main/java,web/src
sonar.tests=…/src/test/java,review-service/src/test/java,web/cypress
```

`sonar.sources` et `sonar.tests` disent **où lire**. Les tests web vivent à côté du code
(`*.spec.ts` dans `src/`), donc les deux propriétés se recouvrent : pour ce cas, SonarQube classe un
fichier comme test quand il correspond à `sonar.test.inclusions`, et `sonar.exclusions` l'écarte des
sources. Sans cette paire, le même fichier serait indexé deux fois — erreur de configuration
classique.

```properties
sonar.java.binaries=…/target/classes
sonar.java.test.binaries=…/target/test-classes
```

Le code Java n'est pas analysé comme du texte : l'analyseur lit les `.class` pour connaître les types
réels (surcharges, génériques, annotations). Des binaires absents ou périmés produisent des faux
positifs silencieux. C'est aussi pourquoi le scan vient **après** `mvn verify`.

```properties
sonar.coverage.jacoco.xmlReportPaths=…/target/site/jacoco/jacoco.xml
sonar.javascript.lcov.reportPaths=web/coverage/acd-web/lcov.info
sonar.junit.reportPaths=…/surefire-reports,…/failsafe-reports
```

Trois formats, trois rôles : JaCoCo et lcov disent **quelles lignes ont été exécutées**, JUnit XML dit
**quels tests ont réussi**. Le front n'importe que sa couverture : SonarQube ne lit les résultats
d'exécution JS/TS qu'au format générique, qui demanderait un convertisseur pour Vitest — un outil de
plus pour une information déjà lisible dans le rapport Vitest.

```properties
sonar.exclusions=**/target/**,**/node_modules/**,**/dist/**,**/coverage/**,…
```

Exclure le code généré et les dépendances est la différence entre une métrique et un chiffre. Une
couverture diluée par `node_modules` ne dit rien de ce dépôt.

## 4. Lire le tableau de bord

Vocabulaire minimal :

| Mesure | Sens | Piège |
| --- | --- | --- |
| **Couverture lignes / branches** | part du code exécuté par les tests | 100 % ne veut pas dire « testé correctement » ; ce n'est qu'une trace d'exécution |
| **Duplication** | lignes copiées-collées | 0 % est atteignable, mais un peu de duplication vaut parfois mieux qu'une abstraction prématurée |
| **Complexité cognitive** | effort pour comprendre une méthode | le vrai signal de maintenance, plus utile que le nombre de lignes |
| **Bugs / Vulnérabilités** | règles de fiabilité et de sécurité | une « vulnérabilité » SonarQube est un motif de code, pas une faille prouvée |
| **Security Hotspots** | code sensible à **relire** | un hotspot n'est pas une faute : c'est une question posée |
| **Code Smells** | dette de maintenabilité | à trier, pas à traiter en masse |

Résultat de l'analyse de ce dépôt (mesuré, pas estimé) :

```
ncloc 4552 · couverture 87,2 % (lignes 89,2 %, branches 82,1 %) · duplication 0,0 %
bugs 0 · vulnérabilités 0 · hotspots 0 · code smells 0 · 195 tests importés, 0 échec
```

## 5. « Nouveau code » et porte de qualité

SonarQube ne juge pas un projet sur son passé : la **porte de qualité** (« quality gate ») s'applique
au **nouveau code** défini par une période (ici la valeur par défaut, « previous version » / first
analysis). Règle « Sonar way » observée sur ce projet :

| Condition | Seuil | Valeur observée | État |
| --- | --- | --- | --- |
| `new_coverage` | ≥ 80 % | 100,0 % | OK |
| `new_duplicated_lines_density` | ≤ 3 % | 0,0 % | OK |
| `new_violations` | = 0 | 0 | OK |

La première analyse rend tout « nouveau », d'où 100 % : le chiffre ne veut pas dire que le dépôt est
parfait, seulement que la porte est passée. C'est le principe **Clean as You Code** : on empêche la
dette d'augmenter, on ne l'éponge pas d'un coup.

Le scan local affiche le résultat mais ne bloque rien. Le job CI utilise
`sonar.qualitygate.wait=true`, qui fait **échouer le job** si la porte est rouge : sans ce drapeau,
un scan est un rapport consultable que personne ne consulte.

## 6. Ce que la première analyse a trouvé — et ce qu'on en a fait

**Un vrai défaut, corrigé.** Règle `java:S1845`, sévérité *Blocker* :

```
DocumentLifecycleEvent.java:31 — Rename field "TYPE" to prevent any misunderstanding/clash with field "type".
```

Le code déclarait `public static final String TYPE` (le nom du contrat, `document.lifecycle.changed`)
à côté du champ `type` (la transition métier, `CREATED`, `SUBMITTED`…). Deux noms qui ne diffèrent que
par la casse, dans la même classe : un lecteur s'y trompe, et le compilateur ne peut pas l'aider. Le
constat est passé à `EVENT_TYPE`, avec un commentaire qui explique pourquoi. Aucun `@SuppressWarnings`,
aucune exclusion : une règle qui a raison se corrige.

**Vingt-deux avis de sécurité sur les dépendances npm, dont six graves.** Tous dans la chaîne de
build (`@angular/cli`, `@angular/build`, `postcss`, `vite`, `undici`, `zod`) — aucun dans le code
livré au navigateur. Traitement :

- mise à jour **de correctif** : `@angular/cli` et `@angular/build` 21.2.26 → 21.2.30, `postcss`
  8.5.6 → 8.5.33 ; les six avis graves disparaissent ;
- restent seize avis **modérés**, tous dans le client de registre npm embarqué par `@angular/cli`
  (`pacote`, `sigstore`, `cacache`…). Le seul correctif est Angular 22, un changement de version
  majeure : non pris pour une faille modérée dans un outil de build. Décision écrite, réévaluée à la
  prochaine montée de version.

**Ce qu'un `npm audit` ne dit pas** : si la faille est atteignable. Ici, aucun de ces paquets n'est
embarqué dans le bundle applicatif. C'est pourquoi `make quality-deps` sert à **lire** les avis, pas
à les compter.

## 7. `make quality-deps` : les contrôles que SonarQube ne fait pas

```sh
./mvnw dependency:analyze    # dépendances déclarées non utilisées / utilisées non déclarées
npm --prefix web audit --audit-level=high
npm --prefix web outdated
```

L'édition *Community* de SonarQube n'inclut pas l'analyse des dépendances (SCA) : c'est une offre
commerciale. Les trois commandes ci-dessus couvrent le besoin sans licence.

Résultat honnête sur ce dépôt : `dependency:analyze` signale des dépendances « utilisées mais non
déclarées » (`spring-jdbc`, `kafka-clients`, `assertj-core`…) dans presque tous les modules. Elles
arrivent par un `spring-boot-starter-*` et sont gérées par le BOM Spring Boot : les déclarer à la
main n'ajouterait aucune version et dupliquerait une décision qui appartient au BOM. C'est une
**convention assumée**, pas un oubli — à l'inverse, une dépendance utilisée qui ne serait pas gérée
par le BOM doit être déclarée, sinon sa version devient implicite au prochain upgrade.

## 8. Les avertissements du scan, expliqués

```
WARN Missing blame information for the following files: web/cypress/e2e/*.cy.ts
```

`git blame` n'a rien à dire sur des fichiers non commités. L'avertissement disparaît après le
premier commit ; il n'indique pas un problème de configuration.

```
WARN Missing 'sonar.java.libraries' property. You might end up with less precise analysis results.
```

L'analyseur sait où sont les classes du projet, pas les jars des bibliothèques. Quelques règles qui
raisonnent sur les types de bibliothèques (par exemple « cette méthode de `String` est dépréciée »)
deviennent alors moins précises. Deux façons de le corriger : monter `~/.m2` dans le conteneur et
passer `-Dsonar.java.libraries=…/**/*.jar` (analyse plus lente, car l'analyseur indexe des milliers de
jars), ou passer au plugin Maven `sonar-maven-plugin`, qui construit le classpath lui-même — au prix
d'une configuration différente pour la partie web. Choix retenu : garder le scanner CLI unique et le
documenter, le gain de précision ne valant pas la complexité pour ce kata.

## 9. Limites de ce montage

- Instance locale unique, base de données embarquée : parfait pour apprendre, inutilisable en équipe.
- Pas d'analyse de branches ni de décoration de *merge request* : il faudrait un serveur partagé et
  un jeton CI.
- Le job CI est **préparé, jamais exécuté** : il ne s'active que si `SONAR_HOST_URL` et `SONAR_TOKEN`
  existent. Voir [S10](phases/S10-quality.md).
- La couverture est importée, jamais mesurée par SonarQube : une erreur de chemin dans
  `sonar-project.properties` se traduit par 0 %, pas par un échec.
- Aucune règle personnalisée, aucun profil de qualité modifié : on utilise « Sonar way » par défaut,
  ce qui est le bon point de départ.

## 10. Pour aller plus loin

1. **Lire une issue dans l'interface** : le panneau de droite montre le code fautif, la règle, et
   souvent « why is this an issue ». C'est la meilleure façon d'apprendre les règles.
2. **Suivre la couverture du nouveau code** sur deux ou trois commits : c'est la seule mesure qui
   change les habitudes.
3. **Installer le plugin IDE** (SonarQube for IDE) : mêmes règles, retour immédiat, aucun serveur.
4. **Configurer un `sonar.qualitygate.wait` en local** pour voir un job échouer volontairement, puis
   le réparer.

Retour : [parcours Cypress](cypress-walkthrough.md) · [fiche S10](phases/S10-quality.md) ·
[S9](phases/S9-e2e.md).
