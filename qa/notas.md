# Notas de crédito/débito · 🟡 recert #9: 0 bloqueantes · 1 importante (cobertura) → corregido (backend + A10), pendiente de recert #10

Estado de cada hallazgo: 🔧 corregido en un PR mergeado, a la espera de la recert · ✅ verificado por una recert posterior (el arreglo resiste) · ⬜ abierto ([pendientes.md](pendientes.md)).

| Ronda | Base | Veredicto | Corrección | Verificada en |
|---|---|---|---|---|
| Auditoría #1 | `37e1a75` | 4 🔴 · 4 🟠 | #119 → #120 → #122 | recert #1 |
| Recert #1 | post-#122 | 3 🔴 | #124 → #125 → #127 | recert #2 |
| Recert #2 | `e68d60b` | 2 🔴 · 4 🟠 | #128 → #129 → #131 | recert #3 |
| Recert #3 | `fc07431` | 1 🔴 · 3 🟠 | #132 → #133 | recert #4 |
| Recert #4 | `9d9e3e5` | 0 🔴 · 1 🟠 | #135 | recert #5 |
| Recert #5 | `bb801d1` | 0 🔴 · 3 🟠 | #136 (backend) → #137 | recert #6 |
| Recert #6 | `29ee9b4` | 0 🔴 · 1 🟠 | #139 (+ NC por importe) | recert #7 |
| Recert #7 | `21cfaa9` | 0 🔴 · 3 🟠 | #141 (backend) → #142 | recert #8 |
| Recert #8 | `7f766b6` | 0 🔴 · 5 🟠 | #145 (backend) → #146 | recert #9 |
| Recert #9 | `dc7b516` | 0 🔴 · 1 🟠 (cobertura) | backend tests → A10 | **recert #10 pendiente** |

Suite tras A10: 127/127 Vitest · 82/82 e2e ×2 · backend 692/692. Recert #9: los 5 arreglos de A9 resisten; 37/48 mutaciones (las vivas: 3 límites del 3503 sin test → A10; IGV/IVAP equivalentes al gravado/3286; el resto no-op); e2e 81/81 ×3; contrato JSON campo a campo; catálogos 09/10 idénticos a la hoja.

## Recert #9 → backend tests + A10

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | 3 de los 8 límites del 3503 sin test que los ate (exportación f114, inafecto f115, gratuitas f117: los que el total no implica); ídem el de exportación en el mock | backend: 3 casos con el total dentro del 3286 y el concepto fuera de la +1 · A10: fixture `f-export-cargo` (total 110, base 9995 = 100) con e2e y paridad del mock | 🔧 recert #10 |
| 🟡 | Cobertura: NC 12 IVAP solo a 1 unidad (ahora por el total) · exención del 10 en el mock nunca ejercida (ahora se emite) · NC ANULADA fuera del acumulado sin test en el backend (ahora con test) | A10 + backend | 🔧 recert #10 |

## Recert #8 → #145 + #146

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | El mock rechazaba **toda NC sobre IVAP** (regresión de A8): fixture `f-ivap` con `{gravado: 0, igv: 4}` en vez de `{gravado: 100, ivap: 4}` como `Totales`; la línea 17 sumaba a `igv`; el 3503 del mock no tenía IVAP | #146 | ✅ recert #9 |
| 🟠 | El mock rechazaba con 3111 el 0.13 que el formulario recomienda y el dominio acepta (base redondeada antes del impuesto) | #146 | ✅ recert #9 |
| 🟠 | `topePorTributo` en exportación devolvía el total, no la base 9995 (cargo 48, descuento global o redondeo lo separan) | #146 | ✅ recert #9 |
| 🟠 | El e2e del tope por tributo dependía del orden de la suite (leía el tope de una factura compartida) → fixture `f-isc` propia, tope exacto | #146 | ✅ recert #9 (mata la mutación en la suite completa) |
| 🟠 | Los límites 3503 del gravado y del IVAP no tenían test en el backend → test del gravado atando con el total dentro del 3286; el del IVAP es redundante con el 3286 (total = base × 1.04), documentado | #145 | ✅ recert #9 |
| 🟡 | Motivo 02 → nota total sin test | #146 | ✅ recert #9 |

## Recert #7 → #141 + #142

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | NC por importe: el tope mostrado era el 3286, pero en facturas con ISC/anticipos/mixtas el límite real es el **3503 por tributo**; el mock no lo implementaba (201 donde el backend da 422). Sin gravadas, la línea caía en «10» → siempre 3503 | #142: `topePorTributo`, `afectacionPredominante` sin comodín, mock con 3503 y totales por tributo | ✅ recert #8 (que halló el hueco IVAP/exportación, arriba) |
| 🟠 | El backend aplicaba el **3503 al motivo 10**, que las filas 114–122 eximen igual que la 111 | #141 | ✅ recert #8 |
| 🟠 | **3111** sin cruzar en ninguna capa: línea IVAP con base > 0.06 e impuesto 0.00 (importes 0.07–0.12) → rechazo con correlativo gastado | #141 (`ItemCalculado`, también facturas) + #142 (aviso) + mock | ✅ recert #8 (frontera del mock corregida en A9) |
| 🟡 | Catálogos del mock completos (09: 02/03/06; 10: 02/03) · código muerto en `lineaRedondeaACero` · totales de la nota en el mock por afectación | #142 | ✅ recert #8 |

## Recert #6 → #139

| Sev | Qué | Corregido | Estado |
|---|---|---|---|
| 🟠 | El formulario aplicaba el tope 3286 al motivo 10, que SUNAT (fila 111) y el backend eximen; el catálogo 09 del mock no traía el 10 | #139 | ✅ recert #7 |
| 🟡 | `lineaRedondeaACero` bloqueaba por un descuento en 0.00 que el dominio acepta (solo el cargo tiene 2955) · `PayableRoundingAmount` de la nota sin test · avisos para concepto < 3 y cantidades en 0 · `E2E_PORT` (dos corridas en :3100 se pisaban) | #139 | ✅ recert #7 |
| ✨ | **NC por importe** (04/05/08/09/10): línea propia concepto + importe, afectación según la factura, tope 3286 salvo el 10 | #139 | ✅ recert #7 (halló el tope por tributo, arriba) |

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
- **3286** (fila 111) y **3503** (filas 114–122): sin tolerancia / +1 por tributo, **ambos eximen al motivo 10**; en las tres capas desde A8. **3111** (NC f211 / ND f192): dominio, portal y mock desde A8.
- Catálogo 10: 3507 solo al 13 («Penalidades»); el 03 con línea gravada es legal. Catálogo 09: 04/05/08 solo observan (4367) sobre boletas.
- `itemParaNota` no reenvía `hidrobiologico`/`transporte`: correcto, ninguna regla de NC los pide. No «arreglar».

## Pendiente (no bloquea)

Todo en [pendientes.md](pendientes.md), sección Notas.

## Siguiente paso

Mergear backend → A10 → **recert #10**. Si sale 0/0: ✅ certificado.
