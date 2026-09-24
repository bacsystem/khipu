# Pendientes de certificación · no bloquean

Todo lo que las auditorías dejaron abierto a propósito, en un solo lugar. Cada línea: qué, dónde, arreglo. El issue de GitHub, cuando existe, es solo el enlace.

## Emisión (certificada `37e1a75`) · issue #117

| # | Qué | Dónde | Arreglo |
|---|---|---|---|
| 1 | Esc descarta el formulario sin confirmar (el clic fuera sí está protegido) | `nuevo-comprobante-dialog.tsx` | interceptar `onOpenChange` y confirmar si hay datos |
| 2 | Sin timeout en `apiRequest`: un POST que nunca responde deja «Emitiendo…» | `lib/api/browser.ts` | `AbortSignal.timeout(…)` → rama de error que ya avisa |
| 3 | Etiqueta «Precio unit. (con IGV)» también en exonerado/inafecto/gratuito; pie «IGV (18 %) S/ 0.00» en factura 100 % exonerada | `nuevo-comprobante-form.tsx` | etiqueta según afectación |
| 4 | «siguiente N.º» obsoleto entre pestañas del mismo tenant (el backend asigna bien) | diálogo | «último emitido: N» |
| 5 | `calcularTotales` devuelve 0.00 en silencio para 17/40 (hoy inalcanzable desde el diálogo) | `totales.ts` | fallar o marcar «no soportado», con test |
| 6 | Enter sobre un campo incompleto no reacciona (antes al menos salía la burbuja nativa) | `formularios.ts` | `reportValidity()` o foco al botón |
| 7 | Botones ± del stepper no alcanzables por Tab (`tabindex=-1`, default de base-ui) | design system `stepper-numerico.tsx` | decidir: exponer o documentar |
| — | Verificación manual pendiente: popup nativo del `<select>` Moneda en macOS (Espacio, flechas, Enter → cambia moneda y **no** emite). No automatizable bajo CDP | — | 30 s a mano |

## Emisión · backend · issue #115

- Idempotencia en `POST /v1/facturas`: un reintento tras corte de red no debe emitir dos facturas (clave de idempotencia por tenant). Hoy el portal avisa «pudo haberse emitido»; el backend no protege.

## Notas (recert #10: 0 bloqueantes · 2 importantes, corregido; recert #11 pendiente)

| Qué | Nota |
|---|---|
| El tope de la NC **parcial** anticipa 3286, no 3503 por tributo (la NC por importe sí, desde A8) | el backend lo rechaza dentro de la transacción, sin gastar correlativo |
| Descuento de monto fijo en parcial acredita por encima de lo proporcional | simétrico al cargo; decisión de producto, el texto de ayuda lo dice |
| «Cancelar» sin confirmar | `<Link>` directo |
| Motivo 03 «corrección por error en la descripción» obliga a mover cantidades | cae en parcial; SUNAT no define importe 0 para el 03 |
| Errores por campo del 422 descartados (`res.errores`) | transversal, ver [validaciones.md](validaciones.md) |
| Mock sin dos guardas de la nota total (anticipos; descuento/cargos globales sin ítems) ni 2642/2644 por motivo | inalcanzables desde el formulario |
| Base del 2955 del mock aproximada (/1.18 gravadas, /1.04 IVAP) | `[POSIBLE]` con cantidad subcentavo en IVAP con cargo % |
| `importeLineaNota` se desvía ≤ 0.02 del dominio en parciales con ISC | 20 000 líneas aleatorias: 91 % exacto, máx. 0.02; exacto con la cantidad facturada |
| `afectacionPredominante` elige 10 en una factura mixta: el descuento acredita IGV sobre todo el importe aunque parte sea exonerada | SUNAT lo acepta dentro del 3503; riesgo fiscal del emisor, no rechazo. Partir la nota por afectación sería otra iteración |
| `Acreditado` suma también las NC de motivo 10 al acumulado | política deliberada (#83), más estricta que SUNAT |
| Mock: 3503 sin el límite de gratuitas; descuento de línea no restado del total; 3111 solo en gravadas onerosas (la fila del 9996 es inalcanzable con IGV ≥ 10.5 %) | sin fixture con gratuitas ni descuento de línea; inalcanzables hoy |
| Backend: el 3503 compara `gravado` sin mirar el tributo (NC 12 con líneas 17 sobre factura no IVAP pasaría) | solo por API; el formulario no ofrece el 12 sin IVAP |
| 2885: SUNAT lo exime en el motivo 10 (NC) y en el 03 (ND); khipu lo aplica siempre | más estricto, inalcanzable desde el portal (el formulario manda siempre la fecha de hoy) |
| Boletas fuera de alcance | issue #20 |

## Bajas (auditoría #1: 2 bloqueantes · 7 importantes, corregido; recert #1 pendiente)

| Qué | Nota |
|---|---|
| `DarDeBajaService.continuar` corre sin lock de la baja: un `GET /v1/bajas/{id}` de un integrador y el outbox a la vez podrían enviar dos veces el mismo RA | `[POSIBLE]`, no alcanzable desde el portal |
| `getStatus` 99 con `content` que no es ZIP (SUNAT devuelve texto en algunos errores) | `[POSIBLE]`; con el arreglo del worker ya no se agota, se reintenta |
| El outbox ordena por `d.fecha_emision NULLS LAST` y una fila `BAJA` (cuyo `agregado_id` no es un documento) queda siempre al final | con ≥50 envíos pendientes la consulta del ticket se posterga |
| El CDR de la baja se rehidrata con `observaciones = List.of()` | se pierden tras un reinicio |
| El ZIP del RA lleva solo el XML; el manual describe además una carpeta dummy | `[POSIBLE]`, igual que en emisión (certificada); SUNAT lo acepta |
| 2957 se mide contra la fecha de generación, SUNAT contra la de recepción | el diálogo avisa cuando es el último día del plazo |
| La ficha no muestra `intentos` salvo cuando no hay CDR | cosmético |
## Registro + onboarding (auditoría #1: 1 bloqueante · 12 importantes, corregido; recert #1 pendiente)

| Qué | Nota |
|---|---|
| No existe endpoint para editar la razón social ni el entorno de una empresa | por eso la validación al crear es la única defensa; un cambio posterior exige intervención manual |
| Un RUC tecleado por error queda tomado en toda la plataforma | denegación de registro, no impersonación: emitir exige que el OU del certificado traiga ese RUC |
| El correo no se verifica y no hay límite de empresas por cuenta | — |
| El wizard no tiene «Volver» | un tipeo solo se corrige antes de enviar el paso |
| `POST /api/auth/registro` con cuerpo no-JSON da 500 en vez de 400 | mismo patrón que el resto de los route handlers |
| El mock revienta con cuerpo vacío en `POST /v1/empresas` | `[POSIBLE]` en cuanto al disparador |

## Transversal

- El portal descarta los errores por campo del 422 (`errores`) y muestra solo `mensaje` — 1 PR; ver [validaciones.md](validaciones.md).
