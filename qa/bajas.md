# Comunicación de baja · 🔴 NO CERTIFICADO → corregido (backend + portal), pendiente de recert #1

Flujo irreversible: dos auditores independientes (Opus + Fable, worktrees y puertos propios) sobre `main@dc7b516`. Coincidieron en 5 hallazgos; Opus aportó 3 que Fable no vio. Leyenda de estados en [README](README.md).

| Ronda | Base | Veredicto | Corrección | Verificada en |
|---|---|---|---|---|
| Auditoría #1 (2 auditores) | `dc7b516` | 2 🔴 · 7 🟠 | #151 (backend) → A1 (portal) | **recert #1 pendiente** |

Suite tras A1: 127/127 Vitest · 89/89 e2e ×2 · backend 700/700 sin Docker · 13/13 mutaciones mueren (5 backend, 8 portal).

Sin camino de doble baja ni de baja del comprobante equivocado (verificado por ambos): `FOR UPDATE` por tenant, `UNIQUE (tenant, fecha, correlativo)`, 2105/2398 sobre no aceptados, 404 para otro tenant, un solo POST ante dblclick/Enter/Space/Escape, motivo escapado en el XML.

## Auditoría #1 → #151 + A1

| Sev | Qué | Quién | Corregido | Estado |
|---|---|---|---|---|
| 🔴 | Corte de red o respuesta no-JSON (504 HTML de un proxy) → «Enviando a SUNAT…» eterno, sin alerta, sin poder cerrar: el usuario no sabe si la baja irreversible salió | ambos | A1: `try/catch`, `toEnvelope` con guarda, aviso «pudo haber llegado», foco al alert | 🔧 recert #1 |
| 🔴 | Baja `ENVIADA` con SUNAT en 98: el worker la reconsultaba 20 veces (30 s) y **borraba la fila** (~10 min) → comprobante «en curso» para siempre, sin otra baja ni notas; si SUNAT sí lo anuló, ACEPTADO en khipu. El portal nunca reconsultaba (`GET /v1/bajas/{id}`) | ambos | #151: la baja nunca se abandona (consulta con intervalo creciente hasta 5 min) · A1: panel con «Actualizar estado» y refresco cada 30 s | 🔧 recert #1 |
| 🟠 | Un 201 con `estado: RECHAZADA` o `ENVIADA` se trataba como éxito: el diálogo se cerraba sin decir nada; el panel quedaba debajo del historial | Opus | A1: RECHAZADA se muestra con el CDR y el comprobante sigue vigente; panel arriba del historial con `role=status` | 🔧 recert #1 |
| 🟠 | El backend no bloqueaba una nota sobre una factura con baja en curso (2120): solo el portal lo defendía; la API quedaba abierta | Opus | #151: `EmitirComprobanteService` + `BajaRepository` | 🔧 recert #1 |
| 🟠 | Una excepción no prevista al enviar (red hacia S3, SDK) dejaba la baja `GENERADA` sin fila de outbox: «en curso» para siempre | Opus | #151: catch genérico → ERROR_ENVIO con reintento | 🔧 recert #1 |
| 🟠 | Mock más permisivo que el backend: sin plazo 2957, motivo vacío/tab/101, cuerpo vacío → 500; siempre ACEPTADA (ENVIADA/RECHAZADA sin e2e) | ambos | A1: paridad (2957, 2315, 2308, 400) y estados simulables «[ENVIADA]»/«[RECHAZADA]» | 🔧 recert #1 |
| 🟠 | Guardas sin test: 2308 boleta, tope 100, control chars, correlativo, irreversibilidad del estado (backend); doble envío, cierre durante envío, motivo exacto, cierre tras éxito (portal) | ambos | #151: `ComunicacionBajaTest` · A1: e2e | 🔧 recert #1 |
| 🟠 | **Hallado al escribir el test del doble envío**: la guarda `if (enviando) return` era inefectiva —dos clics del mismo tick leen el `enviando` viejo del closure y el `disabled` aún no llegó al DOM—. Ahora es un `ref`, y el e2e dispara los dos clics sincrónicos | coordinador | A1 | 🔧 recert #1 |
| 🟠 | 0127 «El ticket no existe» clasificado como transitorio: consumía el presupuesto de consultas | Opus | #151: definitivo → RECHAZADA, que no bloquea | 🔧 recert #1 |
| 🟡 | Sin días restantes del plazo en el diálogo · sin aviso de notas vigentes que quedarán sobre un comprobante anulado · `maxLength` silencioso · `fecha_referencia` no expuesta · `f-anulada` sin baja en el mock · 2398 citado también para no registrados (2105) | ambos | A1 + #151 | 🔧 recert #1 |

## Contraste SUNAT (hoja `Comunicación de Baja1_0`, manual v2.1)

- Coinciden dominio/XML/portal: 2074/2072 (UBL 2.0, Customization 1.0), 2220/2346 (ID = nombre `RUC-RA-yyyymmdd-N`), 2671/2375 (ReferenceDate = emisión ≤ IssueDate, un RA por comprobante), 1034/2287/2229, 2305–2307/2752 (una línea), 2308 (01/07/08), 2310/2312/2313, 2315/4203 (3–100), 2957 (7 días, zona Lima), 2105/2398/2323, 2324 (RA único por día); sendSummary → ticket → getStatus 0/98/99 (manual §2.6–2.7), CDR `R-…zip` guardado.
- 2957 se mide contra la fecha de **generación**; SUNAT contra la de **recepción**: una baja del día 7 reenviada al día 8 la rechaza SUNAT. El diálogo ahora avisa cuando es el último día.
- Notas vigentes sobre la factura anulada: SUNAT no lo prohíbe (sin regla en la hoja). Se avisa en el diálogo.

## Pendiente (no bloquea) · detalle en [pendientes.md](pendientes.md)

`continuar` sin lock de la baja (GET + outbox a la vez) `[POSIBLE]` · getStatus 99 con `content` no-ZIP `[POSIBLE]` · outbox ordena `fecha_emision NULLS LAST` y posterga las bajas con ≥50 envíos pendientes · CDR de la baja rehidratado sin observaciones · ZIP del RA sin carpeta dummy `[POSIBLE]` · el 3503 del backend compara `gravado` sin mirar el tributo (solo por API) · 422 por campo descartado (transversal).

## Siguiente paso

Mergear #151 → A1 → **recert #1** (dos auditores, puertos propios). Si sale 0/0: ✅ certificado.
