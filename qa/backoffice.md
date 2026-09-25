# Backoffice del administrador (épica #11)

Certificación issue por issue, no por flujo: cada uno de la etiqueta `portal admin` se implementa, se prueba y se
verifica por mutación antes de abrir su PR — ver [`README.md`](README.md#backoffice-del-administrador-épica-11).

## #175/#176/#179 · Concepto de PLATFORM_ADMIN, JWT propio y cáscara del panel

Slice mínimo real, no cosmético: sin esto un guard en `/admin` solo podría apoyarse en `Rol.ADMIN` de
`pe.factura.domain.cuenta`, que es **por cuenta de cliente** — el propio #175 exige que un administrador de la
plataforma no pertenezca a ninguna cuenta. 2FA, auditoría, CRUD de administradores y el resto de secciones del
panel quedan fuera a propósito (#177, #178, #180+).

### Diseño

- **Dominio nuevo** `pe.factura.domain.plataforma.Administrador`: sin FK a `cuenta`/`tenant`, sin depender del
  paquete `cuenta` (ni siquiera para reusar `normalizarEmail`/`validarPassword` — se duplican a propósito: son dos
  conceptos que no deben acoplarse).
- **JWT propio** (`AdministradorTokenEmisor` / `JwtAdministradorTokenEmisor`): mismo `JWT_SECRET` que el JWT de
  cliente (un secreto más no compensa el riesgo operativo de gestionar dos), pero con claim `tipo=plataforma`
  exigido explícitamente en la verificación — un JWT de cliente nunca decodifica como uno de administrador, y
  viceversa. Sin refresh: 30 min y a volver a iniciar sesión (#177 define la política de sesión definitiva).
- **`/v1/admin/**`** (`AdminAuthFilter`, antes `PlatformKeyFilter`): acepta `X-Platform-Key` (ops) **o** el JWT del
  backoffice — mismo patrón "cualquiera de las dos" que ya usan las rutas de tenant con `X-Api-Key`/JWT+`X-Empresa`.
  `POST /v1/admin/auth/login` es la única ruta admin sin credencial (el administrador aún no tiene ninguna).
- **Alta de administradores** (`POST /v1/admin/administradores`): sin registro público — la crea quien ya tiene
  `X-Platform-Key` (bootstrap) o un administrador ya autenticado.
- **Portal**: `/admin/login` (público, formulario propio) + `/admin/(panel)` (guardia real: sin refresh, si
  `GET /v1/admin/auth/me` falla con el access de la cookie `khipu_admin_access`, redirige a `/admin/login`) + shell
  de navegación reusando el design system existente (`Menu`, `ThemeToggle`, `LogoMarca`, mismas clases que el
  sidebar de tenant) con placeholders "Pronto" para las secciones que llegan en #180+.

### Hallazgo de diseño verificado con mutación

Al mutar la verificación del JWT de administrador quitando el `.withClaim("tipo", "plataforma")`, la suite
**no se rompía**: el chequeo independiente de `email` nulo ya rechazaba un JWT de cliente (que no trae ese claim),
así que el claim `tipo` quedaba sin test que lo aislara. Se agregó `unTokenConEmailPeroSinTipoPlataformaNoVerifica`
(un JWT forjado con `email` pero sin `tipo=plataforma`) — con ese test, la misma mutación muere.

### Tests

- Dominio: `AdministradorTest` (email/password, igual que `UsuarioTest`).
- Aplicación: `AutenticarAdministradorServiceTest`, `CrearAdministradorServiceTest` (fakes locales).
- Crypto: `JwtAdministradorTokenEmisorTest`, incluyendo el aislamiento cruzado cliente↔administrador en ambos
  sentidos.
- Persistencia: `JdbcAdministradorRepositoryTest` (Testcontainers).
- REST: `AdminAuthControllerTest`, `AdministradorControllerTest`, `AdminAuthFilterTest` (renombrado de
  `PlatformKeyFilterTest`, con los casos originales intactos más el camino JWT y la excepción de `/auth/login`).
- Portal: Vitest de las dos route handlers (`login`/`logout`) + **e2e real** `admin.spec.ts` contra el mock: sin
  sesión redirige a `/admin/login`, login válido llega al shell, credenciales inválidas no salen del login, logout
  vuelve a exigir login, y **una sesión de cliente (tenant) no abre el backoffice** — la prueba de que el guard es
  real y no cosmético.

### Verificación por mutación

| Mutación | Resultado |
|---|---|
| `AdminAuthFilter`: `claims.isPresent()` forzado a `true` (aceptar cualquier JWT admin, válido o no) | muere |
| `JwtAdministradorTokenEmisor`: quitar `.withClaim("tipo", "plataforma")` de la verificación | muere (tras agregar el test de aislamiento; sobrevivía antes) |

### Suites

Backend: `./gradlew test` completo, 0 fallos (incluye `ArchitectureTest`, sin nuevas violaciones de dependencia).
Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 165/165 · Playwright e2e completo (con mocks), incluido
`admin.spec.ts`.

**Estado**: 0 bloqueantes, 0 importantes. Pendiente de recert con contexto limpio sobre la rama mergeada.
