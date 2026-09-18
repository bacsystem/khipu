# Changelog — khipu

Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/); versionado [SemVer](https://semver.org/lang/es/) (pre-1.0: cambios rompientes suben el minor, el resto el patch).

## [0.1.3] - 2026-09-17

### Changed
- UBL factura: `cac:TaxCategory/cbc:ID` (catálogo 05: S/E/O, UN/ECE 5305) en cada línea y en los subtotales globales, y atributos `schemeName`/`schemeAgencyName`/`schemeURI` del catálogo 06 en el RUC del emisor y el documento del receptor, con los valores exactos que exige la hoja `Factura2_0` de las reglas de validación 2026-08-26 (#29, parte a). Nuevo test de contrato `AtributosSunatFacturaTest` que fija por XPath cada atributo que SUNAT observa si difiere (4251–4259).

## [0.1.2] - 2026-09-17

### Fixed
- API: los faults SUNAT 1000–1999 (errores del contenido o del emisor: 1001 formato de serie, 1033 registrado previamente con otros datos, 1034–1036 nombre de archivo ≠ XML, 1059 sin firma, 1078 emisor no autorizado) dejan el comprobante en `RECHAZADO` con el código y la descripción del fault, en un solo intento. Antes se trataban como transitorios y el outbox los reintentaba 20 veces con backoff hasta 6 h, ocultando el error real (#33). Solo 0100–0999 (servicio no disponible, timeout, credenciales) siguen siendo reintentables.
- API: `enlaces.cdr` en `ComprobanteResponse` solo aparece cuando existe la constancia; un rechazo por fault trae `cdr.codigo`/`descripcion` pero no archivo. El portal usa ese enlace para ofrecer la descarga y rotula el código como "Código SUNAT" cuando no hay CDR.

## [0.1.1] - 2026-09-17

### Changed
- El proyecto pasa a llamarse **khipu** (repo `bacsystem/khipu`, `spring.application.name`, remitente por defecto, `khipu-portal`). Paquete Java, base de datos y cookies conservan `factura` como identificador técnico.

### Added
- API: `GET /v1/empresa/api-keys` (lista con prefijo, estado y fechas) y `DELETE /v1/empresa/api-keys/{id}` (revocación idempotente, 404 si es de otro tenant). Crear, listar y revocar keys exige sesión del portal (JWT): con `X-Api-Key` responde 403 `REQUIERE_SESION`, para que una key filtrada no pueda crear otras ni revocar las del tenant.
- API: `X-Total-Count` en `GET /v1/facturas`, receptor e ítems en `ComprobanteResponse`, y `GET /v1/facturas/{id}/cdr?formato=xml`.
- Portal: rediseño "khipu" (estilo Linear) de comprobantes, series, empresa y API keys: franja de métricas, tablas con filtros y paginación, diálogos de creación y de referencia técnica, vista previa de XML/CDR.
- Portal: página de API keys con listado, creación (secreto de un solo uso) y revocación en línea; diálogo "Prueba de emisión" con ejemplos cURL/Node/Python.
- Portal: menú de usuario en el sidebar (cuenta, tema claro/oscuro/sistema, cambio de contraseña, cierre de sesión); componente `ui/menu`.
- Portal: acceso desde otro equipo de la red local (`PORTAL_DEV_ORIGINS`), documentado en `portal/README.md`.
- Portal: `API_PUBLIC_URL` (opcional) para la URL de la API que se muestra al usuario en los ejemplos de integración y en "Try it" de `/developers`, cuando difiere de la interna `API_BASE_URL` (Docker, red privada).
- Portal: componentes compartidos `ui/metrica`, `ui/pie-tabla` (contador + filas por página + paginador), `ui/boton-copiar` y `ui/referencia-tecnica` (modal de referencia con secciones, tabla de campos y reglas), usados por comprobantes, series, empresa y API keys.

### Fixed
- API: rutas inexistentes responden 404 `RUTA_INEXISTENTE` y parámetros con tipo inválido 400 `PARAMETRO_INVALIDO` (antes 500). `GET /v1/facturas/{id}/cdr?formato=` distinto de `zip`/`xml` responde 400; un CDR almacenado ilegible responde 500 `CDR_CORRUPTO` (antes 422) conservando la causa en el log.
- Build: Testcontainers alineado entre `bootstrap` (BOM de Spring Boot) y `out-persistence`; evitaba mezclar dos versiones de docker-java al correr todos los tests desde IntelliJ.
- Portal: los mocks de MSW siguen `API_BASE_URL` y Playwright la fija en localhost, para que un `.env.local` no desvíe los e2e al backend real; e2e de login/comprobantes actualizados al rediseño.

## [0.1.0] - 2026-09-14

- Fase 1A (backend de emisión SUNAT) y etapa 1 del portal.
