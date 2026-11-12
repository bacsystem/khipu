# QA — sesión del portal: reuso de refresh (#635), corte por inactividad (#636) y aviso en el cliente (#637)

Validado sobre `origin/develop` a6bf39e8d (incluye #641–#647), 2026-11-12. Plano: usuarios del tenant (tabla `sesion`, `/v1/auth/refresh`); el backoffice queda fuera.

## Qué se corrió

| Suite | Resultado |
|---|---|
| `npm run typecheck` (portal) | sin salida de error |
| `npm run lint` (portal) | sin salida de error |
| `npm run test` (Vitest, portal) | 283 archivos, 2815 tests, todos verdes |
| `:application:test --tests "*AutenticarUsuarioServiceTest*"` | BUILD SUCCESSFUL (67 tests) |
| `npm run e2e -- e2e/sesion-inactividad.spec.ts` (Playwright, backend simulado) | 5 de 6 verdes; ver defecto D1 |
| Suite completa del backend y `ArchitectureTest` | **no verificadas por qa en esta sesión**, por indicación del tech-lead (pasaron en las PR según sus autores). `gh pr checks` de #641/#643/#644/#646/#647 no reporta checks: no hay CI que lo respalde |

## Prueba de punta a punta con el stack real

Postgres propio (`khipu-qa`, puerto 5434) + backend de `develop` por `bootRun` con secretos desechables + portal `npm run dev` contra ese backend. Se manejó el BFF del portal con `curl` y un frasco de cookies (no hay navegador disponible para qa en esta sesión: lo visual del aviso lo cubren Vitest y el spec de Playwright).

| Paso | Resultado observado |
|---|---|
| Registro por el portal → refresh A. Un «atacante» rota A→B contra el backend; se vence la gracia (`rotada_en = now() - 31 s`); la víctima navega a `/inicio` sin access (vencido) | `307 /login?next=%2Finicio`, las 6 cookies (`khipu_*` y `factura_*`) con `Max-Age=0`; en la base las dos filas de la familia quedan `revocada`; una línea `WARN Refresh reutilizado fuera de la gracia… (usuario=…, familia=…)` |
| `/login` tras la redirección | `200`, formulario con correo y contraseña, sin «Application error» |
| Login con contraseña tras la revocación | `200`, familia nueva (`familia_id` distinto) |
| Mismo caso por `/api/proxy/empresas` con access inválido y refresh reutilizado | `401 NO_AUTORIZADO` y cookies limpiadas (ver observación O1) |
| Login → envejecer la fila a 44 min → refresh por el middleware | rota (nueva fila) |
| Envejecer la fila nueva a 46 min → `/inicio` sin access | `307 /login?next=%2Finicio`, cookies limpiadas, familia entera `revocada`, una línea `INFO Sesión cerrada por inactividad (usuario=…, familia=…)` |
| Refresh copiado de esa familia contra el backend tras el corte | `401 SESION_INVALIDA`, sin segunda línea de log |
| `grep` de los refresh usados en el log del backend | 0 apariciones |
| `GET /v1/auth/me` con sesión normal | trae `sesion_inactividad_minutos: 30` |
| `POST /api/auth/sesion`: access vigente / sin cookies / access vencido con refresh vivo / refresh revocado | `204` / `401 NO_AUTORIZADO` / `204` con cookies nuevas / `401 SESION_INVALIDA` con cookies limpiadas |
| `/login?motivo=inactividad` (es y en); `motivo=otro`; `motivo=<b>x` | el aviso se pinta solo con el literal `inactividad`; con otro valor no, y el valor nunca se refleja |

## Dos pestañas y refresh muerto en el navegador (Playwright, backend simulado, spec desechable no commiteado)

| Prueba | Resultado |
|---|---|
| T1: A y B abiertas; la marca `ultimaActividad` a N − 1 min; evaluar | el aviso aparece en las dos. Marca a N + 1 min y evaluar en A: A y B terminan en `/login?motivo=inactividad` con el texto en pantalla; `/inicio` ya redirige al login |
| T2: «Seguir conectado» en A | el aviso de B se cierra (≤ 15 s). Con A a N − 1 min y una tecla en A, B no avisa y sigue en `/inicio` |
| T3: «Cerrar sesión» desde el menú de A | A va al login. **B se queda en `/inicio`** (12 s después seguía allí, con las cookies ya limpias); al recargar va a `/login?next=%2Finicio`. Ver observación O3 |
| T4: acción de cliente (Generar llave) con el proxy respondiendo `401 NO_AUTORIZADO` y las cookies limpiadas (lo que devolvió el proxy real con curl) | el diálogo muestra «No autorizado.» y la persona sigue en `/api-keys`; la siguiente navegación va a `/login?next=...`. Ver O1 |

## Mutaciones verificadas (servicio), cada una por separado

Aplicadas a mano sobre `AutenticarUsuarioService` y revertidas con `git checkout` (el archivo estaba commiteado):

1. `rechazarRevocada` sin `revocarFamilia`: 3 de 67 fallan: `reusarUnRefreshRotadoFueraDeLaGraciaRevocaLaFamiliaEntera`, `conLaFilaRaizPurgadaElReusoRevocaLaFamiliaCompletaIncluidaLaOtraRama` y `unRefreshRotadoEInactivoSigueElCaminoDelReuso`.
2. `estaInactiva` con `<` en vez de `<=` (frontera): 2 de 67 fallan: `justoAntesDelCorteElRefreshRotaYEnElCorteExactoCierraLaFamilia` y `unaCuentaSuspendidaEInactivaDaSesionInvalidaYNoCuentaSuspendida`.

Con el archivo restaurado: BUILD SUCCESSFUL (67 tests).

## Hallazgos

- **D1 (portal, `e2e/sesion-inactividad.spec.ts:76`, dueño: frontend-dev)**: el test «con interacción constante el keep-alive no pasa de uno cada 5 minutos» falla (8 keep-alives, máximo 3). Lo observado: el spec salta 60 s de reloj virtual (`page.clock.fastForward`) mientras el primer `POST /api/auth/sesion` está en vuelo; la petición termina `net::ERR_ABORTED`, `pedirKeepAlive` devuelve `fallo` y `tocarElServidor` reintenta a los 30 s, así que sale uno en cada minuto virtual. Con una espera real de 1,5 s entre la tecla y el salto, el mismo escenario da dos keep-alives en 12 min (minutos 5 y 10, respuesta 204): el producto cumple el techo. Probable causa (no probada): `AbortSignal.timeout(30 s)` siguiendo el reloj falso. Pasos: `npm run e2e -- e2e/sesion-inactividad.spec.ts`. Hay que arreglar el spec antes de la pasada de e2e del release.
- **P1 (para el po)**: el reintento a los 30 s tras un keep-alive fallido (`REINTENTO_TRAS_FALLO_MS`) es una tercera excepción al «techo de 1 cada 5 min» que el criterio de #637 no lista (solo habla de «Seguir conectado» y de dos pestañas simultáneas). Es razonable, pero está sin escribir en el criterio.
- **O1 (observación, ya existía antes; el po decide si pide issue)**: cuando una acción del navegador pasa por `/api/proxy/**` y el refresh ya murió, el proxy limpia las cookies y devuelve el 401 original (`NO_AUTORIZADO`, no `SESION_INVALIDA`); el diálogo muestra «No autorizado.» y la persona se queda en la página hasta la siguiente navegación, que va al login. No hay pantalla rota ni error no controlado, pero tampoco redirección inmediata. El sondeo de `comprobantes-table` tampoco muestra error: react-query conserva las filas anteriores. No hay un manejo global de 401 en `src/lib/api/browser.ts`.
- **O3 (observación)**: el cierre de sesión desde el menú de A no lleva a B al login: solo el cierre del vigilante («Cerrar sesión» del aviso o corte por inactividad) avisa por `BroadcastChannel`. B se queda con datos viejos hasta su próxima navegación. El criterio de #637 dice «un cierre o un "Seguir conectado" se propaga a las demás»; es ambiguo si incluye el menú. Decisión del po.
- **O2 (documentación)**: `qa/backoffice.md:1451` y `:1466` decían que al reactivar una cuenta «la misma sesión vuelve a servir»; desde #636 solo vale dentro de N + 15 min. Quedó corregido en este cambio. `:1477` (`JwtFilterTest`) y los E2E de `SuspensionE2ETest` siguen siendo ciertos (miran el access y no dejan pasar el tiempo).
