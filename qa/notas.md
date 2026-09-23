# Notas de crédito/débito · 🟡 recert #6: 0 bloqueantes · 1 importante → corregido (#139), recert #7 en curso

Estado de cada hallazgo: 🔧 corregido en un PR mergeado, a la espera de la recert · ✅ verificado por una recert posterior (el arreglo resiste) · ⬜ abierto ([pendientes.md](pendientes.md)).

| Ronda | Base | Veredicto | Corrección | Verificada en |
|---|---|---|---|---|
| Auditoría #1 | `37e1a75` | 4 🔴 · 4 🟠 | #119 → #120 → #122 | recert #1 |
| Recert #1 | post-#122 | 3 🔴 | #124 → #125 → #127 | recert #2 |
| Recert #2 | `e68d60b` | 2 🔴 · 4 🟠 | #128 → #129 → #131 | recert #3 |
| Recert #3 | `fc07431` | 1 🔴 · 3 🟠 | #132 → #133 | recert #4 |
| Recert #4 | `9d9e3e5` | 0 🔴 · 1 🟠 | #135 | recert #5 |
| Recert #5 | `bb801d1` | 0 🔴 · 3 🟠 | #136 (backend) → #137 | recert #6 |
| Recert #6 | `29ee9b4` | 0 🔴 · 1 🟠 | #139 (+ NC por importe) | **recert #7 en curso** |

Suite tras #139: 124/124 Vitest · 77/77 e2e ×2 · out-ubl 4/4 · backend 686/686. Recert #6: 33/34 mutaciones mueren; barrido de los 203 ERROR de las hojas NC/ND sin reglas alcanzables sin cruzar.

## Recert #6 → #139

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | El formulario aplicaba el tope 3286 al motivo 10, que SUNAT (fila 111) y el backend eximen; el catálogo 09 del mock no traía el 10 | #139 | 🔧 recert #7 |
| 🟡 | `lineaRedondeaACero` bloqueaba por un descuento en 0.00 que el dominio acepta (solo el cargo tiene 2955) · `PayableRoundingAmount` de la nota sin test · avisos para concepto < 3 y cantidades en 0 · `E2E_PORT` (dos corridas en :3100 se pisaban) | #139 | 🔧 recert #7 |
| ✨ | **NC por importe** (04/05/08/09/10): línea propia concepto + importe, afectación según la factura, tope 3286 salvo el 10 | #139 | 🔧 recert #7 |

## Recert #5 → #136 + #137

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | Suite intermitente (1 rojo en 5): aserción ambigua con NC 07 paralelas sobre la misma factura | #137 | ✅ recert #6 (72/72 ×4) |
| 🟠 | Importe de la ND sin límite de enteros; mock sin validar el formato del precio (2025) | #137 | ✅ recert #6 |
| 🟠 | Cantidad parcial tan chica que el cargo % redondea a 0.00: dominio 2955, mock 201 | #137 | ✅ recert #6 |
| 🟡 | Concepto de la ND de 3 a 500 (4084) · frontera de la cuota el mismo día (3321) · ficha ±0.02 | #137 | ✅ recert #6 |
| 🔧 | **Backend (#123)**: 3286 sin tolerancia en facturas y exento en motivo 10; 2642/2644/3230/3507 por motivo en el dominio; redondeo copiado a la nota total; citas 1001/2524 | #136 | ✅ recert #6 (11/11 mutaciones) |

## Recert #4 → #135

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | «Concepto» de la ND sin `maxLength` y mock sin validar la descripción del ítem (2026/2027) | #135 | ✅ recert #5 |
| 🟡 | 3507 del mock sobre ítems resueltos · tests de descuento fijo e ICBPER · etiqueta «con IGV» en ND 40/17 · `aria-label` «Quitar» · aviso por decimales | #135 | ✅ recert #5 |

## Recert #3 → #132 + #133

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🔴 | NC sobre IVAP con motivo ≠ 12 → **3230** (fila 223); #129 lo aplicó solo a la ND | #132 | ✅ recert #4 |
| 🟠 | `importeLineaNota` cobraba las **gratuitas** al valor referencial en parcial → bloqueaba NC legítimas | #133 | ✅ recert #4 (18 casos, ±0.01) |
| 🟠 | **ISC + cargo %** en la misma línea: parcial ~2 % corto → dejaba pasar una NC que SUNAT rechaza. Ahora prorratea el `precio_venta` | #133 | ✅ recert #4 |
| 🟠 | NC total no descontaba el `redondeo` (el backend no lo copiaba) | #133 → resuelto de raíz en #136 | ✅ recert #6 |
| 🟡 | Importe de la ND a 2 decimales · `onUnhandledRequest: "warn"` · test del camino `precio_venta` · 3 tests de ND esperaban antes de hidratar | #132 #133 | ✅ recert #4 |

## Recert #2 → #128 + #129 + #131

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🔴 | NC parcial descartaba los **cargos de línea** (47/48): acreditaba de menos | #128 | ✅ recert #3 |
| 🔴 | ISC viajaba como `{sistema, tasa}`: 02 exige `monto_unitario` (backend no lo exponía), 03 `base_pvp` → 422 sin salida | #128 | ✅ recert #3 |
| 🟠 | ND sobre IVAP con motivo ≠ 12 → 3230 (fila 206); sin fixture IVAP | #129 | ✅ recert #3 (que halló el espejo en NC) |
| 🟠 | ND 13 sobre exportación: 30 (3507) vs 40 (2642), sin salida; el mock la aceptaba | #129 | ✅ recert #3 |
| 🟠 | Mock sin contrato de `items` (2642/3107, 2025, ISC, cargos) | #128 #129 | ✅ recert #3 |
| 🟠 | Guardas de dinero sin test: NC anulada fuera del tope, `!conAnticipos`, 3286 del mock | #131 | ✅ recert #3 |
| ⚠️ | `playwright.config` a :8001 (backend real): «Cambiar contraseña» mandaba `POST /v1/auth/recuperar` de verdad | #129 | ✅ recert #3 (0 huecos con puerto muerto) |
| 🟡 | Cantidad > 10 decimales · Reintentar con un solo catálogo caído · foco al alert · `role=status` al emitir · 2.º `<h1>` · «Emitir nota» con baja en curso | #131 | ✅ recert #3 |

## Recert #1 → #124 + #125 + #127

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🔴 | ND 13 «Penalidades» salía gravada (10/40): SUNAT 3507 exige inafecta 30 | #124 | ✅ recert #2 |
| 🔴 | Tope 3286 inerte en exportación (40) e IVAP (17): `calcularTotales` devolvía 0 | #124 | ✅ recert #2 |
| 🔴 | `monto_pendiente` de la NC 13 en punto flotante (30.299999…) → 3250/3319 | #125 | ✅ recert #2 |
| 🟡 | Tab pegado en el sustento (2135) · mock sin 3250/3253/3319 ni guarda de JSON | #127 | ✅ recert #2 |

## Auditoría #1 → #119 + #120 + #122

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🔴 | Enter en cualquier control emitía (5 controles, 5 notas) | #119 | ✅ recert #1 |
| 🔴 | Corte de red → «Emitiendo…» eterno | #119 | ✅ recert #1 |
| 🔴 | Re-clic tras el éxito emitía otra (3 notas) | #119 | ✅ recert #1 |
| 🔴 | Parcial al 100 % y a ciegas → 3286; factura con anticipos no anulable | #119 | ✅ recert #1 |
| 🟠 | Motivos 11/12 sobre factura interna → rechazo con correlativo consumido; 13 sobre contado (3260) | #120 | ✅ recert #1 |
| 🟠 | ND con afectación `10` fija → ninguna ND sobre exportación (2642) | #120 | ✅ recert #1 |
| 🟠 | NC 13: cuota en blanco «lista» (3253/3320/3321) | #120 | ✅ recert #1 |
| 🟠 | Catálogos: `fetch` rechazado → mudo para siempre | #120 | ✅ recert #1 |
| 🧪 | `admiteNotas` sin test; mock sin contrato; 13/16 mutaciones sobrevivían | #122 | ✅ recert #1 |

## Contraste SUNAT (acumulado)

- Coinciden en las tres capas: 2116/2117/2119/2120/2128/2135, 2172, 2885, 3250/3253/3319/3320/3321, 3257, 3259/3260, 3315, 2642/2644/3107/3221, 3230 (NC y ND), 3507, 3194/3261, 2025/2027, 3303.
- **3286**: fila 111 (facturas) **sin tolerancia** y exime al motivo 10; ±1 solo boletas (113). Las tres capas estrictas desde #136. 3503 con +1 (filas 114–122): solo backend.
- Catálogo 10: 3507 solo al 13 («Penalidades»); el 03 con línea gravada es legal. Catálogo 09: 04/05/08 solo observan (4367) sobre boletas.
- `itemParaNota` no reenvía `hidrobiologico`/`transporte`: correcto, ninguna regla de NC los pide. No «arreglar».

## Pendiente (no bloquea)

Todo en [pendientes.md](pendientes.md), sección Notas.

## Siguiente paso

Recert #7 en curso sobre `main@21cfaa9`. Si sale 0/0: ✅ certificado.
