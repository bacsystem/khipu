# 00 · Alcance y decisiones

**Fecha:** 2026-09-13 · **Estado:** aprobado · Índice: [README](README.md)

---

## 1. Objetivo y alcance

Construir el **bloque 2** del diagrama de referencia (`docs/images/image.png`): un servicio multi-tenant que recibe comprobantes en JSON, genera el XML UBL, lo firma, lo envía a SUNAT (u OSE), procesa el CDR y devuelve XML, CDR y PDF al sistema emisor.

**Dentro del alcance**

| Documento | Tipo | Canal SUNAT | Fase |
|---|---|---|---|
| Factura | 01 | SOAP `sendBill` (síncrono) | 1 |
| Boleta | 03 | SOAP `sendSummary` vía Resumen Diario (ticket) | 1 |
| Nota de crédito / débito | 07 / 08 | Como el documento que afectan | 1 |
| Resumen Diario | RC | SOAP `sendSummary` (ticket) | 1 |
| Comunicación de Baja | RA | SOAP `sendSummary` (ticket) | 1 |
| Retención / Percepción | 20 / 40 | SOAP `sendBill` (síncrono, WSDL `otroscpe`) | 2 |
| Resumen de Reversión | RR | SOAP `sendSummary` (ticket) | 2 |
| Guía de Remisión Remitente / Transportista | 09 / 31 | REST `api-cpe` + OAuth2 (ticket) | 3 |
| Administración de tenants, series, certificados, API keys | — | — | 1 |
| Consulta de estado, descarga XML/CDR/PDF | — | — | 1 |
| Modo de despliegue *self-hosted* (instalación en infraestructura del cliente) | — | — | 1 |

**Fuera del alcance:** planes/suscripciones, panel, reportes, cotizaciones, notas de venta, pagos/cobranzas, SIRE, lotes (`sendPack`), UI.

**Decisiones ya tomadas**

- Monolito modular, arquitectura hexagonal canónica (`adapters/in`, `adapters/out`).
- Java 21, Spring Boot 3.x, PostgreSQL, Flyway.
- **Sin broker de mensajería**: el trabajo asíncrono (reintentos, consulta de tickets, resumen diario) se gestiona con una tabla `outbox` en PostgreSQL y un scheduler con `FOR UPDATE SKIP LOCKED`. Decisión revisable en fase 4 si aparecen webhooks o el volumen lo exige; el puerto `OutboxRepository` aísla el cambio.
- Multi-tenant SaaS con API key por tenant.
- Envío híbrido: síncrono cuando SUNAT lo permite, ticket cuando SUNAT lo exige, `ERROR_ENVIO` reintentable cuando SUNAT no responde.
- SUNAT directo en v1; OSE detrás del mismo puerto.
- Infraestructura agnóstica: contenedor + PostgreSQL + object storage intercambiable.
- **Dos modos de operación con la misma imagen**: `multi` (SaaS operado por nosotros) y `single` (*self-hosted*: el cliente lo instala en su infraestructura con un solo tenant).

---

## 2. Fases de entrega

| Fase | Contenido | Resultado |
|---|---|---|
| **1 — Núcleo** | Estructura hexagonal, tenant/API key/series, factura 01, notas 07/08 serie F, boleta 03 + RC, RA, outbox/scheduler, PDF A4/ticket, storage, modo `single`/`multi` + CLI de provisionamiento + paquete `dist/` (compose, Helm, guía), homologación beta | API usable para facturación estándar |
| **2 — Retención / Percepción** | Tipos 20/40 vía `otroscpe`, RR, PDF | Agentes de retención/percepción |
| **3 — Guías de remisión** | Adaptador REST OAuth2, `DespatchAdvice` 09/31, PDF | Traslado de bienes |
| **4 — OSE y extras** | Adaptador OSE, `getStatusCdr`/consulta validez, webhooks de cambio de estado | Operación con OSE |

Cada fase produce su propio plan de implementación.

---

## 3. Riesgos y decisiones abiertas

| Riesgo | Mitigación |
|---|---|
| Entorno beta REST para GRE no documentado | Confirmar con SUNAT antes de la fase 3; diseñar el adaptador con URLs configurables |
| Cambios en reglas de validación (observaciones que migran a error) | Reglas locales versionadas y actualizables sin release (tabla o recurso externo) |
| Certificados de prueba | Usar certificado de pruebas autofirmado con RUC en `OU` para beta |
| Tamaño de MTOM/SOAP con CXF | Prueba temprana contra e-beta en fase 1 |
| Generación de PDF con QR y hash conforme a norma | Validar contra representación impresa de referencia de SUNAT |
| Soporte a instalaciones self-hosted heterogéneas | Solo contenedores; versiones soportadas explícitas; `/health` y logs JSON para diagnóstico remoto; CI prueba instalación limpia y actualización |
| Crecimiento futuro hacia webhooks / mayor volumen | `OutboxRepository` y `OutboxWorker` son reemplazables por un broker (RabbitMQ/SQS) sin tocar dominio ni casos de uso |
