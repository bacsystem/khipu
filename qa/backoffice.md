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

## #180 · Listado de cuentas con búsqueda y filtros — rebanada 1: backend

**Estado: ✅ mergeado (#209), 12/12 mutaciones verificadas — la pantalla del portal (rebanada 2) está en #211. El issue sigue abierto.**

### Diseño

`GET /v1/admin/cuentas?q=&pagina=&por_pagina=` (default 1 y 20, tope 100), `X-Total-Count` con el total que refleja la
búsqueda, más recientes primero con el `id` como desempate. Cada fila: `id`, `nombre`, `email`, `telefono`, `creada_en`,
`empresas` y `ultimo_acceso`. Un campo sin valor no aparece (la API omite los nulos), así que el portal los tipa opcionales.

- **Búsqueda `q`**, sin distinguir mayúsculas: subcadena en el correo y el nombre de la cuenta; RUC **por prefijo**
  (un fragmento interno de un RUC no identifica a nadie) y razón social por subcadena, de cualquiera de sus empresas, de modo
  que buscar por empresa devuelve su cuenta, una sola vez. Los `%` y `_` del texto buscado se toman literalmente.
- **`ultimo_acceso`** es la sesión más reciente de los usuarios de la cuenta: inicio de sesión o refresco de token **en el
  portal**. El uso por API key no cuenta; un cliente que solo integra por API tendría un valor viejo. La pantalla lo rotulará
  «Último inicio de sesión».
- **Sin estado ni plan**, a propósito (regla de columnas aprobada: una columna se entrega cuando existe la funcionalidad que la
  alimenta). «Estado» llega con #182, «sin verificar» con #22 y «plan» con #189/#191. El test `noInventaColumnasQueTodaviaNoExisten`
  lo fija.
- **Las lecturas no se auditan**: la bitácora de #178 registra acciones, no consultas.
- Una sola consulta (subconsultas correlacionadas para empresas y último acceso, `EXISTS` para la búsqueda por empresa): sin N+1
  ni cuentas repetidas. Hexagonal, con `listar` y `contar` separados como el listado de comprobantes.
- **Índices (V27)**: `ix_usuario_cuenta (cuenta_id)` e `ix_sesion_acceso (usuario_id, created_at DESC)`, que usa la subconsulta de
  `ultimo_acceso` en cada fila de la página (el máximo se lee del final del índice, sin recorrer todas las sesiones del usuario), y
  `ix_cuenta_alta (created_at DESC, id)`, que sirve el orden del listado. `tenant(cuenta_id)` ya estaba indexada (V3). `ix_sesion_acceso`
  también cubre las búsquedas solo por `usuario_id`, así que V27 retira `ix_sesion_usuario` (V3), que quedaba redundante: cada inicio de
  sesión y cada refresco habría mantenido dos índices con la misma columna inicial. La búsqueda `ILIKE '%…%'` no usa índice y recorre las cuentas: aceptable con el volumen actual; si crece, `pg_trgm`.

### Tests

- Aplicación: `ListarCuentasAdminServiceTest` (delegación, filtro nulo, normalización de `q`).
- Persistencia (Postgres): `JdbcCuentasAdminRepositoryTest`, 13 casos — orden y desempate, datos y conteo de empresas, último
  acceso (máximo entre usuarios y nulo sin sesiones), búsqueda por correo/nombre, RUC por prefijo, razón social sin duplicar cuentas,
  comodines literales, paginación con su total y los tres índices de V27 (`EXPLAIN` con `enable_seqscan = off`: comprueba que el índice
  sirve a la consulta, no que gane con pocas filas).
- REST: `AdminCuentaControllerTest` — forma de la respuesta, cabecera de total, parámetros, tope de página y ausencia de estado/plan.
- e2e (HTTP + Postgres reales): `AdminCuentasE2ETest`. **Aislamiento**: la clave de plataforma y un administrador con sesión
  leen el listado (200); sin credencial, con el JWT de un cliente del portal, con la API key de una empresa y con una clave de
  plataforma errónea responden 401.

### Verificación por mutación

| Mutación | Resultado |
|---|---|
| Orden ascendente en vez de descendente | muere (`listaDeLaMasRecienteALaMasAntigua…`, `paginaYCuentaRespetandoElFiltro`) |
| RUC por subcadena en vez de por prefijo | muere sola (`buscaElRucPorPrefijoYNoPorUnFragmentoInterno`) |
| Sin escapar los comodines de `LIKE` | muere (`losComodinesDeLikeSeTomanLiteralmente`) |
| Último acceso con `min` en vez de `max` | muere (`elUltimoAccesoEsLaSesionMasReciente…`) |
| Conteo de empresas sin filtrar por cuenta | muere (`devuelveLosDatosDeLaCuentaYCuentaSusEmpresas`) |
| Búsqueda por empresa deshabilitada (`EXISTS … AND false`) | muere (`buscaPorRazonSocial…`, `buscaElRucPorPrefijo…`) |
| El controlador no acota página ni tamaño | muere (`acotaLaPaginaYElTamanoDePagina`) |
| El total de la cabecera sale del tamaño de la página | muere (`devuelveLasCuentasConElTotalEnLaCabecera`, `pasaLaBusquedaYLaPagina…`) |
| `Filtro` sin recortar ni anular la búsqueda en blanco | muere (`laBusquedaSeRecortaYLaVaciaEsNinguna`) |
| V27 sin `ix_cuenta_alta` | muere sola (`elOrdenDelListadoSeApoyaEnUnIndice`), con 10 tests verdes |
| V27 sin `ix_sesion_acceso` | muere sola (`laSesionMasRecienteDeUnUsuarioSeLeeDelIndice…`), con los demás verdes |
| V27 sin el `DROP INDEX ix_sesion_usuario` | muere sola (`sesionTieneUnSoloIndiceSobreUsuario…`), con los demás verdes |

Las mutaciones se aplicaron en dos tandas (seis en el repositorio, tres en el controlador y el filtro) sobre tests distintos;
la del RUC por prefijo se repitió sola para comprobar que muere por sí misma, porque la del `EXISTS` mata el mismo test. Tras cada tanda se restauró
el código y se volvieron a correr `:domain`, `:application`, `:adapters:in-rest`, `:adapters:out-persistence` y, de `:bootstrap`,
`ArchitectureTest`, `AdminCuentasE2ETest`, `AuditoriaAdminE2ETest`, `AuthE2ETest`, `FacturaE2ETest` y `DeveloperPortalE2ETest`: verdes.

### Rebanada 2: la pantalla `/admin/cuentas` del portal

**Estado: ✅ mergeada (#211), 11/11 mutaciones verificadas — falta probarla de punta a punta con el portal conectado al backend.**

- **Página en el servidor** (`app/admin/(panel)/cuentas/page.tsx`): lee `q`, `pagina` y `por_pagina` de la URL y llama al backend
  con el JWT del administrador (`lib/api/admin-cuentas.ts`). **Sin react-query ni refetch desde el navegador**: el proxy genérico
  `/api/proxy` usa las cookies del cliente, no `khipu_admin_access`. Buscar, paginar y cambiar las filas por página cambian la URL
  y el servidor vuelve a renderizar; la URL es compartible.
- **No captura el 401 por separado**: el layout del panel valida la sesión del administrador (`/me`) en cada render, así que un
  token vencido ya redirigió a `/admin/login` antes de llegar a la página. Cualquier otro fallo se muestra dentro de la página,
  con un enlace para reintentar.
- **Página fuera de rango**: con `?pagina=99` (URL editada a mano o marcador viejo) el backend devuelve una página vacía con el total
  intacto y la tabla decía «Mostrando 981–980 de 12» y «Todavía no hay cuentas». `hrefSiFueraDeRango` redirige a la última página
  (conservando búsqueda y tamaño); sin resultados la última es la primera. El `redirect` va fuera del `.then`/catch de la carga, porque
  lanza y no debe confundirse con un fallo del backend.
- **Tabla** con el §12 del design system y solo piezas existentes (`Table`, `PieTabla`, `CAMPO`, `BOTON_SECUNDARIO`, y `CABECERA_TABLA`,
  nueva en `lib/estilos` para no sumar una quinta copia de la cadena de cabecera; las otras cuatro tablas la siguen repitiendo): cuenta
  (nombre y correo), teléfono, empresas, alta y «Último inicio de sesión» («Nunca» si falta; nada inventado). La búsqueda se envía
  con Enter o con el botón, sin debounce. Sin columnas de estado ni plan.
- **Navegación**: «Clientes» (placeholder) pasa a «Cuentas», con enlace; «Empresas» queda como «Pronto» hasta #185.

**Tests**
- Vitest, lógica pura (`admin-cuentas.test.ts`, 15): saneo de parámetros, query al backend, URL de la pantalla, total de la cabecera
  y la corrección de la página fuera de rango (5: pasada de la última, conserva búsqueda y tamaño, sin resultados, dentro de rango,
  total exacto de una página).
- Vitest, componente (`cuentas-tabla.test.tsx`, 10): datos y formato en hora de Lima, «Nunca» y guion, rótulo, ausencia de
  estado/plan, pie, enlaces de paginación que conservan la búsqueda, vacíos, y el envío de la búsqueda.
- Playwright con MSW (`admin-cuentas.spec.ts`, 9): sin sesión redirige; **una sesión de cliente no abre la pantalla**; llegada
  desde el menú; paginación; una página pasada de la última lleva a la última; búsqueda por razón social (la cuenta está en la
  segunda página); RUC por prefijo y no por fragmento interno; sin resultados y «Quitar filtros»; URL compartible. Usa
  `esperarHidratacion` antes de escribir en el buscador.
- El mock (`src/mocks`) sigue el contrato real del backend: solo el administrador, `q` en correo/nombre/razón social y RUC por
  prefijo, total en cabecera, campos sin valor omitidos.

**Verificación por mutación — 11/11 mueren** (las 7 primeras, una por test distinto y a la vez; las 4 de la página fuera de rango, una por una):

| Mutación | Test que muere |
|---|---|
| Página inválida (`-3`, `2.5`) pasa tal cual | `una página que no es un entero positivo…` |
| La búsqueda no se recorta | `la búsqueda se recorta y una en blanco…` |
| `q` viaja siempre al backend, aun vacía | `manda la búsqueda solo si la hay…` |
| El total ignora la cabecera `X-Total-Count` | `llama al backend con el JWT… y lee el total` |
| Los enlaces de paginación pierden la búsqueda | `la paginación conserva la búsqueda…` |
| «Nunca» se reemplaza por un guion | `una cuenta sin teléfono ni sesiones…` |
| Buscar no vuelve a la página 1 | `buscar manda a la primera página…` |
| Fuera de rango con `>=` en vez de `>` | `una página dentro de rango no se toca…` y `el total exacto de una página…` |
| Sin `Math.max(1, …)` en la última página | `una página dentro de rango no se toca…` (la primera de un listado vacío) |
| La corrección manda siempre a la página 1 | `una página pasada de la última lleva a la última…` y `conserva la búsqueda…` |
| La página no llama a `redirect` | e2e `una página pasada de la última lleva a la última…` (los otros 8 pasan) |

Vitest completo 192/192 (antes 167), `eslint` y `tsc` limpios. Playwright, tras el rebase sobre `develop` (#210): los 9 de la pantalla y
los 5 de `admin.spec.ts` pasan.

### Pendiente en #180

Nada de código. El backend (#209) y la pantalla (#211) ya están mergeados; el issue sigue abierto hasta probarlo de punta a punta
con el portal conectado al backend.

## Cabecera del backoffice: miga de ubicación, «Nueva cuenta» y menú en móvil (épica #11, sin issue propio)

**Estado: 🔧 implementada, 9/9 mutaciones verificadas — falta la revisión de la PR (#212). Necesita la ruta `/admin/cuentas`, que llegó con #211.**

Las pantallas de cliente tienen una cabecera con miga y la acción principal de la página; el backoffice no tenía ninguna.

- **Qué agrega** (`admin-top-bar.tsx`, en el layout del panel): miga «Clientes / Cuentas» (el inicio cuelga de «Backoffice»), el botón
  «Nueva cuenta» **deshabilitado** en `/admin/cuentas` con su motivo («próximamente»: crear cuentas desde el backoffice es #188), y el menú de
  navegación en móvil.
- **El backoffice no tenía navegación en móvil**: el sidebar solo se ve desde `md` y no había `MobileNav`. `admin-mobile-nav.tsx` lo abre en un `Sheet`
  y se cierra al cambiar de página.
- **La miga NO es un `h1`** (a diferencia del portal de clientes): cada página del backoffice ya trae el suyo, y dos `h1` por página empeoran la
  accesibilidad. Es un `span` con `aria-current="page"`. El e2e fija que sigue habiendo un solo `h1`.
- **Mapa de migas** en `lib/admin-migas.ts`: `/admin` casa de forma exacta (no engulle las páginas sin miga) y el resto por segmento, así que
  `/admin/cuentas/<id>` sigue bajo «Cuentas» pero `/admin/cuentas-viejas` no.
- **Sin buscador ni «Exportar» deshabilitados**, aunque el portal de clientes los tiene: serían controles muertos sin funcionalidad detrás.

**Tests**
- Vitest, lógica pura (`admin-migas.test.ts`, 6): inicio, cuentas, detalle, `/admin` exacto, prefijo parecido, fuera del backoffice.
- Vitest, componente (`admin-top-bar.test.tsx`, 6): miga y `aria-current`, ausencia de encabezados, sin miga conocida, «Nueva cuenta» deshabilitado
  con su motivo, sin acción en el inicio, botón del menú.
- Playwright con MSW (`admin-topbar.spec.ts`, 3): inicio sin acción de crear; en Cuentas, miga, botón deshabilitado y un solo `h1`; en móvil
  (390×844) el menú abre la navegación, lleva a Cuentas y se cierra solo.

**Verificación por mutación — 9/9 mueren**

| Mutación | Test que muere |
|---|---|
| Prefijo sin límite de segmento (`startsWith(ruta)`) | `un prefijo parecido no cuenta…` |
| La miga es un `h1` | `la miga no es un encabezado…` |
| «Nueva cuenta» sin `disabled` | `en Cuentas ofrece «Nueva cuenta» deshabilitado…` |
| Sin el menú móvil | `trae el botón del menú…` |
| Se ignora `exacta` (`/admin` engulle todo) | `solo es el inicio de forma exacta…` y otros 3 de migas |
| «Nueva cuenta» siempre visible | `en el inicio no hay acción de crear cuentas` |
| Sin `aria-current` | `muestra la miga… y marca la página actual` |
| El menú móvil no se cierra al navegar | e2e `el menú abre la navegación… y se cierra solo` (los otros dos pasan) |
| El layout no monta el TopBar | los 3 e2e |

Las cuatro primeras se aplicaron juntas (cuatro tests distintos); la quinta, sexta y séptima en otra tanda; las dos de e2e, una por una.
Vitest completo 204/204 (antes 192), `eslint` y `tsc` limpios. Playwright: los 3 nuevos, los 9 de cuentas y los 5 de `admin.spec.ts` pasan (17).

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
