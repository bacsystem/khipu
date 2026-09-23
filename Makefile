# Atajos del repo: backend (Gradle, JDK 21) y portal (Next.js). `make` sin argumentos lista los objetivos.
#
# Convenciones que respeta este Makefile:
#   - JAVA_HOME no está fijado en gradle.properties, así que los objetivos de backend lo resuelven a JDK 21.
#   - Los e2e del portal usan E2E_PORT (3100 por defecto) para no chocar con el dev server de nadie.
#   - Nada acá levanta servidores por su cuenta: `dev` y `run` son objetivos que arrancás vos a propósito.

SHELL := /bin/bash
JAVA_HOME_21 := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
GRADLE := JAVA_HOME=$(JAVA_HOME_21) ./gradlew
PORTAL := portal
E2E_PORT ?= 3100
IMAGEN_PORTAL ?= khipu-portal
IMAGEN_BACKEND ?= khipu-backend

.DEFAULT_GOAL := help
.PHONY: help instalar test test-backend test-backend-todo test-portal e2e lint build build-portal verificar docker-portal docker-backend dev api db-up db-down limpiar

help: ## Lista los objetivos
	@grep -hE '^[a-z0-9-]+:.*?## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[1m%-16s\033[0m %s\n", $$1, $$2}'

instalar: ## Instala las dependencias del portal
	cd $(PORTAL) && npm ci

# --- Verificación ------------------------------------------------------------

test: test-backend test-portal ## Tests de backend y portal (sin e2e)

test-backend: ## Tests del backend que no necesitan Docker (domain, application, in-rest, out-ubl, scheduler, soap)
	$(GRADLE) :domain:test :application:test :adapters:in-rest:test :adapters:out-ubl:test :adapters:in-scheduler:test :adapters:out-sunat-soap:test

test-backend-todo: ## Todos los tests del backend, incluidos los de Testcontainers (requiere Docker)
	$(GRADLE) test

test-portal: ## Tests unitarios del portal (Vitest)
	cd $(PORTAL) && npx vitest run

e2e: ## E2E del portal con mocks (Playwright); E2E_PORT=3100 por defecto
	cd $(PORTAL) && E2E_PORT=$(E2E_PORT) npm run e2e

lint: ## ESLint + TypeScript del portal
	cd $(PORTAL) && npx tsc --noEmit && npm run lint

verificar: lint test e2e ## Todo lo que tiene que estar verde antes de un PR

# --- Build -------------------------------------------------------------------

build: ## Jar del backend
	$(GRADLE) :bootstrap:bootJar

build-portal: ## Build de producción del portal (usa un distDir aparte para no pisar el dev server)
	cd $(PORTAL) && NEXT_DIST_DIR=.next-prod npm run build

docker-portal: ## Imagen Docker del portal (la misma que despliega Railway)
	docker build -t $(IMAGEN_PORTAL) $(PORTAL)

docker-backend: ## Imagen Docker del backend (contexto = raíz del repo)
	docker build -f deploy/backend/Dockerfile -t $(IMAGEN_BACKEND) .

# --- Desarrollo --------------------------------------------------------------

db-up: ## Levanta Postgres (lo único containerizado en desarrollo)
	docker compose up -d postgres

db-down: ## Apaga Postgres
	docker compose stop postgres

api: db-up ## Arranca el backend en :8001 (necesita las variables de .env.example)
	$(GRADLE) :bootstrap:bootRun

dev: ## Arranca el portal en :3000
	cd $(PORTAL) && npm run dev

limpiar: ## Borra artefactos de build del portal y del backend
	cd $(PORTAL) && rm -rf .next .next-prod test-results playwright-report
	$(GRADLE) clean
