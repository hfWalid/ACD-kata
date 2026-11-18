.DEFAULT_GOAL := help
ENV_FILE ?= .env.local

.PHONY: help verify local-env local-env-test web-structure-test backend-verify backend-unit-test backend-run web-install web-verify infra-check infra-up infra-down infra-status seed-documents

help:
	@echo "S1-S4: backend-unit-test, backend-verify (Docker required), backend-run"
	@echo "Workspace: web-install, verify, web-verify"
	@echo "Local PostgreSQL: infra-check, infra-up, infra-status, infra-down"
	@echo "S6 exercise: seed-documents adds 10 000 demo documents to the Compose volume"
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
