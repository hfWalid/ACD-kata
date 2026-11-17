# Infrastructure locale du kata

Le Compose racine définit PostgreSQL 16.14, avec volume persistant, port local 55432 et mot de passe fourni par `.env.local`. Depuis S3–S4, le backend utilise cette base et applique les migrations Liquibase au démarrage.

Les tests d'intégration créent leurs propres bases PostgreSQL via Testcontainers et ne se connectent pas au volume Compose. Le rollback destructif du schéma initial est vérifié uniquement dans ces bases jetables.

Première utilisation et redémarrages : `make local-env`, `make infra-up`, `make backend-run`, depuis la racine du kata. `local-env` crée un fichier absent avec un secret aléatoire et des permissions `0600` ; il ne modifie pas un fichier existant. Compose et le lancement Spring reçoivent le même `ENV_FILE`, `.env.local` par défaut. `make infra-down` conserve les données du volume. L'API écoute par défaut sur la boucle locale au port 8081.

Le contrôle de santé effectue une requête authentifiée sur l'adresse réseau du conteneur. Une connexion locale dans le conteneur peut utiliser une règle `trust`, et `pg_isready` peut réussir sans identifiants valides : ces deux vérifications seules ne prouvent pas que le backend pourra se connecter. [Documentation PostgreSQL de `pg_isready`](https://www.postgresql.org/docs/16/app-pg-isready.html).

`POSTGRES_PASSWORD` initialise le rôle lors de la création d'une base vide ; modifier le fichier ne change pas le mot de passe d'un rôle existant. En cas de décalage, le démarrage s'arrête : conserver le volume et le fichier, puis demander un réalignement explicite des identifiants avec assistance technique. Ne pas recréer la configuration pour tenter de réparer l'authentification. [Fonctionnement de l'image PostgreSQL](https://hub.docker.com/_/postgres).

Kafka sera défini en S8, SonarQube en S10 et le packaging conteneur applicatif en S11. Terraform et le cloud restent optionnels en S12. La CI backend préparée utilise un service Docker-in-Docker pour Testcontainers et requiert un runner compatible ; aucun réglage de runner ni ressource cloud n'est créé ici.
