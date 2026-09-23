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

## Notas (recert #7: 0 bloqueantes · 3 importantes, corregido; recert #8 pendiente)

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
| Boletas fuera de alcance | issue #20 |

## Transversal

- El portal descarta los errores por campo del 422 (`errores`) y muestra solo `mensaje` — 1 PR; ver [validaciones.md](validaciones.md).
