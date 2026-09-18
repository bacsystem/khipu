# khipu — Facturación Electrónica SUNAT

Plataforma multi-tenant de facturación electrónica para Perú: **API** Java/Spring Boot (UBL 2.1, firma XML-DSig, validación XSD, envío SOAP a SUNAT, outbox con reintentos) y **portal** Next.js de autoservicio (`portal/`) para que cada empresa se dé de alta, cargue su certificado y credenciales SOL, administre series y API keys y consulte sus comprobantes.

Estado: fase 1A (factura electrónica 01 extremo a extremo con SUNAT beta) + etapa 1 del portal. Design system: [bacsystem/khipu-design-system](https://github.com/bacsystem/khipu-design-system).

> Nomenclatura: el proyecto se llama **khipu**; por historia, el paquete Java es `pe.factura`, el `rootProject` de Gradle y la base de datos se llaman `factura`, y las cookies del portal son `factura_*`. Son identificadores técnicos; renombrarlos implica migración de datos/sesiones y queda como tarea aparte.

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
`./gradlew test` (requiere Docker para Testcontainers). Portal: `cd portal && npm run test && npm run e2e` (ver `portal/README.md`).

### Homologación contra e-beta
`./gradlew :bootstrap:homologacion` emite los escenarios de factura (`bootstrap/src/test/.../homologacion/EscenariosFactura.java`)
contra `e-beta.sunat.gob.pe` con el flujo completo y exige CDR `0` sin observaciones. Deja XML y CDR por escenario en
`bootstrap/build/homologacion/` más un `RESUMEN.md` (evidencia para el trámite de SUNAT). Necesita Docker y salida a Internet;
`test` la excluye. El workflow `homologacion.yml` la corre cada noche (y bajo demanda) y publica la evidencia como artifact.
Cubre facturas, notas de crédito/débito y la comunicación de baja (RA).
Variables opcionales: `HOMOLOGACION_RUC`, `HOMOLOGACION_CERT` / `HOMOLOGACION_CERT_CLAVE` (PKCS#12 con `OU` = RUC; van juntas con
el RUC), `HOMOLOGACION_SERIE`. En Actions se toman de los secretos `HOMOLOGACION_RUC`, `HOMOLOGACION_CERT_B64` y `HOMOLOGACION_CERT_CLAVE`;
sin ellos usa el certificado de prueba del repo.

## Convenciones de código

- **Lombok** en todos los módulos (`compileOnly` + `annotationProcessor`; solo compilación, no llega al runtime).
  `lombok.config` fija `lombok.accessors.fluent = true`: los getters generados se llaman como el campo (`comprobante.serie()`), igual que los `record`.
- Objetos de valor y DTOs son `record` (sin Lombok). Lombok se usa en: `@Getter` (entidades/enums/excepciones con estado), `@RequiredArgsConstructor` (servicios, repositorios, controllers, filtros con inyección por constructor) y `@Slf4j` (loggers).
- Constructores con lógica (validación, derivación de campos) se escriben a mano.
