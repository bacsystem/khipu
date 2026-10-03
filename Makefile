# Atajos del repo: backend (Gradle, JDK 21) y portal (Next.js). `make` sin argumentos lista los objetivos.
#
# Convenciones que respeta este Makefile:
#   - JAVA_HOME no está fijado en gradle.properties, así que los objetivos de backend lo resuelven a JDK 21.
#   - Los e2e del portal usan E2E_PORT (3100 por defecto) para no chocar con el dev server de nadie.
#   - Nada acá levanta servidores por su cuenta: `dev` y `run` son objetivos que arrancás vos a propósito.

SHELL := /bin/bash
# En macOS se resuelve solo. En otros sistemas define JAVA_HOME_21 (el JAVA_HOME del entorno puede apuntar a otro JDK):
#   make JAVA_HOME_21=/ruta/al/jdk-21 test-backend      o     export JAVA_HOME_21=/ruta/al/jdk-21
JAVA_HOME_21 ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null)
# `=` (no `:=`): el $(error) solo se evalúa cuando un objetivo de backend lo expande, así `make help` y los de portal no lo sufren.
GRADLE = $(if $(JAVA_HOME_21),JAVA_HOME=$(JAVA_HOME_21) ./gradlew,$(error No se encontró un JDK 21: define JAVA_HOME_21=/ruta/al/jdk-21))
PORTAL := portal
E2E_PORT ?= 3100
IMAGEN_PORTAL ?= khipu-portal
IMAGEN_BACKEND ?= khipu-backend

.DEFAULT_GOAL := help
.PHONY: help instalar test test-backend test-backend-todo test-portal e2e lint build build-portal verificar docker-portal docker-backend dev api db-up db-down limpiar env comprobar-env develop-sync deploy-develop develop-logs develop-stop develop-reset develop-version

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

# --- Despliegue de develop en Docker -----------------------------------------
# Un entorno estable para probar lo ya mergeado, sin depender de la rama que tengas abierta: construye backend y portal desde un
# worktree propio fijado a origin/develop y los levanta con su propio Postgres. Proyecto y volúmenes aparte (khipu-develop):
# no toca el Postgres ni los datos de tu desarrollo local. Para probar un PR sin mergear usa `api` y `dev` desde su worktree.
#
# Puertos por defecto: backend :8001, portal :3000 (los mismos de desarrollo: apaga `api` y `dev` antes) y Postgres :5433
# (para no chocar con el :5432 del compose de desarrollo); la interfaz de Mailpit (correos que envía el backend) en :8026.
# Se cambian con DEVELOP_BACKEND_PORT, DEVELOP_PORTAL_PORT, DEVELOP_POSTGRES_PORT y DEVELOP_MAILPIT_PORT.
DEVELOP_WT ?= $(abspath ../khipu-wt-develop)
DEVELOP_PROJECT ?= khipu-develop
# Las claves del despliegue viven en UN archivo fijo, junto al worktree (../khipu-develop.env) y no en el .env de cada checkout:
# el volumen de datos persiste, así que un segundo `make env` desde otro worktree generaría claves distintas sobre datos ya
# cifrados con las primeras (MASTER_KEY) o con API keys emitidas con otro pepper, y se perderían sin ningún error.
DEVELOP_ENV ?= $(abspath ../khipu-develop.env)
DEVELOP_BACKEND_PORT ?= 8001
DEVELOP_PORTAL_PORT ?= 3000
DEVELOP_POSTGRES_PORT ?= 5433
DEVELOP_MAILPIT_PORT ?= 8026
# El portal habla con el backend por la red del compose (http://backend:8001); el navegador, por el puerto publicado.
COMPOSE_DEVELOP = POSTGRES_PORT=$(DEVELOP_POSTGRES_PORT) BACKEND_PORT=$(DEVELOP_BACKEND_PORT) PORTAL_PORT=$(DEVELOP_PORTAL_PORT) \
	MAILPIT_UI_PORT=$(DEVELOP_MAILPIT_PORT) \
	PORTAL_API_BASE_URL=http://backend:8001 PORTAL_API_PUBLIC_URL=http://localhost:$(DEVELOP_BACKEND_PORT) \
	PORTAL_URL=http://localhost:$(DEVELOP_PORTAL_PORT) \
	docker compose -p $(DEVELOP_PROJECT) --project-directory $(DEVELOP_WT) -f $(DEVELOP_WT)/docker-compose.yml --env-file $(DEVELOP_ENV)

env: ## Crea el archivo de claves del despliegue (../khipu-develop.env) con secretos nuevos si no existe; nunca pisa uno existente
	@if [ -f $(DEVELOP_ENV) ]; then echo "$(DEVELOP_ENV) ya existe: no se toca"; else \
		cp .env.example $(DEVELOP_ENV); \
		for v in MASTER_KEY API_KEY_PEPPER PLATFORM_ADMIN_KEY JWT_SECRET; do \
			sed "s|^$$v=.*|$$v=$$(openssl rand -base64 32)|" $(DEVELOP_ENV) > $(DEVELOP_ENV).tmp; mv $(DEVELOP_ENV).tmp $(DEVELOP_ENV); \
		done; \
		echo "$(DEVELOP_ENV) creado con secretos nuevos. No rotes MASTER_KEY ni API_KEY_PEPPER cuando ya haya datos."; \
	fi

comprobar-env:
	@test -f $(DEVELOP_ENV) || { echo "Falta $(DEVELOP_ENV): corre 'make env'"; exit 1; }
	@for v in MASTER_KEY API_KEY_PEPPER PLATFORM_ADMIN_KEY JWT_SECRET; do \
		grep -Eq "^$$v=.+" $(DEVELOP_ENV) || { echo "$$v está vacío o ausente en $(DEVELOP_ENV): corre 'make env' o complétalo"; exit 1; }; \
	done

develop-sync: ## Fija el worktree de despliegue en origin/develop (lo crea si no existe; nunca recibe commits)
	git fetch origin develop
	@test -d $(DEVELOP_WT) || git worktree add --detach $(DEVELOP_WT) origin/develop
	git -C $(DEVELOP_WT) checkout --detach origin/develop

deploy-develop: comprobar-env develop-sync ## Construye y levanta develop en Docker (backend, portal y su Postgres)
	$(COMPOSE_DEVELOP) --profile app up -d --build
	@echo "Desplegado: $$(git -C $(DEVELOP_WT) log -1 --oneline)"
	@echo "Portal http://localhost:$(DEVELOP_PORTAL_PORT) · API http://localhost:$(DEVELOP_BACKEND_PORT)/swagger-ui · Correo http://localhost:$(DEVELOP_MAILPIT_PORT)"

develop-logs: comprobar-env ## Sigue los logs del backend y del portal desplegados
	$(COMPOSE_DEVELOP) --profile app logs -f --tail=100 backend portal

develop-stop: comprobar-env ## Detiene el despliegue de develop (conserva sus datos)
	$(COMPOSE_DEVELOP) --profile app stop

develop-reset: comprobar-env ## Borra el despliegue de develop Y sus datos (base y storage); el próximo deploy parte de cero
	$(COMPOSE_DEVELOP) --profile app down -v

develop-version: ## Muestra qué commit de develop hay fijado para desplegar
	@git -C $(DEVELOP_WT) log -1 --oneline
