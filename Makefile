.DEFAULT_GOAL := help
ENV_FILE ?= .env.local

.PHONY: help verify backend-verify backend-unit-test backend-run web-install web-verify infra-check infra-up infra-down infra-status

help:
	@echo "S1-S4: backend-unit-test, backend-verify (Docker required), backend-run"
	@echo "Workspace: web-install, verify, web-verify"
	@echo "Local PostgreSQL: infra-check, infra-up, infra-status, infra-down"
	@echo "Copy .env.example to .env.local and choose a local password before infra-up."

verify: backend-verify web-verify

backend-verify:
	./mvnw --batch-mode --no-transfer-progress verify

backend-unit-test:
	./mvnw --batch-mode --no-transfer-progress test

backend-run:
	./mvnw --batch-mode --no-transfer-progress -DskipTests package
	java -jar contribution-service/boot/target/contribution-boot-0.1.0-SNAPSHOT.jar

web-install:
	npm --prefix web ci

web-verify:
	npm --prefix web run format:check
	npm --prefix web run test:ci
	npm --prefix web run build

infra-check:
	docker compose --env-file "$(ENV_FILE)" config --quiet

infra-up: infra-check
	docker compose --env-file "$(ENV_FILE)" up -d --wait

infra-down:
	docker compose --env-file "$(ENV_FILE)" down

infra-status:
	docker compose --env-file "$(ENV_FILE)" ps
