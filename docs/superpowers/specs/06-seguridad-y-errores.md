# 06 · Seguridad y manejo de errores

**Fecha:** 2026-09-13 · **Estado:** aprobado · Índice: [README](README.md)

---

## 1. Seguridad

| Aspecto | Decisión |
|---|---|
| Autenticación de integradores | `X-Api-Key`; se almacena solo el hash (SHA-256 + pepper); prefijo en claro para identificar. Rotación: varias keys activas por tenant |
| Alta de tenants | Modo `multi`: endpoint `/admin/tenants` protegido por clave maestra de plataforma (env). Modo `single`: provisionamiento por CLI/variables al arrancar; el endpoint no existe. Sin UI en v1 |
| Secretos del tenant | Clave SOL, PKCS#12 y su clave, `client_secret` GRE: AES-256-GCM con clave maestra (env/KMS). Nunca se devuelven por API |
| Certificado en memoria | Se descifra y carga en `KeyStore` solo durante la firma; caché con TTL corto por tenant |
| Aislamiento | `TenantContext` obligatorio en todos los repositorios; tests que verifican que ninguna consulta omite `tenant_id` |
| Transporte | TLS terminado en reverse proxy; HSTS |
| Rate limiting | Por API key (bucket), configurable; `429` |
| Auditoría | `EVENTO_DOCUMENTO` por cambio de estado; logs estructurados con `tenant_id`, `documento_id`, sin secretos |
| Validación de entrada | Bean Validation en DTOs + reglas de dominio; tamaño máximo de body; XXE deshabilitado en todos los parsers XML |

---

## 2. Manejo de errores

| Origen | Tratamiento |
|---|---|
| JSON malformado | `400` |
| DTO inválido | `422` con mapa campo → mensajes |
| Regla de dominio / XSD | `422` con código (`SERIE_INVALIDA`, `REFERENCIA_NO_ACEPTADA`, `TOTALES_DESCUADRADOS`, `XSD_INVALIDO`…) — el documento no se persiste ni consume numeración |
| Duplicado | `409` |
| SOAPFault 0100–1999, timeout, red, HTTP 5xx SUNAT/REST | Documento queda `ERROR_ENVIO` (respuesta `201` con `ultimo_error`) + outbox con backoff exponencial hasta máx. intentos |
| CDR 2xxx–3xxx | `RECHAZADO`; en 01/07/08-F la numeración queda consumida; respuesta `201` con el CDR |
| CDR 4xxx | `ACEPTADO_CON_OBS` con lista de observaciones |
| `getStatus` 99 sin CDR | `ERROR_ENVIO` y reintento del ticket; si persiste, alerta |
| Token GRE `401` | invalidar caché, renovar una vez, reintentar |
| Certificado vencido / no cargado | `422` `CERTIFICADO_INVALIDO` antes de generar nada |
| Bug interno | `500` con `trace_id`; nunca detalles internos |

Todo error SUNAT se registra con código, descripción y payload de respuesta (sin credenciales) en `EVENTO_DOCUMENTO`.

