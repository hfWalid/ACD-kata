# S2 — Cas d'usage et ports

Les commandes et les requêtes passent par `DocumentCommands` et `DocumentQueries`. Leurs services applicatifs orchestrent un dépôt, l'horloge du JDK et la génération d'identifiants, sans importer Spring.

Les commandes créent et corrigent les documents puis soumettent, commencent une revue, approuvent, rejettent, publient ou archivent. Une mutation exige une version attendue positive. Un document absent et une version périmée produisent des exceptions applicatives distinctes.

Les tests utilisent des faux dépôts écrits à la main, une horloge fixe et des identifiants déterministes. Les règles ArchUnit contrôlent la pureté du domaine, les dépendances de l'application, l'indépendance des adapters et l'emplacement des implémentations de ports. Des fixtures négatives prouvent le rejet des violations.

`EventPublisher` est différé à S8 : cette tranche ne possède aucun destinataire externe. L'audit est conservé dans l'agrégat et sera durable en S3.

Vérification ciblée :

```sh
./mvnw -pl contribution-service/application -am verify
```

Les contrôles d'architecture globaux résident dans `boot` et sont inclus dans la vérification du réacteur.
