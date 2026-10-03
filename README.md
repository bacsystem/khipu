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

### Probar `develop` en Docker (`make`)
Para probar lo ya mergeado sin depender de la rama que tengas abierta, `develop` se despliega en Docker con backend, portal y
su propio Postgres. `make` sin argumentos lista todos los objetivos.
```bash
make env              # una sola vez: crea ../khipu-develop.env con secretos nuevos (nunca pisa uno existente)
make deploy-develop   # fija un worktree en origin/develop, construye backend y portal y los levanta
make develop-logs     # logs del backend y del portal
make develop-version  # qué commit de develop está fijado
make deploy-develop   # tras cada merge: vuelve a fijar origin/develop y reconstruye
```
- **Puertos**: portal `:3000`, backend `:8001`, Postgres `:5433` y correos (Mailpit) `:8026` (`DEVELOP_PORTAL_PORT`, `DEVELOP_BACKEND_PORT`,
  `DEVELOP_POSTGRES_PORT`, `DEVELOP_MAILPIT_PORT`). Portal y backend son los mismos de desarrollo: apaga `make api` y `make dev` antes, o cámbialos.
- **Correo**: el backend desplegado envía a Mailpit (`MAIL_SMTP_AUTH=false`, sin credenciales); la recuperación de contraseña y el envío de
  comprobantes se ven en su interfaz web.
- **Datos aparte**: usa el proyecto `khipu-develop` (volúmenes propios), así que no toca el Postgres ni los datos de tu desarrollo local.
  `make develop-stop` conserva los datos; `make develop-reset` los borra y el siguiente deploy parte de cero.
- **Las claves** están en un único archivo, `../khipu-develop.env` (`DEVELOP_ENV`), y no en el `.env` de cada checkout: así dan igual el
  worktree y la rama desde los que corras `make`. No lo borres ni lo regeneres mientras exista el despliegue: los datos persisten y
  `MASTER_KEY`/`API_KEY_PEPPER` no se rotan (si lo pierdes, `make develop-reset`, que no necesita el archivo, borra el despliegue y sus datos; después `make env` y
  `make deploy-develop` parten de cero. No corras `make env` y `deploy-develop` sin resetear antes: las claves nuevas no descifran los
  datos viejos). `make develop-stop`, `develop-logs` y `develop-reset` tampoco lo necesitan. Es independiente del `.env` de `make api`.
- **El worktree** (`../khipu-wt-develop`) solo se usa como contexto de build y nunca recibe commits.
- Para probar un PR sin mergear, usa `make api` y `make dev` desde el worktree de ese PR.
- Los objetivos de backend por Gradle necesitan `JAVA_HOME_21=/ruta/al/jdk-21` fuera de macOS.

## Storage de XML y CDR (conservación)

SUNAT obliga a conservar los XML firmados y sus CDR durante el plazo de prescripción y a entregarlos al adquirente cuando
los pida. `STORAGE_TYPE` elige dónde viven:

| `STORAGE_TYPE` | Uso | Variables |
|---|---|---|
| `fs` (por defecto) | Desarrollo y una sola máquina. Escritura atómica (temporal + rename); la durabilidad depende del volumen. | `STORAGE_FS_ROOT` (`./storage`) |
| `s3` | Producción: S3 o compatible (MinIO, Backblaze B2, Cloudflare R2). Cada objeto se sube con checksum SHA-256 que el servidor verifica. | `STORAGE_S3_BUCKET` (obligatorio), `STORAGE_S3_REGION`, `STORAGE_S3_ENDPOINT` (vacío = AWS), `STORAGE_S3_ACCESS_KEY`/`STORAGE_S3_SECRET_KEY` (vacíos = cadena de credenciales de AWS), `STORAGE_S3_PATH_STYLE` (`true` con MinIO) |

**Política de retención (producción, `s3`).** Configúrala en el bucket, no en khipu:
- **Versionado activado**: una sobreescritura o un borrado accidental deja la versión anterior recuperable.
- **Object Lock / retención en modo *compliance* ≥ 5 años** (o la regla de ciclo de vida equivalente en B2/R2): ni el operador
  puede borrar un XML antes del plazo. El plazo de prescripción tributaria es de 4 años desde el 1 de enero siguiente a la
  presentación de la declaración (6 si no se presentó); 5 años cubre el caso general con margen — ajusta si tu asesor indica más.
- **Replicación o backup a otra región/proveedor** para el bucket, con la misma retención.
- Las claves son `{tenant_id}/{yyyy}/{MM}/{RUC-TIPO-SERIE-NUMERO}.xml` (XML firmado), `R-….zip` (CDR) y `….pdf`
  (representación impresa): un prefijo por empresa y mes, así un auditor localiza un periodo sin recorrer el bucket.

**Verificación de integridad.** Una vez al día (`INTEGRIDAD_INTERVALO_MS`, 24 h) khipu recorre los comprobantes de los últimos
`INTEGRIDAD_DIAS` (7) y comprueba que cada XML exista con el `DigestValue` con el que se firmó y que el CDR exista si SUNAT lo
emitió; cada falta sale en el log como `ERROR Integridad del storage: XML_FALTANTE|XML_CORRUPTO|CDR_FALTANTE|STORAGE_INACCESIBLE …`.
Para un periodo cualquiera: `POST /v1/admin/integridad?desde=2026-01-01&hasta=2026-01-31` con `X-Platform-Key` devuelve el
informe. Solo lee: reparar es restaurar la versión/backup del objeto, o `POST /v1/facturas/{id}/cdr/recuperar` si lo que falta es
el CDR y la empresa está en producción.

**Migrar de disco local a S3.** Las claves son las mismas en ambos backends, así que basta copiar el árbol:
```bash
# 1. Con la app detenida (o con emisión pausada), sube el árbol completo conservando las rutas relativas:
aws s3 sync ./storage s3://mi-bucket/ --exact-timestamps          # AWS / B2 / R2 (con --endpoint-url)
mc mirror ./storage minio/mi-bucket                                 # MinIO
# 2. Cambia el entorno y arranca:
STORAGE_TYPE=s3 STORAGE_S3_BUCKET=mi-bucket STORAGE_S3_ENDPOINT=https://… STORAGE_S3_PATH_STYLE=true ./gradlew :bootstrap:bootRun
# 3. Verifica el periodo migrado antes de retirar el disco:
curl -X POST -H "X-Platform-Key: $PLATFORM_ADMIN_KEY" "http://localhost:8001/v1/admin/integridad?desde=2024-01-01&hasta=$(date +%F)"
```
Para probar en local, `docker compose --profile s3 up -d minio minio-init` levanta MinIO en `http://localhost:9000` (consola en
`:9001`, usuario/clave `khipu`/`khipu-minio`) y `minio-init` crea el bucket `khipu` con versionado; `.env.example` trae las
variables comentadas. MinIO dejó de publicar sus imágenes en Docker Hub y Quay (oct. 2025), así que el compose y los tests de S3
usan las de Chainguard (`cgr.dev/chainguard/minio`), que solo ofrecen `latest`.

## Flujo mínimo
1. `POST /v1/admin/tenants` con header `X-Platform-Key` → devuelve `api_key`.
2. `PUT /v1/empresa/credenciales-sol` (beta: `MODDATOS` / `moddatos`).
3. `POST /v1/empresa/certificado` (multipart PFX + clave).
4. `POST /v1/series` `{"tipo":"01","serie":"F001","correlativo_inicial":0}`.
5. `POST /v1/facturas` → `201` con estado y CDR.
6. `GET /v1/facturas/{id}/xml` · `/pdf` · `/cdr`; `POST /v1/facturas/{id}/correo` envía los tres al cliente.
7. `POST /v1/notas` (crédito/débito) y `POST /v1/facturas/{id}/baja` (anulación dentro de 7 días).
8. `PUT /v1/empresa/personalizacion-pdf` y `PUT /v1/empresa/logo`: diseño de la representación impresa (plantilla, color, logo, textos).

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
