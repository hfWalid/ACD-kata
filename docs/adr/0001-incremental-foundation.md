# ADR 0001 — Un socle incrémental à frontières de compilation explicites

**Statut : accepté pour le scaffold S0.**

## Contexte

Le kata doit préparer une contribution senior Java / Angular tout en laissant au développeur la pratique des comportements et des tests. La cible pédagogique distingue domaine, application, adapters et composition ; deux contextes sont envisagés à terme. Le scaffold initial ne doit pas préimplémenter ces exercices.

Les sources alternent un dessin par packages et une demande de modules Maven séparés. Il faut choisir une structure initiale lisible et une vérification proportionnée.

## Alternatives

1. **Un module Maven, frontières par packages.** Moins de configuration, navigation simple ; la pureté du cœur repose surtout sur les règles d'architecture et la discipline d'import.
2. **Six modules Maven sous `contribution-service`.** Davantage de POM et de configuration de réacteur ; les dépendances sont visibles à la compilation et le domaine peut rester sans dépendance externe de production.

## Décision

Retenir six modules : `domain`, `application`, `adapters-rest`, `adapters-persistence`, `adapters-messaging`, `boot`. Cette structure suit le programme détaillé et rend la direction des dépendances observable dans un exercice pédagogique.

- `domain` contient uniquement du Java pur et bénéficie d'un garde-fou Maven sur ses dépendances de production.
- `application` dépend de `domain` ; il accueillera les cas d'usage et les ports nécessaires.
- Chaque adapter dépend du cœur nécessaire à sa frontière.
- `boot` compose l'application et pourra dépendre des adapters.

En S0, ces frontières sont déclarées mais leur contenu reste vide. Le bootstrap Spring utilise uniquement le starter de base, sans serveur HTTP. Un test de contexte et une première règle ArchUnit exercent l'entrée de l'application. Les règles complètes et leurs preuves de violation sont un livrable S2.

`review-service` ne devient pas un service exécutable en S0. Les contrats, Kafka, la persistance applicative et l'infrastructure supplémentaire apparaîtront avec la phase qui leur donne une responsabilité réelle.

## Conséquences

Le domaine ne peut pas importer un framework absent de son classpath ; le garde-fou de dépendances évite d'en ajouter un pour contourner cette séparation. Les dépendances de test nécessaires restent possibles.

Le coût accepté est la répétition de quelques métadonnées Maven et une commande de réacteur plutôt qu'un build de fichier isolé. Aucune couche générique ou classe future n'est créée pour justifier les modules.

Cette décision ne prétend pas démontrer que l'ACD réel utilise ces frontières ni justifier deux microservices en production. La séparation distribuée sera réévaluée en S8 avec ses transactions, contrats, effets dupliqués et responsabilités de décision.

## Vérification

Utiliser `./mvnw verify` et le build ciblé `./mvnw -pl contribution-service/boot -am verify`. Consigner les résultats observés dans la fiche S0. La présence de ces commandes dans l'ADR ne signifie pas qu'elles ont déjà réussi.
