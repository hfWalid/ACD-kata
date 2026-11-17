# S3 — PostgreSQL et migrations

L'adapter JPA transforme les objets du domaine en entités techniques séparées. Les tables contiennent le document, l'ordre des sections et les entrées d'audit. Hibernate valide le schéma créé par Liquibase 5 ; il ne remplace pas les migrations.

Les lectures et écritures de l'agrégat sont transactionnelles. Une mise à jour vérifie la version métier attendue et utilise aussi la protection optimiste JPA. La projection de liste ne charge pas les collections de contenu ou d'audit.

Les tests Testcontainers utilisent PostgreSQL 16.14, sans H2 ni dépendance à la base Compose de l'utilisateur. Docker est obligatoire pour ces tests. L'absence de Docker doit faire échouer la vérification, pas produire une validation silencieusement incomplète.

Le premier changelog crée un schéma neuf : aucune donnée historique n'est migrée. Son rollback supprime les tables et les données. Le test exécute ce rollback uniquement dans une base jetable puis réapplique la migration. Il ne touche pas le volume Compose et ne constitue pas une procédure de restauration de données métier.

```sh
./mvnw -pl contribution-service/adapters-persistence -am verify
```

Les tests vérifient la sauvegarde/relecture, l'ordre des sections, l'audit, les conflits et l'atomicité, les filtres, le tri et les fenêtres décalées.
