# QA · Certificación por flujo

Una ficha por flujo. Certificado = **0 bloqueantes, 0 importantes** tras auditoría independiente + recertificación.

Cómo leer un hallazgo en una ficha: 🔧 **corregido** = PR mergeado, a la espera de que la siguiente recert lo confirme · ✅ **verificado** = una recert posterior (contexto limpio) comprobó que el arreglo resiste y sus mutaciones mueren · ⬜ **abierto** = en [pendientes.md](pendientes.md). Un flujo pasa a ✅ cuando una recert termina en 0/0.

| # | Flujo | Estado | Ficha |
|---|---|---|---|
| 1 | Emisión de factura | ✅ certificado `37e1a75` | [emision.md](emision.md) |
| 2 | Notas de crédito/débito | 🟡 recert #9: 0 bloqueantes · 1 importante (cobertura), corregido (backend + A10), pendiente recert #10 | [notas.md](notas.md) |
| 3 | Registro + onboarding | ⬜ | — |
| 4 | Login / recuperar / restablecer | ⬜ | — |
| 5 | Empresa: fiscales, certificado, SOL | ⬜ | — |
| 6 | Establecimientos | ⬜ | — |
| 7 | Series | ⬜ | — |
| 8 | Listado + filtros | ⬜ | — |
| 9 | Detalle + XML/PDF/CDR | ⬜ | — |
| 10 | Comunicación de baja | 🔴 auditoría #1 (2 auditores): 2 bloqueantes · 7 importantes, corregido (#151 + A1), pendiente recert #1 | [bajas.md](bajas.md) |
| 11 | API keys | ⬜ | — |
| 12 | Personalización PDF | ⬜ | — |
| 13 | Developers | ⬜ | — |

## Pendientes

Todo lo abierto que no bloquea, de todos los flujos, en [pendientes.md](pendientes.md). Los issues de GitHub (#115, #117, #20) son solo enlaces; el control es este directorio.

## Transversales

| Tema | Estado | Ficha |
|---|---|---|
| Validación de entradas (SQL, endpoints, frontend) | ⚠️ 1 hueco: el portal descarta los errores por campo del 422 | [validaciones.md](validaciones.md) |
| CI: workflow «Homologación e-beta» inválido (`secrets` en un `if:`) → run fallido con 0 jobs en cada push desde el 18 | ✅ #144 | — |

## Método (fijo)

1. Matriz de contrato: cada control vs. DTO + dominio Java **vs. fuente SUNAT** (`docs/sunat/ref/reglas_validacion_*.xlsx`, guías UBL, XSD, catálogos). El backend puede citar mal una regla.
2. Auditores con contexto limpio, cada uno en su `git worktree`, con `API_BASE_URL` a un puerto muerto (lo sin mock debe fallar, no llegar al backend real) y su propio `E2E_PORT` (dos corridas en :3100 comparten servidor y mock). Dos en flujos irreversibles.
3. Cada hallazgo se verifica antes de aceptarlo. Foco/teclado: ≥150 ms entre teclas.
4. Corregir → **recertificar** → repetir hasta cero.
5. Cada arreglo con test verificado por mutación.
