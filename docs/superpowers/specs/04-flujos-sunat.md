# 04 · Flujos con SUNAT

**Fecha:** 2026-09-13 · **Estado:** aprobado · Índice: [README](README.md)

---

## 1. Diagramas de secuencia

### 1.1 Factura (síncrono, `sendBill`)

![Factura — envío síncrono](../../architecture/06-seq-factura-sincrona.svg)

*Fuente: [`docs/architecture/06-seq-factura-sincrona.mmd`](../../architecture/06-seq-factura-sincrona.mmd)*

### 1.2 Boleta → Resumen Diario → ticket

![Boleta → Resumen Diario → ticket](../../architecture/07-seq-boleta-resumen-diario.svg)

*Fuente: [`docs/architecture/07-seq-boleta-resumen-diario.mmd`](../../architecture/07-seq-boleta-resumen-diario.mmd)*

### 1.3 Comunicación de Baja (RA)

![Comunicación de Baja](../../architecture/08-seq-comunicacion-baja.svg)

*Fuente: [`docs/architecture/08-seq-comunicacion-baja.mmd`](../../architecture/08-seq-comunicacion-baja.mmd)*

### 1.4 Guía de Remisión (REST + OAuth2)

![Guía de Remisión — REST + OAuth2](../../architecture/09-seq-guia-remision-rest.svg)

*Fuente: [`docs/architecture/09-seq-guia-remision-rest.mmd`](../../architecture/09-seq-guia-remision-rest.mmd)*

### 1.5 Reintento por outbox

![Reintento vía outbox](../../architecture/10-seq-reintento-outbox.svg)

*Fuente: [`docs/architecture/10-seq-reintento-outbox.mmd`](../../architecture/10-seq-reintento-outbox.mmd)*

