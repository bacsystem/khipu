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

**Estado: 🔧 implementada, 11/11 mutaciones verificadas — falta la revisión de la PR (#212). Necesita la ruta `/admin/cuentas`, que llegó con #211.**

Las pantallas de cliente tienen una cabecera con miga y la acción principal de la página; el backoffice no tenía ninguna.

- **Qué agrega** (`admin-top-bar.tsx`, en el layout del panel): miga «Clientes / Cuentas» (el inicio cuelga de «Backoffice»), el botón
  «Nueva cuenta» **deshabilitado** en `/admin/cuentas` con su motivo («próximamente»: crear cuentas desde el backoffice es #188), y el menú de
  navegación en móvil.
- **El backoffice no tenía navegación en móvil**: el sidebar solo se ve desde `md` y no había `MobileNav`. `admin-mobile-nav.tsx` lo abre en un `Sheet`
  y se cierra al cambiar de página.
- **La miga NO es un `h1`** (a diferencia del portal de clientes): cada página del backoffice ya trae el suyo, y dos `h1` por página empeoran la
  accesibilidad. Es un `span` con `aria-current="page"`. El e2e fija que sigue habiendo un solo `h1`.
- **Mapa de migas** en `lib/admin-migas.ts`: `/admin` casa de forma exacta (no engulle las páginas sin miga) y el resto por segmento, así que
  `/admin/cuentas/<id>` sigue bajo «Cuentas» pero `/admin/cuentas-viejas` no. La **acción** de la página («Nueva cuenta») también vive en
  el mapa y solo se devuelve con la ruta exacta de la lista, no en un detalle: así la cabecera no conoce rutas (antes comparaba contra el
  literal `"/admin/cuentas"`, una cuarta copia).
- **Sin buscador ni «Exportar» deshabilitados**, aunque el portal de clientes los tiene: serían controles muertos sin funcionalidad detrás.

**Tests**
- Vitest, lógica pura (`admin-migas.test.ts`, 6): inicio, cuentas con su acción, detalle sin la acción, `/admin` exacto, prefijo parecido,
  fuera del backoffice.
- Vitest, componente (`admin-top-bar.test.tsx`, 7): miga y `aria-current`, ausencia de encabezados, sin miga conocida, «Nueva cuenta» deshabilitado
  con su motivo, sin acción en el detalle de una cuenta, sin acción en el inicio, botón del menú. La prop `administrador` va tipada
  (`Administrador`), no con `as never`.
- Playwright con MSW (`admin-topbar.spec.ts`, 3): inicio sin acción de crear; en Cuentas, miga, botón deshabilitado y un solo `h1`; en móvil
  (390×844) el menú abre la navegación, lleva a Cuentas y se cierra solo.

**Verificación por mutación — 11/11 mueren**

| Mutación | Test que muere |
|---|---|
| Prefijo sin límite de segmento (`startsWith(ruta)`) | `un prefijo parecido no cuenta…` |
| La miga es un `h1` | `la miga no es un encabezado…` |
| «Nueva cuenta» sin `disabled` | `en Cuentas ofrece «Nueva cuenta» deshabilitado…` |
| Sin el menú móvil | `trae el botón del menú…` |
| Se ignora `exacta` (`/admin` engulle todo) | `solo es el inicio de forma exacta…` y otros 3 de migas |
| «Nueva cuenta» en cualquier página con miga | `en el inicio no hay acción…` y `en el detalle de una cuenta conserva la miga…` |
| El mapa devuelve la acción también en el detalle | `el detalle de una cuenta… sin la acción de la lista` y el de componente |
| Sin `aria-current` | `muestra la miga… y marca la página actual` |
| El menú móvil no se cierra al navegar | e2e `el menú abre la navegación… y se cierra solo` (los otros dos pasan) |
| El layout no monta el TopBar | los 3 e2e |

Las cuatro primeras se aplicaron juntas (cuatro tests distintos); `exacta` y `aria-current` en otra tanda; «cualquier página con miga»
(antes «siempre visible», que se probó en esa tanda), la del mapa de la acción y las dos de e2e, una por una.
Vitest completo 205/205 (antes 192), `eslint` y `tsc` limpios. Playwright: los 3 nuevos, los 9 de cuentas y los 5 de `admin.spec.ts` pasan (17).

## #208 · IP real del administrador en la bitácora, detrás del BFF y del proxy

**Estado: 🔧 implementado, 16/16 mutaciones verificadas y cadena probada en Docker — falta la revisión de la PR. No se calibró en Railway
(sin acceso desde aquí): queda el mecanismo y el procedimiento (`deploy/README.md` §5), y los valores reales los fija quien despliega.**

### Diseño

Dos mitades que van juntas, **ninguna confía en nadie por defecto**:

- **Portal** (`lib/ip-cliente.ts`, `lib/origen.ts`): `TRUSTED_PROXY_HOPS` (0 por defecto) cuenta cuántos proxies de confianza hay delante.
  Se lee `X-Forwarded-For` **desde la derecha** y solo cuentan las últimas `saltos` entradas; lo de la izquierda lo controla el navegador y
  nunca se mira. Al backend sale **una sola IP ya resuelta**, nunca la cadena. Con 0 saltos, cadena más corta o entrada inválida, no sale nada.
  Lo pasan explícitamente las rutas del BFF que lo necesitan: el proxy de clientes, el login del administrador y la ruta de calibración.
- **Backend** (`ProxyDeConfianzaConfig`): `TRUSTED_PROXIES` (vacío por defecto) es una regex de proxies de confianza. Vacía, no se instala nada.
  Con valor, un `RemoteIpValve` de Tomcat reescribe la IP solo cuando el par casa, leyendo desde la derecha; `X-Forwarded-Proto` queda
  desactivado. Una regex inválida impide arrancar. `AdministradorActual` no cambia: `getRemoteAddr()` ya es la IP correcta.
- **Calibración**: `GET /v1/admin/origen` (backend, solo lectura, administrador o clave de plataforma) y `GET /api/admin/origen` (portal, solo
  con sesión de administrador) ponen lado a lado la cadena recibida, la IP resuelta y la que el backend registraría.

### Hallazgos que cambiaron la implementación

- **Railway no se comporta igual según la fuente** (hilos del foro, no documentación): un empleado dice que el borde borra el `X-Forwarded-For`
  del cliente, otro que es lo más fiable, y usuarios dicen que agrega sin borrar. De ahí el número de saltos configurable y la calibración.
- **Next solo rellena `X-Forwarded-For` si falta** (`base-server.js:568`, `??=`): con una falsa del navegador y sin proxy, la conserva.
- **El `RemoteIpValve` trae `X-Forwarded-Proto` activo por defecto** (lo vio el test, no lo supuse): se desactiva para tocar solo la IP.
- **No se puede inyectar `next/headers` en `client.ts`**: varios componentes de cliente importan valores de módulos que lo importan, y el
  build de producción de Next lo rechaza. Vitest, `tsc` y `eslint` no lo detectan; solo `next build`. Por eso la IP se pasa explícitamente.
- **Una mutación sobrevivió** (P4, abajo) y destapó un defecto: un `,` suelto a la izquierda anulaba la lectura, y eso habría permitido
  **borrar la propia IP de la bitácora**. Ahora solo se valida lo que escribieron los proxies de confianza.
- **Una misma IPv6 se guardaba con dos grafías** (hallazgo de la revisión de la PR): el portal reenvía la forma comprimida
  (`2001:db8::1`) y la JVM da `getRemoteAddr()` sin comprimir (`2001:db8:0:0:0:0:0:1`), así que `WHERE ip = '2001:db8::1'` se perdía las
  filas de las llamadas directas. `ActorAdmin.normalizarIp` la deja siempre en la forma de la JVM (y una IPv4 mapeada en su IPv4), en un
  solo sitio —el constructor del record—, y el endpoint de calibración la muestra igual para que se calibre contra lo que se guardará.
  Sin `InetAddress.getByName`: ante un texto de apariencia inválida consulta el DNS, y esto está en la ruta de auditoría.

### Tests

- Backend: `ActorAdminTest` (12, 7 de ellos de la normalización de la IP), `ProxyDeConfianzaConfigTest` (4), `AdminOrigenControllerTest` (3)
  y dos e2e con HTTP real, donde la válvula participa de verdad:
  `OrigenAdminConProxyE2ETest` (6: la IP reenviada llega a la bitácora, una IP falsa a la izquierda no gana, el endpoint de calibración,
  una IPv6 reenviada queda en la misma grafía que una conexión directa, sin cabecera, y el aislamiento) y `OrigenAdminSinProxyE2ETest`
  (4: la cabecera falsificada se ignora, también la cadena y el endpoint, y el aislamiento).
- Aislamiento de `GET /v1/admin/origen` (en la base de ambos e2e, así que corre con y sin proxy): 401 sin credencial, solo con
  `X-Forwarded-For`, con el JWT de un cliente, con una API key y con una clave de plataforma equivocada; 200 con la correcta.
- Portal: `ip-cliente.test.ts` (24), `origen.test.ts` (5) y casos nuevos en el proxy (2), el login del administrador (2) y la ruta de calibración (5).

### Verificación por mutación — 16/16 mueren

| Lado | Mutación | Qué muere |
|---|---|---|
| Backend | La válvula no se instala nunca | 5 (2 unitarios y los 3 e2e con proxy) |
| Backend | La válvula se instala aunque no haya nada configurado (hereda los rangos privados de Tomcat) | 4 (1 unitario y los 3 e2e sin proxy) |
| Backend | La válvula sin `internalProxies` propio | 2 unitarios |
| Backend | El endpoint devuelve la cabecera cruda en vez de la IP resuelta | `noDevuelveNadaMasQueLaIp` |
| Portal | Se lee la entrada de la izquierda, no la de la derecha | 5 |
| Portal | 0 saltos devuelve IP | 3 |
| Portal | `cabecerasDeOrigen` reenvía la cadena cruda del navegador | 5 (helper y las tres rutas) |
| Portal | Sin comprobar el rango de los octetos (`999.1.1.1`) | `octeto fuera de rango` |
| Portal | Sin descartar entradas vacías entre las de confianza | `una entrada vacía entre las que escribieron…` (**sobrevivió la primera vez**: el test no discriminaba) |
| Portal | El proxy de clientes no reenvía la IP | `manda al backend la IP de confianza resuelta…` |
| Portal | El login del administrador no pasa la IP | `manda al backend la IP de confianza ya resuelta…` |
| Portal | La ruta de calibración sin comprobar sesión | `sin sesión de administrador responde 401…` |
| Backend | `AdminAuthFilter` deja pasar `/v1/admin/origen` sin credencial | `elEndpointDeCalibracionSoloLoLeeQuienEsAdministrador`, en las dos clases e2e |
| Backend | `ActorAdmin` no normaliza la IP | 3 unitarios y el e2e de la IPv6 reenviada |
| Backend | El endpoint de calibración no normaliza | `muestraUnaIpv6EnLaMismaFormaQueLaBitacora` |
| Backend | Sin tratar la IPv4 mapeada en IPv6 | `unaIpv4MapeadaEnIpv6EsLaIpv4` |

### Cadena completa en Docker (código final, proyecto desechable)

Navegador → portal → backend → Postgres, con `TRUSTED_PROXIES` cubriendo la red del compose:

| Escenario | `ip_resuelta` | `ip_backend` |
|---|---|---|
| Sin cabecera: Next la rellena con la IP de la conexión (`172.19.0.1`) | `172.19.0.1` | `172.19.0.1` |
| `6.6.6.6, 203.0.113.7` con 1 salto (como un proxy que agrega) | `203.0.113.7` | `203.0.113.7` |
| `6.6.6.6` con 1 salto y **sin proxy real delante** | `6.6.6.6` | `6.6.6.6` (límite conocido) |
| Comas sueltas a la izquierda: `, ,203.0.113.7` | `203.0.113.7` | `203.0.113.7` |
| Portal con **0 saltos**, cabecera falsa del navegador | `null` | IP del portal en la red (`172.19.0.2`) |
| Portal con 1 salto pero **backend sin proxies de confianza** | `203.0.113.7` | IP del portal (`172.19.0.2`) |

### Límites conocidos

- **`TRUSTED_PROXY_HOPS` mayor que 0 sin un proxy real delante del portal permite falsificar la IP** (tercera fila). Es un ajuste del operador,
  documentado en `deploy/README.md` §5.
- **Sin calibrar en Railway.** La cadena real (cuántos saltos, qué rango privado) hay que medirla con `/api/admin/origen` en el despliegue.
- Solo reenvían la IP el proxy de clientes, el login del administrador y la calibración. Las rutas de autenticación de clientes y las lecturas
  de Server Components no (nadie las audita). Las lecturas del backoffice no se auditan (#178).
- `TRUSTED_PROXIES` debe ser lo más estrecha posible: un rango de plataforma compartido con otros servicios los hace «de confianza».
- No hay forma de leer la bitácora por API ni por el portal: solo la tabla `auditoria_admin`.

## #188 · Alta asistida de cliente, empresa y primera serie — rebanada 1: backend

**Estado: ✅ mergeado (#216), 33/33 mutaciones verificadas (26 del alta y 7 de las correcciones de la revisión). Seguimientos de la revisión en #218 (correo de
comprobantes sin SMTP) y #219 (idempotencia del alta). El formulario del portal es la rebanada 2.**

Con el registro público cerrado (#174) no había camino para incorporar a un cliente: `POST /v1/admin/tenants` crea la empresa pero no la cuenta.

### Diseño

- **`POST /v1/admin/cuentas`** (`AdminAltaAsistidaController`): `{ nombre, email, telefono?, empresa: { ruc, razon_social, entorno? }, serie: { tipo, serie } }`.
  Responde `201` con `cuenta_id`, `tenant_id`, `ruc`, `api_key` (solo aquí), la serie e `invitacion_enviada`. Solo la clave de plataforma o un administrador.
- **`AltaAsistidaService`**: valida todo antes de escribir (correo, teléfono, RUC, razón social, serie del tipo pedido, correo y RUC sin repetir) y escribe
  **en una sola transacción** la cuenta, su usuario ADMIN, la empresa atada a la cuenta, la API key, la serie, la invitación y el registro de bitácora.
- **El administrador no elige la contraseña.** El usuario queda con el hash de una contraseña aleatoria que nadie conoce (`Usuario` exige un hash no vacío, así que
  no hace falta tocar el dominio ni el esquema) y el cliente fija la suya con la invitación.
- **Invitación**: mismo mecanismo que restablecer (token de un solo uso, solo se guarda su hash) con **7 días** de vigencia, no la hora de recuperar. El enlace es
  `/restablecer/<token>?invitacion=1`: funciona con la página que ya existe y el portal adapta el texto con ese parámetro.
- **El correo sale fuera de la transacción.** Si falla, el alta **no** se revierte: la respuesta trae `invitacion_enviada: false` y el cliente puede pedir un
  enlace con «olvidé mi contraseña». Revertirla habría hecho que un SMTP caído impida dar de alta a nadie. Sin SMTP configurado (`MAIL_HABILITADO=false`, el
  default) también es `false`: el correo solo queda en el log, que en desarrollo es de donde se lee el enlace.
- **Bitácora**: una sola entrada `CREAR_CUENTA` con la cuenta y la empresa. El detalle lleva RUC, serie y entorno; **nunca** la API key, el token ni el correo.
- Sin migración: `auditoria_admin.accion` es un `VARCHAR`.

### Hallazgos que cambiaron la implementación

- **Una mutación murió por la razón equivocada** (E1): que el filtro dejara pasar `POST /v1/admin/cuentas` sin validar nada rompió **todos los casos felices**
  (la clave de plataforma dejaba de reconocerse), pero el test de aislamiento **sobrevivió**. Es defensa en profundidad real: `AdministradorActual.actor` falla cerrado,
  así que el filtro por sí solo no es la única barrera y ningún test de comportamiento puede distinguirlo. El aislamiento se verifica con mutaciones que quitan las dos
  capas a la vez (E2) o que hacen que el filtro acepte una clave equivocada (E3); las dos las mata `soloLaPlataformaOUnAdministradorPuedenDarDeAltaAUnCliente`.
- **Sin tope, un nombre de más de 150 caracteres era un 500 de la base** (`cuenta.nombre` es `VARCHAR(150)`, `email` 254). El test rojo lo mostró antes de poner
  `@Size`, igual que el registro público.
- **El rollback no se puede probar con fakes** (no hay transacción): `AltaAsistidaTransaccionalTest` usa Postgres y un trigger que falla al insertar en **cada**
  tabla del alta, una por una, para que cada paso tenga su turno de ser el que falla con todo lo anterior ya escrito.

### Hallazgos de la revisión de la PR (corregidos)

- **H1 · `invitacion_enviada: true` sin entrega.** Con `MAIL_HABILITADO=false` (el default) el adaptador `LogCorreoSender` escribe el correo en el log y **no lanza**,
  así que el servicio lo daba por enviado: el administrador leía «enviamos la invitación» y el cliente nunca la recibía. El puerto `CorreoSender` ahora dice si
  entrega de verdad (`entregaDeVerdad()`, `false` en el de log) y el servicio lo usa. El correo se sigue «enviando» al log, porque en desarrollo es por ahí que se lee
  el enlace.
- **H2 · La causa del fallo se perdía.** El servicio convierte el fallo en `false` y el adaptador SMTP no registraba nada. Ahora `SmtpCorreoSender` registra el error
  con el destinatario y la causa, **sin el cuerpo** (puede llevar un enlace de un solo uso), y lo propaga.
- **H3 · La construcción de una API key nueva estaba duplicada** con la del alta de una empresa. Ahora vive en un solo lugar, `ApiKeyGenerator.nueva`, que usan los dos
  servicios: un cambio en cómo se guardan las keys ya no puede quedar a medias.
- Quedó como seguimiento (fuera de alcance): un reintento tras un corte de red recibe 409 y la API key del primer intento, que solo viajaba en esa respuesta, no se
  puede recuperar. Lo resolvería una clave de idempotencia (#115).

### Tests

- Servicio (`AltaAsistidaServiceTest`, 15): todo el alta, el entorno por defecto y el pedido, la contraseña que nadie conoce, la invitación (enlace, vigencia, solo
  el hash), la bitácora sin secretos, **todas las escrituras dentro de la transacción**, el correo fuera de ella, un correo caído, un correo que solo queda en el log,
  validación previa sin dejar rastro, serie del tipo, y correo o RUC repetidos.
- Correo (`SmtpCorreoSenderTest` +2, `LogCorreoSenderTest` +1): quién entrega de verdad, y que un fallo de envío queda registrado con su causa y sin el cuerpo.
- `ApiKeyGeneratorTest` (2): la key nueva guarda solo hash y prefijo, activa y sin revocar, y el hash depende del pepper.
- Controlador (`AdminAltaAsistidaControllerTest`, 9): forma de la respuesta, atribución a la clave o al administrador con su IP, 401 sin credencial, 409, 422 y siete
  cuerpos inválidos (incluidos los más largos que las columnas).
- Persistencia (`AltaAsistidaTransaccionalTest`, 3, Postgres real): el alta sana deja todo atado; **un fallo en cualquiera de las siete tablas no deja nada**; y el
  mismo alta se puede reintentar tras un fallo.
- E2E (`AltaAsistidaE2ETest`, 7, HTTP y Postgres reales): el cliente acepta la invitación, entra con la contraseña que elige y ve su empresa; la API key sirve
  (`GET /v1/series`); la invitación es de un solo uso; el listado de cuentas la muestra; la bitácora; el aislamiento (sin credencial, solo `X-Forwarded-For`,
  JWT de cliente, API key, clave equivocada); duplicados y solicitudes inválidas sin dejar nada.

### Verificación por mutación — 33/33 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | Las escrituras fuera de la transacción | 1 unitario y 2 de rollback en Postgres |
| Servicio | La invitación vence en 1 hora, no en 7 días | `mandaUnaInvitacion…` |
| Servicio | La contraseña inicial es una fija | `elAdministradorNuncaEligeLaContrasena…` |
| Servicio | El correo sale dentro de la transacción | `elCorreoSaleDespuesDeLaTransaccion…` |
| Servicio | Sin comprobar que el correo ya exista | `unCorreoYaRegistrado…` |
| Servicio | Sin comprobar que el RUC ya exista | `unaEmpresaYaRegistrada…` |
| Servicio | La empresa no se ata a la cuenta | 2 unitarios y 1 de Postgres |
| Servicio | La bitácora lleva la API key en el detalle | `laBitacoraRegistra…SinSecretos` |
| Servicio | La bitácora registra otra acción | `laBitacoraRegistra…` |
| Servicio | No se crea la primera serie | 2 unitarios y los 3 de Postgres |
| Servicio | `invitacion_enviada` siempre `true` | `siElCorreoFalla…` |
| Servicio | El usuario queda inactivo | `creaLaCuenta…` |
| Servicio | El usuario no es ADMIN de su cuenta | `creaLaCuenta…` |
| Servicio | El token de invitación se guarda en claro | `mandaUnaInvitacion…` |
| Servicio | Sin comprobar que la serie sea del tipo | 2 unitarios |
| Servicio | El entorno por defecto es PRODUCCION | 2 unitarios |
| Servicio | El enlace no lleva `?invitacion=1` | `mandaUnaInvitacion…` |
| Controlador | URL de portal vacía | 7 |
| Controlador | El tipo de serie siempre es factura | `sinEntornoYConUnaBoleta…` |
| Controlador | Se ignora el entorno pedido | 6 |
| Controlador | Responde 200 en vez de 201 | 5 |
| Controlador | La empresa del cuerpo no se valida | `cuerpoIncompletoOMalFormado…` |
| Controlador | La respuesta no entrega la API key | 2 |
| Controlador | La serie del cuerpo no se valida | `cuerpoIncompletoOMalFormado…` |
| Seguridad | El filtro y `actor()` dejan pasar sin credencial (las dos capas) | el aislamiento (e2e), `sinCredencialNoCreaNadaYEs401` y la atribución al administrador |
| Seguridad | El filtro acepta cualquier clave de plataforma | `soloLaPlataformaOUnAdministrador…` |
| Revisión (H1) | `invitacion_enviada` ignora si el correo se entregó de verdad | `siElCorreoSoloQuedaEnElLog…` |
| Revisión (H1) | El adaptador de log dice que entrega | `diceQueNoEntregaDeVerdad` |
| Revisión (H2) | El adaptador SMTP no registra el fallo | `unFalloDeEnvioSeRegistra…` |
| Revisión (H2) | Lo registra sin la causa | `unFalloDeEnvioSeRegistra…` |
| Revisión (H2) | Se traga el fallo en vez de propagarlo | `unFalloDeEnvioSeRegistra…` |
| Revisión (H3) | La fábrica ignora el pepper | los 2 de `ApiKeyGeneratorTest` |
| Revisión (H3) | La fábrica crea la key inactiva | `unaKeyNuevaGuardaSoloElHash…` |
| *(no concluyente)* | El filtro deja pasar la ruta, con `actor()` intacto | murió 5/7 **por romper los casos felices**; el aislamiento sobrevive: la segunda capa lo sostiene |

### Límites conocidos

- **Sin reenvío de la invitación** (#183). Pasados los 7 días, o si el correo no salió, el cliente usa «olvidé mi contraseña» (enlace de 1 hora).
- **El texto de la invitación llega con la PR del portal.** Hasta entonces el enlace abre la página de restablecer con su texto genérico («Elige una nueva contraseña»).
- **Sin SMTP, el enlace de la invitación queda en el log** (como el de recuperar contraseña): es como se trabaja en desarrollo. En producción `MAIL_HABILITADO`
  es obligatorio (`deploy/README.md`); si faltara, la respuesta ya no dice «enviada».
- Un reintento tras perder la respuesta recibe 409 y la API key del primer intento no se recupera (seguimiento: idempotencia, #219).
- La serie arranca en 0 (el primer comprobante lleva el 1): migrar desde otro sistema con numeración ya usada necesitaría un `correlativo_inicial` que este alta no pide.
- El teléfono es opcional (el registro público lo exige): quien da de alta puede no tenerlo.
- La cuenta nueva no tiene plan (#189).

## #188 · Alta asistida de cliente, empresa y primera serie — rebanada 2: portal

**Estado: 🔧 portal implementado, 30/30 mutaciones verificadas (más 1 equivalente) — falta la revisión de la PR (#217). Usa `POST /v1/admin/cuentas`, ya mergeado (#216).
Con esta mergeada el issue queda completo.**

### Diseño

- **`/admin/cuentas/nueva`** (`AltaAsistidaForm`): una sola pantalla con tres bloques —cuenta, primera empresa, primera serie— y el mismo patrón de formularios
  del portal (react-hook-form + zod, los esquemas de RUC y razón social del onboarding). **No pide contraseña**: lo dice, y es el cliente quien la elige.
- **BFF `POST /api/admin/cuentas`**: exige la sesión de administrador (cookie `httpOnly`; el JWT no llega al JS), reenvía la IP ya resuelta (#208) para que la bitácora
  registre la del administrador, y responde con `Cache-Control: no-store` porque lleva la API key.
- **La API key inicial se muestra una sola vez** con el mismo bloque que el diálogo de «Crear API key» del portal de clientes, extraído a `ApiKeyRevelada` (el
  diálogo ahora lo usa, sin duplicarlo). Aquí el aviso cambia: la ve quien la entrega a un cliente, no quien la guarda para sí.
- **Si el correo no salió** (`invitacion_enviada: false`) lo dice y explica cómo pedir el enlace desde «¿Olvidaste tu contraseña?»; el alta quedó hecha y la key se entrega igual.
- **Un correo o un RUC repetido** muestra lo que dijo el backend: el 409 es el mismo código para los dos y un texto genérico («ya existe una cuenta con ese correo»)
  engañaría cuando lo repetido es la empresa.
- **El enlace de la invitación** es `/restablecer/<token>?invitacion=1`: misma página y mismo endpoint, con otro texto («Crea tu contraseña», «Crear contraseña»).
- **«Nueva cuenta» en la cabecera** deja de estar deshabilitado y pasa a ser un enlace; la miga reconoce `/admin/cuentas/nueva` (antes que `/admin/cuentas`, que la
  engulliría por prefijo).
- La regla de la serie (SUNAT 1001: `F` + 3 para facturas, `B` + 3 para boletas) se extrajo a `serieCoincideConTipo` y la usan el onboarding y el alta asistida.

### Hallazgos que cambiaron la implementación

- **El mock en memoria se comparte entre todas las specs de la corrida, en paralelo.** Si el alta guardara la cuenta, rompería `admin-cuentas.spec.ts` (cuenta exactamente 12) y
  las specs del alta chocarían entre sí por correo y RUC repetidos. El mock valida contra los datos sembrados pero **no guarda**; que la cuenta aparezca en el listado lo
  prueba el e2e del backend (`AltaAsistidaE2ETest`).
- **`Button render={<Link/>}` publica un enlace como `role="button"`.** El test de «Ver cuentas» lo mostró: para un destino que navega se usa un `<Link>` real con la clase de
  botón. El enlace «Ir a iniciar sesión» que ya existía en restablecer conserva su markup (no es parte de este issue).
- **Vitest lanzado con `--root` desde otro directorio da un falso rojo** en `browserslist.test.ts`: lee su configuración del directorio de trabajo. Con `npm run test` desde
  `portal/` pasa. Los comandos de verificación de esta rebanada se lanzaron así.
- **El runner de mutaciones no veía nada** porque Vitest escribe el JSON en `.vitest/json/output.json` (lo fija su configuración) y no en stdout: las primeras 31 «mutaciones» dieron
  todas «sin resultado». Se corrigió y se probó con una mutación antes de repetirlas; ninguna de esas lecturas fallidas se cuenta.

### Hallazgos de la revisión de la PR (corregidos)

- **H1 · La pestaña contradecía a la página.** El `metadata` de `/restablecer/[token]` era estático: con `?invitacion=1` la pestaña decía «Elige una nueva
  contraseña» y el encabezado «Crea tu contraseña». Ahora `generateMetadata` usa el mismo criterio que la página. El e2e del enlace comprueba el título en los dos
  casos (falló antes del cambio).
- **H2 · La receta del `<select>` nativo estaba copiada** en el alta asistida y dos veces en el onboarding. Ahora es `SELECT_NATIVO` en `lib/estilos.ts`, junto a
  las demás recetas, y la cadena aparece una sola vez en `src/`.

### Tests

- Vitest (+46, de 243 a 289): `AltaAsistidaForm` (17), `ApiKeyRevelada` (5), `RestablecerForm` (6), la ruta del BFF (7), el cliente `altaAsistida` (4), `serieCoincideConTipo` (5), y la cabecera
  y las migas (+1 cada una).
- Playwright (`admin-alta.spec.ts`, 12): sin sesión y con sesión de cliente no se abre; «Nueva cuenta» lleva al alta con su miga; no pide contraseña; el alta completa deja la key a la vista
  una sola vez; el correo no enviado; correo y RUC repetidos; RUC con el dígito mal y serie de boleta en una factura sin salir del navegador; «Dar de alta a otro cliente» limpia todo;
  y el enlace de invitación frente al de restablecer. La suite completa: 127/127.
- `tsc`, `eslint` y `next build` limpios.

### Verificación por mutación — 30/30 mueren

| Pieza | Mutación | Qué muere |
|---|---|---|
| BFF | No exige sesión de administrador | `sin sesión de administrador responde 401…` |
| BFF | No reenvía la IP resuelta | `manda al backend la IP de confianza ya resuelta…` |
| BFF | Responde 200 en vez de 201 | `reenvía el cuerpo… y devuelve el alta con 201` |
| BFF | La respuesta con la API key puede cachearse | `…no debe quedar en ninguna caché` |
| BFF | Un cuerpo que no es JSON responde 500 | `un cuerpo que no es JSON responde 400…` |
| BFF | No propaga el error del backend | `propaga el status y el código del backend…` |
| Cliente | No manda el JWT del administrador | 2 |
| Cliente | No manda la IP resuelta | `agrega la IP ya resuelta…` |
| Cliente | Llama a otra ruta | `hace POST a /v1/admin/cuentas…` |
| Formulario | El celular pasa a ser obligatorio | 10 |
| Formulario | No se comprueba que la serie sea del tipo | `rechaza una serie que no corresponde al tipo` |
| Formulario | Manda una contraseña | `manda el alta tal como el backend la espera…` |
| Formulario | Un correo repetido muestra el texto genérico | 2 |
| Formulario | Una sesión vencida muestra un «no autorizado» a secas | `si la sesión del administrador venció…` |
| Formulario | Se dice siempre que la invitación salió | `si el correo no salió lo dice claro…` |
| Formulario | No se valida el RUC | 2 |
| Formulario | «Dar de alta a otro cliente» no limpia el formulario | `«Dar de alta a otro cliente» vuelve a un formulario limpio…` |
| Formulario | El botón no se bloquea mientras envía | `mientras envía bloquea el botón…` |
| Formulario | El entorno por defecto es Producción | 2 |
| Formulario | «Ver cuentas» lleva a otra ruta | `desde el resultado se puede volver…` |
| Formulario | No se muestra la API key tras el alta | 2 |
| `ApiKeyRevelada` | «Copiar» no copia | 2 |
| `ApiKeyRevelada` | Se pierde el aviso de que no volverá a mostrarse | `avisa que no volverá a mostrarse…` |
| Cabecera | «Nueva cuenta» apunta a otra ruta | `en Cuentas ofrece «Nueva cuenta» como un enlace…` |
| Cabecera | «Nueva cuenta» se ofrece en todas las páginas | 3 |
| Migas | La miga del alta va después de la de cuentas (la engulle) | 2 |
| Restablecer | La invitación usa los textos de restablecer | 2 |
| Restablecer | Una invitación vencida muestra el texto de restablecer | `una invitación vencida o ya usada dice cómo pedir otro enlace…` |
| Serie | La serie de boleta se acepta con prefijo de factura | 4 |
| Serie | La serie admite cualquier largo | `rechaza lo que no tiene exactamente 4 caracteres` |
| Formulario | El teléfono no se normaliza | **sobrevive: equivalente** (ver abajo) |

**La mutación que sobrevive es equivalente, no un hueco de test.** El campo del celular filtra lo tecleado con `soloTelefono` (solo dígitos y `+`) antes de que llegue al esquema, así que
`telefonoSchema.parse` nunca recibe algo que normalizar: el resultado es idéntico con y sin ella. Se deja como defensa en profundidad, por si cambia el filtro.

### Límites conocidos

- **El mock no guarda el alta**: el e2e del portal no puede comprobar que la cuenta aparece en el listado (lo hace el del backend).
- **Sin reenvío de la invitación** (#183): si el correo no salió o pasaron los 7 días, el cliente pide el enlace con «¿Olvidaste tu contraseña?».
- La pantalla no tiene la API key después: si el administrador cierra o recarga antes de copiarla, la cuenta queda sin key y hay que crear otra desde el portal del cliente (no hay acción de
  administrador para eso todavía: #187).
- El formulario no ofrece correlativo inicial, ni establecimientos anexos, ni credenciales SOL ni certificado: eso lo completa el cliente en su portal.
- La página pesa 346 kB de primera carga (233 kB la lista de cuentas): el formulario trae `react-hook-form` y `zod`.

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
