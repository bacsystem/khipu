# Notas de crédito/débito · 🟡 recert #6: 0 bloqueantes · 1 importante → corregido + NC por importe, pendiente de recert #7

| | |
|---|---|
| Auditoría #1 | `main@37e1a75` · 4 bloqueantes · 4 importantes → #119 → #120 → #122 |
| Recert #1 | 3 bloqueantes (ND 13 gravada 3507, tope inerte en 40/17, pendiente en flotante) → #124 → #125 → #127 |
| Recert #2 | `main@e68d60b` · 2 bloqueantes · 4 importantes → #128 → #129 → #131 |
| Recert #3 | `main@fc07431` · **1 bloqueante · 3 importantes** → #132 → #133 |
| Recert #4 | `main@9d9e3e5` · **0 bloqueantes · 1 importante** → #135 |
| Recert #5 | `main@bb801d1` · **0 bloqueantes · 3 importantes** → #136 (backend) + #137 |
| Recert #6 | `main@29ee9b4` · Opus, worktree, puerto muerto · **0 bloqueantes · 1 importante** · 33/34 mutaciones (11 backend, 22 portal) · barrido de los 203 ERROR de las hojas NC/ND: ninguno alcanzable sin cruzar · e2e 72/72 ×4 aislada |
| Suite tras A7 | 124/124 Vitest · 77/77 e2e ×2 · out-ubl NotaUblTest 4/4 · 8/8 mutaciones nuevas mueren |

## Recert #6: encontrado y corregido · NC por importe

| Sev | Qué | PR |
|---|---|---|
| 🟠 | El formulario aplicaba el tope 3286 al motivo 10, que SUNAT (fila 111) y el backend eximen; nadie lo veía porque el catálogo 09 del mock no traía el 10 | A7 |
| 🟡 | `lineaRedondeaACero` bloqueaba por un descuento en 0.00 que el dominio acepta (solo el cargo tiene 2955) · `PayableRoundingAmount` de la nota sin test en out-ubl · avisos para concepto < 3 y cantidades en 0 · `E2E_PORT` en `playwright.config.ts` (dos corridas en :3100 se pisaban) | A7 |
| ✨ | **NC por importe** (catálogo 09: 04 descuento global, 05 por ítem, 08 bonificación, 09 disminución, 10 otros): una línea propia con concepto + importe, afectación según la factura (10 si tiene gravadas; 20/30 si es toda exonerada/inafecta; 40/17 en exportación/IVAP), tope 3286 salvo el 10. SUNAT no impone otra estructura (solo 4367 observa 04/05/08 sobre boletas). Catálogo 09 del mock completo | A7 |

## Recert #5: encontrado y corregido

| Sev | Qué | PR |
|---|---|---|
| 🟠 | Suite intermitente (1 rojo en 5): aserción ambigua en el panel de notas con NC 07 paralelas sobre la misma factura | A6 |
| 🟠 | Importe de la ND sin límite de enteros y mock sin validar el formato del precio (2025, 12 enteros) | A6 |
| 🟠 | Cantidad parcial tan chica que el cargo en porcentaje redondea a 0.00: dominio 2955, mock 201 | A6 |
| 🟡 | Concepto de la ND de 3 a 500 (4084) · frontera de la cuota el mismo día (3321) · ficha ±0.02 | A6 |
| 🔧 | **Backend (#123 cerrado)**: 3286 sin tolerancia en facturas y exento en motivo 10; 2642/2644/3230/3507 cruzados en el dominio; redondeo copiado a la nota total; citas 1001/2524 | backend |

## Recert #4: encontrado y corregido

| Sev | Qué | PR |
|---|---|---|
| 🟠 | «Concepto» de la ND sin `maxLength` y el mock sin validar la descripción del ítem (2026/2027): 501 caracteres viajaban, mock 201, backend 422 | A5 |
| 🟡 | 3507 del mock sobre ítems resueltos (ND 13 sin ítems pasaba) · tests de descuento fijo e ICBPER (2 mutaciones sobrevivían) · etiqueta «con IGV» en ND 40/17 · `aria-label` en «Quitar» · aviso cuando el botón se deshabilita por decimales | A5 |

## Recert #3: encontrado y corregido

| Sev | Qué | PR |
|---|---|---|
| 🔴 | NC sobre IVAP con motivo ≠ 12 → **3230** (`NotaCredito2_0` fila 223, espejo de la 206); #129 lo aplicó solo a la ND | #132 |
| 🟠 | `importeLineaNota` cobraba las **gratuitas** al valor referencial en parcial → bloqueaba NC legítimas | B4 |
| 🟠 | Con **ISC + cargo %** en la misma línea el parcial quedaba ~2 % corto → dejaba pasar una NC que SUNAT rechaza sin tolerancia. Ahora se prorratea el `precio_venta` del backend (exacto) restando solo los ajustes de monto fijo que no viajan | B4 |
| 🟠 | NC **total no descuenta el `redondeo`**: el backend no lo copia a la nota (#123) y sale por encima de la factura. El formulario lo muestra y bloquea | B4 |
| 🟡 | Importe de la ND a 2 decimales (2025) · `onUnhandledRequest: "warn"` · comentario 4331 · test del camino `precio_venta` (mutación que sobrevivía) · 3 tests de ND esperaban antes de hidratar | #132 B4 |

## Contraste SUNAT (acumulado)

- Coinciden en las tres capas: 2116/2117/2119/2120/2128/2135, 2172, 2885, 3250/3253/3319/3320/3321, 3257, 3259/3260, 3315, 2642/2644/3107/3221, 3230 (NC y ND), 3507, 3194/3261, 2025/2027.
- **3286**: fila 111 (facturas) **sin tolerancia** y exime al motivo 10; ±1 solo boletas (113). Las tres capas estrictas desde el backend de #123. 3503 con +1 (filas 114–122): solo backend.
- Catálogo 10: 3507 solo al 13 («Penalidades»); el 03 con línea gravada es legal.
- `itemParaNota` no reenvía `hidrobiologico`/`transporte`: correcto, ninguna regla de NC los pide y el `NotaBuilder` no los valida. No «arreglar».

## Pendiente (no bloquea)

- Tope solo anticipa 3286, no 3503 por tributo (el backend lo rechaza sin gastar correlativo). Mock sin dos guardas de la nota total (anticipos, descuento/cargos globales sin ítems) ni 2642/2644 por motivo: inalcanzables desde el formulario. Descuento fijo en parcial acredita por encima de lo proporcional (simétrico al cargo; decisión de producto, el texto de ayuda lo dice).
- Cancelar sin confirmar · motivo 03 obliga a mover dinero · errores por campo del 422 ignorados (transversal) · 12 enteros en cuotas (inalcanzable).
- Boletas fuera de alcance (#20).

## Siguiente paso

Mergear A7 → **recert #7** (auditor limpio, worktree, `E2E_PORT` propio). Si sale 0/0: ✅ certificado.
