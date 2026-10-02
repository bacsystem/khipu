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

## #178 · Bitácora de auditoría de las acciones del administrador

**Estado: 🔧 implementado, 9/9 mutaciones verificadas — falta la revisión de la PR antes de certificar.**

### Diseño

- **Dominio** (`pe.factura.domain.plataforma`): `ActorAdmin` (quién: administrador con sesión o clave de plataforma, más IP),
  `AccionAdmin` (qué) y `RegistroAuditoria` (quién + qué + cuenta/empresa opcionales + detalle + cuándo).
  La clave de plataforma (`X-Platform-Key`) no identifica a una persona: queda como `CLAVE_PLATAFORMA` sin `administrador_id`.
- **Puerto** `AuditoriaAdminRepository.registrar`, adaptador `JdbcAuditoriaAdminRepository`, tabla `auditoria_admin` (V26).
  Sin FK a administrador/cuenta/tenant: el registro de lo que pasó no depende de que el objeto siga existiendo (#201).
  La base misma rechaza un actor incoherente (`CHECK`: administrador ⇔ lleva `administrador_id`).
- **Misma transacción**: `AdministrarTenantService.crearTenant` y `CrearAdministradorService.crear` escriben el registro
  **dentro** del `UnitOfWork` de la acción. Si la bitácora falla, la acción no queda hecha.
- **Quién actúa, con fallo cerrado**: `AdminAuthFilter` marca la petición (`ATRIBUTO` del administrador o
  `ATRIBUTO_CLAVE_PLATAFORMA`) y `AdministradorActual.actor(req)` exige una de las dos; sin ninguna responde 401 en vez de
  fabricar un autor. Los casos de uso exigen el `ActorAdmin` como parámetro: no hay camino de alta sin actor.
- El `detalle` nunca lleva secretos (ni la API key del tenant ni la contraseña del administrador).

### Alcance real

Acciones auditadas hoy: **crear tenant** y **crear administrador** (las únicas acciones administrativas que existen).
Las que enumera el issue — impersonación, suspensión/reactivación, cambio de plan, cambio de entorno, revocación de API
key — **no existen todavía** (#182, #184, #187, #191): cada una agrega su valor a `AccionAdmin` y escribe su registro con
este mismo patrón al implementarse. El criterio «se registran al menos…» queda **abierto** hasta entonces.

### Tests

- Dominio: `ActorAdminTest`, `RegistroAuditoriaTest`.
- Aplicación: `AdministrarTenantServiceTest`, `CrearAdministradorServiceTest` — registro con actor/acción/empresa,
  escrito dentro de la transacción, sin secretos, ausente si la acción se rechaza, y la acción falla si falla la bitácora.
- Persistencia (Testcontainers): `JdbcAuditoriaAdminRepositoryTest` y `AuditoriaTransaccionalTest`, que prueba el
  **rollback real**: con una bitácora rota no queda ni el tenant, ni su API key, ni el administrador.
- REST: `AdminAuthFilterTest`, `AdministradorActualTest`, `AdministradorControllerTest`, `EmpresaControllerTest`.
- e2e (HTTP + Postgres reales): `AuditoriaAdminE2ETest` — clave de plataforma, sesión de administrador (login → JWT) y
  acciones rechazadas o sin credencial.

### Verificación por mutación

| Mutación | Resultado |
|---|---|
| La bitácora se escribe fuera de la transacción de la acción | muere (2 tests: `elRegistroSeEscribeDentroDeLaTransaccionDeLaAccion`, `siLaBitacoraFallaElTenantNoSeCrea`) |
| `AdminAuthFilter` no marca la petición de la clave de plataforma | muere (`claveCorrectaMarcaElRequestComoClaveDePlataforma`) |
| `ActorAdmin` sin la validación «un administrador debe tener id» | muere (`unAdministradorSinIdNoEsUnActorValido`) |
| `ActorAdmin` sin la validación «la clave de plataforma no lleva id» | **sobrevivía** → se agregó `laClaveDePlataformaNoPuedeAtribuirseAUnAdministrador`, con el cual muere |
| Tabla sin el `CHECK` de actor coherente | muere (`laBaseRechazaUnActorIncoherente`) |
| `actor()` asume «clave de plataforma» sin credencial (fallo abierto) | muere (3 tests: `sinNingunaCredencialNoSeInventaUnActor`, `sinCredencialNoCreaNadaYEs401` en administradores y en tenants) |
| El controlador de tenants ignora el actor de la petición y atribuye siempre a la clave | muere (`adminCreaTenantAtribuidoAlAdministradorQueTieneSesion`, `adminCreaTenantSinCredencialNoCreaNadaYEs401`) |
| `CrearAdministradorService` no registra | muere (5 tests, entre ellos `dejaEnLaBitacoraQuienCreoAlAdministrador` y `siLaBitacoraFallaLaAccionFalla`) |
| El `detalle` del alta de tenant incluye la API key en claro | muere (`laBitacoraNuncaLlevaLaApiKeyEnClaro`) |

Cada mutación se aplicó sola (salvo las dos últimas, en servicios y tests distintos), se corrió su test objetivo, se
anotó qué murió y se restauró el código; después se volvieron a correr `:domain`, `:application`,
`:adapters:in-rest` y `:adapters:out-persistence` sin mutaciones, verdes.

### Suites

`:domain`, `:application`, `:adapters:in-rest` y `:adapters:out-persistence` completos, y de `:bootstrap`:
`ArchitectureTest`, `AuditoriaAdminE2ETest`, `FacturaE2ETest`, `AuthE2ETest` — verdes. El resto de módulos (`out-ubl`,
`out-sunat-soap`, `out-signing`, `out-crypto`, `out-mail`, `out-pdf`, `in-scheduler`, `out-storage` salvo MinIO) — verdes.
**No ejecutables cuando se hizo esta ficha**: `S3DocumentStorageTest` y `FacturaS3E2ETest`, porque Testcontainers no podía descargar
`quay.io/minio/minio:latest` (el primero se colgaba en la descarga; el segundo fallaba con `Can't get Docker image`). MinIO había
retirado sus imágenes públicas; ambos tests usan ahora `cgr.dev/chainguard/minio` y pasan (ver #210).

### Limitaciones conocidas

- **IP**: se registra `getRemoteAddr()`. Detrás de un proxy o del BFF del portal será la de ese salto hasta configurar qué
  proxies son de confianza (`server.forward-headers-strategy`) y que el BFF reenvíe `X-Forwarded-For`. Hoy el único
  llamador de `/v1/admin/**` es ops con la clave de plataforma, directo; hay que resolverlo antes de que el backoffice del
  portal ejecute acciones.
- **No se audita el login** del administrador (no es una acción sobre cuentas); se decide con #177 (2FA).
- **No es de solo-anexar a nivel de base**: nada impide un `UPDATE`/`DELETE` manual sobre `auditoria_admin`.
- **Sin consulta**: no hay endpoint ni pantalla para leer la bitácora; es un issue aparte («consultable y exportable»).

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
