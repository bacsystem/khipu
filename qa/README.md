# QA · Certificación por flujo

Una ficha por flujo. Certificado = **0 bloqueantes, 0 importantes** tras auditoría independiente + recertificación.

Cómo leer un hallazgo en una ficha: 🔧 **corregido** = PR mergeado, a la espera de que la siguiente recert lo confirme · ✅ **verificado** = una recert posterior (contexto limpio) comprobó que el arreglo resiste y sus mutaciones mueren · ⬜ **abierto** = en [pendientes.md](pendientes.md). Un flujo pasa a ✅ cuando una recert termina en 0/0.

| # | Flujo | Estado | Ficha |
|---|---|---|---|
| 1 | Emisión de factura | ✅ certificado `37e1a75` | [emision.md](emision.md) |
| 2 | Notas de crédito/débito | 🟡 recert #10: 0 bloqueantes · 2 importantes, corregido (backend + A11), pendiente recert #11 | [notas.md](notas.md) |
| 3 | Registro + onboarding | 🔴 auditoría #1: 1 bloqueante · 12 importantes, corregido (#158 + portal), pendiente recert #1 | [registro.md](registro.md) |
| 4 | Login / recuperar / restablecer | 🟡 aud. #1: 1 bloqueante (sesiones cruzadas al refrescar) + 3 importantes corregidos; 4 en pendientes | [login.md](login.md) |
| 5 | Empresa: fiscales, certificado, SOL | 🟡 aud. #1: 1 bloqueante (certificado no vigente) + 3 importantes corregidos; 5 abiertos | [empresa.md](empresa.md) |
| 6 | Establecimientos | 🔴 aud. #1: **2 bloqueantes** (3 distritos del catálogo 13 imposibles de registrar; el PDF cambia si se edita el anexo) · 5 importantes, todos abiertos | [establecimientos.md](establecimientos.md) |
| 7 | Series | 🟡 aud. #1: 1 bloqueante (correlativo sin tope, 1001) + 4 importantes corregidos; 4 abiertos | [series.md](series.md) |
| 8 | Listado + filtros | 🔴 aud. #1: **1 bloqueante** (al cambiar de empresa muestra los comprobantes de la anterior) · 5 importantes, todos abiertos | [listado.md](listado.md) |
| 9 | Detalle + XML/PDF/CDR | 🔴 aud. #1: **3 bloqueantes** (Reenviar sin manejo de error; el PDF sin las leyendas del emisor; el PDF con los datos fiscales de hoy) · 7 importantes | [detalle.md](detalle.md) |
| 10 | Comunicación de baja | 🔴 auditoría #1 (2 auditores): 2 bloqueantes · 7 importantes, corregido (#151 + A1), pendiente recert #1 | [bajas.md](bajas.md) |
| 11 | API keys | ⬜ | — |
| 12 | Personalización PDF | ⬜ | — |
| 13 | Developers | ⬜ | — |

> **El bloqueante del PDF con los datos fiscales de hoy lo encontraron dos auditorías por separado** (flujos 6 y 9). Corregido: el PDF toma la identidad del emisor del XML firmado, así que la impresa no puede contradecir a lo que SUNAT recibió, y los comprobantes ya emitidos también quedan bien. Pendiente de recert.

## Backoffice del administrador (épica #11)

Certificación issue por issue, no por flujo: cada uno de la etiqueta `portal admin` se implementa, se prueba y se
verifica por mutación antes de abrir su PR. Detalle en [backoffice.md](backoffice.md).

| Issue | Estado |
|---|---|
| #174 Cerrar el registro público en el backend | ✅ certificado, 0/0 |

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
