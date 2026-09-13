# 05 · Contrato REST

**Fecha:** 2026-09-13 · **Estado:** aprobado · Índice: [README](README.md)

---

## 1. Contrato REST

Base: `/v1`. Auth: `X-Api-Key: <key>`. JSON UTF-8. Nombres en español, `snake_case`, siguiendo la convención de la colección de referencia donde no contradiga a SUNAT.

### 1.1 Sobre de respuesta

```json
{ "estado": "exito", "datos": { ... }, "meta": { "pagina": 1, "por_pagina": 20, "total": 135 } }
{ "estado": "error", "mensaje": "Validación fallida", "errores": { "items[0].cantidad": ["debe ser > 0"] }, "codigo": "VALIDACION" }
```

Códigos HTTP: `201` creado · `200` OK · `202` aceptado con ticket · `400` JSON inválido · `401` API key inválida · `404` · `409` duplicado (serie/número o idempotency) · `422` reglas de dominio / XSD · `429` límite · `502` SUNAT no disponible (solo en `/enviar` explícito) · `500`.

### 1.2 Endpoints

| Recurso | Método y ruta | Notas |
|---|---|---|
| **Facturas** | `POST /facturas` | `correlativo` opcional; `enviar_automatico` (default `true`) |
| | `GET /facturas` | filtros `estado`, `serie`, `numero`, `desde`, `hasta`; paginado |
| | `GET /facturas/{id}` | incluye `cdr` resumido y `enlaces` |
| | `POST /facturas/{id}/enviar` | solo desde `FIRMADO` o `ERROR_ENVIO`; 409 en otro estado |
| | `GET /facturas/{id}/xml` · `/cdr` · `/pdf?formato=a4\|a5\|ticket80\|ticket58` | binarios |
| **Boletas** | mismo conjunto en `/boletas` | siempre `PENDIENTE_AGRUPACION`; `/enviar` = "incluir en el próximo RC" |
| **Notas** | `/notas-credito`, `/notas-debito` | body con `documento_afectado {tipo, serie, numero}` y `motivo` (catálogo 09/10) |
| **Resúmenes** | `POST /resumenes {fecha}` | genera y envía RC(s) de la fecha; `202` con ids/tickets |
| | `POST /resumenes/anulacion {fecha, boletas:[…]}` | RC condición 3 |
| | `GET /resumenes`, `GET /resumenes/{id}`, `/xml`, `/cdr` | |
| **Bajas** | `POST /anulaciones {detalles:[{tipo, serie, numero, motivo}]}` | solo serie F; `202` |
| | `GET /anulaciones`, `GET /anulaciones/{id}`, `/xml`, `/cdr` | |
| **Retenciones / Percepciones** | `/retenciones`, `/percepciones` + `/enviar`, `/xml`, `/cdr`, `/pdf` | fase 2 |
| **Reversiones** | `POST /reversiones` | fase 2 |
| **Guías** | `/guias-remision` con `tipo` `09`\|`31` + `/enviar`, `/xml`, `/cdr`, `/pdf` | fase 3 |
| **Consulta** | `GET /documentos/{id}/estado` | fuerza consulta de ticket si aplica |
| | `POST /consultas/cdr {tipo, serie, numero}` | `getStatusCdr` (producción) |
| **Admin tenant** | `GET/PUT /empresa` | datos, entorno, destino, hora de resumen |
| | `POST /empresa/certificado` (multipart PFX + clave) | valida vigencia y RUC en `OU` |
| | `PUT /empresa/credenciales-sol` · `PUT /empresa/credenciales-gre` | |
| | `POST /empresa/api-keys` · `DELETE /empresa/api-keys/{id}` | la key solo se muestra al crearla |
| | `GET/POST/PUT /series` | tipo, código, correlativo inicial, activa |
| **Plataforma** | `POST /admin/tenants` (clave maestra de plataforma) | alta de empresa; devuelve primera API key. **Deshabilitado en modo `single`** |
| | `GET /health`, `GET /openapi.json` | |

### 1.3 Ejemplo de cuerpo — factura

```json
{
  "serie": "F001",
  "correlativo": null,
  "fecha_emision": "2026-09-13",
  "tipo_operacion": "0101",
  "moneda": "PEN",
  "forma_pago": { "tipo": "Contado" },
  "cliente": { "tipo_doc": "6", "num_doc": "20601234567", "razon_social": "…", "direccion": "…" },
  "items": [
    { "codigo": "P001", "descripcion": "…", "unidad": "NIU", "cantidad": 1,
      "precio_unitario": 118.00, "tipo_afectacion_igv": "10" }
  ],
  "enviar_automatico": true
}
```

Respuesta `201`:

```json
{
  "estado": "exito",
  "datos": {
    "id": "6f1c…", "tipo": "01", "serie": "F001", "numero": 601,
    "estado_documento": "ACEPTADO", "hash": "+pruib33…",
    "cdr": { "codigo": "0", "descripcion": "La Factura numero F001-601, ha sido aceptada", "observaciones": [] },
    "totales": { "gravado": 100.00, "igv": 18.00, "total": 118.00 },
    "enlaces": { "xml": "/v1/facturas/6f1c…/xml", "cdr": "/v1/facturas/6f1c…/cdr", "pdf": "/v1/facturas/6f1c…/pdf" }
  }
}
```

Los totales los calcula la API a partir de los ítems; si el cliente los envía se comparan y un descuadre devuelve `422`.

