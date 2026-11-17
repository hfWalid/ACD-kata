# Notes de lecture des références

## Provenance et rôle

Les fichiers suivants sont conservés sans modification, tels que fournis :

- [kata-acd.md](kata-acd.md) : spécification pédagogique complète.
- [learning-path.md](learning-path.md) : sélection de cours et premier parcours.
- [programme-14j.md](programme-14j.md) : proposition de calendrier daté.

Ces documents servent de matière de préparation. Leurs affirmations sur EDF, les cours et les outils ne sont pas toutes établies par la fiche de mission. Les titres, durées, dates de mise à jour et accès aux laboratoires n'ont pas été vérifiés dans le cadre du scaffold.

La [roadmap locale](../roadmap.md) remplace le calendrier historique pour suivre le travail ; elle ne réécrit pas les références. Aucun jour passé ni score indiqué comme objectif ne doit devenir un résultat acquis.

## Ajustements pour le kata

| Sujet | Lecture retenue |
|---|---|
| Métier et architecture EDF | ACD et sa connexion à un ECM sont établis par la mission. Le workflow détaillé, l'acquittement, Kafka, deux services et leurs contextes sont des choix pédagogiques à distinguer du produit réel. |
| Étendue de S0 | `kata-acd.md` prévoit PostgreSQL ; le programme J0 ajoute Kafka et SonarQube. Conserver PostgreSQL seul ; introduire les autres avec S8 et S10. |
| Modules ou packages | Choisir six modules Maven, conformément au programme détaillé ; voir ADR 0001. Les packages métier restent vides en S0. |
| OpenAPI-first et springdoc | Générer une spécification depuis les contrôleurs relève du code-first. En S4, choisir soit un contrat source écrit avant le code et vérifié, soit du code-first nommé explicitement. |
| AG Grid Enterprise | Enterprise ne prouve pas l'utilisation du Server-Side Row Model. SSRM est un exercice choisi ; le volume de 10 000 lignes ne suffit pas à rendre ce choix obligatoire. |
| Angular et tests | Une version Angular ne prouve pas le mode de l'application EDF. La cohabitation Jasmine / Vitest ne prouve pas à elle seule une migration Karma → Vitest. Le kata choisit standalone, zoneless et Vitest pour son propre socle. |
| Angular et HTTP | Le mécanisme de chargement sera choisi en S5 selon le besoin et les API stables disponibles. Une référence à `resource()` ne vaut pas décision de contourner `HttpClient` ou les tests HTTP Angular. |
| Tailwind | Version 3 demandée par la mission ; installation reportée à S7, lorsque le formulaire et les styles sont travaillés. |
| Outbox | La transaction couvre agrégat et outbox. Le relais peut republier. En S8, la déduplication et l'effet consommateur doivent partager une frontière atomique ; ne pas promettre un exactly-once distribué. |
| Pagination | Un `count` séparé n'en supprime pas le coût. En S4/S6, préciser bornes, limite de taille, tri stable, champs autorisés et besoin de total exact. |
| Java 21 | `switch (status, command)` n'est pas directement valide. Travailler sur un sélecteur unique avec une représentation adaptée ; ne pas activer des previews par inadvertance. |
| ArchUnit | Autoriser les dépendances internes du domaine et du JDK. Distinguer les adapters entre eux sans interdire leurs propres dépendances internes. Les fakes de test restent dans les sources de test. |
| Temps | Examiner `java.time.Clock` avant d'introduire un port au rôle identique. Exposer explicitement le temps utile au comportement métier. |
| Sécurité | Burp, Checkmarx, SonarQube et l'analyse de dépendances couvrent des besoins différents. Une checklist ou un outil alternatif ne démontre pas l'exécution d'un contrôle Checkmarx. |
| Observabilité | Prometheus / Grafana constitue une chaîne locale d'entraînement ; elle ne reproduit pas à elle seule CloudWatch / Grafana ni l'infrastructure EDF. |
| Réversibilité des migrations | Définir en S3 la stratégie de changement et les données préservées. Un rollback automatique de chaque changement n'est pas une garantie universelle de restauration. |
| Formation et mesure | Les diagnostics sont utiles, mais les scores ne sont ni connus ni l'unique preuve d'apprentissage. Le code expliqué, les tests et les scénarios démontrés fournissent aussi des éléments observables. |

Les références évoquent un trial AG Grid, des laboratoires cloud et des conditions d'accès. Ne pas lancer de trial, activer un compte ou provisionner une ressource en S0. Vérifier les conditions lorsque la phase concernée sera explicitement demandée.
