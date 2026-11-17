# Contrat HTTP — S4

`openapi.json` est l'export code-first de `/v3/api-docs`, produit par springdoc à partir des contrôleurs et DTO. Le test `DocumentApiIT` compare le contrat réel à cet export en retirant uniquement l'URL de serveur variable du test.

Vérifier : `./mvnw verify`. Mettre à jour volontairement après revue : `./mvnw verify -DupdateOpenApiSnapshot=true`, puis relire le diff JSON. Docker est nécessaire au test HTTP avec PostgreSQL.

La pagination expose une fenêtre `[startRow, endRow[`, un total exact filtré et des lignes résumées. Les erreurs sont des `ProblemDetail` ; les mutations exigent un auteur pédagogique et, pour les documents existants, la version métier attendue.

Les contrats d'événements appartiennent à S8. Aucun contrat Kafka n'est défini ici.
