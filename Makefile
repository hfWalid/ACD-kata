.DEFAULT_GOAL := help
ENV_FILE ?= .env.local

.PHONY: help verify local-env local-env-test web-structure-test backend-verify backend-unit-test backend-run review-run web-install web-verify web-coverage e2e-run infra-check infra-up infra-down infra-status seed-documents import-documents quality-up quality-token quality-scan quality-deps quality-down

help:
	@echo "S1-S4: backend-unit-test, backend-verify (Docker required), backend-run"
	@echo "S8: review-run starts the review context consumer after backing services"
	@echo "Workspace: web-install, verify, web-verify"
	@echo "Local backing services: infra-check, infra-up (PostgreSQL + Kafka), infra-status, infra-down"
	@echo "S6 exercise: seed-documents adds 10 000 demo documents to the Compose volume"
	@echo "E1 import: import-documents imports a CSV or JSON file (FILE=..., MODE=draft|submitted|approved, DRY_RUN=true)"
	@echo "S9 e2e: e2e-run runs Cypress against a backend and a dev server already started"
	@echo "S10 quality: quality-up, quality-token, quality-scan (SonarQube profile "quality"); quality-deps, quality-down"
	@echo "Local configuration: local-env creates it once; existing configuration is preserved."

verify: local-env-test web-structure-test backend-verify web-verify

local-env:
	node scripts/init-local-env.mjs "$(ENV_FILE)"

local-env-test:
	node --test scripts/init-local-env.test.mjs

web-structure-test:
	node --test scripts/web-structure.test.mjs

backend-verify:
	./mvnw --batch-mode --no-transfer-progress verify

backend-unit-test:
	./mvnw --batch-mode --no-transfer-progress test

backend-run: infra-up
	./mvnw --batch-mode --no-transfer-progress -DskipTests package
	java -jar contribution-service/boot/target/contribution-boot-0.1.0-SNAPSHOT.jar --spring.config.import="file:$(abspath $(ENV_FILE))[.properties]"

review-run: infra-up
	./mvnw --batch-mode --no-transfer-progress -DskipTests package
	java -jar review-service/target/review-service-0.1.0-SNAPSHOT.jar --spring.config.import="file:$(abspath $(ENV_FILE))[.properties]"

IMPORT_FILE ?= scripts/import/annuaire-2026.csv
IMPORT_SOURCE ?= annuaire
IMPORT_MODE ?= draft
IMPORT_ACTOR ?= importateur
IMPORT_APPROVER ?= relecteur
IMPORT_DRY_RUN ?= false

import-documents: infra-up
	./mvnw --batch-mode --no-transfer-progress -DskipTests package -pl contribution-service/boot -am
	java -jar contribution-service/boot/target/contribution-boot-0.1.0-SNAPSHOT.jar \
		--spring.config.import="file:$(abspath $(ENV_FILE))[.properties]" \
		--spring.main.web-application-type=none \
		--acd.import.file="$(IMPORT_FILE)" \
		--acd.import.source="$(IMPORT_SOURCE)" \
		--acd.import.mode="$(IMPORT_MODE)" \
		--acd.import.actor="$(IMPORT_ACTOR)" \
		--acd.import.approver="$(IMPORT_APPROVER)" \
		--acd.import.dry-run="$(IMPORT_DRY_RUN)" \
		--acd.import.specs-directory="contracts/import"

web-coverage:
	npm --prefix web run coverage

# Cypress drives a real browser against the real stack: it needs both servers.
# ELECTRON_RUN_AS_NODE is cleared because some sandboxes export it, which turns the Cypress
# application into plain Node and makes it reject its own options.
e2e-run:
	@curl --fail --silent --max-time 3 http://127.0.0.1:8081/actuator/health >/dev/null \
		|| { echo "Backend absent : lancer make backend-run dans un autre terminal."; exit 1; }
	@curl --fail --silent --max-time 3 http://127.0.0.1:4200/ >/dev/null \
		|| { echo "Interface absente : lancer npm --prefix web start dans un autre terminal."; exit 1; }
	env -u ELECTRON_RUN_AS_NODE npm --prefix web run e2e:ci

quality-up:
	docker compose --env-file "$(ENV_FILE)" --profile quality up -d sonarqube
	@set -a; . "./$(ENV_FILE)"; set +a; \
		echo "SonarQube démarre sur http://127.0.0.1:$${SONAR_PORT:-9000} (une minute environ). make quality-token attend la fin du démarrage."

# SonarQube insists on the token, so this script also changes the default admin password.
# The port comes from SONAR_PORT, because 9000 is often taken by something else.
quality-token:
	@set -a; . "./$(ENV_FILE)"; set +a; \
	SONAR_HOST_URL="$${SONAR_HOST_URL:-http://127.0.0.1:$${SONAR_PORT:-9000}}" \
	SONAR_ADMIN_PASSWORD="$${SONAR_ADMIN_PASSWORD:-}" \
		node scripts/sonar-token.mjs "$(abspath $(ENV_FILE))"

quality-scan:
	@set -a; . "./$(ENV_FILE)"; set +a; \
	test -n "$${SONAR_TOKEN:-}" || { echo "SONAR_TOKEN absent de $(ENV_FILE) : lancer make quality-token."; exit 1; }; \
	docker run --rm \
		--add-host=host.docker.internal:host-gateway \
		-e SONAR_HOST_URL="$${SONAR_HOST_URL:-http://host.docker.internal:$${SONAR_PORT:-9000}}" \
		-e SONAR_TOKEN \
		-v "$(CURDIR):/usr/src" \
		sonarsource/sonar-scanner-cli:12.4

quality-deps:
	./mvnw --batch-mode --no-transfer-progress dependency:analyze
	npm --prefix web audit --audit-level=high
	npm --prefix web outdated || true

quality-down:
	docker compose --env-file "$(ENV_FILE)" --profile quality down

web-install:
	npm --prefix web ci

web-verify:
	npm --prefix web run format:check
	npm --prefix web run test:ci
	npm --prefix web run build

infra-check:
	@test -f "$(ENV_FILE)" || { printf '%s\n' 'Local configuration missing. Run make local-env first.'; exit 1; }
	docker compose --env-file "$(ENV_FILE)" config --quiet

infra-up: infra-check
	@docker compose --env-file "$(ENV_FILE)" up -d --wait --wait-timeout 90 || { \
		printf '%s\n' 'PostgreSQL did not become ready. An existing data volume keeps its original password: preserve the volume and check the local credentials.'; \
		exit 1; \
	}

infra-down:
	docker compose --env-file "$(ENV_FILE)" down

infra-status:
	docker compose --env-file "$(ENV_FILE)" ps

# Local S6 exercise only: writes demo rows directly to the Compose volume.
seed-documents: infra-up
	docker compose --env-file "$(ENV_FILE)" exec -T postgres psql \
		--username acd --dbname acd_contribution --set ON_ERROR_STOP=1 \
		< scripts/seed-documents.sql
