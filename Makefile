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
.PHONY: help instalar test test-backend test-backend-todo test-portal e2e lint build build-portal verificar docker-portal docker-backend dev api db-up db-down probar-respaldo limpiar env comprobar-env comprobar-host comprobar-despliegue develop-sync deploy-develop develop-datos develop-logs develop-stop develop-reset develop-version

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

probar-respaldo: ## Prueba de punta a punta del respaldo y la restauración de la base, en contenedores temporales (deploy/respaldo)
	sh deploy/respaldo/probar.sh

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
# Puertos por defecto: backend :18001, portal :13000, Postgres :5433 y la interfaz de Mailpit (correos que envía el backend)
# :8026. Ninguno es de desarrollo (`api` :8001, `dev` :3000, Postgres :5432): con los mismos puertos, lo que respondía en :8001
# dependía de cuál de los dos estuviera levantado, y cambiar de uno a otro parecía que «borraba todo» (eran dos bases distintas).
# Se cambian con DEVELOP_BACKEND_PORT, DEVELOP_PORTAL_PORT, DEVELOP_POSTGRES_PORT y DEVELOP_MAILPIT_PORT.
#
# Host con el que se abre el despliegue (DEVELOP_HOST, `localhost` por defecto). Para abrirlo desde otro dispositivo (el celular):
# `make deploy-develop DEVELOP_HOST=192.168.x.x`. De él salen PORTAL_URL —el único origen que el backend admite en CORS y la base de
# los enlaces de los correos— y la URL pública de la API; con `localhost` el celular apuntaría a sí mismo y CORS rechazaría su origen.
# El despliegue es HTTP local, así que además apaga la cookie Secure (COOKIE_SECURE): por HTTP desde otro dispositivo el navegador
# la descarta y el login no deja sesión. Es solo para este entorno local; la IP de la red cambia con DHCP.
DEVELOP_HOST ?= localhost
DEVELOP_WT ?= $(abspath ../khipu-wt-develop)
DEVELOP_PROJECT ?= khipu-develop
# Las claves del despliegue viven en UN archivo fijo, junto al worktree (../khipu-develop.env) y no en el .env de cada checkout:
# el volumen de datos persiste, así que un segundo `make env` desde otro worktree generaría claves distintas sobre datos ya
# cifrados con las primeras (MASTER_KEY) o con API keys emitidas con otro pepper, y se perderían sin ningún error.
DEVELOP_ENV ?= $(abspath ../khipu-develop.env)
DEVELOP_BACKEND_PORT ?= 18001
DEVELOP_PORTAL_PORT ?= 13000
DEVELOP_POSTGRES_PORT ?= 5433
DEVELOP_MAILPIT_PORT ?= 8026
# El portal habla con el backend por la red del compose (http://backend:8001); el navegador, por el puerto publicado.
# `--env-file` solo si el archivo existe: levantar (`up`) exige las claves y lo valida `comprobar-env`, pero parar, ver logs o
# resetear no las necesitan (en el compose llevan `:-`). Así `develop-reset` funciona justo cuando se perdió el archivo.
COMPOSE_DEVELOP = POSTGRES_PORT=$(DEVELOP_POSTGRES_PORT) BACKEND_PORT=$(DEVELOP_BACKEND_PORT) PORTAL_PORT=$(DEVELOP_PORTAL_PORT) \
	MAILPIT_UI_PORT=$(DEVELOP_MAILPIT_PORT) \
	PORTAL_API_BASE_URL=http://backend:8001 PORTAL_API_PUBLIC_URL=http://$(DEVELOP_HOST):$(DEVELOP_BACKEND_PORT) \
	PORTAL_URL=http://$(DEVELOP_HOST):$(DEVELOP_PORTAL_PORT) PORTAL_COOKIE_SECURE=false \
	docker compose -p $(DEVELOP_PROJECT) --project-directory $(DEVELOP_WT) -f $(DEVELOP_WT)/docker-compose.yml $(if $(wildcard $(DEVELOP_ENV)),--env-file $(DEVELOP_ENV))

# `env` arma el archivo en un temporal y solo lo mueve a su sitio si las cuatro claves salieron: un fallo a medias (p. ej. sin
# openssl) no puede dejar un archivo con claves vacías, que el siguiente `make env` ya no tocaría. Cualquier paso que falle aborta
# con error en vez de imprimir «creado».
env: ## Crea el archivo de claves del despliegue (../khipu-develop.env) con secretos nuevos si no existe; nunca pisa uno existente
	@if [ -f $(DEVELOP_ENV) ]; then echo "$(DEVELOP_ENV) ya existe: no se toca"; exit 0; fi; \
	command -v openssl >/dev/null || { echo "Falta openssl para generar los secretos"; exit 1; }; \
	tmp=$(DEVELOP_ENV).tmp; \
	cp .env.example $$tmp || exit 1; \
	for v in MASTER_KEY API_KEY_PEPPER PLATFORM_ADMIN_KEY JWT_SECRET; do \
		valor=$$(openssl rand -base64 32) && [ -n "$$valor" ] || { echo "No se pudo generar $$v"; rm -f $$tmp $$tmp.2; exit 1; }; \
		sed "s|^$$v=.*|$$v=$$valor|" $$tmp > $$tmp.2 && mv $$tmp.2 $$tmp || { echo "No se pudo escribir $$v"; rm -f $$tmp $$tmp.2; exit 1; }; \
	done; \
	mv $$tmp $(DEVELOP_ENV) && echo "$(DEVELOP_ENV) creado con secretos nuevos. No rotes MASTER_KEY ni API_KEY_PEPPER cuando ya haya datos."

comprobar-env:
	@test -f $(DEVELOP_ENV) || { echo "Falta $(DEVELOP_ENV): corre 'make env'"; exit 1; }
	@for v in MASTER_KEY API_KEY_PEPPER PLATFORM_ADMIN_KEY JWT_SECRET; do \
		grep -Eq "^$$v=.+" $(DEVELOP_ENV) || { echo "$$v está vacío o ausente en $(DEVELOP_ENV): complétalo a mano ('make env' no pisa un archivo existente; si no hay datos que conservar, bórralo y vuelve a correrlo)"; exit 1; }; \
	done

comprobar-despliegue: comprobar-host
	@test -d $(DEVELOP_WT) || { echo "No hay despliegue de develop: falta $(DEVELOP_WT) (se crea con 'make deploy-develop')"; exit 1; }

develop-sync: ## Fija el worktree de despliegue en origin/develop (lo crea si no existe; nunca recibe commits)
	git fetch origin develop
	@test -d $(DEVELOP_WT) || git worktree add --detach $(DEVELOP_WT) origin/develop
	git -C $(DEVELOP_WT) checkout --detach origin/develop

# El host va dentro de PORTAL_URL, que el backend compara tal cual en CORS: solo letras, dígitos, puntos y guiones. Se valida desde el
# entorno (`export`) y no pegándolo en el texto del comando: un host con una comilla rompería el quoting y ejecutaría lo que siga.
export DEVELOP_HOST
comprobar-host:
	@printf '%s' "$$DEVELOP_HOST" | grep -Eq '^[A-Za-z0-9.-]+$$' || { echo "DEVELOP_HOST no es un host válido: solo letras, dígitos, puntos y guiones (p. ej. localhost o 192.168.18.13)"; exit 1; }

deploy-develop: comprobar-host comprobar-env develop-sync ## Construye y levanta develop en Docker (backend, portal y su Postgres); DEVELOP_HOST=<ip> para abrirlo desde otro dispositivo
	$(COMPOSE_DEVELOP) --profile app up -d --build
	@echo "Desplegado: $$(git -C $(DEVELOP_WT) log -1 --oneline)"
	@echo "Portal http://$(DEVELOP_HOST):$(DEVELOP_PORTAL_PORT) · API http://$(DEVELOP_HOST):$(DEVELOP_BACKEND_PORT)/swagger-ui · Correo http://localhost:$(DEVELOP_MAILPIT_PORT)"
	@$(MAKE) --no-print-directory develop-datos

# Lee la base del despliegue (no la de desarrollo): ver que los datos siguen ahí después de un deploy es la forma de saber que la
# base es la esperada. Un deploy nunca borra el volumen; solo `develop-reset` lo hace.
SQL_DEVELOP = $(COMPOSE_DEVELOP) exec -T postgres psql -U factura -d factura -tAc
develop-datos: comprobar-despliegue ## Muestra qué datos tiene la base del despliegue de develop (cuentas, empresas, administradores)
	@migrada=$$($(SQL_DEVELOP) "select count(*) from information_schema.tables where table_name = 'cuenta'" 2>/dev/null | tr -d '[:space:]'); \
	if [ "$$migrada" = "1" ]; then \
		echo "Datos en develop: $$($(SQL_DEVELOP) "select (select count(*) from cuenta) || ' cuentas, ' || (select count(*) from tenant) || ' empresas, ' || (select count(*) from administrador) || ' administradores'")"; \
	elif [ -n "$$migrada" ]; then \
		echo "Datos en develop: base nueva, todavía sin migrar (el backend la migra al arrancar)"; \
	else \
		echo "Datos en develop: Postgres no responde (¿está levantado el despliegue?)"; \
	fi

develop-logs: comprobar-despliegue ## Sigue los logs del backend y del portal desplegados
	$(COMPOSE_DEVELOP) --profile app logs -f --tail=100 backend portal

develop-stop: comprobar-despliegue ## Detiene el despliegue de develop (conserva sus datos)
	$(COMPOSE_DEVELOP) --profile app stop

# Irreversible: exige CONFIRMAR=si. Sin la guarda bastaba un `make develop-reset` suelto (p. ej. al probar el Makefile) para perder
# la base y el storage del despliegue sin ningún aviso. Tiene que venir en la línea de comandos: make importa las variables de
# entorno, y un `export CONFIRMAR=si` olvidado en la shell volvería a dejar pasar un reset suelto.
develop-reset: comprobar-despliegue ## Borra el despliegue de develop Y sus datos (base y storage); exige CONFIRMAR=si
	@[ "$(origin CONFIRMAR)" = "command line" ] && [ "$(CONFIRMAR)" = "si" ] || { echo "develop-reset borra la base y el storage del despliegue de develop, sin vuelta atrás."; \
		echo "Si es lo que quieres: make develop-reset CONFIRMAR=si"; exit 1; }
	$(COMPOSE_DEVELOP) --profile app down -v

develop-version: ## Muestra qué commit de develop hay fijado para desplegar
	@git -C $(DEVELOP_WT) log -1 --oneline
