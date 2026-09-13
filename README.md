# factura — API de Facturación Electrónica SUNAT

Fase 1A: factura electrónica (01) extremo a extremo con SUNAT beta.

## Requisitos
Java 21, Docker.

### Selección del JDK
`gradle.properties` deliberadamente **no** fija un `java.home`: cada desarrollador puede tener
distintas rutas de instalación del JDK. Antes de invocar `./gradlew` asegúrate de que
`JAVA_HOME` apunte a un JDK 21.

En macOS:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

En otros sistemas, exporta `JAVA_HOME` a la ruta de tu instalación de JDK 21 antes de continuar.

## Arranque local
```bash
docker compose up -d
cp .env.example .env && export $(grep -v '^#' .env | xargs)   # o usar un gestor de .env
export MASTER_KEY=$(openssl rand -base64 32)
./gradlew :bootstrap:bootRun
```

## Flujo mínimo
1. `POST /v1/admin/tenants` con header `X-Platform-Key` → devuelve `api_key`.
2. `PUT /v1/empresa/credenciales-sol` (beta: `MODDATOS` / `moddatos`).
3. `POST /v1/empresa/certificado` (multipart PFX + clave).
4. `POST /v1/series` `{"tipo":"01","serie":"F001","correlativo_inicial":0}`.
5. `POST /v1/facturas` → `201` con estado y CDR.
6. `GET /v1/facturas/{id}/xml` · `/cdr`.

Documentación de diseño: `docs/superpowers/specs/README.md`. Plan: `docs/superpowers/plans/fase-1a/README.md`.

## Pruebas
`./gradlew test` (requiere Docker para Testcontainers).
