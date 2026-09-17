# Changelog — khipu

Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/); versionado [SemVer](https://semver.org/lang/es/) (pre-1.0: cambios rompientes suben el minor, el resto el patch).

## [0.1.1] - 2026-09-17

### Changed
- El proyecto pasa a llamarse **khipu** (repo `bacsystem/khipu`, `spring.application.name`, remitente por defecto, `khipu-portal`). Paquete Java, base de datos y cookies conservan `factura` como identificador técnico.

### Added
- API: `GET /v1/empresa/api-keys` (lista con prefijo, estado y fechas) y `DELETE /v1/empresa/api-keys/{id}` (revocación idempotente, 404 si es de otro tenant).
- API: `X-Total-Count` en `GET /v1/facturas`, receptor e ítems en `ComprobanteResponse`, y `GET /v1/facturas/{id}/cdr?formato=xml`.
- Portal: rediseño "khipu" (estilo Linear) de comprobantes, series, empresa y API keys: franja de métricas, tablas con filtros y paginación, diálogos de creación y de referencia técnica, vista previa de XML/CDR.
- Portal: página de API keys con listado, creación (secreto de un solo uso) y revocación en línea; diálogo "Prueba de emisión" con ejemplos cURL/Node/Python.
- Portal: menú de usuario en el sidebar (cuenta, tema claro/oscuro/sistema, cambio de contraseña, cierre de sesión); componente `ui/menu`.
- Portal: acceso desde otro equipo de la red local (`PORTAL_DEV_ORIGINS`), documentado en `portal/README.md`.

### Fixed
- API: rutas inexistentes responden 404 `RUTA_INEXISTENTE` y parámetros con tipo inválido 400 `PARAMETRO_INVALIDO` (antes 500).
- Build: Testcontainers alineado entre `bootstrap` (BOM de Spring Boot) y `out-persistence`; evitaba mezclar dos versiones de docker-java al correr todos los tests desde IntelliJ.
- Portal: los mocks de MSW siguen `API_BASE_URL` y Playwright la fija en localhost, para que un `.env.local` no desvíe los e2e al backend real; e2e de login/comprobantes actualizados al rediseño.

## [0.1.0] - 2026-09-14

- Fase 1A (backend de emisión SUNAT) y etapa 1 del portal.
