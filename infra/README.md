# Infrastructure locale du kata

Le Compose racine définit PostgreSQL 16.14, avec volume persistant, port local 55432 et mot de passe fourni par `.env.local`. Depuis S3–S4, le backend utilise cette base et applique les migrations Liquibase au démarrage.

Les tests d'intégration créent leurs propres bases PostgreSQL via Testcontainers et ne se connectent pas au volume Compose. Le rollback destructif du schéma initial est vérifié uniquement dans ces bases jetables.

Suivre le README racine : `make infra-check`, `make infra-up`, `make backend-run`. `make infra-down` conserve les données du volume. L'API écoute par défaut sur la boucle locale au port 8081.

Kafka sera défini en S8, SonarQube en S10 et le packaging conteneur applicatif en S11. Terraform et le cloud restent optionnels en S12. La CI backend préparée utilise un service Docker-in-Docker pour Testcontainers et requiert un runner compatible ; aucun réglage de runner ni ressource cloud n'est créé ici.
