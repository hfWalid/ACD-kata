# S2 — Cas d'usage et ports

Les commandes et les requêtes passent par `DocumentCommands` et `DocumentQueries`. Leurs services applicatifs orchestrent un dépôt, l'horloge du JDK et la génération d'identifiants, sans importer Spring.

Les commandes créent et corrigent les documents puis soumettent, commencent une revue, approuvent, rejettent, publient ou archivent. Une mutation exige une version attendue positive. Un document absent et une version périmée produisent des exceptions applicatives distinctes.

Les tests utilisent des faux dépôts écrits à la main, une horloge fixe et des identifiants déterministes.

Le modèle qui franchit la frontière applicative est `DocumentDetail` : les requêtes et les huit commandes le renvoient, l'agrégat reste interne. La projection est décidée en [ADR 0003](../../adr/0003-application-read-model-and-atomic-ui.md).

Les règles ArchUnit contrôlent la pureté du domaine, les dépendances de l'application, l'indépendance des adapters, l'emplacement des implémentations de ports, la lecture des seuls ports par les adapters, l'absence de l'agrégat dans le transport REST et la séparation d'avec la racine de composition. Chaque règle possède une fixture négative qui prouve qu'elle mord.

`EventPublisher` est différé à S8 : cette tranche ne possède aucun destinataire externe. L'audit est conservé dans l'agrégat et sera durable en S3.

Vérification ciblée :

```sh
./mvnw -pl contribution-service/application -am verify
```

Les contrôles d'architecture globaux résident dans `boot` et sont inclus dans la vérification du réacteur.
