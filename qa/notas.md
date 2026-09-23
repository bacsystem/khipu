# Notas de crédito/débito · 🟡 recert #4: 0 bloqueantes · 1 importante → corregido (A5), pendiente de recert #5

| | |
|---|---|
| Auditoría #1 | `main@37e1a75` · 4 bloqueantes · 4 importantes → #119 → #120 → #122 |
| Recert #1 | 3 bloqueantes (ND 13 gravada 3507, tope inerte en 40/17, pendiente en flotante) → #124 → #125 → #127 |
| Recert #2 | `main@e68d60b` · 2 bloqueantes · 4 importantes → #128 → #129 → #131 |
| Recert #3 | `main@fc07431` · **1 bloqueante · 3 importantes** → #132 → #133 |
| Recert #4 | `main@9d9e3e5` · Opus, worktree, puerto muerto · **0 bloqueantes · 1 importante** · importe a ±0.01 del dominio en 18 casos · 25/28 mutaciones → A5 |
| Suite tras A5 | 120/120 Vitest · 70/70 e2e ×2 · 7/7 mutaciones nuevas mueren |

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

- Coinciden en las tres capas: 2116/2117/2119/2120/2128/2135, 2172, 2885, 3250/3253/3319/3320/3321, 3257, 3259/3260, 3315, 2642/3107/3221, 3230 (NC y ND), 3507 (portal y mock; backend #123), 3194/3261.
- **3286**: fila 111 (facturas) **sin tolerancia** y exime al motivo 10; ±1 solo boletas (113). Backend ±1 a todo → #123. Portal estricto.
- 3503 (por tributo): solo backend. 2644 cerrado: `Totales` prohíbe mezclar IVAP con otras afectaciones.
- Catálogo 10: 3507 solo al 13 («Penalidades»); el 03 con línea gravada es legal.
- `itemParaNota` no reenvía `hidrobiologico`/`transporte`: correcto, ninguna regla de NC los pide y el `NotaBuilder` no los valida. No «arreglar».

## Pendiente (no bloquea)

- Backend (#123): 3286 sin tolerancia en facturas y exento en motivo 10 · 3503 ok · 3507, 3230 y **2644** sin cruzar · copiar `redondeo` a la nota total (hoy una factura con redondeo no se puede acreditar entera desde el portal).
- Tope solo anticipa 3286, no 3503 por tributo (el backend lo rechaza sin gastar correlativo). Descuento fijo en parcial acredita por encima de lo proporcional (simétrico al cargo; decisión de producto, el texto de ayuda lo dice).
- Cancelar sin confirmar · motivo 03 obliga a mover dinero · errores por campo del 422 ignorados (transversal) · 12 enteros en cuotas (inalcanzable).
- Decisión de producto: NC **por importe** (04 descuento, 09 disminución) — hoy solo por unidades.
- Boletas fuera de alcance (#20).

## Siguiente paso

Mergear A5 → **recert #5** (auditor limpio, worktree, `API_BASE_URL` muerto). Si sale 0/0: ✅ certificado.
