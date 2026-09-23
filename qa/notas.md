# Notas de crédito/débito · 🟡 recert #5: 0 bloqueantes · 3 importantes → corregido (A6 + backend #123), pendiente de recert #6

| | |
|---|---|
| Auditoría #1 | `main@37e1a75` · 4 bloqueantes · 4 importantes → #119 → #120 → #122 |
| Recert #1 | 3 bloqueantes (ND 13 gravada 3507, tope inerte en 40/17, pendiente en flotante) → #124 → #125 → #127 |
| Recert #2 | `main@e68d60b` · 2 bloqueantes · 4 importantes → #128 → #129 → #131 |
| Recert #3 | `main@fc07431` · **1 bloqueante · 3 importantes** → #132 → #133 |
| Recert #4 | `main@9d9e3e5` · **0 bloqueantes · 1 importante** → #135 |
| Recert #5 | `main@bb801d1` · Opus, worktree, puerto muerto · **0 bloqueantes · 3 importantes** · importe a ±0.02 del dominio (20 000 líneas aleatorias, 91 % exacto) · 19/20 mutaciones → A6 + backend |
| Suite tras A6 | 122/122 Vitest · 72/72 e2e ×2 · backend 686/686 · 12/12 mutaciones nuevas mueren |

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

- Tope solo anticipa 3286, no 3503 por tributo (el backend lo rechaza sin gastar correlativo). Descuento fijo en parcial acredita por encima de lo proporcional (simétrico al cargo; decisión de producto, el texto de ayuda lo dice).
- Cancelar sin confirmar · motivo 03 obliga a mover dinero · errores por campo del 422 ignorados (transversal) · 12 enteros en cuotas (inalcanzable).
- Decisión de producto: NC **por importe** (04 descuento, 09 disminución) — hoy solo por unidades.
- Boletas fuera de alcance (#20).

## Siguiente paso

Mergear backend → A6 → **recert #6**. Si sale 0/0: ✅ certificado. Después: NC por importe (04/05/09) según catálogo 09 → recert.
