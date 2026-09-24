# Flujo 4 · Login / recuperar / restablecer

Auditoría independiente (contexto limpio, worktree propio, `E2E_PORT` propio, `API_BASE_URL` a puerto muerto) sobre `main@472f9d4`.
**Veredicto de la auditoría #1: NO CERTIFICADO — 1 bloqueante, 7 importantes.**

Leyenda: 🔧 corregido, a la espera de recert · ✅ verificado por una recert posterior · ⬜ abierto, en [pendientes.md](pendientes.md).

## Bloqueantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | **Dos sesiones distintas refrescando a la vez comparten tokens: el segundo usuario queda dentro de la sesión del primero.** El single-flight del refresh ignoraba su argumento y devolvía la promesa en curso fuera quien fuera. Reproducido: la cookie de B termina con el access de A. El test que existía mandaba el **mismo** refresh en las dos peticiones, así que tapaba el hueco | `app/api/proxy/[...path]/route.ts` | mapa de refrescos en curso **con el refresh como clave**; test con dos refresh distintos solapados |

## Importantes

| Estado | Hallazgo | Dónde | Arreglo |
|---|---|---|---|
| 🔧 | Open redirect: `?next=/%0A/evil.com` pasaba el chequeo de prefijo, y el navegador descarta el salto de línea al parsear la URL → navega a `//evil.com`, protocolo-relativo, fuera del portal | `lib/ruta-segura.ts` | se rechaza todo el rango de control y la barra invertida en cualquier posición |
| 🔧 | Corte de red silencioso: el botón quedaba en «Ingresando…»/«Enviando…» para siempre, sin aviso. La raíz era el cliente compartido, que lanzaba en vez de devolver un sobre de error (afectaba a los 23 formularios, no solo a los de login) | `lib/api/browser.ts` | ni `fetch` ni el `JSON.parse` lanzan: devuelven sobre `RED` / `RESPUESTA_INVALIDA`, con mensaje |
| 🔧 | `/establecimientos` estaba fuera del matcher del middleware: al usuario lo expulsaba al login a los 15 minutos con la sesión viva, porque nadie refrescaba el access antes de renderizar | `middleware.ts` | ruta agregada + test que compara el matcher contra los directorios de `app/(privado)` |
| ⬜ | Enumeración de cuentas por tiempo de respuesta (324 ms con bcrypt vs ~1–5 ms cuando el correo no existe) | backend | hash dummy en la rama de correo inexistente |
| ⬜ | Sin rate limiting en login, recuperar ni restablecer | backend | trabajo propio, ver pendientes |
| ⬜ | Logout no revoca el refresh en el backend cuando el access ya venció | `api/auth/logout` | revocar por refresh, no por access |
| ⬜ | Paridad del mock: no verifica firma ni `exp` del JWT, no tiene handler de `/v1/auth/restablecer`, y compara el correo respetando mayúsculas | `src/mocks/handlers.ts` | ver pendientes |

## Verificación de los arreglos

Mutaciones, con `returncode` directo (sin pipes) y un no-op de control que sobrevive:

| Mutación | Resultado |
|---|---|
| Single-flight sin clave (compartir la promesa en curso) | muere |
| `rutaSegura` sin el filtro de caracteres de control | muere |
| `rutaSegura` solo con la barra invertida al inicio | muere |
| Matcher sin `/establecimientos` | muere |
| No-op (control) | sobrevive |

Suites: Vitest 150/150 · `tsc` y ESLint limpios.

**Pendiente**: recert #1 con contexto limpio sobre la rama mergeada.
