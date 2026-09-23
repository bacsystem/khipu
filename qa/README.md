# QA · Certificación por flujo

Una ficha por flujo. Certificado = **0 bloqueantes, 0 importantes** tras auditoría independiente + recertificación.

| # | Flujo | Estado | Ficha |
|---|---|---|---|
| 1 | Emisión de factura | ✅ certificado `37e1a75` | [emision.md](emision.md) |
| 2 | Notas de crédito/débito | 🟡 recert #4: 0 bloqueantes · 1 importante, corregido (A5), pendiente recert #5 | [notas.md](notas.md) |
| 3 | Registro + onboarding | ⬜ | — |
| 4 | Login / recuperar / restablecer | ⬜ | — |
| 5 | Empresa: fiscales, certificado, SOL | ⬜ | — |
| 6 | Establecimientos | ⬜ | — |
| 7 | Series | ⬜ | — |
| 8 | Listado + filtros | ⬜ | — |
| 9 | Detalle + XML/PDF/CDR | ⬜ | — |
| 10 | Bajas | ⬜ irreversible → 2 auditores | — |
| 11 | API keys | ⬜ | — |
| 12 | Personalización PDF | ⬜ | — |
| 13 | Developers | ⬜ | — |

## Transversales

| Tema | Estado | Ficha |
|---|---|---|
| Validación de entradas (SQL, endpoints, frontend) | ⚠️ 1 hueco: el portal descarta los errores por campo del 422 | [validaciones.md](validaciones.md) |

## Método (fijo)

1. Matriz de contrato: cada control vs. DTO + dominio Java **vs. fuente SUNAT** (`docs/sunat/ref/reglas_validacion_*.xlsx`, guías UBL, XSD, catálogos). El backend puede citar mal una regla.
2. Auditores con contexto limpio, cada uno en su `git worktree`, con `API_BASE_URL` a un puerto muerto (lo sin mock debe fallar, no llegar al backend real). Dos en flujos irreversibles.
3. Cada hallazgo se verifica antes de aceptarlo. Foco/teclado: ≥150 ms entre teclas.
4. Corregir → **recertificar** → repetir hasta cero.
5. Cada arreglo con test verificado por mutación.
