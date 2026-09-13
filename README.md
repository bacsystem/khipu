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
cp .env.example .env
# Genera los secretos UNA sola vez y guárdalos en .env (la app no arranca con valores vacíos ni con el placeholder):
#   MASTER_KEY=$(openssl rand -base64 32)
#   API_KEY_PEPPER=$(openssl rand -base64 32)
#   PLATFORM_ADMIN_KEY=$(openssl rand -base64 32)
set -a; source .env; set +a
./gradlew :bootstrap:bootRun
```

**No rotes `MASTER_KEY`.** Cifra los certificados PKCS#12 y las claves SOL almacenados en la base de datos;
si cambia, esos secretos dejan de poder descifrarse y cada tenant tendría que volver a cargarlos.
Respáldala junto con la base de datos. Lo mismo aplica a `API_KEY_PEPPER`: rotarlo invalida todas las API keys emitidas.

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

## Convenciones de código

- **Lombok** en todos los módulos (`compileOnly` + `annotationProcessor`; solo compilación, no llega al runtime).
  `lombok.config` fija `lombok.accessors.fluent = true`: los getters generados se llaman como el campo (`comprobante.serie()`), igual que los `record`.
- Objetos de valor y DTOs son `record` (sin Lombok). Lombok se usa en: `@Getter` (entidades/enums/excepciones con estado), `@RequiredArgsConstructor` (servicios, repositorios, controllers, filtros con inyección por constructor) y `@Slf4j` (loggers).
- Constructores con lógica (validación, derivación de campos) se escriben a mano.
