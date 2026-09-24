# Backoffice del administrador (épica #11) · certificación por issue

Cada issue del backoffice (etiqueta `portal admin`) se certifica en el momento: implementar → probar → verificar por
mutación → 0 bloqueantes / 0 importantes antes de abrir la PR. No se acumula una ronda de auditoría al final.

Leyenda: ✅ certificado (0/0, mutación verificada) · 🔧 corregido, a la espera de recert · ⬜ abierto.

## #174 · Cerrar el registro público en el backend y en el portal

**Estado: ✅ certificado, 0/0.**

Dos hallazgos, uno de cada lado — el issue empezó como "cerrar el backend" y terminó siendo "cerrar las dos puertas
al backend", porque la segunda apareció al verificar si con la primera alcanzaba.

### 1. El backend aceptaba altas directas

**Hallazgo**: el registro solo estaba cerrado en el portal (`/api/auth/registro` del BFF). El backend
(`POST /v1/auth/registro`) aceptaba altas de cualquiera que lo llamara directo, sin invitación. La beta cerrada no
lo era.

**Arreglo**: `AuthController` recibe `app.registro-abierto` (`@Value`, default `false`) y rechaza con
`403 REGISTRO_CERRADO` antes de llamar al caso de uso. Mismo código y mismo mensaje que ya usa el portal
(«El registro está por invitación: escríbenos para pedir acceso.»), para que la experiencia no cambie según por
dónde entre la petición. `REGISTRO_CERRADO` se mapea a 403 en `GlobalExceptionHandler`.

La propiedad va en `application.yml` con default `false` (cerrado de fábrica): el backend no tiene noción de
"entorno de desarrollo" como el portal (`NODE_ENV`), así que fallar cerrado es la única opción segura.

**Por qué está en el módulo REST y no en `AutenticarUsuarioService`**: `application` no puede depender de Spring
(`ArchitectureTest.applicationNoDependeDeAdaptadores`), así que un flag de configuración de despliegue no puede
vivir ahí. Mismo patrón que `portalUrl`, que ya llega al controlador por `@Value` y no al servicio.

**Tests**:
- `AuthControllerTest` (existente): ahora declara `@TestPropertySource(properties = "app.registro-abierto=true")`
  explícito, porque asume que el registro funciona.
- `AuthControllerRegistroCerradoTest` (nuevo): **no** declara la propiedad a propósito, para probar el default real
  de fábrica, no un valor puesto a mano. Verifica 403, el código, el mensaje exacto, y que el caso de uso ni se
  llega a invocar (`verifyNoInteractions`).
- `AuthE2ETest` (Testcontainers) y el resto de e2e con `@ActiveProfiles("test")` siguen registrando cuentas: la
  propiedad se puso explícita en `application-test.yml` porque esos flujos necesitan el registro abierto.

**Mutaciones — 4/4 mueren, el no-op sobrevive**:

| Mutación | Resultado |
|---|---|
| El default de fábrica pasa de cerrado a abierto | muere |
| La guarda invertida (rechaza abierto, deja pasar cerrado) | muere |
| El código de error cambiado | muere |
| Sin mapeo a 403 (cae al 422 por defecto) | muere |
| No-op (control) | sobrevive |

**Suites**: `:adapters:in-rest:test` completo, y `./gradlew test` (todos los módulos, incluido `:bootstrap:test`
con Testcontainers y `ArchitectureTest`) — verdes, ejecutados desde cero (`--rerun-tasks`), no desde caché.

**Documentación**: `deploy/README.md` y `deploy/frontend/README.md` explican que la misma variable
`REGISTRO_ABIERTO` hay que ponerla en **los dos servicios** — cierran rutas independientes, no se enteran una de
la otra. `.env.example` la deja en `true` para desarrollo local.

### 2. El proxy genérico del portal saltaba el cierre del propio portal

**Hallazgo**: al verificar si cerrar el backend bastaba, apareció un bypass independiente y anterior a este issue.
`portal/src/app/api/proxy/[...path]/route.ts` reenvía cualquier `/v1/**` al backend, y **no exige sesión**:
`middleware.ts` solo protege las páginas privadas, no `/api/proxy/**`. Un `POST /api/proxy/auth/registro` sin
ninguna cookie llegaba directo a `/v1/auth/registro` del backend, **sin pasar** por `/api/auth/registro`, que es
donde vivía (y sigue viviendo) el chequeo de «registro cerrado» del portal. El candado de la página no protegía
nada si alguien llamaba a la ruta genérica en vez de a la página.

Verificado que era real y no solo teórico: `esApiV1`/`esPublica` del backend confirman que `/v1/auth/**` no exige
JWT (por diseño: no estás logueado todavía), así que nada del lado del backend bloqueaba esa llamada — hasta el
arreglo del punto 1, que cierra el mismo hueco **como efecto colateral**, sin que el portal supiera que lo estaba
tapando. Sin el arreglo del backend, este bypass creaba cuentas igual, registro-cerrado del portal o no.

**Arreglo**: el proxy genérico rechaza con `404` cualquier ruta que empiece con `auth` antes de reenviar nada.
Confirmado por grep que ninguna parte del portal llama a `/api/proxy/auth/*` — los endpoints de auth ya tienen su
propia ruta dedicada (`/api/auth/login`, `/registro`, `/recuperar`, `/restablecer`; el refresh no necesita una
porque solo lo llama el propio servidor, nunca el navegador). Bloquear el prefijo entero cierra la *clase* de
bypass, no solo el caso puntual del registro: mañana cualquier otra regla de negocio que viva en un wrapper de
`/api/auth/*` queda protegida por el mismo candado, sin tener que acordarse de repetirlo.

**Tests**: dos casos nuevos en `route.test.ts` — `auth/registro` y `auth/login` — comprobando que el `fetch` al
backend **nunca se llama** (no solo que el status sea el esperado; eso es lo que distingue "se bloqueó antes de
reenviar" de "se reenvió y el backend contestó 404 por otra razón").

**Mutaciones — 3/3 mueren, el no-op sobrevive**:

| Mutación | Resultado |
|---|---|
| La guarda nunca se activa (`return false`) | muere |
| La guarda solo mira `auth/registro`, no todo `auth/*` (dejaría pasar `auth/login`) | muere |
| El chequeo se quita del flujo y sigue reenviando igual | muere |
| No-op (control) | sobrevive |

**Suites**: Vitest completo del portal (164/164) y Playwright completo (98/98, incluidos login, registro y
onboarding de punta a punta) — nada se rompió, porque nada legítimo usaba esa ruta. `tsc` y ESLint limpios.

**Nada queda pendiente de este issue.**
