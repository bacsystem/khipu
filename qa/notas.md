# Notas de crédito/débito · 🔴 NO CERTIFICADO → recert #2 corregida, pendiente de recert #3

| | |
|---|---|
| Auditoría #1 | `main@37e1a75` · 4 bloqueantes · 4 importantes → pila #119 → #120 → #122 |
| Recert #1 | 3 bloqueantes nuevos (ND 13 gravada 3507, tope inerte en 40/17, pendiente en flotante) → #124 → #125 → #127 |
| Recert #2 | `main@e68d60b` · Opus, worktree, puerto muerto · **2 bloqueantes · 4 importantes** → pila #128 → #129 → C3 |
| Suite tras C3 | 116/116 Vitest · 66/66 e2e ×2 · 15/15 mutaciones mueren |

## Recert #2: encontrado y corregido

| Sev | Qué | PR |
|---|---|---|
| 🔴 | NC parcial descartaba los **cargos de línea** (47/48): acreditaba de menos, el importe en pantalla tampoco los veía | #128 |
| 🔴 | ISC viajaba como `{sistema, tasa}`: 02 exige `monto_unitario` (backend no lo exponía), 03 exige `base_pvp` → 422 sin salida | #128 |
| 🟠 | ND sobre IVAP con motivo ≠ 12 → **3230** (fila 206) con correlativo consumido; sin fixture IVAP | #129 |
| 🟠 | ND 13 sobre exportación: 30 (3507) vs 40 (2642), sin salida; el mock la aceptaba | #129 |
| 🟠 | Mock sin contrato de `items` (2642/3107, 2025, ISC, cargos) → los bloqueantes pasaban verdes | #128 #129 |
| 🟠 | Guardas de dinero sin test: NC anulada fuera del tope, `!conAnticipos`, 3286 del mock | C3 |
| ⚠️ | `playwright.config` apuntaba a :8001 (backend real): «Cambiar contraseña» mandaba `POST /v1/auth/recuperar` de verdad | #129 |
| 🟡 | Menores: cantidad > 10 decimales (2025) · Reintentar con un solo catálogo caído · foco al alert · `role=status` al emitir · 2.º `<h1>` · «Emitir nota» con baja en curso | C3 |

## Contraste SUNAT (acumulado)

- Coinciden en las tres capas: 2119/2120, 2135, 2172, 2885, 3250, 3253, 3257, 3259/3260, 3315, 3319, 3320, 3321, 2642/3107, 3230, 3507 (mock y portal; backend pendiente #123).
- **3286 zanjado con la fuente**: fila 111 (facturas) **sin tolerancia** y exime al motivo 10; la ±1 es solo boletas (fila 113). Backend aplica ±1 a todo → #123. Portal estricto (correcto).
- 3503 (límite por tributo): solo el backend. 2644 (motivo 12 exige todo en 17): nadie; `[POSIBLE]` sobre IVAP mixta.
- Extra de khipu sobre SUNAT: acumulado de NC previas (SUNAT compara nota a nota); igual en las tres capas.

## Pendiente (no bloquea)

- `importeNota` parcial difiere ≤ 0.01/línea del dominio en gravadas (base redondeada a 2 antes del IGV).
- Cancelar sin confirmar · motivo 03 obliga a mover dinero · errores por campo del 422 ignorados (transversal).
- Decisión de producto: NC **por importe** (04 descuento, 09 disminución) — hoy solo por unidades.
- Boletas fuera de alcance (#20).

## Siguiente paso

Mergear #128 → #129 → C3 → **recert #3** (auditor limpio, worktree, `API_BASE_URL` muerto).
