# Notas de crédito/débito · 🔴 NO CERTIFICADO → corregido, pendiente de recertificar

| | |
|---|---|
| Auditado | `main@37e1a75` · 2026-09-22 |
| Veredicto | 4 bloqueantes · 4 importantes · 0 certificable |
| Auditores | Opus + Fable (paralelo, worktrees) + matriz de contrato vs. SUNAT |
| Corrección | pila #119 → #120 → #122 (bloqueantes → importantes → tests) |
| Suite tras la pila | 104/104 Vitest · 51/51 e2e |

## Encontrado y corregido

| Sev | Qué | PR |
|---|---|---|
| 🔴 | Enter en cualquier control emitía (5 controles, 5 notas) | #119 |
| 🔴 | Corte de red → «Emitiendo…» eterno | #119 |
| 🔴 | Re-clic tras el éxito emitía otra (3 notas) | #119 |
| 🔴 | Parcial al 100 % y a ciegas → 3286; factura con anticipos no anulable | #119 |
| 🟠 | Motivos 11/12 sobre factura interna → SUNAT rechaza con correlativo consumido; 13 sobre contado (3260) | #120 |
| 🟠 | ND con afectación `10` fija → ninguna ND sobre exportación (2642) | #120 |
| 🟠 | NC 13: cuota en blanco «lista» (3253/3320/3321) | #120 |
| 🟠 | Catálogos: `fetch` rechazado → mudo para siempre | #120 |
| 🧪 | `admiteNotas` sin test; mock sin contrato; 13/16 mutaciones sobrevivían | #122 |

## Contraste SUNAT

- Catálogos 09/10 del backend **idénticos** al anexo oficial.
- 15 reglas del backend coinciden con la tabla (2119/2120, 2135, 2885, 3257, 3259/3260, 3315, 3319–3321, 2128, 2172, 2642, 3503).
- Citas imprecisas: 1001 (la letra de la serie no viene de ahí), 2524 (mezcla con 2128). → issue backend.
- **Sin resolver**: tolerancia ±1 en 3286 para facturas (Fable: solo boletas; Opus: correcta). → issue backend.

## Pendiente (no bloquea)

- Menores: tab en sustento · 2.º `<h1>` · foco a `body` tras error · Cancelar sin confirmar · motivo 03 obliga a mover dinero · «Emitir nota» visible con anulación vigente · errores por campo del 422 ignorados.
- Decisión de producto: NC **por importe** (04 descuento, 09 disminución) — hoy solo por unidades.
- Boletas fuera de alcance (#20).

## Siguiente paso

Mergear la pila → **recertificar** (auditor limpio, worktree, `API_BASE_URL` a puerto muerto).
