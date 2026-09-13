# 01 · Requisitos no funcionales

**Fecha:** 2026-09-13 · **Estado:** aprobado · Índice: [README](README.md)

---

## 1. Requisitos no funcionales

| Categoría | Requisito | Meta | Cómo se cumple / verifica |
|---|---|---|---|
| **Rendimiento** | Latencia `POST /facturas` (excluyendo SUNAT) | p95 < 500 ms | Generación UBL + XSD + firma en memoria; sin I/O innecesario; pruebas de carga con k6 |
| | Latencia `POST /facturas` incluyendo `sendBill` | p95 < 8 s; timeout SUNAT 15 s configurable | Timeout por llamada; si vence → `ERROR_ENVIO` + outbox, nunca bloquea al cliente más del timeout |
| | Throughput | 50 documentos/s sostenidos por instancia; 100 000 documentos/día en total | Pool de conexiones dimensionado; correlativo con bloqueo de fila corto; prueba de carga k6 en CI nocturno |
| | Descargas XML/CDR/PDF | p95 < 300 ms (PDF cacheado tras la primera generación) | Streaming desde storage; PDF generado una vez y persistido |
| | Resumen diario | 10 000 boletas/tenant/día en < 5 min | Bloques de 500; generación paralela por tenant |
| **Disponibilidad** | API | 99.9 % mensual (≈ 43 min de indisponibilidad) | ≥ 2 réplicas sin estado; health checks; despliegue sin downtime (rolling) |
| | Independencia de SUNAT | Caída de SUNAT no impide emitir | Documento firmado y numerado se persiste antes de enviar; envío diferido por outbox |
| **Fiabilidad** | Pérdida de documentos | 0 | Documento + outbox en la misma transacción; numeración jamás reutilizada |
| | Duplicados hacia SUNAT | 0 envíos duplicados aceptados | Idempotencia por estado; `Idempotency-Key`; `UNIQUE(tenant, tipo, serie, numero)` |
| | Reintentos | Backoff exponencial 1 min → 6 h, máx. 20 intentos, luego alerta | `OutboxWorker`; métrica de outbox envejecido |
| | Durabilidad de archivos | XML, CDR y PDF conservados ≥ 5 años (obligación tributaria) | Object storage con versionado/backup; nunca se borran por API |
| **Escalabilidad** | Horizontal | Añadir réplicas sin cambios de código ni coordinación | Sin estado en memoria compartido; `SKIP LOCKED`; certificados cacheados por instancia con TTL |
| | Tenants | 1 000 tenants, 10 000 documentos/tenant/mes sin degradación | Índices por `tenant_id`; particionado de `DOCUMENTO` por mes cuando supere 50 M filas |
| **Seguridad** | Autenticación | API key hasheada (SHA-256 + pepper), rotación sin downtime | [06 Seguridad](06-seguridad-y-errores.md) |
| | Secretos | Cifrado AES-256-GCM en reposo; nunca en logs ni respuestas | `SecretCipher`; tests que buscan secretos en logs |
| | Aislamiento multi-tenant | Ninguna consulta sin `tenant_id` | ArchUnit + tests de repositorio |
| | Transporte | TLS 1.2+; HSTS | Reverse proxy |
| | Entrada | Body ≤ 1 MB; XXE deshabilitado; validación estricta de DTOs | Filtros y configuración de parsers |
| | Rate limiting | 20 req/s por API key, ráfaga 50 | Bucket en memoria por instancia (v1); Redis si se necesita global |
| | Vulnerabilidades | 0 críticas/altas conocidas en dependencias | OWASP Dependency-Check en CI |
| **Cumplimiento SUNAT** | Formato | XML válido contra XSD UBL 2.1 oficiales antes de firmar | `XsdValidator` bloqueante |
| | Firma | XML-DSig conforme al Manual del Programador §3 | Test de verificación de firma |
| | Homologación | Suite de casos aceptados en e-beta antes de producción | Perfil `beta`, ejecución en CI nocturna |
| | Reglas de validación | Actualizables sin release | Reglas externas versionadas (recurso/tabla) |
| **Observabilidad** | Logs | JSON estructurado con `trace_id`, `tenant_id`, `documento_id`; sin secretos ni datos personales completos | Logback JSON + MDC |
| | Métricas | Prometheus: documentos por estado, latencias, errores SUNAT por código, outbox pendiente/envejecido | Micrometer |
| | Trazas | OpenTelemetry en REST → casos de uso → SUNAT | Spring + OTel agent |
| | Alertas | Outbox con filas > 6 h, tasa de `ERROR_ENVIO` > 5 %, certificado por vencer < 30 días, SUNAT no responde > 10 min | Reglas Prometheus |
| **Mantenibilidad** | Arquitectura | Reglas hexagonales verificadas automáticamente | ArchUnit en CI |
| | Cobertura | ≥ 80 % en `domain` y `application`; ≥ 60 % global | JaCoCo con umbral |
| | Contrato | OpenAPI 3.1 generado y versionado (`/v1`); cambios incompatibles solo con nueva versión | springdoc |
| | Migraciones | Flyway, siempre compatibles hacia atrás una versión (expand/contract) | Revisión en PR |
| **Portabilidad** | Infraestructura | Cualquier entorno con contenedores + PostgreSQL 15+ + storage S3-compatible o disco | Sin dependencias de nube específica |
| | Self-hosted | Instalable por el cliente en < 1 h siguiendo la guía; requisitos mínimos 2 vCPU, 4 GB RAM, 50 GB disco, Docker 24+ | Paquete de distribución ([07 Despliegue](07-despliegue-y-operacion.md)); prueba de instalación limpia en CI |
| | Salida a internet | Solo `*.sunat.gob.pe:443`; soporte de proxy HTTP corporativo; ninguna telemetría hacia terceros | Configuración `HTTPS_PROXY`; revisión de dependencias |
| | Entornos | `local` (docker-compose), `test`, `beta`, `prod` con misma imagen | Config por variables de entorno |
| **Datos** | Retención BD | Documentos y eventos ≥ 5 años; outbox purgado a los 30 días de completado | Job de purga |
| | Backup | Diario, RPO ≤ 24 h, RTO ≤ 4 h | Backups de PostgreSQL y storage |
| | Zona horaria | `America/Lima` para fechas de emisión y RC/RA; UTC en almacenamiento | `Clock` inyectado |

