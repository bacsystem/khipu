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

## #218 · Enviar un comprobante por correo sin SMTP no lo da por enviado

**Estado: 🔧 implementado, 6/6 mutaciones verificadas (5 más la de la revisión, H1).** Seguimiento de la revisión de #216 (#188, hallazgo H1).

Con `MAIL_HABILITADO=false` (el default) el adaptador `LogCorreoSender` escribe el correo en el log y no lanza: `POST /v1/facturas/{id}/correo` respondía
`202` y el portal mostraba «Enviado a …» aunque el adquirente no recibía nada.

### Diseño

- **`CompartirComprobanteService` pregunta `CorreoSender.entregaDeVerdad()`** (el puerto que agregó #188) y, si el adaptador no entrega, responde
  **`503 CORREO_NO_CONFIGURADO`**. Es 503 y no 502: no falló un servicio externo, el servidor no tiene correo, y reintentar no sirve hasta que el operador lo habilite.
- **No se intenta el envío.** El alta asistida sí lo «envía» al log, porque ahí se lee el enlace de la invitación en desarrollo; aquí el log no le sirve a nadie y
  armar los adjuntos (PDF, XML y CDR) sería trabajo tirado. Se comprueba después de `NO_ACEPTADO`, para que un comprobante no aceptado siga diciendo eso.
- **El portal no cambia de código**: `CorreoButton` ya muestra el `mensaje` del backend, así que el texto vive ahí y dice qué hacer («descargue el PDF y envíelo
  por su cuenta, o contacte a soporte»). Con SMTP configurado nada cambia: éxito si sale, `502 CORREO_NO_ENVIADO` si el SMTP lo rechaza.
- `/developers/errores` documenta el código y el 503.

### Tests

- Servicio (`CompartirComprobanteServiceTest` +2): con un `CorreoSender` que no entrega, `CORREO_NO_CONFIGURADO` con el número del comprobante en el mensaje, y
  **no se llama a `enviar`**; y, con ese mismo sender, un comprobante `FIRMADO` sigue respondiendo `NO_ACEPTADO` (H1 de la revisión: el orden de las dos
  comprobaciones estaba afirmado en la PR pero no fijado por ningún test).
- Controlador (`FacturaControllerTest`, +1 caso): `503` con el código.
- E2E (`comprobantes.spec.ts` +1): un correo `sin-smtp@…` hace que el mock responda 503; el formulario muestra el aviso y **no** aparece «Enviado a …».

### Verificación por mutación — 6/6 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | Sin preguntar `entregaDeVerdad()` (el código anterior) | `sinCorreoQueEntregueDeVerdadNoSeDaPorEnviado` (fue el rojo del TDD) |
| Servicio | La condición invertida | 3 de 5 (también los casos con SMTP) |
| Servicio | La comprobación va antes de `NO_ACEPTADO` | `sinCorreoUnComprobanteNoAceptadoSigueDiciendoNoAceptado` (sobrevivía hasta la revisión; responde `CORREO_NO_CONFIGURADO` en vez de `NO_ACEPTADO`) |
| Servicio | Escribe el correo antes de fallar | `sinCorreoQueEntregueDeVerdad…` («no se intenta») |
| Controlador | `CORREO_NO_CONFIGURADO` sin su 503 (cae en 422) | `correo…` de `FacturaControllerTest` |
| Mock | Sin el caso `sin-smtp` | el e2e nuevo |

### Límites conocidos

- La recuperación de contraseña sigue respondiendo `202` sin SMTP, a propósito: un código distinto revelaría qué correos existen.
- El portal no ofrece «enviar» deshabilitado de antemano: se entera al intentarlo. Saberlo antes pediría un endpoint de capacidades del servidor.

## #177 · 2FA obligatorio y sesión corta para el administrador

**Estado: 🔧 implementado, 60/60 mutaciones verificadas (46 backend, 13 BFF, 1 interfaz): 55 de la primera vuelta, de las que 7 se sustituyeron por 12 al
corregir el tope de intentos (H1 de la revisión).**

Un administrador ve los datos fiscales de todos los clientes: una contraseña filtrada no debe alcanzar para entrar.

### Diseño

- **Login en dos pasos.** `POST /v1/admin/auth/login` ya no devuelve una sesión: devuelve un **desafío** (JWT de 5 minutos, `tipo=plataforma-desafio`) y el
  paso siguiente. `segundo-factor/configurar` (QR + secreto) y `segundo-factor/confirmar` (primer código → sesión + 10 códigos de recuperación) la primera vez;
  `segundo-factor/verificar` (código de la app o de recuperación) después. Las cuatro rutas son públicas en el filtro (`RutaRequest`, lista exacta) y las tres
  del segundo factor exigen el desafío. El filtro de sesión exige `tipo=plataforma` exacto: **un desafío nunca abre el backoffice**.
- **TOTP propio** (`TotpRfc6238`): RFC 6238 con HMAC-SHA1, 6 dígitos, pasos de 30 s y ±1 paso de tolerancia, sobre `javax.crypto`. Sin dependencia nueva.
  Secreto de 160 bits en Base32, comparación de tiempo constante.
- **Estado del 2FA en tablas aparte** (`V28`): `administrador_segundo_factor` (secreto **cifrado con MASTER_KEY**, confirmado, último paso aceptado, fallos,
  bloqueo) y `administrador_codigo_recuperacion` (solo SHA-256; son 50 bits al azar, no adivinables por diccionario). El estado cambia en cada login y no
  reescribe la fila de la identidad.
- **Reglas en la condición del UPDATE, no en una lectura previa**: anti-reuso (`ultimo_paso < ?`), código de recuperación de un solo uso (`usado_at IS NULL`)
  y **reserva del intento** (`reservarIntento`: la compuerta «no está bloqueada» y el conteo, `CASE WHEN fallos + 1 >= ?`, en la misma sentencia). Así valen
  también entre peticiones simultáneas.
- **Con el 2FA confirmado, la contraseña sola no lo reemplaza** (`409 SEGUNDO_FACTOR_YA_CONFIGURADO`): si no, quien robara la contraseña enrolaría su teléfono.
- **Bloqueo**: 5 intentos con código equivocado → `429 DEMASIADOS_INTENTOS` durante 15 minutos, aun con el código correcto. Cuentan también los intentos al
  confirmar. El intento se reserva **antes** de comprobar el código; el quinto todavía se concede y deja el bloqueo puesto, y un acierto (de la app o de
  recuperación) lo levanta.
- **Sesión corta configurable**: `ADMIN_SESION_MINUTOS` (default 30), entre 5 y 60 o el arranque aborta. El backend responde `expira_en` y el BFF fija con eso la
  vida de la cookie: se configura en un solo lugar.
- **Bitácora**: `CONFIGURAR_SEGUNDO_FACTOR` e `INICIAR_SESION` (`segundo_factor=app|codigo_recuperacion`), con la IP del administrador (#208), en la misma
  transacción que el cambio de estado.
- **Portal**: el desafío vive en una cookie httpOnly limitada a `/api/admin/auth` (5 minutos) y el navegador nunca lo ve; el login cierra cualquier sesión de
  administrador previa. El formulario va en pasos y el campo del código solo admite 6 dígitos (`autocomplete="one-time-code"`). El QR lo genera el backend
  (zxing, el mismo `ZxingCodigoQr` que ahora usa el PDF del comprobante): el portal no suma dependencias.

### Hallazgos que cambiaron la implementación

- **El fallo se contaba dentro de la transacción.** La primera versión de `verificar` lanzaba el error dentro de `uow.ejecutar`: con JDBC real, el conteo del fallo
  (entonces `registrarFallo`, hoy `reservarIntento`) se revertía junto con la excepción y el bloqueo **nunca** habría llegado. Los fakes no lo mostraban (no hay
  rollback). Ahora el intento se reserva fuera de la transacción, y el fake registra si se llamó dentro (`intentoDentro`) para que el test lo detecte.
- **Leer y escribir el contador por separado dejaba pasar intentos en paralelo**: todos leían «0 fallos». Primero se hizo una sola sentencia para contar; la
  revisión mostró que faltaba lo mismo para la compuerta (ver «Corrección de la revisión»).
- **Tres mutaciones sobrevivieron la primera vuelta** y reforzaron los tests: un bloqueo de 1 minuto (el test saltaba 15 de golpe; ahora comprueba que a los 14
  sigue bloqueado) y la vida de la sesión fija en el servicio y en el BFF (los fakes usaban justo el valor por defecto, 1800).

### Tests

- Servicio (`AutenticarAdministradorServiceTest`, 26): desafío en vez de sesión, paso según el estado, configurar (secreto cifrado, QR, reemplazo), confirmar
  (códigos con formato y solo sus hashes, bitácora dentro de la transacción), verificar (ventana de reloj, anti-reuso, código de la confirmación no reusable,
  recuperación de un solo uso y normalizada, códigos inventados), bloqueo (5 intentos reservados fuera de la transacción, 14 y 15 minutos, reinicio con un
  acierto, intentos al configurar, **20 peticiones simultáneas → 5 comprueban el código y 15 se bloquean**, un acierto en el quinto intento no deja la
  cuenta bloqueada), desafío inválido o de un administrador desactivado.
- TOTP (`TotpRfc6238Test`, 7): los cinco vectores SHA-1 del RFC 6238, ventana, códigos malformados, secreto en minúsculas y con espacios, secretos nuevos, URI.
- JWT (`JwtAdministradorTokenEmisorTest`, +4): vida configurable, desafío de 5 minutos, **desafío ≠ sesión en ambos sentidos**, desafío vencido o ajeno.
- QR (`ZxingCodigoQrTest`): el PNG se decodifica de vuelta a la URI.
- Persistencia (`JdbcSegundoFactorRepositoryTest`, 11, Postgres): pendiente y confirmación, reemplazo, anti-reuso, **8 accesos simultáneos → uno**, intentos que
  suman y el quinto que bloquea, intento negado con la cuenta bloqueada (14 minutos) y concedido al vencer, **20 intentos simultáneos → exactamente 5
  concedidos**, un acierto que levanta el bloqueo de su propio intento (app y recuperación), recuperación de un solo uso y de otro administrador.
- REST (`AdminAuthControllerTest`: 9 en total, +5; `AdminAuthFilterTest`: 13 en total, +2, es decir +7 en esta PR): forma de cada respuesta, IP de la conexión,
  estado de cada error, validación; rutas públicas exactas, prefijos parecidos y rutas disfrazadas exigen credencial, un desafío como `Bearer` da 401.
- Configuración (`AppConfigSesionAdminTest`, 2): 5–60 y fuera de rango aborta.
- E2E backend (`SegundoFactorAdminE2ETest`, 7, HTTP y Postgres reales): la contraseña sola no abre nada, primer login y siguientes, reuso, recuperación, bloqueo,
  bitácora y secreto cifrado en la base. Los e2e que necesitan sesión de admin (`AdminCuentas`, `AltaAsistida`, `AuditoriaAdmin`) entran con
  `SesionAdminDePrueba`, que genera el código como el teléfono.
- BFF (Vitest, `login/route.test.ts` 5, `segundo-factor.test.ts` 7): cookie del desafío (httpOnly, ruta y vida), el login no abre sesión, cada paso sin desafío,
  vida de la cookie desde `expira_en`, el token nunca en el cuerpo, la IP de origen.
- E2E portal (`admin.spec.ts` 11): segundo factor, contraseña sola sin sesión, reintento, bloqueo, código de recuperación, configuración con QR y códigos, campo
  solo dígitos. Los specs de admin entran con `e2e/admin-sesion.ts`.

### Verificación por mutación — 60/60 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | El login siempre pide verificar | `laContrasenaSola…`, `unSecretoPendiente…` |
| Servicio | Se reconfigura con el 2FA ya confirmado | `conElSegundoFactorYaConfigurado…` |
| Servicio | Confirmar no rechaza un 2FA ya confirmado | `conElSegundoFactorYaConfigurado…` |
| Servicio | Se ignora el anti-reuso | `unCodigoYaUsado…`, `elCodigoConElQueSeConfiguro…` |
| Servicio | Un secreto pendiente cuenta como configurado | `verificarSinSegundoFactor…` |
| Servicio | La reserva negada no bloquea (compuerta ignorada) | 3, incluido `peticionesSimultaneasNoPruebanMasDeCincoCodigos` |
| Servicio | Tope de 6 intentos | 3 |
| Servicio | Bloqueo de 1 minuto | `cincoCodigosFallidos…` (tras reforzarlo) |
| Servicio | Confirmar no reserva el intento | `losFallosAlConfigurar…` |
| Servicio | Verificar no reserva el intento | 2 |
| Servicio | Verificar reserva dentro de la transacción | `cincoCodigosFallidos…` |
| Servicio | Secreto en claro | 18 |
| Servicio | Verificar / confirmar sin bitácora | 2 y 1 |
| Servicio | El desafío no revisa que el administrador siga activo | 2 |
| Servicio | Hash de recuperación sin normalizar | `unCodigoDeRecuperacion…` |
| Servicio | Nueve códigos / códigos en claro | 1 y 2 |
| Servicio | La sesión ignora la vida del emisor | 2 (tras reforzarlo) |
| Persistencia | `registrarAcceso` sin la condición de paso | 2, incluido el de concurrencia |
| Persistencia | `registrarAcceso` no reinicia fallos | 1 |
| Persistencia | Recuperación reusable / de cualquier administrador | 1 y 1 |
| Persistencia | `reservarIntento` sin la condición de bloqueo | 2, incluido el de 20 simultáneos |
| Persistencia | Tope de 6 en el SQL | 4 |
| Persistencia | El bloqueo vence un instante tarde (`<` en vez de `<=`) | `conLaCuentaBloqueada…` |
| Persistencia | `registrarAcceso` / la recuperación no levantan el bloqueo | 1 y 1 |
| Persistencia | El tope no deja bloqueo efectivo | 3 |
| Persistencia | Otro pendiente no borra códigos / conserva confirmado | 1 y 1 |
| TOTP | Ventana de 2 pasos / sin ventana | `toleraUnPaso…` |
| TOTP | Truncamiento sin el bit de signo | 4 vectores del RFC |
| TOTP | Secreto de 80 bits | `cadaSecretoNuevo…` |
| JWT | La sesión acepta el desafío / el desafío acepta la sesión | `unDesafioNoSirveComoSesion…` |
| JWT | Desafío de 30 minutos / vida de sesión fija | 1 y 1 |
| Filtro | Cualquier `/v1/admin/auth/*` es pública | 4 |
| Filtro | `verificar` deja de ser pública | `loginDelBackofficeYSuSegundoFactor…` |
| Errores | `DEMASIADOS_INTENTOS` sin 429 / `CODIGO_INVALIDO` sin 401 | `cadaFalloDelSegundoFactor…` |
| Configuración | Sesión de admin sin tope | `fueraDelRango…` |
| BFF | El desafío viaja con todas las peticiones (`path=/`) / dura 30 min | 1 y 1 |
| BFF | El login no cierra la sesión anterior / no guarda el desafío | 1 y 1 |
| BFF | Confirmar / verificar ignoran `expira_en` | 1 y 1 (verificar, tras reforzarlo) |
| BFF | Confirmar / verificar no borran el desafío | 1 y 1 |
| BFF | Configurar / confirmar / verificar no exigen el desafío | 1 cada uno |
| BFF | Confirmar expone el token al navegador | 1 |
| BFF | Confirmar no manda la IP | 1 |
| Interfaz | El campo del código acepta letras y más de 6 dígitos | e2e `el campo del código solo acepta seis dígitos` |

### Límites conocidos

- **Sin reinicio del 2FA desde el backoffice**: si un administrador pierde el teléfono y los diez códigos, hoy hay que borrar su fila de
  `administrador_segundo_factor` a mano o crear otro administrador. Encaja con «forzar restablecimiento» (#183).
- El tope es por administrador, no por IP: quien tenga la contraseña puede mantener la cuenta bloqueada (15 minutos cada 5 intentos). Es preferible a dejar que
  pruebe códigos.
- El mock del portal no guarda estado del 2FA (los e2e corren en paralelo): el anti-reuso y el bloqueo reales se prueban en el backend.
- **Hasta el primer ingreso de un administrador, su contraseña sola alcanza para entrar.** El segundo factor se enrola en el primer login y `configurar` solo
  exige el desafío, que da la contraseña; mientras el 2FA no esté confirmado, pedir otro secreto reemplaza el pendiente (`pedirOtroSecretoAntesDeConfirmar…`).
  Quien tenga la contraseña de un administrador aún no enrolado puede enrolar *su* teléfono, recibir sesión y los diez códigos, y dejar al legítimo fuera.
  Los administradores que ya existían están en ese estado desde el despliegue hasta que entren. «Sin 2FA nadie opera el backoffice» vale una vez enrolado.
  Mitigación operativa en `deploy/README.md` §7: que cada administrador enrole de inmediato, y si una contraseña se expuso o alguien se enroló sin serlo,
  reemplazar al administrador (borrarlo en la base y crearlo de nuevo con `X-Platform-Key`): el producto no cambia contraseñas de administrador ni los
  desactiva, y una sesión ya emitida no se revoca (dura hasta `ADMIN_SESION_MINUTOS`). Cerrarlo del todo pide una prueba de posesión que solo tenga quien
  opera la plataforma (p. ej. un token de enrolamiento de un solo uso): queda como seguimiento, junto con cambiar la contraseña y desactivar desde el backoffice.
- La bitácora registra el inicio de sesión exitoso y la primera configuración, no los intentos fallidos ni los bloqueos: un ataque de adivinanza contra la
  cuenta no deja rastro más que el log HTTP. Seguimiento.

### Corrección de la revisión (H1, H2, H3)

- **H1, el tope de intentos no era atómico con el intento.** `verificar` y `confirmar` comprobaban el bloqueo con un estado leído al inicio y contaban el
  fallo al final: N peticiones que leían «sin bloqueo» antes del primer fallo contado probaban las N un código. Con una barrera tras la lectura, 20 peticiones
  probaron **20** códigos (el diseño promete 5); el mismo experimento por HTTP real en loopback (60 peticiones) no se desbordó (5×401, 55×429), porque a ese
  ritmo las primeras cinco terminan antes de que lean las demás: con latencia real a Postgres la ventana crece. Con 5 intentos cada 15 minutos son 480 al día
  (unos 2 años esperados para acertar un código válido en ±1 paso, 3 de 10⁶); con ráfagas de 200, ~19 000 al día (~17 días). Ahora `reservarIntento` es una
  sola sentencia que niega el intento si la cuenta está bloqueada y, si lo concede, lo cuenta y bloquea al llegar al tope; reemplaza a `registrarFallo` y se
  invoca antes de mirar el código y fuera de la transacción. Como el quinto intento deja el bloqueo puesto antes de saber si era bueno, `registrarAcceso` y
  `consumirCodigoRecuperacion` ahora también levantan el bloqueo. 12 mutaciones nuevas, todas mueren; de las 55 de la primera vuelta se sustituyeron 7 (las
  que apuntaban a `registrarFallo` y a `exigirNoBloqueado`) y se repitieron las 19 restantes del servicio y la persistencia, que siguen muriendo.
- **H2, el hueco del primer ingreso**, documentado arriba (límites) y en `deploy/README.md` §7, con la mitigación operativa.
- **H3, la cifra de REST**: «12» no salía de ningún conteo; son +5 en el controlador (9 en total) y +2 en el filtro (13 en total).
- **H4 (segunda pasada), la mitigación no se podía ejecutar**: el README mandaba a «rotar la contraseña» del administrador, cosa que el producto no hace, y
  en un orden (borrar el 2FA y después cambiar la contraseña) que dejaba al atacante volver a enrolarse en el medio. Ahora da un procedimiento con lo que
  existe (borrar al administrador, que arrastra su 2FA en cascada, y crearlo de nuevo) y avisa que una sesión ya emitida no se revoca.
- Los administradores que ya existen configuran el 2FA en su próximo login.

## #214 · La búsqueda de cuentas ignora tildes

**Estado: 🔧 implementado, 9/9 mutaciones verificadas (7 y 2 de la corrección de la revisión).** Seguimiento de #180.

`GET /v1/admin/cuentas?q=libreria` no encontraba «Librería El Saber»: `ILIKE` ignora mayúsculas pero no tildes.

### Diseño

- **`translate()` y no `unaccent`.** `translate` es del núcleo de Postgres: funciona igual en el compose, en Testcontainers y en cualquier proveedor, sin
  migración ni extensión (el issue dejaba abierto si el proveedor de producción ofrece `unaccent`; así deja de importar). Se aplica a la columna y al texto
  buscado, así que la regla vale en los dos sentidos. Solo en el nombre de la cuenta y la razón social: el correo y el RUC no llevan tildes.
- **La tabla incluye mayúsculas** (`Í` → `I`, y `Ñ` → `ñ`): no se depende de que el `LC_CTYPE` de la base sepa pasar `Í` o `Ñ` a minúscula para el `ILIKE`.
  Con `LC_CTYPE=C`, `ILIKE` solo lo hace con el ASCII: `'Peña' ILIKE '%PEÑA%'` es falso.
- **La `ñ` se distingue de la `n`** (decisión del issue): «peña» y «pena» son palabras distintas, y en un teclado en español —también el del móvil— la `ñ` no
  cuesta. Documentado en la descripción OpenAPI del endpoint.
- **El texto buscado se normaliza a NFC**: una tilde que llega como `i` + acento combinado cuenta igual que `í`.
- Lo ya resuelto no cambia: sin distinguir mayúsculas, RUC por prefijo, comodines literales, `X-Total-Count`. Rendimiento igual que antes (`ILIKE '%…%'` ya no
  usaba índice); si el volumen crece, `pg_trgm`, como dice #180.
- El mock del portal usa **la misma tabla** que el backend, carácter por carácter, y normaliza a NFC solo el texto buscado.
- No hay otros buscadores con `ILIKE` en el backend: es el único.

### Tests

- Persistencia (`JdbcCuentasAdminRepositoryTest` +4, Postgres): tildes en los dos sentidos y en mayúsculas en el nombre; razón social con tilde y diéresis;
  acento grave; `ñ` distinta de `n`; tilde combinada. Los 13 tests anteriores (mayúsculas, RUC por prefijo, comodines, total) siguen pasando sin cambios.
- Persistencia con `LC_CTYPE=C` (`JdbcCuentasAdminRepositoryLocaleCTest`, 3, Postgres propio inicializado con `--locale=C`): la base es de verdad `C` y en ella
  `ILIKE` solo no basta; «PEÑA», «peña» y «muñoz» encuentran lo suyo y «pena» sigue sin encontrar «Peña»; «LIBRERÍA» encuentra «Librería». El resto de los
  tests corre con `en_US.utf8` (el default de la imagen), que tapaba el caso de la `Ñ`.
- E2E portal (`admin-cuentas.spec.ts` +1): «panadería sol sac» encuentra `PANADERIA SOL SAC`, que sin la regla no coincide ni por nombre ni por razón social.

### Verificación por mutación — 9/9 mueren

| Mutación | Qué muere |
|---|---|
| El nombre sin normalizar | 3 |
| La razón social sin normalizar | `tambienEnLaRazonSocial…` |
| El texto buscado sin normalizar (falla el sentido «librería» → «Libreria») | 3 |
| Sin NFC | `unaTildeEscritaComoAcentoCombinado…` |
| La `ñ` se vuelve `n` | `laEnieNoSeConfundeConLaEne` |
| La tabla solo con minúsculas | 2 |
| Mock: la razón social sin normalizar | el e2e nuevo |
| La tabla sin la `Ñ` (revisión, H1) | `laEnieEnMayusculasYMinusculasSinDependerDeLaBase` (base `C`) |
| La `Ñ` se vuelve `n` en vez de `ñ` (revisión, H1) | 2, incluido `laEnieNoSeConfundeConLaEne` |

### Límites conocidos

- Solo vocales con tilde, diéresis, acento grave o circunflejo, y la `Ñ` a `ñ`: otras letras con marca (ç, å) no se normalizan. No aparecen en nombres peruanos
  ni en razones sociales de SUNAT. El mock del portal sigue exactamente la misma tabla, así que tampoco las normaliza.

### Corrección de la revisión (H1, H2)

- **H1, la `Ñ` dependía del `LC_CTYPE`.** La tabla de `translate` tenía las vocales mayúsculas justamente para no depender de la base, pero no la `Ñ`.
  Comprobado con dos Postgres 16: con `C`, `'Peña Hermanos' ILIKE '%PEÑA%'` es falso; con `en_US.utf8`, verdadero. No se sabe qué `LC_CTYPE` tiene el
  Postgres de producción, así que se agregó `Ñ` → `ñ` (sigue distinta de la `n`) y una clase de test con su propio Postgres en `C`, que estaba en rojo antes
  del cambio («PEÑA» no encontraba nada).
- **H2, el mock no era el backend.** Quitaba cualquier marca combinante salvo tras la `n`, así que contra el mock «conceicao» encontraba «Conceição» y contra el
  backend no. Ahora usa la misma tabla. No se pudo correr el e2e: el Control de aplicaciones de Windows bloquea el binario nativo de Next en esta máquina
  (`next-swc.win32-x64-msvc.node`); ESLint limpio.

## #115 · Idempotencia en POST /v1/facturas

**Estado: 🔧 implementado, 17/17 mutaciones verificadas, más 6/6 de la corrección H1 de la revisión.** No es del backoffice, pero va en la pila: el alta asistida (#219)
reutiliza el mismo mecanismo.

Un corte de red después de que el backend emitió dejaba al cliente sin respuesta; si reintentaba, salía otra factura con otro correlativo, que solo se
deshace con nota de crédito.

### Diseño

- **`Idempotency-Key`** (opcional) en `POST /v1/facturas`: de 8 a 100 letras, dígitos, `-` o `_`, se recomienda un UUID. Sin la cabecera, todo igual que antes.
- **Reserva dentro de la transacción de la emisión, antes de tomar la serie**: `INSERT ... ON CONFLICT DO NOTHING` en `idempotencia` (`V29`, clave primaria
  `(alcance, clave)`). Si otro pedido con la misma clave está en curso, Postgres hace esperar a este hasta que el primero confirme (y entonces ve su factura) o
  se revierta (y entonces reserva él). Leer antes de insertar habría dejado pasar a los dos. Si la emisión falla, la reserva se revierte con ella y la clave
  queda libre.
- **Un pedido repetido** devuelve `200` con el comprobante de entonces **en su estado actual**: no toma la serie, no consume número y no se reenvía a SUNAT.
  **La misma clave con otro contenido** responde `422 IDEMPOTENCIA_INVALIDA`.
- **Huella**: SHA-256 del pedido ya interpretado y vuelto a serializar (Jackson), no de los bytes: otro espaciado es el mismo pedido; otro dato, otra huella.
- **Alcance por empresa** (`factura:<tenant>`) y **vigencia de 24 horas**: `LimpiezaIdempotenciaWorker` borra cada hora las claves vencidas (índice por
  `creado_at`).
- **Portal**: el diálogo de emisión manda la clave por *intento* (`intentoPara`): la misma mientras se reintente el mismo contenido, otra si el contenido cambia.
  El proxy reenvía solo esa cabecera del navegador, también en el reintento tras renovar el token. Ante un corte, el mensaje ya no manda a revisar el listado:
  invita a volver a emitir sin cambiar nada. CORS admite la cabecera para «Try it» de `/developers`.
- `/developers/errores` y la descripción OpenAPI documentan la cabecera, el `200` y el `422`.

### Tests

- Servicio (`EmitirComprobanteServiceTest` +5, y +4 de la corrección H1, abajo): clave nueva emite y anota (reserva dentro de la transacción), repetida devuelve el mismo sin consumir número ni
  reenviar a SUNAT, otra huella 422, alcance por empresa, sin clave como siempre. `LimpiarIdempotenciaServiceTest`: 24 horas.
- Persistencia (`JdbcIdempotenciaRepositoryTest`, 7 y +2 de H1, Postgres): reserva y registro, alcance, **reserva revertida libera la clave**, **un pedido simultáneo
  espera al primero y ve su resultado**, **si el primero se revierte el segundo reserva**, limpieza, `completar` no toca otro alcance.
- REST (`FacturaControllerTest` +5): 201 nueva / 200 repetida, la huella no depende del formato del JSON pero sí de los datos, clave mal formada 422 sin
  emitir, 422 del caso de uso.
- E2E backend (`FacturaIdempotenciaE2ETest`, 6, HTTP y Postgres reales): reintento → misma factura y número libre; **8 pedidos idénticos simultáneos → 1
  factura** (1×201, 7×200); otra factura con la misma clave 422; una emisión rechazada no gasta la clave; pasadas 24 h la clave vuelve a ser nueva; sin clave,
  dos facturas.
- Portal: `intentoPara` (4), proxy (+3: reenvía solo la clave, la misma tras renovar el token, no inventa una); e2e (`emision.spec.ts` +2): **el pedido llega
  y emite pero la respuesta se corta; reemitir sin cambiar nada lleva a la misma factura con la misma clave**; si se cambia la factura después del corte, la
  clave es otra.

### Verificación por mutación — 17/17 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | No se consulta la reserva / no se compara la huella | 1 y 2 (recontadas tras H1 y H2) |
| Servicio | Alcance común a todas las empresas | 2 (`laClaveEsPorEmpresa` y otro) |
| Servicio | No se anota el comprobante bajo la clave | 5 |
| Servicio | La repetida se reenvía a SUNAT / se marca como nueva | 1 y 4 |
| Persistencia | Reservar con un `SELECT` previo (sin la espera del `INSERT`) | 3, incluidos los de concurrencia |
| Persistencia | Borrar todas las claves / `completar` sin el alcance | 1 y 1 (este, tras agregar su test) |
| REST | Repetida con 201 / sin validar la clave / huella constante / la clave no llega | 1 cada una |
| Limpieza | Vigencia de 1 hora | 1 |
| Portal | El proxy no reenvía la clave | 2 |
| Portal | Clave nueva en cada intento / la misma aunque cambie el contenido | 1 y 1 |

Una primera versión de la mutación «huella» no compilaba y se descartó; el script de mutaciones ahora distingue «no compila» de «muere».

### Corrección de la revisión (H1): el reintento se contesta antes de validar

La primera versión consultaba la clave en la reserva, **después** de `tenantListo` y de crear el comprobante. Un reintento que llegaba con el plazo de envío
vencido (2108, 3 días calendario) o con el certificado vencido recibía `422` en vez de la factura ya emitida, que es justo el caso para el que existe la
clave; y quien cambiaba la fecha para que pasara emitía un duplicado con otra clave. Se comprobó con un test que fallaba con
`2108 - Con fecha de emisión 2026-09-10 el plazo de envío a SUNAT venció el 2026-09-13`.

- `IdempotenciaRepository.buscar(alcance, clave)`: lectura de lo confirmado, sin reservar ni esperar. `emitirFactura` la usa **antes** de validar y devuelve
  la factura ya emitida (`200`) con la misma comparación de huella. La exclusión entre pedidos simultáneos sigue siendo la reserva del `INSERT` dentro de la
  transacción: una clave nueva se valida y reserva como antes, y si se rechaza fuera de plazo **no** queda reservada.
- Tests (servicio +4): reintento con plazo vencido, reintento con certificado vencido, otra huella fuera de plazo sigue siendo `IDEMPOTENCIA_INVALIDA`, clave
  nueva fuera de plazo se rechaza y no queda reservada. Persistencia +2: `buscar` no reserva y no ve ni espera una reserva sin confirmar.
- Mutaciones, 6/6 mueren: sin consulta previa (3 tests), otro alcance en la consulta (3), consulta sin comparar huella (2), la consulta reserva en vez de
  leer (9, recontada tras el test de H2: eran 8), `buscar` sin filtrar el alcance (2), `buscar` que reserva (2).

### Corrección de la revisión (H2): la rama de la reserva vuelve a tener test de servicio

Con la consulta previa, todo reintento secuencial se contesta antes de `emitir`, así que ningún test de servicio llegaba a la rama de la reserva
(`if (previo.isPresent()) return yaEmitida(...)`), la que protege la carrera real. Al repetir las mutaciones, **«no se consulta la reserva» sobrevivía** en el
servicio y solo la mataba el e2e de 8 hilos (`FacturaIdempotenciaE2ETest`, que necesita Docker): la fila de arriba ya no era cierta.

- Test nuevo `siLaReservaEncuentraLaFacturaQueLaConsultaPreviaNoVioLaDevuelveSinEmitirOtra`: un fake «ciego al buscar» (la consulta previa no ve nada, la
  reserva sí), con envío automático. Pide la misma factura, ni otra emisión, el número 2 libre y ningún reenvío a SUNAT. Para eso `Fakes.Idempotencias` se
  puede heredar y su `reservar` lee `filas` directo, como el `INSERT ... ON CONFLICT` real.
- Una primera versión del test usaba `enviar_automatico: false` y **no mataba «la repetida se reenvía a SUNAT»** (ese `return` solo lo alcanza la repetida
  que llega por la reserva): se endureció y las seis mutaciones del servicio (I1–I6) se repitieron, **6/6 mueren** (1, 2, 2, 5, 1 y 4 tests).

### Límites conocidos

- Solo facturas. Las notas de crédito y débito (`POST /v1/notas`) siguen sin clave: el mecanismo es el mismo y queda como seguimiento.
- La huella es del pedido interpretado: dos JSON con los mismos datos pero un campo desconocido distinto (que se ignora) son el mismo pedido.

## #219 · Idempotencia del alta asistida

**Estado: 🔧 implementado, 25/25 mutaciones verificadas (21 y 4 de la corrección de la revisión).** Seguimiento de la revisión de #216 (#188). Usa el
mecanismo de #115.

El alta devuelve la API key inicial solo en su respuesta (se guarda el hash). Si la respuesta se perdía, el reintento recibía `409 DUPLICADO` y la
key no se recuperaba.

### Diseño

- **`Idempotency-Key`** en `POST /v1/admin/cuentas`, con la misma validación y huella que la emisión: `ClaveDeIdempotencia` (in-rest) las comparten los dos
  controladores y `Idempotencia` pasó a ser un tipo propio de `port.in`.
- **El reintento se reconoce antes de validar** (`buscar`): si no, el correo ya registrado lo volvería un `409 DUPLICADO`.
- **La reserva va al inicio de la transacción del alta**: dos altas simultáneas con la misma clave pasan las dos la búsqueda previa, y la reserva de la
  segunda espera a la primera y devuelve su respuesta sin escribir nada.
- **La respuesta se guarda cifrada con `MASTER_KEY`** (columna `respuesta_cifrada`, `V30`), en la misma transacción que el alta y actualizada después de
  enviar la invitación, así el reintento muestra lo mismo que vio el administrador (`invitacion_enviada` incluido). Formato: campos separados por `\u001f`,
  que ningún campo puede contener.
- **Ventana de una hora para la respuesta**, que **se cumple al leer**: el servicio no la devuelve pasada una hora desde que se reservó la clave
  (`creado_at`), aunque la limpieza todavía no la haya borrado. `LimpiarIdempotenciaService` la borra físicamente (`olvidarRespuestasAnterioresA`, cada hora,
  con la misma vigencia) y la clave sigue 24 h. Un
  reintento tardío se reconoce y responde **`409 IDEMPOTENCIA_VENCIDA`** («el cliente puede crear otra API key desde su portal») en vez de un `DUPLICADO`
  confuso.
- La misma clave con otro pedido: `422 IDEMPOTENCIA_INVALIDA`. Alcance único `alta-cuenta` (las altas no son de ninguna empresa).
- **Portal**: el formulario manda la clave por intento (`intentoPara`) y la estrena al «dar de alta a otro cliente»; ante un corte, el aviso invita a reenviar sin
  cambiar nada. El BFF reenvía la clave.

### Tests

- Servicio (`AltaAsistidaServiceTest` +9): reintento con la misma API key sin crear, invitar ni auditar otra vez; respuesta cifrada; reserva y guardado en la
  transacción; el reintento conserva `invitacion_enviada` (enviada y no enviada); otra huella 422; respuesta olvidada 409; **reserva que encuentra un alta
  simultánea**; sin clave sigue siendo `DUPLICADO`.
- Persistencia (`JdbcIdempotenciaRepositoryTest` +2): `buscar` no reserva y devuelve la respuesta; olvidar las viejas conserva la clave.
  `LimpiarIdempotenciaServiceTest` +1: una hora para las respuestas.
- REST (`AdminAltaAsistidaControllerTest` +3): 201 nueva / 200 repetida con la misma key, clave mal formada 422, 409 vencida.
- E2E backend (`AltaAsistidaE2ETest` +5): reintento con la misma key **que funciona** contra `/v1/series`, respuesta cifrada en la base, otra alta con la
  misma clave 422, pasada la hora 409 sin crear nada, **4 altas simultáneas → 1 cuenta** (1×201, 3×200).
- Portal: formulario (+3: el corte invita a reenviar, misma clave al reenviar y otra si cambia, otra al «dar de alta a otro cliente»), BFF (+1), e2e (+1:
  **el alta se hace, la respuesta se corta, el reenvío muestra la misma API key**).

- Corrección de la revisión (H1): servicio +2 (a los 61 minutos, sin que pase la limpieza, ya no devuelve la API key; a los 59, sí) y persistencia +1 (lo
  registrado trae `creado_at`).

### Verificación por mutación — 25/25 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | Sin reconocer el reintento antes de validar | 4 |
| Servicio | Sin consultar la reserva en la transacción | `siLaReservaEncuentraUnAltaSimultanea…` (sobrevivía; se agregó ese test) |
| Servicio | Sin comparar la huella / respuesta olvidada tratada como repetida | 1 y 1 |
| Servicio | Respuesta en claro | 4 |
| Servicio | No se guarda la respuesta en la transacción / no se actualiza tras la invitación | 2 y 2 |
| Servicio | El reintento se marca como nuevo | 1 |
| Persistencia | `buscar` sin el alcance / `completar` no guarda la respuesta / olvidar borra todas | 1, 2 y 1 |
| Persistencia | Reservar ignorando si la clave ya existía | 4 |
| Persistencia | Reservar leyendo antes de insertar (sin la espera del `INSERT`) | el de concurrencia |
| Limpieza | Respuestas de 24 h / la limpieza no olvida | 1 y 2 |
| REST | Repetida con 201 / `IDEMPOTENCIA_VENCIDA` sin 409 | 1 y 1 |
| Portal | El BFF no reenvía la clave / otro cliente reusa la clave / el corte no invita a reenviar | 1 cada una |
| Servicio (revisión, H1) | No se mira la edad de la respuesta | `aLaHoraLaApiKeyYaNoSeDevuelve…` |
| Servicio (revisión, H1) | Vence con el doble de tiempo / a la media hora | 1 y 1 (`antesDeLaHora…` fija que no venza antes) |
| Persistencia (revisión, H1) | `buscar` no lee `creado_at` | `loRegistradoDiceCuandoSeReservoLaClave` |

**Corrección sobre #115.** Dos mutaciones de persistencia de su tabla murieron la primera vez por SQL inválido, no por los tests: la de #115 que «leía
antes de insertar» usaba un parámetro sin tipo, y una de esta tanda tenía paréntesis desbalanceados. Se rehicieron con cambios válidos (las dos filas de
«Reservar…» de arriba, sobre el mismo código que trae #115) y mueren por la razón correcta. El script de mutaciones ya marca «no compila», pero un SQL
inválido solo se ve leyendo el motivo del fallo.

### Límites conocidos

- Pasada la hora, la API key del alta no se recupera: es el precio de no guardarla más tiempo. El cliente crea otra desde su portal (o un administrador, cuando
  exista #187).
- La respuesta guardada es la del momento del alta: si después cambian la razón social o la serie, el reintento muestra los datos de entonces.
- La hora se cuenta desde `creado_at`, que pone Postgres (`now()`), y se compara con el reloj de la aplicación: un desfase entre los dos relojes corre la
  ventana por esa diferencia.

### Corrección de la revisión (H1)

La primera versión no miraba la edad de la respuesta: la devolvía mientras estuviera guardada, y solo la borra la limpieza, que corre cada hora y olvida lo
que ya tiene más de una. Una respuesta creada justo después de una pasada sobrevivía a la siguiente: la API key podía salir en un reintento hasta unas dos
horas, contra el «solo una hora» del OpenAPI. Ahora `Registro` trae `creado_at` y `AltaAsistidaService` la da por vencida pasada `VIGENCIA_RESPUESTA`, la
misma constante que usa la limpieza. El test de los 61 minutos estaba en rojo antes del cambio.

## #22 · Verificación de correo obligatoria en el registro

**Estado: ✅ hallazgos de la revisión de la PR (#228) corregidos, 31/31 mutaciones verificadas (18 y 13 de la corrección).** No es del backoffice, pero va en la pila: #183 («reenviar
verificación») y el estado «sin verificar» de #180 dependen de él.

### Diseño

- **`Usuario.correoVerificadoEn`** (columna `correo_verificado_at`, `V31`). Nulo = sin verificar. **Los usuarios que ya existían quedan verificados** en la
  migración: entraron cuando no se pedía y bloquearlos de golpe les cortaría la emisión.
- **Enlace** en `token_verificacion` (tabla aparte de `token_recuperacion`, para que un enlace de verificación no sirva para cambiar la contraseña): solo el
  hash, **24 horas**, **un solo uso** (la condición `usado = false` está en el `UPDATE`: dos clics simultáneos, uno verifica).
- **El registro manda el enlace** fuera de la transacción y sin propagar el error: un SMTP caído no impide registrarse (el usuario pide otro).
- **Bloqueo en el `JwtFilter`**: con la sesión del portal y el correo sin verificar, **cualquier escritura** (todo lo que no sea `GET`/`HEAD`/`OPTIONS`)
  responde `403 CORREO_SIN_VERIFICAR`, salvo `/v1/auth/**` (reenviar el enlace, cerrar sesión). Se mira el usuario **en la base**, no en el token: verificado en
  otra pestaña, la siguiente escritura ya pasa. Un usuario que ya no existe o está inactivo no escribe (falla cerrado). La ruta se decide normalizada.
- **Las API keys no se bloquean**: son credenciales de un integrador o del alta asistida, emitidas por alguien ya verificado o por un operador.
- **Restablecer la contraseña (y aceptar la invitación del alta asistida, que usa el mismo enlace) también verifica**: el enlace llegó a ese correo.
- `POST /v1/auth/verificar {token}` (público) y `POST /v1/auth/verificacion` (reenviar, con sesión; `409 CORREO_YA_VERIFICADO`). `me` devuelve
  `correo_verificado`.
- **Tope de enlaces: 5 cada 24 horas, contando el del registro**; el sexto pedido responde `429 DEMASIADOS_ENLACES` sin mandar nada. Sin verificar, el
  correo puede no ser de quien se registró: sin tope, el reenvío serviría para llenar el buzón de otro desde nuestro dominio. Se cuentan los enlaces sin
  vencer del usuario (como todos duran 24 h, son los de las últimas 24 h), con índice `(usuario_id, expira_en)`.
- **Verificar solo marca la columna** (`UPDATE ... WHERE correo_verificado_at IS NULL`), sin reescribir el usuario: un restablecer que termine a la vez no
  pierde la contraseña nueva.
- **Portal**: el onboarding muestra «Revisa tu correo» (con reenvío y «Ya lo verifiqué») en vez del formulario, y el layout privado lo avisa arriba.
  `/verificar/[token]` verifica **con un botón y no al abrir la página**: los filtros y antivirus del correo abren los enlaces y gastarían uno de un solo uso.
  El segmento del token se decodifica: un cliente de correo puede haber reescrito el enlace codificándolo.

### Tests

- Dominio (`UsuarioTest` +1): empieza sin verificar, la fecha es la primera y se conserva al cambiar la contraseña o desactivar.
- Servicio (`AutenticarUsuarioServiceTest` +10): el registro manda el enlace (solo el hash, 24 h); el enlace verifica una vez; vencido o inventado no;
  no sirve para cambiar la contraseña; reenviar; ya verificado no reenvía; restablecer verifica; un SMTP caído no rompe el registro; tope de enlaces;
  verificar no pisa una contraseña cambiada a la vez.
- Persistencia (`JdbcVerificacionCorreoRepositoryTest`, 5, Postgres): guardar/buscar, un solo uso, el usuario guarda la verificación, marcar solo toca esa
  columna, contar los enlaces sin vencer.
- REST: `AuthControllerTest` +6 (verificar, reenviar, 409, 429, `me` con el campo), `JwtFilterTest` +5 (ninguna escritura sin verificar; mirar y la sesión sí;
  verificado escribe; sin usuario o inactivo 401; ruta disfrazada de auth).
- E2E backend (`AuthE2ETest` +6, con el enlace sacado del correo real): sin verificar se mira y no se crea empresa; con el enlace se crea **con el mismo token
  de sesión**; un solo uso; reenviar; tope de reenvíos; una API key no depende de la verificación. `AdminCuentasE2ETest` verifica su cliente; `AltaAsistidaE2ETest` ignora el
  correo de verificación del registro que usa para su prueba de aislamiento.
- Portal: BFF (+5), e2e `verificacion-correo.spec.ts` (7: pantalla en vez del formulario, `403` por HTTP directo, reenvío, tope de reenvíos, el enlace
  verifica, un solo uso, «Ya lo verifiqué» tras verificar en otra pestaña); `onboarding.spec.ts` verifica antes del asistente (helper `registrarYVerificar`).

### Verificación por mutación — 18/18 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | El registro no manda el enlace | 6 |
| Servicio | El enlace dura 1 hora / acepta uno vencido | 1 y 1 |
| Servicio | Verificar no marca usado / no guarda la verificación | 1 y 3 |
| Servicio | Reenviar con el correo ya verificado | 1 |
| Servicio | Restablecer no verifica | 1 |
| Servicio | Un SMTP caído rompe el registro | 1 |
| Dominio | Verificar otra vez cambia la fecha / cambiar la contraseña pierde la verificación | 1 y 1 |
| Filtro | Sin el bloqueo | 2 |
| Filtro | Bloquea también las lecturas / sin la excepción de `/v1/auth/` | 1 y 1 |
| Filtro | Decide con la URI cruda | `unaRutaDisfrazadaDeAuth…` |
| Filtro | Un usuario inactivo escribe | 1 |
| Persistencia | `usar` sin exigir que esté sin usar / el usuario no guarda la verificación | 1 y 1 |
| BFF | El reenvío sin sesión llama al backend | 1 |

### Corrección de la revisión de #228

- **H1 (importante): el reenvío no tenía tope**, y la justificación («llegan a su propio correo») era falsa: sin verificar, el correo puede no ser suyo.
  Quien se registrara con el correo de otro podía pedir enlaces en bucle. Ahora 5 por día (ver Diseño). Tests: servicio
  `losEnlacesDeVerificacionTienenTopePorDia` (el sexto no sale; al vencer el primero, sí), persistencia `cuentaLosEnlacesSinVencerDelUsuario`, REST
  `reenviarPasadoElTopeEs429`, e2e backend `elReenvioTieneTope` (Postgres, 5 correos), Playwright «pasado el tope de enlaces…» (el mock aplica el mismo tope).
- **H2 (menor):** el comentario de `clavesAlta` en el mock había quedado encima de `verificaciones`; cada campo tiene el suyo.
- **H3 (menor): verificar podía deshacer un cambio de contraseña.** Leía el usuario fuera de la transacción y lo guardaba entero. Ahora
  `UsuarioRepository.marcarCorreoVerificado` toca solo esa columna. Test `verificarNoPisaUnaContrasenaCambiadaMientrasTanto` (en rojo con el código anterior)
  y `marcarElCorreoVerificadoSoloTocaEsaColumna` (Postgres).

Mutaciones de la corrección, **13/13 mueren**:

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | Sin tope / deja pasar uno más / tope de 6 | 1, 1 y 1 |
| Persistencia | Contar solo los no usados / los de todos los usuarios / incluir el que vence justo ahora | 1, 1 y 1 |
| REST | `DEMASIADOS_ENLACES` no es `429` | 1 |
| Servicio | Verificar reescribe el usuario leído antes de la transacción (como estaba) / releído dentro / no marca | 1, 1 y 4 |
| Persistencia | Marcar pisa la primera fecha / no hace nada | 1 y 1 |
| Mock | Sin tope | Playwright «pasado el tope…» |

La de «releído dentro de la transacción» **sobrevivió la primera vuelta**: el restablecer simulado solo entraba al gastar el enlace, y una relectura posterior
ya veía la contraseña nueva. El test ahora lo simula también tras cada lectura del usuario, con una contraseña distinta cada vez.

Después de la corrección: suite backend completa en verde, Vitest 318/318, `tsc` y `eslint` limpios, Playwright 145/145.

### Límites conocidos

- El tope de enlaces cuenta y luego inserta sin bloqueo: pedidos simultáneos justo en el límite pueden pasarlo por uno o dos. Alcanza para que el reenvío no
  sirva de bombardeo; no es un límite exacto.
- `token_verificacion` no se limpia: los enlaces vencidos o usados se quedan (pocos por usuario gracias al tope).
- El bloqueo cuesta una lectura de `usuario` por cada escritura con sesión del portal (no con API key).
- El estado «sin verificar» todavía no se ve en el listado de cuentas del backoffice (#180): llega con su columna de estado (#182).

## #181 · Detalle de una cuenta (solo lectura)

**Estado: ✅ hallazgos de la revisión de la PR (#229) corregidos, 22/22 mutaciones verificadas más 1 equivalente.** Rebanada de lectura de la épica #11.

`GET /v1/admin/cuentas/{id}` y la página `/admin/cuentas/[id]`: usuarios, empresas con el estado de su certificado, últimos comprobantes y las últimas
acciones del administrador sobre la cuenta. Sin botones de acción: suspender, impersonar o cambiar de plan llegan en sus issues, cada uno con su
confirmación y su registro en la bitácora.

### Diseño

- **Lo que no sale del backend:** del certificado y de la clave SOL solo se sabe si existen (`cert_pkcs12_enc IS NOT NULL`, usuario **y** clave SOL
  presentes) y hasta cuándo vale el certificado. Ni el PKCS#12, ni la clave, ni el hash de la contraseña, ni la IP o el id del administrador de la bitácora.
- **Cuatro consultas, todas acotadas por la cuenta.** Los comprobantes salen de un `CROSS JOIN LATERAL` por empresa (los 10 más recientes de cada una, luego
  los 10 más recientes en total) para que una empresa con millones de documentos no obligue a ordenarlos todos. Las acciones, de `auditoria_admin`, con el
  índice nuevo `ix_auditoria_cuenta (cuenta_id, ocurrido_en DESC)` (V32); una prueba de plan verifica que lo usa.
- **El último acceso es el de la sesión del portal** (`sesion.created_at`); el uso por API key no cuenta, y se dice en el contrato.
- **Cuenta que no existe → 404 `NO_ENCONTRADO`**; un id que no es UUID → 400 (lo rechaza el backend al convertir la ruta, sin llamar al caso de uso).
- **Certificado «por vencer» = menos de 30 días** (épica #11: «< 30 días»): con 30 justos todavía es vigente, con 29 ya no; el último día de vigencia aún
  vale y el día siguiente es «vencido». Cargado pero sin fecha: se dice «sin fecha de vigencia», no se inventa una. La fecha de hoy es la de Lima.
- **Portal:** Server Component. El id de la URL se valida como UUID antes de pedirlo al backend (`../auth/me` o un espacio no llegan a la llamada); un 404
  del backend es `notFound()`, cualquier otro fallo es una alerta con «Reintentar». El nombre de la cuenta en el listado enlaza al detalle.
- El mock del portal sembró los ids como UUID (`idCuentaMock`) y responde 400 ante un id que no lo es, como el backend; sin eso, quitar el filtro de la
  página seguía dando 404 y nadie lo notaba.

### Tests

- Servicio: `DetalleCuentaAdminServiceTest` (existe / no existe / id nulo).
- Persistencia (`JdbcCuentasAdminRepositoryTest`, Postgres): el detalle completo de una cuenta, sin mezclar usuarios, empresas, comprobantes ni eventos de
  otras; certificado y SOL (cargados, a medias, ausentes); los 10 comprobantes más recientes de varias empresas; las acciones por orden y sin IP; el
  último acceso por usuario; planes de consulta que usan los índices.
- REST (`AdminCuentaControllerTest`) y E2E real (`AdminCuentasE2ETest`, Postgres + Spring completo): abre la clave de plataforma o el JWT de administrador;
  sin credencial, con el JWT del propio cliente, con una API key o con una clave errónea es 401; 404 y 400; y la respuesta no trae secretos ni la IP del
  administrador.
- Portal, Vitest (`admin-cuenta-detalle.test.ts`, 8): estado del certificado en sus bordes (30/29/0/−1 días, sin fecha), `esIdDeCuenta`, `hrefDetalleCuenta`.
- Portal, Playwright (`admin-cuenta-detalle.spec.ts`, 6): sin sesión → login; el enlace del listado; usuarios (verificado / sin verificar), empresas con
  «Vence el … (10 días)», comprobantes y bitácora (quién actuó y una acción desconocida con su código); sin botones de acción; estados vacíos; 404 con un
  id inexistente y con uno que no es UUID.

### Verificación por mutación — 22/22 mueren, 1 equivalente

| Capa | Mutación | Qué muere |
|---|---|---|
| Backend | Las empresas de todas las cuentas / los comprobantes de todas / las acciones de todas | 1 + 1 + 1 |
| Backend | El certificado siempre «cargado» | 1 |
| Backend | La clave SOL con solo uno de sus dos campos | 1 |
| Backend | Los comprobantes o las acciones, los más viejos en vez de los más nuevos | 1 + 1 |
| Backend | El último acceso, el más viejo | 1 |
| Vitest | «Por vencer» con 30 días / «vencido» el último día / umbral 31 | 1 + 1 + 1 |
| Vitest | El UUID sin ancla final / el enlace a `/admin/cuenta/` / una fecha inventada si falta | 1 + 1 + 1 |
| Playwright | El nombre del listado no enlaza al detalle | 1 |
| Playwright | Correo siempre «Verificado» | 1 |
| Playwright | El actor siempre «Administrador» / siempre «Clave de plataforma» | 1 + 1 |
| Playwright | Una acción desconocida se esconde | 1 |
| Playwright | Un 404 del backend no es `notFound()` / sin el filtro de UUID | 1 + 1 |
| Playwright | El mock siembra un rol que no es del dominio (`USER`) — revisión de #229 | 1 |
| Playwright | **Equivalente:** `return null` en vez de `redirect` sin sesión | sobrevive |

Dos incidentes de la propia verificación, para que no se repitan:

- **El actor de la bitácora sobrevivía** (forzarlo a «Administrador»): el mock solo sembraba un evento. Ahora siembra uno de la clave de plataforma y otro
  con una acción que el portal no conoce, y la spec los afirma.
- **El script de mutaciones de Playwright no restauraba los archivos** (usaba `$2` después de `shift`): la primera tanda de resultados quedó inválida porque
  las mutaciones se acumulaban. Se comprobó que el diff fuera solo las mutaciones, se restauró, se arregló el script y se repitió la tanda completa.

La mutación equivalente es defensa en profundidad: el layout del panel ya redirige al login antes de renderizar la página, así que la línea no se puede
distinguir desde fuera (el listado tiene la misma). Se conserva porque también estrecha el tipo de `access`.

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest`, `AdminCuentasE2ETest` y los tests de migraciones).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 326/326.
- Playwright completo con el código final de la página: 149/150 en la primera corrida; la que cayó (`emision.spec.ts`, #115) corría mientras Gradle ejecutaba
  mutaciones y pasó 48/48 al repetir el archivo tres veces. Es de carga de la máquina, no de este cambio.

### Corrección de la revisión de #229

Tres hallazgos menores, todos del portal:

- **H1:** el comentario de `CuentaAdminMock` había quedado encima de `idCuentaMock`; cada uno tiene el suyo.
- **H2: el mock no seguía el contrato.** Ante un id que no es UUID respondía `400 VALIDACION`, cuando el backend responde `400 PARAMETRO_INVALIDO`
  (`GlobalExceptionHandler`), y sembraba un usuario con `rol: "USER"`, que no existe en `Rol` (`ADMIN`, `EMISOR`, `LECTURA`). Ahora usa
  `PARAMETRO_INVALIDO` y `EMISOR`. La spec del detalle comprueba el rol de cada usuario: estaba en rojo con el mock anterior (mostraba «USER»).
- **H3:** el rótulo «Correo» del encabezado estaba escrito en el componente; ahora sale de `messages.admin.detalle.correo`, como sus vecinos.

Después: Playwright de cuentas 16/16 (detalle y listado), Vitest 326/326, `tsc` y ESLint limpios.

### Límites conocidos

- Solo lectura: sin acciones sobre la cuenta; cada una llega en su propio issue.
- Los últimos 10 comprobantes y las últimas 10 acciones no se paginan: es un vistazo, no un historial.
- El último acceso no cuenta el uso por API key.

## #185 · Listado de empresas de la plataforma

**Estado: 🔧 implementado, 42/42 mutaciones verificadas — falta la revisión de la PR.** Rebanada de lectura de la épica #11, sección 2.1.

`GET /v1/admin/empresas` y la página `/admin/empresas`: todas las empresas con su cuenta, entorno, estado del certificado y días restantes,
credenciales SOL, series, comprobantes del mes y última emisión; filtros por entorno y por estado del certificado; paginado. Las que tienen el
certificado vencido o por vencer se distinguen a simple vista.

### Diseño

- **Una sola regla para el certificado, en SQL, que usan la columna y el filtro.** Un `CASE` calcula `SIN_CERTIFICADO` / `SIN_FECHA` / `VENCIDO` /
  `POR_VENCER` / `VIGENTE`; el mismo texto va en el `SELECT` y en el `WHERE`, así lo que el filtro deja pasar es exactamente lo que la fila dice ser (un
  test lo comprueba para cada estado). Vencido es antes de hoy (el último día todavía vale, como en `Tenant`); por vencer, menos de 30 días (épica #11):
  con 30 justos todavía es vigente.
- **«Hoy» y «el mes» los pone la aplicación, no la base.** El servicio toma la fecha del `Clock` (America/Lima) y se la pasa al repositorio: con las 21:00
  del 30 de septiembre en Lima, «hoy» es el 30 y no el 1 de octubre de UTC. Un test fija ese reloj; otro cambia el «hoy» que se pasa y comprueba que el
  estado y el mes lo siguen.
- **«Comprobantes del mes» y «última emisión» salen de la fecha de emisión**, no de `created_at`: así las dos se resuelven por el índice que ya existe,
  `(tenant_id, fecha_emision)` (una prueba de plan lo verifica), sin recorrer los documentos de cada empresa. «Del mes» es de la fecha de emisión del
  1 al último día del mes de hoy, todos los documentos; «series» son las activas.
- **Incluye las empresas sin cuenta.** Las que da de alta una integración (`POST /v1/admin/tenants`) no tienen `cuenta_id`: el listado usa `LEFT JOIN` y
  esas filas salen sin los campos de la cuenta. Con `JOIN` desaparecerían del listado en silencio.
- Una sola consulta; los números de cada fila son subconsultas correlacionadas que Postgres calcula solo para las filas de la página. Índice nuevo
  `ix_tenant_alta (created_at DESC, id)` (V33) para el orden, con su prueba de plan.
- **Nada secreto:** del certificado y de las credenciales solo se informa si están (la clave SOL cuenta cargada solo con usuario y clave). Un valor de
  filtro que no existe responde `400`, no se ignora.
- **Portal:** los dos filtros viven en la URL (compartible); un filtro inventado en la URL se descarta antes de llamar al backend; cambiar un filtro
  navega a la página 1; una página fuera de rango se corrige a la última. La etiqueta del certificado es la misma que usa el detalle de una cuenta
  (`etiquetas.tsx`, extraída), así las dos pantallas lo muestran idéntico. Las filas con el certificado vencido o por vencer se tiñen.
- El menú activa «Empresas» (estaba deshabilitado con «Pronto») y la cabecera la ubica bajo «Clientes».

### Tests

- Servicio (`ListarEmpresasAdminServiceTest`, 3): el «hoy» de Lima, listar y contar con el mismo «hoy», sin filtro.
- Persistencia (`JdbcEmpresasAdminRepositoryTest`, 19, Postgres): los siete bordes del certificado (sin certificado, sin fecha, ayer, hoy, 29, 30 y 365
  días), el estado cambia con el «hoy» que se pasa, una fecha suelta sin certificado no cuenta, filtro y columna coinciden para cada estado, entorno,
  filtros combinados con su total, series activas, mes (30 de septiembre y 1 de noviembre fuera; 1 y 31 de octubre dentro), última emisión, empresa sin
  cuenta, paginado y los dos planes de consulta.
- REST (`AdminEmpresaControllerTest`, 7) y E2E real (`AdminEmpresasE2ETest`, 9, Postgres + Spring completo con el reloj real): columnas, campos ausentes,
  filtros y total, un valor de filtro inexistente es 400, tope de página, ningún secreto en la respuesta, una empresa de integración sin cuenta, y las
  puertas: abre la clave de plataforma o un administrador; sin credencial, con el JWT de un cliente, con una API key o con una clave errónea es 401.
- Portal, Vitest: `admin-empresas.test.ts` (14: parámetros de la URL, `href`, fuera de rango, mapeo del estado), `empresas-tabla.test.tsx` (8: a dónde
  navega cada selector, la paginación conserva filtros y tamaño, los estados vacíos) y la miga de `/admin/empresas`.
- Portal, Playwright (`admin-empresas.spec.ts`, 15): sin sesión → login; menú → listado con sus columnas; vencida y por vencer se distinguen (atributo y
  tinte); cada estado trae sus empresas; el borde de los 30 días (30 vigente, 29 por vencer); entorno y combinado; los dos filtros vuelven a la página 1;
  «Quitar filtros»; URL compartible; un filtro inventado se ignora; sin resultados; segunda página y página fuera de rango; la cuenta enlaza a su detalle;
  el menú marca «Empresas».

### Verificación por mutación — 42/42 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Backend SQL | Vencido también el último día / por vencer con 30 días / «sin certificado» nunca | 3 + 2 + 3 |
| Backend SQL | Días con el signo al revés / fecha o días visibles sin certificado | 2 + 1 + 1 |
| Backend SQL | Series inactivas cuentan / mes sin el día 1 / mes con el 1 del siguiente | 1 + 1 + 2 |
| Backend SQL | Última emisión la más vieja | 1 |
| Backend SQL | `JOIN` en vez de `LEFT JOIN` (las de integración desaparecen) | 15 |
| Backend SQL | Orden ascendente / página salteada | 2 + 16 |
| Backend SQL | Filtro de entorno ignorado / filtro de certificado invertido | 3 + 4 |
| Servicio | El «hoy» del reloj del sistema / contar con otro «hoy» | 1 + 1 |
| REST | El controlador pierde el entorno / el certificado / el total sin filtro / sin tope de página | 2 + 2 + 1 + 1 |
| REST | El DTO cruza series y comprobantes del mes | 1 |
| Vitest | Filtro inventado pasa / ruta base con `pagina=1` / query sin certificado / href sin entorno | 2 + 3 + 1 + 2 |
| Vitest | Fuera de rango en la última / vigente como por vencer / sin guarda de fecha / página decimal / miga de otra ruta | 1 + 1 + 1 + 1 + 1 |
| Vitest (componente) | Cualquiera de los dos filtros sin volver a la página 1 | 1 + 1 |
| Playwright | Vencida o por vencer sin tinte | 1 + 1 |
| Playwright | La cuenta sin enlace al detalle / «Sin cuenta» sin decirlo / «Nunca emitió» roto | 1 + 1 + 9 |
| Playwright | Sin resultados dice «no hay empresas» / «Quitar filtros» nunca aparece | 1 + 1 |
| Playwright | Sin la corrección de página fuera de rango / el menú no lleva a Empresas | 1 + 2 |
| Playwright | El detalle de una cuenta pierde el estado del certificado (regresión de la etiqueta compartida) | 1 |

Un hallazgo de la propia verificación: **no volver a la página 1 al cambiar un filtro sobrevivía** (los dos selectores). Lo tapaba la corrección de
página fuera de rango: con 8 o 5 resultados, `pagina=2` se redirige sola a la 1 y la URL queda limpia, así que el e2e no lo veía. Solo se nota cuando el
resultado todavía tiene varias páginas, y eso no cabe en la siembra de 12 empresas. Se cubrió con un test de componente que mira a dónde navega cada
selector (`empresas-tabla.test.tsx`) y se repitieron las dos mutaciones: mueren.

Otros dos tropiezos, ya resueltos: los RUC de prueba del primer E2E no tenían dígito verificador válido (422 al dar de alta la empresa), y dos tests
existentes usaban `/admin/empresas` como ejemplo de «ruta sin miga»; ahora usan `/admin/planes`, que sigue sin tenerla.

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest`, `AdminEmpresasE2ETest` y las migraciones hasta V33).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 349/349 · Playwright completo 165/165, sin nada más corriendo en la máquina.
- La mutación C5 de Playwright salió con el conteo de fallos en blanco en la tanda; se repitió a mano con el log a la vista: muere porque el enlace llevó a
  `/admin/cuentas` en vez de al detalle de la cuenta.

### Límites conocidos

- Sin búsqueda de texto (RUC o razón social): el issue pide filtros por entorno y estado del certificado. La búsqueda por RUC y razón social ya existe en el
  listado de cuentas.
- «Credenciales SOL cargadas», no «validadas»: validarlas depende de la prueba de conexión con SUNAT (#14).
- Los comprobantes del mes cuentan todos los documentos de la empresa con fecha de emisión en el mes, sea cual sea su estado; el consumo facturable por plan
  se definirá con los planes.
- El listado calcula los números de cada fila en el momento: la lectura sigue siendo por índice, pero un contador materializado sería lo siguiente si el
  listado se vuelve lento con muchísimas empresas con millones de documentos.

## #186 · Detalle de una empresa (solo lectura)

**Estado: 🔧 implementado, 47/47 mutaciones verificadas — falta la revisión de la PR.** Rebanada de lectura de la épica #11, sección 2.1.

`GET /v1/admin/empresas/{id}` y la página `/admin/empresas/[id]`: lo que ve el dueño de la empresa, en solo lectura y sin secretos. Sin botones de acción
(cambiar el entorno, revocar API keys, probar la conexión): llegan en su issue.

### Diseño

- **Qué trae:** datos fiscales y domicilio, estado del certificado y de las credenciales SOL, series, establecimientos anexos, API keys, personalización del
  PDF, los 10 comprobantes más recientes con el detalle del CDR de SUNAT (código, descripción, observaciones, intentos y último error), los últimos 20
  cambios de estado de esos comprobantes y el outbox pendiente de la empresa (el total y las 10 próximas tareas).
- **Qué no sale nunca del backend:** el contenido del certificado y de las credenciales SOL (solo si están), el secreto y el **hash** de las API keys (solo
  el prefijo; la columna `key_hash` ni se lee), y **dónde está guardado el logo** (solo si hay uno). Lo comprueban la persistencia, el DTO y el E2E con la API
  key real que entrega el alta: ni el secreto, ni su hash, ni la ruta del logo aparecen en la respuesta.
- **El estado del certificado es el del listado (#185): una sola regla.** El `CASE` y las expresiones de vigencia, días y credenciales SOL pasaron a
  constantes que usan el listado y el detalle; un test compara las dos pantallas para cada borde (sin fecha, ayer, hoy, 29 y 30 días). «Hoy» lo pone el
  servicio con el reloj de Lima, como en el listado.
- **Los eventos son los de los comprobantes que se muestran**, no la historia entera de la empresa: se piden por el id de esos comprobantes (a lo más 10) y
  por un índice nuevo. Con el comprobante número 11 en adelante no hay eventos en esta pantalla; se dice en los límites.
- **Dos índices nuevos (V34):** `api_key (tenant_id, created_at DESC)` y `evento_documento (documento_id, ocurrido_en DESC)`. Sin ellos cada lectura recorría la
  tabla entera de todas las empresas. El segundo también le sirve a la consulta de eventos de un comprobante que ya usa el portal de los clientes
  (`JdbcComprobanteRepository.eventosDe`); eso es por la forma de la consulta, no se midió. El outbox no necesita índice: se vacía al completar cada tarea.
- Cuenta ausente (empresas de integración) y domicilio ausente se dicen con nulos y se muestran como «Sin cuenta» y «Todavía no declaró su domicilio fiscal»;
  una empresa sin nada cargado dice en cada sección que no hay nada, en vez de dejar huecos.
- **Portal:** Server Component. El id de la URL se valida como UUID antes de pedirlo al backend; un 404 del backend es `notFound()` y cualquier otro fallo es una
  alerta con «Reintentar». La razón social del listado enlaza al detalle y la cuenta de la empresa al detalle de la cuenta.
- **Refactor de paso, con sus pruebas:** `Seccion` y `Vacio` salieron de `cuenta-detalle.tsx` a un componente compartido; `esUuid` salió a su módulo
  (`lib/uuid.ts`); los dos handlers del mock que sumaban días a mano usan `sumarDias`; y los ids sembrados de las empresas pasaron a UUID, porque la página
  descarta lo que no lo sea.

### Tests

- Servicio (`DetalleEmpresaAdminServiceTest`, 4): el detalle, el «hoy» de Lima, no encontrada y un id nulo (sin consultar).
- Persistencia (`JdbcEmpresaDetalleAdminRepositoryTest`, 24, Postgres): datos fiscales y de la cuenta; empresa sin cuenta ni domicilio; certificado y SOL
  (también a medias); el estado coincide con el del listado en cada borde; PDF con y sin logo; series, establecimientos y API keys sin mezclar empresas (la key
  revocada, con su fecha); los 10 comprobantes más recientes; el CDR con observaciones, sin observaciones y sin CDR; los eventos de los comprobantes
  recientes (no los de uno más viejo ni los de otra empresa), con tope y sin estado anterior; el outbox con su total y sus 10 próximas; y los planes de
  consulta de los cuatro índices.
- REST (`AdminEmpresaControllerTest`, 13 en total: 6 del detalle) y E2E real (`AdminEmpresasE2ETest`, 14 en total: 5 del detalle, con Postgres y Spring
  completo): el detalle completo, la API key que no suelta su secreto ni su hash ni el logo, 404, 400, un administrador con sesión, y 401 sin credencial, con el
  JWT del propio dueño, con una API key o con una clave errónea.
- Portal, Vitest: `admin-empresa-detalle.test.ts` (3), `uuid.test.ts` (2), `empresa-detalle.test.tsx` (10: la dirección y sus separadores, singular y plural
  del outbox, «Inicio», estado desconocido, CDR con y sin observaciones, el prefijo, sin botones) y la miga del detalle.
- Portal, Playwright (`admin-empresa-detalle.spec.ts`, 11): sin sesión; listado → detalle; datos fiscales y certificado; series, establecimientos y API keys;
  PDF; comprobantes con su CDR (aceptado con observaciones, rechazado con su error, sin respuesta); eventos y outbox; solo lectura; la cuenta enlaza; una
  empresa de integración sin nada cargado; 404 con un id inexistente y con uno que no es UUID.

### Verificación por mutación — 47/47 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Backend SQL | Series / establecimientos / API keys / comprobantes / próximas del outbox de todas las empresas | 1 + 1 + 1 + 2 + 1 |
| Backend SQL | Total del outbox de todas las empresas | 1 |
| Backend SQL | API key revocada sin fecha / logo siempre cargado / domicilio siempre presente | 1 + 1 + 1 |
| Backend SQL | Más de 10 comprobantes / los más viejos / más de 20 eventos / los eventos más viejos | 2 + 3 + 1 + 2 |
| Backend SQL | CDR siempre presente / CDR sin observaciones / outbox al revés | 1 + 1 + 1 |
| Backend SQL | `JOIN` en vez de `LEFT JOIN` (la empresa sin cuenta no se abre) | 18 |
| Servicio | El «hoy» del reloj del sistema / consultar con un id nulo | 1 + 1 |
| REST | Etiqueta del comprobante sin ceros / el DTO pierde las API keys / CDR siempre / código y nombre del establecimiento cruzados / pierde el logo | 1 + 1 + 1 + 1 + 1 |
| Vitest | Cualquier id vale / href equivocado / UUID sin ancla inicial / sin ancla final | 1 + 1 + 1 + 1 |
| Vitest (componente) | Otro separador / separadores colgando / singular / total siempre / sin «Inicio» / estado desconocido escondido | 1 + 1 + 1 + 1 + 1 + 1 |
| Vitest (componente) | Observaciones vacías sin decirlo / CDR siempre / prefijo sin puntos | 1 + 1 + 1 |
| Playwright | Sin nombre comercial / la cuenta sin enlace / último error oculto / serie inactiva como activa | 1 + 1 + 1 + 1 |
| Playwright | Siempre con logo / outbox vacío sin decirlo / el listado sin enlace al detalle | 1 + 1 + 1 |
| Playwright | Un 404 no es `notFound()` / sin el filtro de UUID | 1 + 1 |
| Playwright | La sección compartida pierde su nombre (regresión del refactor, en la spec del detalle de cuenta) | 1 |

Qué se aprendió al verificar:

- **El script de mutaciones de Playwright ahora distingue «murió por el test» de «no arrancó».** Un fallo sin conteo de tests fallidos ya no se cuenta: sale
  «REVISAR» y deja el log. En esta tanda lo detectó una vez (E1): el servidor de desarrollo no arrancó (error resolviendo una fuente de Google y *timeout* de
  60 s). Repetida sola, muere por el test. Es el mismo defecto que en #185 dejó una mutación con el conteo en blanco.
- Dos errores de mis tests, no del código: `getAllByRole("row")` recorría todas las tablas de la página, y «Sin cargar» aparece dos veces en una empresa sin
  certificado ni credenciales (una por cada etiqueta). Los dos se acotaron a su sección.

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest`, `AdminEmpresasE2ETest` y las migraciones hasta V34).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 365/365 · Playwright completo 176/176, sin nada más corriendo en la máquina.

### Límites conocidos

- Solo lectura: sin acciones sobre la empresa; cada una llega en su propio issue.
- Los comprobantes recientes (10), los cambios de estado (20) y las tareas del outbox (10) no se paginan: es un vistazo para diagnosticar, no un historial. Los
  eventos son solo los de esos comprobantes recientes.
- El outbox solo muestra lo pendiente o lo que está fallando (cada tarea completada se borra); no hay historial de envíos.
- No se muestra el logo, solo si hay uno; ni el contenido del certificado ni de las credenciales, por diseño.

## #182 · Suspender y reactivar una cuenta

**Estado: ✅ hallazgos de la revisión de la PR (#232) corregidos, 75/75 mutaciones verificadas.** Primera acción de escritura del backoffice sobre un cliente.

`POST /v1/admin/cuentas/{id}/suspender` y `/reactivar`. Una cuenta suspendida no entra al portal y ninguna de sus empresas emite por API: responden
`403 CUENTA_SUSPENDIDA`, un código propio. No se borra nada, y reactivar lo devuelve todo con las mismas credenciales.

### Diseño

- **Dónde se corta.** Hay cuatro puertas, todas con el estado de la cuenta leído **en la base en cada petición**, no del token:
  1. `JwtFilter`: toda petición del portal (lectura y escritura), también con una sesión que ya estaba abierta. Las rutas de la propia sesión (`/v1/auth/**`:
     quién soy, cerrar sesión) siguen funcionando para que el portal pueda decirle «tu cuenta está suspendida». Va antes que la verificación del correo.
  2. `ApiKeyFilter`: cualquier llamada con la API key de cualquier empresa de la cuenta, para ver y para emitir.
  3. El login: después de comprobar la contraseña, para no revelar el estado de una cuenta a quien no se identificó.
  4. El refresh: **sin revocar la sesión**, así que al reactivar la misma sesión vuelve a servir.
- **Los filtros rechazan primero lo inválido.** Un token o una API key inválidos dan 401 sin consultar el estado: nadie se entera de qué cuentas están suspendidas.
- **Los documentos ya emitidos siguen enviándose a SUNAT.** El outbox no pasa por los filtros: cortarlos los dejaría fuera del plazo de envío (`FUERA_DE_PLAZO`, estado terminal: hay que
  emitir de nuevo), con consecuencia para el cliente. Suspender corta emitir y consultar, no el envío de lo ya emitido.
- **Las empresas de integración, sin cuenta, nunca están suspendidas** (no hay a quién suspender).
- **El cambio es condicional y atómico.** `UPDATE … WHERE suspendida_en IS NULL` (y `IS NOT NULL` para reactivar) devuelve cuántas filas cambió: de dos pedidos a la vez
  solo uno lo logra, y el otro recibe `409 CUENTA_YA_SUSPENDIDA` / `CUENTA_NO_SUSPENDIDA`. Va en **la misma transacción que el registro de la bitácora**
  (`SUSPENDER_CUENTA` / `REACTIVAR_CUENTA`, con el motivo si lo hubo): si la bitácora falla, la cuenta tampoco queda suspendida.
- **El estado es una fecha** (`cuenta.suspendida_en`, V35; nulo = activa), así el estado y el «desde cuándo» no pueden contradecirse. El listado y el detalle de la
  cuenta (#180, #181) dicen `ESTADO` y `suspendida_en`; una cuenta suspendida **sigue apareciendo** para poder reactivarla.
- **El motivo es opcional**, de hasta 200 caracteres (recortado), y va a la bitácora; más largo es `422 MOTIVO_INVALIDO` y no suspende.
- **Portal (backoffice):** columna «Estado» en el listado (fila teñida si está suspendida); en el detalle, el estado, «Suspendida desde …» y **un solo botón según el
  estado**. Suspender abre un diálogo de confirmación que dice a cuántas empresas alcanza y qué hace y qué NO hace (no borra, no corta los envíos de lo ya emitido, es
  reversible) — el mismo patrón que la baja de comprobantes. Las acciones pasan por dos rutas del BFF que validan el id y reenvían la IP real del administrador (#208).
- **Portal (cliente):** una página pública `/cuenta-suspendida` que explica la suspensión («no se borró nada») y deja cerrar la sesión; la lleva el middleware cuando el refresh
  responde `CUENTA_SUSPENDIDA` (**sin limpiar las cookies**, para que al reactivar siga la misma sesión) y el layout privado cuando el listado de empresas lo responde. El login
  muestra el mensaje del backend.

### Tests

- Servicio (`SuspenderCuentaServiceTest`, 13): suspender y reactivar, nada se borra, la bitácora (acción, actor, motivo recortado, sin motivo, misma transacción, falla →
  falla la acción), conflictos sin registro de bitácora, cuenta inexistente o nula, motivo demasiado largo y de exactamente el máximo.
- Login y refresh (`AutenticarUsuarioServiceTest`, 21 en total, 5 nuevos): una cuenta suspendida no inicia sesión ni abre una; con la contraseña errónea no se revela; el refresh
  no la renueva y **no revoca la sesión**; reactivada vuelve; suspender una cuenta no afecta a las demás.
- Persistencia (`JdbcSuspensionRepositoryTest`, 13, Postgres): el cambio atómico y condicional, dos veces seguidas, cuenta inexistente, no toca a otras cuentas; empresas de una
  cuenta suspendida, de otra, reactivadas, sin cuenta y que no existen. `JdbcCuentasAdminRepositoryTest` (+3): listado y detalle dicen desde cuándo, y una suspendida sigue apareciendo.
- Filtros: `JwtFilterTest` (+7: lectura y escritura, con la empresa elegida, `/v1/auth/**` sigue, reactivada vuelve con la misma sesión, otra cuenta no, token inválido sin
  consultar, la suspensión va antes que el correo) y `ApiKeyFilterTest` (+5: ver y emitir con una key, reactivada, otra empresa no, key inválida o inactiva sin consultar, una
  petición ya autenticada por JWT no vuelve a consultar).
- REST (`AdminCuentaControllerTest`, +12): estado en el listado y el detalle, actor y motivo, sin cuerpo, conflictos 409, 404, 422, 401 sin actor y 400 con un id mal formado.
- **E2E real** (`SuspensionE2ETest`, 9, Spring completo + Postgres + los filtros reales): una cuenta con **dos empresas y dos API keys**; antes todo funciona; suspendida, se corta el portal
  (con la sesión ya abierta, lectura y escritura), el login, el refresh y la API de las dos empresas (ver y emitir) con `CUENTA_SUSPENDIDA`; otra cuenta no se entera; no se borró ninguna fila ni
  se revocó ninguna key ni sesión; **reactivada, vuelve con el mismo JWT, el mismo refresh y las mismas API keys**; la bitácora (actor, motivo, cuenta); conflictos; listado y detalle;
  y que nadie más puede suspender (sin credencial, JWT del dueño, API key, clave errónea).
- Portal, Vitest: BFF (`suspender` 9 y `reactivar` 5), `acciones-de-cuenta` (14: qué botón se ofrece, qué dice el diálogo, qué se envía, errores, 409, corte de red, doble clic), `cuentas-tabla` (+4),
  `cuenta-suspendida` (predicado, 4; página, 4), `layout` privado (6) y `middleware` (+3).
- Portal, Playwright (`admin-suspension.spec.ts`, 11): el listado distingue suspendidas; una suspendida sigue en la búsqueda; el detalle según el estado; **el ciclo completo
  suspender → listado → reactivar**, con cancelar y confirmación; un 409 porque otro administrador ya la suspendió; el tope del motivo y el alcance; el BFF sin sesión y con un id inválido;
  el login de un cliente suspendido; y la página de cuenta suspendida.

### Verificación por mutación — 74/74 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | Suspender dos veces / reactivar una activa no es conflicto | 1 + 1 |
| Servicio | La bitácora de suspender dice «reactivar» y al revés | 1 + 1 |
| Servicio | El motivo pierde su prefijo / no se recorta / en blanco se guarda vacío | 1 + 1 + 1 |
| Servicio | El máximo del motivo se rechaza / una cuenta inexistente se acepta / reactivar sin comprobar que exista | 1 + 1 + 1 |
| Servicio | Reactivar devuelve una fecha / suspender devuelve otra hora | 1 + 1 |
| Login y refresh | El login no mira la suspensión / el refresh no la mira | 1 + 1 |
| Login y refresh | Otro código de error / se consulta el usuario en vez de la cuenta | 2 + 2 |
| Repositorio | Suspender o reactivar sin condición | 1 + 3 |
| Repositorio | Suspender siempre dice que cambió / una cuenta suspendida suspende todas las empresas / la suspendida es la activa | 2 + 2 + 4 |
| Repositorio | El listado o el detalle pierden la fecha | 1 + 1 |
| `JwtFilter` | No corta / corta solo las rutas de sesión / corta solo las escrituras | 5 + 6 + 1 |
| `JwtFilter` | Consulta al usuario y no a la cuenta / responde 401 / responde otro código | 4 + 2 + 2 |
| `ApiKeyFilter` | No corta / responde 401 / responde otro código | 3 + 1 + 1 |
| Controlador y DTO | Suspender pierde el motivo / reactivar suspende / los conflictos no son 409 | 1 + 2 + 2 |
| Controlador y DTO | Estado invertido / el listado, el detalle o la acción pierden la fecha | 6 + 1 + 1 + 1 |
| E2E | La suspensión responde 422 y no 403 / las empresas nunca están suspendidas | 1 + 1 |
| Vitest | Predicado: cualquier 403 / cualquier código | 1 + 1 |
| Vitest | Middleware: no reconoce / limpia la sesión / manda al login | 1 + 1 + 1 |
| Vitest | Layout: no redirige / redirige cualquier error | 2 + 1 |
| Vitest (componente) | Motivo sin recortar / sin tope / doble clic / 409 sin recargar / corte de red / rutas cruzadas | 1 + 1 + 1 + 1 + 1 + 3 |
| Vitest (componente) | Reactivar pide motivo / «1 empresa» mal dicho / sin empresas sin decir / no recarga tras confirmar | 1 + 1 + 1 + 2 |
| Vitest (BFF) | Suspender: id cualquiera / motivo que no es texto / sin IP / sin sesión | 1 + 1 + 1 + 1 |
| Vitest (BFF) | Reactivar: id cualquiera / sin IP / sin sesión | 1 + 1 + 1 |
| Vitest | Fila suspendida sin tinte / listado sin estado / etiqueta «Suspendida» en verde / página del cliente sin explicación | 1 + 1 + 1 + 1 |
| Playwright | El detalle no dice desde cuándo / acciones siempre «activa» / alcance siempre cero / sin etiqueta de estado / la página del cliente no cierra sesión | 2 + 3 + 1 + 4 + 1 |

Dos sobrevivían en la primera tanda y eran huecos reales de mis pruebas, no equivalentes:

- **Quitar la guardia anti doble envío (`if (enviandoRef.current) return`).** Mi test hacía dos clics seguidos, pero `fireEvent` aplica el estado entre uno y otro: el botón ya estaba
  `disabled` y la guardia nunca se ejercitaba. Ahora los dos clics van dentro de un mismo `act`, que es cuando el segundo manejador ve el `enviando` viejo; sin la guardia se envían dos pedidos.
- **Poner la etiqueta «Suspendida» en verde.** Nadie comprobaba el color, que es lo que el administrador lee de un vistazo. Ahora el test comprueba rojo y verde, y que no se crucen.

Otro hallazgo, del primer E2E del portal: un test esperaba el texto propio del portal («ya estaba suspendida») y el diálogo muestra el del backend («La cuenta ya está suspendida»), que es lo correcto
(el backend es quien sabe). Se corrigió la prueba, no el código.

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest`, `SuspensionE2ETest` y las migraciones hasta V35).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 414/414 · Playwright completo 187/187, sin nada más corriendo en la máquina.

### Corrección de la revisión de #232

- **H1: la bitácora del detalle de cuenta mostraba en crudo las dos acciones de esta PR.** El catálogo `admin.detalle.eventos.acciones` no tenía
  `SUSPENDER_CUENTA` ni `REACTIVAR_CUENTA`, y la spec de #181 afirmaba ver «SUSPENDER_CUENTA» (ahí era el ejemplo de acción desconocida). Ahora se leen
  «Suspendió la cuenta» y «Reactivó la cuenta»; el ejemplo de acción desconocida pasa a ser `ACCION_FUTURA`. La spec, que pide «Suspendió la cuenta» y que no
  aparezca el código, estaba en rojo antes del cambio (cuenta como la mutación 75: «la suspensión sin entrada en el catálogo»).
- **H2:** los mocks de suspender y reactivar respondían `400 VALIDACION` ante un id que no es UUID; ahora `400 PARAMETRO_INVALIDO`, como el backend.

Después: Playwright de cuentas, detalle y suspensión 27/27, Vitest 414/414, `tsc` y ESLint limpios.

### Límites conocidos

- **Un cliente con la sesión abierta puede ver un error genérico unos minutos.** El layout lo detecta, pero las páginas privadas se renderizan en paralelo con él y una puede fallar antes; el token
  de acceso dura 15 minutos y al renovarse el middleware ya lo lleva a la página de suspensión. Los formularios sí muestran el mensaje (`CUENTA_SUSPENDIDA`). Cerrarlo del todo pide revisar cada página
  privada o una llamada más por render; no se hizo aquí.
- **Una consulta a la base más por cada petición autenticada** (por clave primaria) en `JwtFilter` y en `ApiKeyFilter`. Si el volumen la hace notar, el siguiente paso es un caché corto del estado, con la
  contrapartida de que una suspensión tardaría ese tiempo en notarse.
- **Suspender corta todo el uso de la API de la cuenta**, no solo la emisión (también consultar y descargar): es lo más simple y lo más estricto. El issue pide cortar la emisión; si hiciera falta dejar
  descargar, se afloja en `ApiKeyFilter`.
- **El mensaje manda a «soporte» sin enlace**: el canal de soporte llega con #200 (servicio externo).
- Sin filtro por estado en el listado todavía (el estado se ve, pero no se filtra por él), ni «sin verificar» como estado de cuenta: ese estado es del correo de cada usuario (#22).

## #183 · Soporte de acceso: mandar el correo de restablecimiento y reenviar la verificación

**Estado: 🔧 implementado, 61/61 mutaciones verificadas — falta la revisión de la PR.** El administrador dispara el correo; **nunca ve ni fija una contraseña**: el usuario elige la suya con el enlace.

`POST /v1/admin/cuentas/{cuentaId}/usuarios/{usuarioId}/restablecimiento` y `/verificacion`. Cada una crea un enlace de un solo uso, se lo manda al correo del usuario y
responde **a quién** se le mandó (`usuario_id`, `correo`): ni el enlace ni su token salen en la respuesta, ni en la bitácora.

### Diseño

- **El mismo correo que el del propio usuario.** El asunto y el texto salen de un solo lugar (`CorreosDeAcceso`), que ahora usan también el «olvidé mi contraseña» y la verificación del
  registro (`AutenticarUsuarioService`): lo que dispara el administrador es exactamente lo que el usuario recibiría por su cuenta, con la misma vida del enlace (1 hora el restablecimiento,
  24 horas la verificación).
- **El usuario se busca dentro de la cuenta de la ruta.** Un usuario de otra cuenta responde `404 NO_ENCONTRADO` aunque exista: la pantalla de una cuenta no alcanza a los de otra.
- **Un usuario desactivado no recibe nada** (`409 USUARIO_INACTIVO`): mandarle un enlace que no puede usar sería ruido. Reenviar la verificación a quien ya la tiene es `409 CORREO_YA_VERIFICADO`;
  restablecer sí vale con el correo verificado.
- **Sin correo configurado no se da nada por enviado.** Sin SMTP el adaptador solo escribe en el log y no falla; contestar «enviado» sería mentir. `CorreoSender.entregaDeVerdad()` lo dice y, si es
  falso, `503 CORREO_NO_CONFIGURADO` **antes de crear nada** (misma lección que #218).
- **Enlace y bitácora van en la misma transacción; el correo sale después.** Si la bitácora falla no sale ningún correo y no queda un enlace que nadie sabe quién pidió. Si el servidor de correo
  rechaza el envío, `502 CORREO_NO_ENVIADO`: el intento ya quedó en la bitácora (`ENVIAR_RESTABLECIMIENTO` / `REENVIAR_VERIFICACION`, con `usuario=<correo>`) y el enlace sin usar vence solo.
- **No se tocan sesiones ni contraseñas.** El restablecimiento no revoca nada ni cambia la clave: la actual vale hasta que el usuario use el enlace. Los dobles del test lanzan `AssertionError` si el
  servicio toca una sesión, guarda un usuario o consume un enlace.
- **Portal:** en la tabla de usuarios del detalle de la cuenta, por fila, **Restablecer contraseña** y (solo si el correo no está verificado) **Reenviar verificación**; un usuario desactivado no
  tiene ninguna. Cada acción abre un modal que dice a quién le llega, qué hace y que el administrador no ve la contraseña; el resultado («Correo … enviado a …» o el error del backend) se queda en el
  modal hasta que se cierre, no se cierra con Escape mientras se envía, y tras un corte de red se avisa que **no se sabe si salió** y se manda a mirar la bitácora (reintentar a ciegas mandaría dos
  correos). Pasan por dos rutas del BFF que validan los dos ids, exigen sesión de administrador y reenvían la IP real del administrador (#208).

### Tests

- Servicio (`SoporteDeAccesoServiceTest`, 14): el enlace y su vida, el correo al usuario, la bitácora sin el enlace ni el token, misma transacción y el correo después, nunca se fija ni se muestra una
  contraseña, si la bitácora falla no sale correo, usuario de otra cuenta / inexistente / id nulo, desactivado, sin SMTP (no se crea nada), servidor de correo que rechaza (queda en la bitácora),
  verificación (24 h, bitácora, transacción), ya verificado, y un verificado sí puede restablecer.
- REST (`AdminUsuarioControllerTest`, 6): qué se le pasa al caso de uso (actor, ids, URL del portal), la respuesta sin enlace ni token, el estado de cada error, sin administrador no se ejecuta nada y un
  id que no es UUID es 400.
- **E2E real** (`SoporteDeAccesoE2ETest`, 13, Spring completo + Postgres + los filtros reales, con el correo capturado): **el usuario usa el enlace que le llegó y elige su contraseña** (la anterior deja de
  servir y el enlace es de un solo uso) sin que el administrador vea nada; el reenvío de la verificación llega y se puede usar; la bitácora de las dos acciones sin token; un administrador con sesión deja su nombre; usuario de otra
  cuenta, inexistente y mal formado; desactivado; sin SMTP (503, sin filas nuevas); servidor que rechaza (502, queda en la bitácora); y que nadie más puede dispararlos.
- Portal, Vitest: BFF (`restablecimiento` 6 y `verificacion` 5: sin sesión, ids inválidos, JWT, IP, errores, sin caché), cliente (`admin-acceso`, 6) y `acciones-de-usuario` (15: qué acciones se ofrecen, qué
  se envía y a qué ruta, doble clic, «Enviando…», Escape durante el envío, errores, reintento, corte de red, reapertura).
- Portal, Playwright (`admin-acceso-usuario.spec.ts`, 9 + los ajustes de `admin-cuenta-detalle.spec.ts`): el modal pide confirmación y dice a quién; cancelar no manda nada; confirmar dice a quién se mandó; la
  verificación al usuario sin verificar; el verificado solo ofrece restablecer; el desactivado no tiene acciones; sin SMTP el modal lo dice y no afirma que se envió; el BFF rechaza un id que no es UUID y sin sesión.

### Verificación por mutación — 61/61 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | El usuario se busca sin mirar la cuenta / un inactivo recibe el correo / sin SMTP se da por enviado / se reenvía a quien ya verificó | 1 + 1 + 1 + 1 |
| Servicio | El restablecimiento o la verificación no crean su enlace | 2 + 2 |
| Servicio | Cada enlace vale lo que el otro | 1 + 1 |
| Servicio | La bitácora dice la acción contraria (x2) / sin la cuenta / sin el usuario | 1 + 1 + 1 + 2 |
| Servicio | El correo sale antes de la transacción / un fallo del servidor de correo no se informa | 5 + 1 |
| Servicio | El correo va a otra dirección / el destinatario de la respuesta es otro | 2 + 2 |
| Textos | El enlace de cada correo apunta al del otro / el asunto cambia | 1 + 1 + 1 |
| Controlador | Cada ruta llama al caso de uso contrario / los ids van cruzados / el inactivo no es 409 | 1 + 1 + 1 + 1 |
| Vitest (BFF) | Sin sesión / id de cuenta o de usuario cualquiera / sin IP / sin `no-store` / traga el error (restablecimiento) | 1 + 1 + 1 + 1 + 1 + 1 |
| Vitest (BFF) | Lo mismo en la verificación (sin `no-store`, que ahí no se prueba) | 1 + 1 + 1 + 1 + 1 |
| Vitest (cliente) | La ruta de restablecer apunta a la de verificar / no manda el JWT | 1 + 2 |
| Vitest (componente) | Doble clic / un error se da por enviado / rutas cruzadas / tras enviar se ofrece otro | 1 + 3 + 2 + 1 |
| Vitest (componente) | Un corte de red como error común / sin recargar / cerrar con Escape mientras envía / el resultado anterior queda al reabrir / el error anterior queda al reabrir | 1 + 1 + 1 + 1 + 1 |
| Vitest (componente) | Un inactivo con acciones / un verificado con «reenviar» / el modal sin el correo / el resultado sin el correo | 1 + 1 + 1 + 1 |
| Playwright | Un inactivo con acciones / un verificado con «reenviar» / un error se da por enviado / tras enviar se ofrece otro | 2 + 2 + 1 + 1 |
| Playwright | El modal no nombra al destinatario / confirmar cierra sin mostrar el resultado / el error no se ve | 2 + 3 + 1 |
| Playwright | Todos aparecen verificados / todos activos / el correo, el usuario o la cuenta del botón son otros | 2 + 2 + 2 + 3 + 3 |

Lo que sobrevivía en la primera tanda y se arregló con una prueba, no con código:

- **La guardia del doble clic (`enviandoRef`).** Un doble clic de Playwright no la ejercita: el primer clic deshabilita el botón antes de que llegue el segundo. Solo la mata el test de componente con los dos
  clics dentro de un mismo `act` (como en #182). Se **quitó** el test e2e del doble clic porque pasaba con o sin la guardia y daba una seguridad falsa.
- **Cerrar el modal con Escape mientras se envía** y **el resultado de un envío anterior al reabrir**: el botón «Cancelar» ya estaba deshabilitado y el test de error cubría solo el reabrir tras un fallo.
  Ahora hay un test de cada uno.
- **Las rutas cruzadas (`restablecimiento` ↔ `verificacion`) sobreviven en Playwright**: el mock responde igual a las dos y no hay diferencia observable. Las mata el test de componente (que comprueba la ruta
  exacta) y el e2e real del backend; es una equivalencia del mock, no un hueco.
- **El cliente de la API (`admin-acceso.ts`)** no tenía test propio: los de las rutas lo mockean, así que cambiar su ruta o quitar el JWT sobrevivía. Ahora tiene los suyos.

Dos fallos de herramienta, no del código, que invalidaron tandas enteras y se detectaron por la salida y no por el aviso de «MUERE»: un filtro de Vitest con corchetes escapados no encontraba ningún test y salía con
código 1 (todo «moría» sin haber corrido nada: el script ahora lo marca como inválido), y un servidor de desarrollo huérfano de una corrida detenida en el puerto 3100 hacía fallar el arranque de Playwright (502).

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest` y `SoporteDeAccesoE2ETest`).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 446/446 · Playwright: pasan `admin-acceso-usuario` y `admin-cuenta-detalle` (los dos specs que toca esta PR). **La corrida completa de Playwright no
  terminó**: el sistema se quedó sin memoria y Claude Code detuvo el proceso, así que no hay un resultado completo que citar; queda por correr antes de fusionar.

### Límites conocidos

- **No hay un límite de envíos.** Un administrador puede mandar cuantos correos quiera a un usuario; cada uno queda en la bitácora, pero no hay un tope por hora. Si se abusara, el siguiente paso es un
  límite por usuario destino.
- **Mandar el correo no revoca las sesiones abiertas** ni toca la contraseña actual: el servicio no toca sesiones (los dobles del test lo verifican). Si hiciera falta cortar las sesiones de un usuario
  desde el backoffice, sería otra acción, con su propio issue.
- **El mock del portal no guarda estado ni bitácora** (las specs comparten el mock en paralelo y la bitácora de «Panadería Sol» se cuenta): que el envío quede registrado lo prueba `SoporteDeAccesoE2ETest`.

## #201 · Baja lógica de un cliente conservando sus comprobantes

**Estado: ✅ hallazgos de la revisión de la PR (#234) corregidos, 90/90 mutaciones verificadas (83 y 7 de la corrección).** Para el cliente que se fue: sale de los listados operativos y del cobro, **sin borrar nada**.

`POST /v1/admin/cuentas/{id}/baja` y `/reponer`, y un filtro explícito `bajas` en los listados de cuentas y de empresas. Distinto de suspender (#182): la suspensión corta el servicio de quien no paga y se revierte
a diario; la baja es una marca administrativa, con fecha, sobre la cuenta.

### Diseño

- **Una marca con fecha, nada más.** `cuenta.baja_en` (V36; nulo = en servicio). No se toca `tenant`, `documento`, `comprobante`, `api_key`, `usuario` ni `sesion`: los comprobantes, XML y CDR se conservan (la retención
  es una obligación legal del emisor) y **el RUC sigue ocupado**, porque la empresa sigue ahí y la restricción de unicidad de la base sigue impidiendo registrarlo otra vez (lo prueba el E2E por las tres vías: portal,
  `POST /v1/admin/tenants` y el alta asistida).
- **Independiente de la suspensión.** Dos columnas, dos acciones: reponer no reactiva una cuenta suspendida y reactivar no repone una de baja. El estado que se muestra es `BAJA > SUSPENDIDA > ACTIVA` (la baja
  manda), pero las dos fechas se informan siempre.
- **No corta el acceso.** Decisión consciente: el issue pide sacar al cliente de los listados y del cobro, no cortarle el servicio, y cortarlo es lo que ya hace suspender. El diálogo lo dice sin rodeos («No corta el
  acceso… Para cortar el servicio, suspende la cuenta») y el E2E lo fija con un test para que nadie lo cambie sin darse cuenta.
- **Visibilidad explícita, una sola regla.** `bajas = OCULTAS | INCLUIDAS | SOLO`, por defecto `OCULTAS`, en `GET /v1/admin/cuentas` y `GET /v1/admin/empresas`. La regla vive en un solo sitio
  (`BajasEnListado`) y la usan los dos listados, así dicen lo mismo; vale igual para la página y para el total (`X-Total-Count`), y se combina con «y» con la búsqueda y los demás filtros. Un valor que no existe es
  `400`. Una empresa sin cuenta (de integración) nunca está de baja: se ve por defecto y no sale con `SOLO`.
- **El detalle no filtra:** una cuenta o una empresa de baja se abre igual, con todo lo suyo (sus comprobantes siguen consultables); el detalle dice `BAJA` y desde cuándo.
- **Cambio atómico y bitácora en la misma transacción**, como #182: `UPDATE … WHERE baja_en IS NULL` (y `IS NOT NULL` para reponer) devuelve cuántas filas cambió; de dos pedidos a la vez solo uno lo logra y el otro
  recibe `409 CUENTA_YA_DE_BAJA` / `CUENTA_NO_DE_BAJA`. `DAR_DE_BAJA_CUENTA` lleva el motivo (opcional, 200 caracteres, recortado; más es `422 MOTIVO_INVALIDO`); `REPONER_CUENTA`, no.
- **Portal:** en el detalle de la cuenta, **Dar de baja** (con confirmación que dice qué se conserva, que el RUC sigue ocupado y que NO corta el acceso) junto a Suspender; una cuenta de baja solo ofrece **Reponer**
  (suspenderla no tiene sentido mientras el cliente no está en servicio). En los dos listados, un selector «Cuentas dadas de baja» (Ocultar / Incluir / Solo las dadas de baja) cuya elección vive en la URL, vuelve a
  la página 1 y se conserva al buscar, paginar y cambiar las filas por página; la fila de una cuenta de baja dice «De baja» en neutro (no es una alarma) y la de una empresa lo dice junto a su cuenta.

### Tests

- Servicio (`DarDeBajaCuentaServiceTest`, 13): marca con la hora del servidor, reponer, nada se borra, la bitácora (acción, actor, motivo recortado, sin motivo, misma transacción, falla → falla la acción), conflictos
  sin registro de bitácora, cuenta inexistente o nula, motivo demasiado largo y de exactamente el máximo.
- Persistencia (`JdbcBajaDeCuentaRepositoryTest`, 10, Postgres real): el cambio atómico y condicional, no toca a otras cuentas, **independiente de la suspensión en los dos sentidos**, no borra la empresa y **el RUC
  sigue ocupado** (`DuplicateKeyException`). `JdbcCuentasAdminRepositoryTest` (+6) y `JdbcEmpresasAdminRepositoryTest` (+6): oculta por defecto en la página y en el total, `INCLUIDAS`, `SOLO`, la fila dice desde cuándo,
  se combina con la búsqueda y con los otros filtros, la empresa sin cuenta nunca está de baja y el detalle de una cuenta o empresa de baja se abre con sus comprobantes.
- REST: `AdminBajaCuentaControllerTest` (9), `AdminCuentaControllerTest` (+7) y `AdminEmpresaControllerTest` (+5): actor, motivo y fecha, 409/404/422, sin credencial no se ejecuta nada, id mal formado es 400, `bajas` llega al
  listado y al total, un valor inexistente es 400, la baja manda sobre la suspensión en el estado y las dos fechas se conservan.
- **E2E real** (`BajaDeClienteE2ETest`, 11, Spring completo + Postgres + los filtros reales): una cuenta con dos empresas sale de los dos listados y del total, y aparece con `INCLUIDAS` y `SOLO`; reponer la devuelve;
  **los comprobantes se conservan (ni una fila menos) y siguen consultables** por el administrador y por su dueño con la API key; **la baja no corta el acceso** y es independiente de la suspensión; **el RUC sigue ocupado**
  por portal, por integración y por alta asistida; la bitácora (actor, motivo, cuenta); conflictos, 404, 400 y 422; y que nadie más puede darla de baja (sin credencial, JWT del dueño, API key, clave errónea).
- Portal, Vitest: clientes (`admin-baja` 8, `admin-cuentas` +7, `admin-empresas` +4), BFF (`baja` 9 y `reponer` 5), `acciones-de-baja` (18: qué botón se ofrece, qué dice el diálogo, qué se envía y a qué ruta, doble clic,
  Escape durante el envío, errores, 409 con recarga, corte de red), `cuentas-tabla` (+10) y `empresas-tabla` (+7).
- Portal, Playwright (`admin-baja.spec.ts`, 18 + el ajuste de `admin-cuenta-detalle.spec.ts`): por defecto no salen y el total las descuenta; `SOLO`, `INCLUIDAS`, el selector cambia la URL sin perder lo elegido, la búsqueda no
  encuentra una de baja salvo que se pida, un valor inventado se ignora; lo mismo en empresas, y la empresa de una cuenta de baja se abre; el detalle de una cuenta de baja dice desde cuándo y solo ofrece reponer; el diálogo;
  cancelar no envía; y el BFF de punta a punta sin mutar (409 con su código, 400, 404, 422, 401).

### Verificación por mutación — 83/83 mueren

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | Dar de baja dos veces / reponer una que no está de baja no es conflicto | 1 + 1 |
| Servicio | La bitácora dice la acción contraria (x2) / el motivo pierde su prefijo / no se recorta / en blanco se guarda | 1 + 1 + 1 + 1 + 1 |
| Servicio | El máximo del motivo se rechaza / reponer sin comprobar que exista / reponer devuelve una fecha | 1 + 1 + 1 |
| Persistencia | Dar de baja o reponer sin condición / reponer también reactiva / la hora se ignora | 1 + 3 + 1 + 2 |
| Listados | Ocultar no filtra cuentas / `SOLO` es lo contrario / `INCLUIDAS` filtra | 2 + 3 + 3 |
| Listados | Lo mismo en empresas (ocultar, `SOLO`) / la subconsulta no une por cuenta | 2 + 3 + 3 |
| Listados | El listado de cuentas o de empresas ignora la visibilidad / búsqueda y visibilidad con «o» | 3 + 4 + 9 |
| Listados | El detalle o la fila no dicen la baja (cuentas y empresas) | 1 + 2 + 1 |
| Puertos | El defecto es incluir (cuentas y empresas) | 7 + 6 |
| REST | Dar de baja pierde el motivo / los conflictos no son 409 / el controlador ignora `bajas` (cuentas y empresas) | 1 + 2 + 3 + 2 |
| REST | La baja no manda en el estado / la suspensión manda sobre la baja | 3 + 1 |
| REST | El listado, el detalle, la empresa o la respuesta de la acción pierden la fecha | 2 + 1 + 1 + 2 |
| Vitest (clientes) | `OCULTAS` como valor de la URL / dar de baja y reponer pegan a la ruta contraria / el motivo, el JWT o la IP no viajan | 1 + 1 + 1 + 1 + 2 + 2 |
| Vitest (clientes) | Cuentas y empresas: no mandan `bajas` al backend / no lo ponen en la URL / no lo leen de la URL | 2 + 1 + 2 + 2 + 1 + 1 |
| Vitest (BFF) | Baja: sin sesión / id cualquiera / motivo que no es texto / sin IP / sin `no-store` | 1 + 1 + 1 + 1 + 1 |
| Vitest (BFF) | Reponer: sin sesión / id cualquiera / sin IP | 1 + 1 + 1 |
| Vitest (componente) | Doble clic / motivo sin recortar / rutas cruzadas / un 409 sin recargar / corte de red como error común | 1 + 1 + 3 + 2 + 2 |
| Vitest (componente) | Sin tope / reponer pide motivo / Escape cierra mientras envía / el motivo queda escrito / no recarga tras confirmar | 1 + 1 + 1 + 1 + 2 |
| Vitest (componente) | Una cuenta de baja ofrece dar de baja / el mensaje de error se pierde | 5 + 3 |
| Vitest (etiqueta y tablas) | «De baja» en rojo / el selector no vuelve a la página 1 (cuentas, empresas) / buscar pierde las bajas | 1 + 1 + 1 + 1 |
| Vitest (tablas) | «Quitar filtros» ignora las bajas (cuentas, empresas) / las filas por página pierden las bajas (cuentas, empresas) / la empresa de una cuenta de baja no se marca | 1 + 1 + 1 + 1 + 1 |
| Playwright | La cuenta de baja ofrece suspender / el detalle no dice desde cuándo / la acción recibe siempre «activa» / no ofrece dar de baja | 1 + 1 + 2 + 5 |

Lo que sobrevivía en la primera tanda y se arregló con una prueba, no con código:

- **Las filas por página perdían el filtro de bajas.** Cuentas y empresas conservaban `bajas` al buscar, filtrar y paginar, pero nadie comprobaba el selector de filas por página: cambiarlo habría dejado al administrador
  mirando otro conjunto sin saberlo. Ahora hay un test en cada listado.

Un tropiezo de herramienta que ya conocía: pasé a Vitest un filtro con corchetes escapados y no encontraba ningún test (todo «moría» sin correr nada). El script ya lo marca como inválido y esta tanda usó subcadenas simples.

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest`, `BajaDeClienteE2ETest` y las migraciones hasta V36).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 514/514 · Playwright completo 214/214 (con 2 workers y nada más corriendo).
- **Un fallo de la primera corrida completa, que no era de ningún test:** `:bootstrap:test` terminó en rojo con `OutOfMemoryError: Java heap space` y ninguna prueba fallida. Cada E2E de Spring levanta su propio Postgres y su
  propio contexto, que Spring deja en caché toda la corrida; con los 512 MB que Gradle da por defecto a un worker de pruebas, el contexto número dieciséis (el de esta PR) agotaba el heap. Se subió a 1 GB en
  `bootstrap/build.gradle.kts`: es un cambio de infraestructura de pruebas, no de comportamiento.

### Corrección de la revisión de #234

- **H1: suspender o reactivar una cuenta de baja respondía un estado falso.** `EstadoCuentaResponse` calculaba el estado solo con la suspensión: con una
  cuenta de baja, `suspender` respondía `SUSPENDIDA` y `reactivar` `ACTIVA`, mientras el detalle y el listado decían `BAJA` (la baja manda). El portal no
  lo mostraba (refresca la página), pero sí quien usa la API con `X-Platform-Key`. Ahora `EstadoDeCuenta` trae la fecha de baja, leída por
  `BajaDeCuentaRepository.bajaEn` en la misma transacción del cambio, y la respuesta usa `EstadoCuenta.de(suspendidaEn, bajaEn)`. Se quitó la variante de
  `EstadoCuenta.de` sin la baja, para que no se pueda volver a usar. Tests: servicio (`suspenderYReactivarUnaCuentaDeBajaInformanLaBaja`, en rojo antes),
  REST (`suspenderOReactivarUnaCuentaDeBajaRespondeBaja`, en rojo con `SUSPENDIDA`), Postgres (`bajaEn` por cuenta) y E2E
  (`suspenderYReactivarUnaCuentaDeBajaRespondenElMismoEstadoQueElDetalle`).
- **H2:** los mocks de `bajas` (los dos listados), `…/baja` y `…/reponer` respondían `400 VALIDACION`; ahora `400 PARAMETRO_INVALIDO`, como el backend.

Mutaciones de la corrección, **7/7 mueren**: el servicio no lee la baja al suspender / al reactivar; la respuesta ignora la baja / no lleva su fecha
(REST y E2E); `bajaEn` de cualquier cuenta / siempre nulo (Postgres).

Después: `application`, `in-rest`, persistencia y los E2E de baja y suspensión en verde; portal `tsc` y ESLint limpios, Vitest 514/514 y Playwright de
bajas, cuentas, empresas, suspensión y detalle 60/60.

### Límites conocidos

- **«Del cálculo de consumo y cobro» todavía no tiene dónde aplicarse:** en el código no existe aún ningún cálculo de consumo ni de cobro (llegan con los planes, #189 y siguientes). La marca `cuenta.baja_en` es la
  que esos cálculos deberán mirar; hasta entonces, la baja ya los deja fuera por diseño pero no hay nada que probar.
- **La baja no corta el acceso del cliente** (ver Diseño): un cliente dado de baja puede seguir entrando y emitiendo hasta que se suspenda. Si se quisiera que la baja implicara el corte, se extiende `JwtFilter` y
  `ApiKeyFilter` como en #182; no se hizo aquí porque no lo pide el issue.
- **Las rutas del BFF y el diálogo se parecen mucho a los de suspender** (el motivo, la guardia del doble clic, el manejo de errores): son dos copias de ~150 líneas. Extraer un diálogo de confirmación común es un
  seguimiento razonable, pero toca código de la PR de #182.
- **El e2e de Playwright no muta:** dar de baja o reponer una cuenta cambia lo que cuentan las demás specs que corren a la vez contra el mismo mock (12 cuentas, 12 empresas); por eso «Cliente 13» está de baja de
  siembra y las specs solo la leen. El ciclo completo, con la bitácora, lo prueba `BajaDeClienteE2ETest`, y el diálogo y sus estados, `acciones-de-baja`.

## #187 · Acciones sobre una empresa: entorno, API keys y prueba de conexión

**Estado: ✅ revisión de la PR (#235) corregida, 84/84 mutaciones verificadas (2 equivalentes documentadas).** La acción con más consecuencias del backoffice: el entorno decide contra qué URLs de SUNAT se emite.

`POST /v1/admin/empresas/{id}/entorno`, `POST /v1/admin/empresas/{id}/api-keys/{apiKeyId}/revocar` y `POST /v1/admin/empresas/{id}/prueba-de-conexion`. Las tres quedan en la bitácora, también en la de la cuenta
dueña (si la tiene), y ninguna lleva secretos.

### Diseño

- **Cambiar el entorno** (`BETA` ↔ `PRODUCCION`) toca **una sola columna**, con `UPDATE … WHERE entorno = <el que se vio>`: no pasa por `JdbcTenantRepository.guardar`, que reescribe la fila entera (certificado y credenciales
  SOL cifrados incluidos). Es condicional y va en la misma transacción que la bitácora (`desde=BETA hacia=PRODUCCION`). **No toca los comprobantes ya emitidos**: el E2E compara el documento y el comprobante antes y después.
- **Con envíos pendientes en el outbox el cambio se rechaza** (`409 EMPRESA_CON_ENVIOS_PENDIENTES`). El worker del outbox reintenta contra el entorno que la empresa tenga *en ese momento*: sin este freno, un comprobante
  emitido en beta podía enviarse a la SUNAT de producción (o al revés). Una sola tarea pendiente basta. Es una decisión de diseño que el issue no pedía y que conviene revisar: si el outbox queda atascado, hay que vaciarlo
  antes de poder cambiar el entorno. La comprobación y el cambio no son atómicos entre sí: una emisión que cree una tarea justo entre las dos pasaría (ventana pequeña, documentada).
- **Revocar una API key concreta**, solo esa: `UPDATE api_key SET activa = false, revoked_at = ? WHERE id = ? AND tenant_id = ? AND activa`. De dos pedidos a la vez solo uno lo logra y el otro recibe `409
  API_KEY_YA_REVOCADA`; una key de otra empresa o inexistente es `404`, sin confirmar que existe. La key revocada deja de autenticar de inmediato (el E2E lo comprueba con una llamada real) y las otras siguen
  sirviendo. La bitácora lleva el **prefijo**, nunca la clave.
- **Probar la conexión con SUNAT** consulta, con las credenciales SOL y el entorno de la empresa, `getStatus` de un **ticket que no existe**: es de solo lectura, no envía ni cambia nada. Responde `CONECTADO` (SUNAT
  contestó con normalidad), `RECHAZADO` (contestó con un error definitivo) o `SIN_RESPUESTA` (red, tiempo de espera, error del servicio o de autenticación HTTP), con el **código y el mensaje de SUNAT tal cual**. La
  llamada se hace **fuera de la transacción**. Sin credenciales SOL, `409 SOL_NO_CARGADAS` y no se llama a SUNAT.
- **Portal:** en el detalle de la empresa, **Cambiar entorno** (el diálogo siempre ofrece el contrario al que se ve; pasar a producción dice que emite de verdad, que hacen falta las credenciales y el certificado de
  producción, que no toca lo emitido y que no se puede con envíos pendientes), **Revocar** en cada key vigente (dice cuál, que deja de autenticar de inmediato, que las demás siguen y que no se deshace) y **Probar
  conexión** (sin confirmación, porque no cambia nada; el resultado se muestra en la propia página con su código y su mensaje). Salen por tres rutas del BFF que validan los ids, exigen sesión de administrador y
  reenvían la IP real (#208).
- **Un diálogo de confirmación común** (`DialogoDeAccion`) reúne lo que ya habían aprendido suspender, dar de baja y los correos de acceso: guardia contra el doble clic con un ref, no se cierra mientras envía (Escape
  incluido), un corte de red no se reintenta a ciegas y un error que dice que la página quedó vieja recarga. Las tres acciones nuevas lo usan; las anteriores siguen con su copia (migrarlas toca código de PRs de más abajo).

### Tests

- Servicio (`AccionesDeEmpresaServiceTest`, 23): cambio y bitácora (acción, actor, origen y destino, cuenta, misma transacción, falla → falla), el mismo entorno, **un solo envío pendiente ya frena**, entorno nulo, empresa
  inexistente en las tres acciones; revocar (prefijo en la bitácora, ya revocada, otra empresa o inexistente, falla de la bitácora); prueba (credenciales y entorno de la empresa, ticket de solo lectura, error
  definitivo, sin respuesta, sin SOL, bitácora sin credenciales, **SUNAT se consulta fuera de la transacción**).
- Persistencia (`JdbcAccionesDeEmpresaRepositoryTest`, 12, Postgres real): el cambio condicional, **no toca ninguna otra columna** (certificado y SOL cifrados siguen), no afecta a otras empresas; los pendientes son
  de la empresa; la key se lee con su prefijo, la de otra empresa no se encuentra ni se revoca, revocar dos veces no pisa la hora.
- REST (`AdminEmpresaAccionesControllerTest`, 13): actor, ids, respuesta sin secretos, un entorno inexistente es 400, los cuatro conflictos son 409 con su código, 404, sin credencial no se ejecuta nada y ids mal formados.
- **E2E real** (`AccionesDeEmpresaE2ETest`, 13, Spring completo + Postgres + los filtros reales, con SUNAT sustituida por un doble que registra con qué empresa lo llamaron): el cambio de entorno no toca los comprobantes, ni
  las keys, ni las credenciales SOL, ni a otra empresa, y se puede volver; con envíos pendientes se rechaza y al vaciarlos procede; **una key revocada deja de autenticar y la otra no**; la prueba usa el RUC, las
  credenciales SOL descifradas y el entorno de la empresa, y distingue los tres resultados; la bitácora (en la de la cuenta también, sin secretos, con la empresa de integración sin cuenta); y que nadie más puede.
- Portal, Vitest: cliente (`admin-acciones-empresa` 10), BFF (`entorno` 8, `revocar` 7, `prueba-de-conexion` 6), `dialogo-de-accion` (16) y `acciones-de-empresa` (17: qué entorno se ofrece y qué dice el diálogo, qué se
  envía y a qué ruta, errores y estado viejo; la prueba con sus tres resultados, sin SOL, doble clic y resultado viejo), más el ajuste de `empresa-detalle` (qué botones hay y cuáles se ofrecen a cada key).
- Portal, Playwright (`admin-acciones-empresa.spec.ts`, 14 + el ajuste de `admin-empresa-detalle.spec.ts`): la prueba de conexión de punta a punta con los tres resultados; sin SOL deshabilitada; los diálogos de cambiar
  de entorno y de revocar; con envíos pendientes el diálogo muestra el rechazo y sigue abierto; cancelar no envía; y los conflictos, 400, 404, 422 y 401 del BFF.

### Verificación por mutación — 84/84 mueren (2 equivalentes)

| Capa | Mutación | Qué muere |
|---|---|---|
| Servicio | El mismo entorno no es conflicto / los envíos pendientes no frenan / **un envío no basta** / entorno nulo | 1 + 1 + 1 + 1 |
| Servicio | La bitácora del entorno pierde el origen / la cuenta / dice otra acción / no se registra | 1 + 1 + 1 + 3 |
| Servicio | La bitácora de la key pierde el prefijo / no se registra / sin la empresa | 1 + 3 + 1 |
| Servicio | Sin SOL se llama a SUNAT / el error definitivo y la falta de respuesta se confunden / la bitácora lleva el mensaje o no se registra | 1 + 2 + 1 + 1 + 3 |
| Servicio | La prueba pierde el código / consulta con otra empresa | 2 + 1 |
| Persistencia | Cambiar el entorno sin condición / revocar sin comprobar que estuviera activa / revocar o buscar una key de otra empresa | 1 + 1 + 1 + 1 |
| Persistencia | Los pendientes son de todas las empresas / la revocación ignora la hora / el cambio de entorno desactiva las credenciales | 1 + 2 + 1 |
| REST | El cuerpo se pierde / cada uno de los cuatro conflictos deja de ser 409 | 2 + 1 + 1 + 1 + 1 |
| REST | El cambio intercambia origen y destino / la revocación o la prueba pierden un dato | 1 + 1 + 1 + 1 |
| Vitest (cliente) | Rutas equivocadas (3) / el entorno, el JWT o la IP no viajan / un tercer entorno | 1 + 1 + 1 + 1 + 3 + 3 + 1 |
| Vitest (BFF) | Entorno: sin sesión / id cualquiera / entorno inventado / sin IP / sin `no-store` | 1 + 1 + 1 + 1 + 1 |
| Vitest (BFF) | Revocar y probar: sin sesión / ids cualquiera (empresa y key) / sin IP | 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 |
| Vitest (componentes) | Se ofrece el mismo entorno / pasar a producción no pesa más / ruta o cuerpo equivocados | 4 + 1 + 2 + 2 |
| Vitest (componentes) | Un estado viejo no recarga (entorno y key) / revocar a otra ruta | 1 + 1 + 1 |
| Vitest (componentes) | La prueba: sin SOL / doble clic / el error se pinta de verde / resultado viejo / mensaje perdido / entorno al revés / código oculto / prefijo sin nombrar | 1 + 1 + 1 + 1 + 1 + 2 + 1 + 1 |
| Vitest (diálogo) | Doble clic / Escape / corte de red / estado viejo / la respuesta no llega / no recarga / cancelar no avisa / el cuerpo se lee al montar / el error queda al reabrir | 1 + 1 + 2 + 1 + 1 + 2 + 1 + 2 + 1 |
| Vitest y Playwright (detalle) | Se ofrece revocar una key revocada / la key que se revoca es otra / se prueba sin SOL / el entorno del diálogo es fijo | 1 + 1 + 1 + 1 |

Lo que sobrevivía en la primera tanda:

- **Un solo envío pendiente no frenaba el cambio** (`> 0` → `> 1`): mi test usaba tres. Era un hueco real, y el peor posible en esta acción. Ahora hay un test con exactamente una tarea.
- **Dos mutaciones equivalentes**, que no son huecos: la comprobación previa de «la key ya está revocada» (el cambio condicional ya devuelve ese 409 sin dejar registro, así que la comprobación sobraba: se **quitó**)
  y la guarda de una key con id nulo (la ruta exige un UUID y una key nula no existe de todas formas: sigue siendo `404`).

### Suites

- Backend: `./gradlew test` completo sobre la rama, código de salida 0 (incluye `ArchitectureTest` y `AccionesDeEmpresaE2ETest`).
- Portal: `tsc --noEmit` limpio · ESLint limpio · Vitest 580/580 · Playwright completo 228/228 (2 workers y nada más corriendo). La primera corrida no arrancó: el servidor de desarrollo agotó su espera
  porque no pudo descargar las fuentes de Google (fallo del entorno, no de las pruebas); la segunda pasó entera.

### Corrección de la revisión de #235

- **H1 (menor): los mocks de las tres acciones respondían `400 VALIDACION`** donde el backend responde `400 PARAMETRO_INVALIDO` (un id que no es UUID),
  `400 JSON_INVALIDO` (un entorno que no existe en el cuerpo: Jackson no lo convierte) o `422 ENTORNO_INVALIDO` (el cuerpo sin entorno, que rechaza el
  servicio). Era la quinta PR seguida con el mismo desajuste, así que el mock ganó dos funciones con el contrato exacto del backend, `parametroInvalido(nombre)`
  y `jsonInvalido()`, y todos los handlers las usan, también los filtros `entorno` y `certificado` del listado de empresas (#185, ya en `develop`). Ningún test
  depende del código del 400 (el BFF valida antes); después: `tsc` y ESLint limpios y las specs del backoffice 88/88.

### Límites conocidos

- **La prueba de conexión no diagnostica por sí sola.** Consulta un ticket que no existe, así que un error de ticket es esperable y no significa que las credenciales estén mal. Se muestra el código y el mensaje de SUNAT
  tal cual, sin clasificar «credenciales inválidas»: no se pudo verificar contra SUNAT real qué responde exactamente en cada caso, y no se promete lo que no se verificó.
- **No se mide cuánto tarda la prueba** ni se cachea su resultado: cada pulsación es una consulta a SUNAT y una fila en la bitácora.
- **Cambiar el entorno no cambia las credenciales ni el certificado**: pasar a producción con los de pruebas hará que SUNAT rechace los envíos. El diálogo lo advierte; validarlo antes sería otra funcionalidad.
- **El e2e de Playwright no ejecuta los cambios que sí proceden** (pasar a producción, revocar una key): alteran lo que cuentan las demás specs que corren a la vez contra el mismo mock. Lo que no muta (la prueba de
  conexión, los diálogos y los rechazos) sí está de punta a punta; los cambios reales los prueban `AccionesDeEmpresaE2ETest` y los componentes.

## #184 · Impersonar a un usuario del cliente (sesión de soporte)

**Estado: ✅ revisión de la PR (#236) corregida, 86/86 mutaciones verificadas (2 equivalentes documentadas).** La función más sensible del backoffice: se hizo lo más acotada posible.

`POST /v1/admin/cuentas/{cuentaId}/usuarios/{usuarioId}/impersonar` abre una **sesión de soporte**; `GET /v1/cuenta/accesos-de-soporte` es el historial que ve el propio cliente. Ya no hay nada más que pedir al
soporte para que un cliente sepa que alguien miró su portal.

### Diseño

- **Un token distinto de una sesión normal.** Es un JWT del usuario al que se mira, con un claim propio (`imp`: el administrador que la abrió) y **su propia expiración, 15 minutos, sin refresh**: no se puede renovar. Para seguir
  mirando se impersona de nuevo, y cada vez deja su registro. Un claim `imp` que no se entiende **invalida el token** en vez de degradarlo a una sesión normal, y quitarlo para volverlo normal rompe la firma (ambos tienen test).
- **Solo lectura, en el filtro de JWT.** Cualquier petición que no sea `GET`/`HEAD`/`OPTIONS` con una sesión de soporte responde `403 SOPORTE_SOLO_LECTURA` **antes** de consultar nada, incluidas las de la propia
  sesión (`/v1/auth/**`). Eso es lo que cumple «no permite cambiar la contraseña ni las credenciales SOL» —y también las API keys, el certificado, los datos fiscales, las series y, sobre todo, **emitir**, que sería
  firmar un comprobante en nombre del cliente—. El issue no pedía tanta restricción; la elegí a propósito (ver abajo). La sesión sigue acotada a su cuenta (`X-Empresa` de otra cuenta es `403 EMPRESA_AJENA`) y una cuenta
  suspendida tampoco se mira.
- **Solo una persona puede impersonar.** La clave de plataforma (`X-Platform-Key`) no identifica a nadie y la bitácora no podría decir «quién»: `403 REQUIERE_ADMINISTRADOR`. Un usuario de otra cuenta es `404`, uno
  desactivado `409 USUARIO_INACTIVO`. Un usuario con el correo sin verificar sí se puede mirar.
- **Sin bitácora no hay impersonación.** El token se firma primero (no tiene efectos) y la bitácora va después, en una transacción: si falla, la excepción se lleva el token y nunca sale uno sin su registro. La bitácora dice
  **quién** (administrador), **a quién** (cuenta y correo del usuario), **cuándo** y **por cuánto tiempo** (`usuario=… duracion_s=900`), y nunca el token. El formato del detalle vive en un solo sitio
  (`DetalleDeSoporte`: lo escribe el servicio y lo lee el historial del cliente, así no pueden desacordarse).
- **El cliente lo ve, sin saber quién fue.** `GET /v1/cuenta/accesos-de-soporte` (solo con sesión de cuenta; con API key no hay cuenta) lista cuándo, a qué usuario y por cuánto tiempo, de lo más reciente a lo más antiguo
  (hasta 100). El DTO **no tiene ningún campo del administrador** (hay un test que lo fija) y un registro que no se entiende se muestra igual, solo con su fecha: el cliente tiene derecho a ver que hubo un acceso.
- **El token nunca llega al JS del administrador.** El BFF lo guarda en la cookie `httpOnly` de acceso del cliente, con la vida que le queda (como mucho 15 minutos, mínimo 1 s), **sin refresh**, y la respuesta solo dice a quién
  se mira y hasta cuándo. Fija como empresa activa la primera de la cuenta (las páginas la leen de su cookie, como hace el login) con la misma vida. El middleware deja pasar una sesión de soporte sin refresh mientras no
  venza (lo decide por el claim `imp`, sin verificar la firma: si fuera falso, el backend rechaza el token); cualquier otro access sin refresh sigue yendo al login.
- **Aviso permanente.** En el portal del cliente, `/v1/auth/me` dice `soporte_hasta` (lo dice el backend, que es quien conoce el token; no se lee el JWT en el cliente, y no se dice qué administrador es) y el layout privado
  pinta arriba de todo un aviso fijo (`sticky`) en cada página: «Modo soporte. Estás viendo el portal como … Solo puedes mirar. La sesión termina el …», con **Salir del modo soporte**, que borra las cookies del cliente y vuelve
  a la cuenta en el backoffice (la sesión del administrador es otra cookie y sigue abierta). Un 401 al cargar el portal ahora lleva al login en vez de a una página de error.
- **Backoffice:** botón **Entrar como este usuario** en cada usuario activo del detalle de la cuenta, con un diálogo que dice que dura 15 minutos y no se renueva, que solo se mira, que queda a su nombre y que el cliente lo ve,
  y que reemplaza la sesión de cliente que hubiera en ese navegador.
- **Portal del cliente:** página **Accesos de soporte** (`/cuenta/accesos-de-soporte`, con enlace en el menú) con el historial.

### Tests

- Token (`JwtTokenEmisorTest`, +6): una sesión normal no es de soporte; la de soporte lleva al administrador y su expiración, que manda sobre los 15 minutos normales; vencida no verifica; un claim malformado invalida; quitar el claim
  rompe la firma.
- Filtro (`JwtFilterTest`, +7): una sesión de soporte mira; no escribe **nada** (doce rutas, incluida la propia sesión); se corta antes de consultar; marca la petición con el administrador y la expiración; sigue acotada a su cuenta;
  una cuenta suspendida tampoco se mira; la sesión normal sigue escribiendo.
- Servicio (`ImpersonarUsuarioServiceTest`, 11) e historial (`AccesosDeSoporteServiceTest`, 6), formato (`DetalleDeSoporteTest`, 3, incluidos once textos que no se leen) y persistencia (`JdbcAccesosDeSoporteRepositoryTest`, 6, Postgres real:
  solo la cuenta pedida, solo impersonaciones, orden, límite e índice).
- REST: `AdminImpersonacionControllerTest` (7: sin caché, sin el hash de la contraseña, la clave de plataforma, sin credencial no se ejecuta nada), `CuentaAccesosDeSoporteControllerTest` (5: nada del administrador) y `AuthControllerTest` (+3: `/me` dice
  `soporte_hasta` y nunca el administrador).
- **E2E real** (`ImpersonacionE2ETest`, 12, Spring completo + Postgres + los filtros reales): el administrador recibe un token que vence en ~15 minutos y no tiene refresh; con él se mira como el usuario y `/me` lo dice; **con él no se puede
  cambiar nada** (ocho rutas de escritura y un DELETE responden `SOPORTE_SOLO_LECTURA` y se comprueba que la contraseña, las credenciales SOL, las empresas, las keys, las series, los documentos y las sesiones no cambiaron);
  sigue acotado a su cuenta y a una suspendida; queda en la bitácora con quién, a quién, cuándo y cuánto, sin el token; **el cliente lo ve en su historial sin el administrador** y otra cuenta no ve nada; la clave de plataforma no puede; usuario de
  otra cuenta, inexistente o desactivado; nadie más puede (sin credencial, JWT del dueño, API key, clave errónea); y el token de soporte no abre las rutas de administrador.
- Portal, Vitest: BFF (`impersonar` 14, `salir` 4), `aviso-de-soporte` (6), `accesos-de-soporte` (7) y su cliente (2), `acciones-de-usuario` (+7), `jwt` (+4), `middleware` (+4) y `layout` privado (+6: el aviso sale y va antes que el resto, no sale en una sesión
  normal, salir va a la cuenta del usuario, y un 401 lleva al login).
- Portal, Playwright (`admin-impersonacion.spec.ts`, 9 + el ajuste del detalle de la cuenta): el diálogo; entrar como el usuario deja el aviso permanente; el aviso sigue en cada página; **el token vive en una cookie `httpOnly`** (el JS no la ve y no hay refresh); salir vuelve
  a la cuenta, cierra la sesión del cliente y deja la del administrador; un desactivado no tiene botón; el BFF rechaza ids inválidos, usuarios inexistentes y la falta de sesión; y el cliente ve su historial (con el acceso sin detalle) sin una palabra sobre el administrador.

### Verificación por mutación — 85/85 mueren (2 equivalentes)

| Capa | Mutación | Qué muere |
|---|---|---|
| Token | Sin claim / vence como uno normal / `verificar` lo ignora / un claim malformado no invalida / la expiración leída es otra | 2 + 3 + 2 + 2 + 1 |
| Filtro | Soporte escribe / solo se bloquea el POST / tampoco lee / la petición no queda marcada / el bloqueo responde otro código | 2 + 1 + 4 + 1 + 1 |
| Servicio | La clave de plataforma impersona / un usuario de otra cuenta / un desactivado | 1 + 1 + 1 |
| Servicio | Dura 30 minutos / la bitácora dice otra acción, sin la cuenta o sin la duración / no se registra | 3 + 1 + 1 + 1 + 4 |
| Servicio | El administrador del token es el usuario / el token no es de soporte / es de otro rol / la expiración es otra | 1 + 1 + 1 + 1 |
| Historial | Pierde el usuario y la duración / esconde lo que no entiende / el límite es otro | 2 + 1 + 1 |
| Formato | El texto se escribe distinto / la duración no se lee | 2 + 2 |
| Persistencia | Todas las cuentas / todas las acciones / orden inverso / límite ignorado | 1 + 1 + 2 + 1 |
| REST | La respuesta queda en caché / `REQUIERE_ADMINISTRADOR` no es 403 / pierde la expiración | 1 + 1 + 1 |
| REST | `/me` no dice hasta cuándo o ignora la sesión de soporte / el historial pierde usuario, duración o fecha | 1 + 1 + 1 + 1 + 2 |
| Vitest (jwt y middleware) | Un claim vacío o todo token cuenta como soporte / una sesión vencida pasa / cualquier access sin refresh pasa | 1 + 1 + 1 + 1 |
| Vitest (BFF) | Sin sesión / un id o un usuario cualquiera / **el token viaja en el cuerpo** / sin IP / sin `no-store` | 1 + 1 + 1 + 1 + 1 + 1 |
| Vitest (BFF) | La cookie vive más de 15 minutos o puede ser inmortal / el refresh previo sobrevive / no se fija la empresa activa o dura 30 días / un fallo al listar rompe la sesión / la respuesta no dice a quién | 1 + 1 + 1 + 1 + 1 + 1 + 1 |
| Vitest (aviso) | No es fijo / el doble clic sale dos veces / se navega aunque falle / sale a otra ruta, sin hasta cuándo, sin a quién, sin región, sin error, por otra ruta | 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 |
| Vitest (layout) | No muestra el aviso / un 401 no lleva al login / el aviso llama a otra cuenta o sale sin correo | 1 + 1 + 1 + 1 |
| Vitest (acción) | Navega a otra página / otra ruta / se ofrece a los desactivados / otro usuario / el diálogo no nombra al usuario | 1 + 1 + 1 + 1 + 1 |
| Vitest (historial) | Los minutos se dicen mal / se esconde el acceso sin detalle / sin accesos no se dice / duración vacía / el cliente de la API usa otra ruta o no manda el JWT | 1 + 1 + 1 + 1 + 1 + 1 |
| Playwright | Sin enlace al historial / el correo del acceso está oculto | 1 + 1 |

Lo que sobrevivía en la primera tanda:

- **El enlace de salida usaba una cuenta equivocada y nadie lo notaba** (`cuentaId={activa.id}` en vez de la cuenta del usuario): el aviso se probaba con la propia fecha y el correo, no con adónde lleva «Salir». Ahora el test de la página
  hace clic en salir y comprueba que va a `/admin/cuentas/<la cuenta del usuario>`.
- **El correo del acceso podía estar oculto (`hidden`) sin que ningún test lo viera**: jsdom no aplica CSS y `toContainText` cuenta el DOM, no lo visible. Ahora el e2e exige `toBeVisible`.
- **Dos equivalentes**: los anclajes `^…$` del formato del detalle (con `matches()` ya se exige coincidir con todo: se quitaron) y el `if (!access) redirect("/login")` de la página del historial (el middleware y el layout ya redirigen; el `if` solo
  estrecha el tipo para TypeScript).
- **Dos hallazgos reales del e2e, antes de las mutaciones**: el middleware echaba al login a cualquier sesión sin refresh (justo lo que es una sesión de soporte), y sin la empresa activa «Empresa» llevaba al onboarding. Se arreglaron con sus tests.

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (7 min 34 s, Testcontainers incluido).
- Portal: `tsc --noEmit` y ESLint limpios; Vitest **632/632** (76 archivos); Playwright completo (`--workers=2`) **237/237**.

### Corrección de la revisión de #236

- **H1 (importante): la bitácora mostraba `IMPERSONAR_USUARIO` crudo.** La acción nueva no tenía nombre en el catálogo del portal
  (`admin.detalle.eventos.acciones`), así que en el detalle de la cuenta salía el código. Era la segunda vez (la suspensión de #182 pasó igual), así que además
  del nombre («Acceso de soporte como un usuario») hay una guarda, `src/lib/catalogo-bitacora.test.ts`: lee las constantes de `AccionAdmin.java` del dominio y
  exige que cada una tenga su nombre en `es.json`. Se vio en rojo antes del nombre (`expected [ 'IMPERSONAR_USUARIO' ] to deeply equal []`), que es también su
  mutación; un segundo test comprueba que de verdad lee el enum, para que un cambio de ruta no la deje pasando en vacío.
- **H2 (menor): el mock respondía `400 VALIDACION`** a un id que no es UUID; ahora `parametroInvalido("cuentaId")` / `parametroInvalido("usuarioId")`, como
  el backend. Ningún test depende del código (el BFF valida antes).
- Después: `tsc` y ESLint limpios; Vitest **634/634** (77 archivos); Playwright `admin-impersonacion` y `admin-cuenta-detalle` **15/15**.

### Límites conocidos

- **Una sesión de soporte no se puede revocar antes de que venza.** Es un JWT sin estado: si hay que cortarla ya, solo vence a los 15 minutos (o se suspende la cuenta, que se mira en la base en cada petición, pero eso corta también al cliente).
- **Es de solo lectura por completo, más de lo que pedía el issue.** El issue prohibía cambiar la contraseña y las credenciales SOL; yo bloqueé toda escritura, porque emitir o crear una API key «como el cliente» tiene consecuencias fiscales y de seguridad
  que no se arreglan con una bitácora. Si el soporte necesita reproducir una emisión, hace falta una decisión explícita (y su propia bitácora).
- **Reemplaza la sesión de cliente del navegador del administrador** (el diálogo lo dice). Para tener las dos a la vez hace falta otro navegador o un perfil aparte.
- **La identidad del administrador no se muestra al cliente.** Queda en la bitácora interna, que el cliente no ve. Si algún cliente la pidiera, sería una decisión de producto.
- **Mientras se mira no se registra lo que se mira**, solo que se miró: la bitácora de accesos no es un registro de páginas vistas.
- **El mock del portal** abre la sesión con el cliente de demostración marcado como soporte (su mundo de clientes es otro): lo que prueba el e2e es el recorrido, no los datos; que la sesión es de solo lectura lo prueba el backend, no el mock.

## #189 · Planes: modelo de plan y suscripción en el dominio

**Estado: ✅ revisión de la PR (#237) corregida, 72/72 mutaciones verificadas (71 y 1 de la corrección).** Es la base de #190–#194 y #196: solo el modelo, sin pantallas ni endpoints (no los pedía el issue).

### Diseño

- **El plan es un dato.** Tabla `plan`: nombre, precio mensual, documentos al mes, RUC, usuarios, API keys, retención en años y estado (`ACTIVO`/`INACTIVO`), más `por_defecto`. Un límite en `NULL` es **«sin límite»** (el dominio lo dice con `Limite.sinLimite()`, nunca con un número mágico) y **cero no existe**: ni en el dominio ni en un `CHECK` de la base (un plan que no deja emitir un documento no se vende).
- **Los cuatro planes de la página de precios**, cargados por la propia migración (V37): Gratis (S/ 0, 30 docs, 1 RUC, 1 usuario, 1 key, retención 1 año), Emprende (S/ 29, 300, 1, 1, 2, 5), Negocio (S/ 69, 1 500, 3, 3, 5, 5) y Pro (S/ 129, ilimitados, 10 RUC, usuarios y keys ilimitados, 5). **La página no dice la retención de Negocio y Pro**: heredan los 5 años de Emprende («Todo lo de Emprende»); es una suposición mía y está a una línea de cambiarse.
- **La suscripción liga una cuenta con un plan** con inicio, vencimiento (`NULL`: no vence, el plan gratis; exclusivo: en ese instante ya empieza la gracia), días de gracia y `termina_en`. **`termina_en NULL` = la suscripción vigente de la cuenta**; con fecha, fue reemplazada y queda como historial. Estados en un instante: `VIGENTE`, `EN_GRACIA`, `VENCIDA`, `REEMPLAZADA`.
- **«No dos suscripciones activas» lo garantiza la base**, no solo el código: índice único parcial `ux_suscripcion_activa ON suscripcion(cuenta_id) WHERE termina_en IS NULL`. Dos cambios de plan a la vez no pueden dejar a la cuenta con dos, venga el código de donde venga.
- **«Toda cuenta tiene siempre un plan»**, por tres lados:
  - **Las cuentas existentes migran a Gratis**, desde el día que se crearon (`inicia_en = cuenta.created_at`).
  - **Toda cuenta nueva nace con el plan por defecto por un trigger** (`AFTER INSERT ON cuenta`). Lo elegí en vez de ponerlo en cada servicio porque una cuenta se crea por varios caminos (registro, alta asistida, scripts, los INSERT de las pruebas) y «siempre» no puede depender de que cada uno se acuerde. Si no hubiera plan por defecto, **crear la cuenta falla** en vez de dejarla sin plan.
  - **El plan por defecto es uno solo y siempre activo** (índice único parcial + `CHECK`), y el dominio no deja desactivarlo: dejaría a las cuentas siguientes sin plan. Se identifica por la marca `por_defecto`, no por el nombre, para que renombrar «Gratis» (#190) no rompa nada.
- **Dominio (`pe.factura.domain.plan`):** `Limite`, `Plan` (valida nombre ≤ 40 sin espacios sobrantes, precio ≥ 0 con a lo sumo dos decimales —se rechaza, no se redondea en silencio—, límites > 0, retención > 0), `Suscripcion` y **`PlanesDeCuenta`**, el agregado que impone las dos reglas del issue: no se puede construir (ni llegar por un cambio) a una cuenta sin suscripción activa (`CUENTA_SIN_PLAN`) ni con dos (`SUSCRIPCIONES_ACTIVAS_MULTIPLES`). `cambiarA` devuelve otro agregado que cierra la activa y abre la nueva **en el mismo instante** (sin hueco ni solape) y no muta el original.
- **Puertos y adaptadores:** `PlanRepository` (buscar, listar del más barato al más caro, plan por defecto) y `SuscripcionRepository` (`deLaCuenta`, `cambiar`). **`cambiar` es una única sentencia** (una CTE que cierra la activa y, solo si la cerró, inserta la nueva): no hay instante en que la cuenta quede sin plan ni con dos, y de dos cambios a la vez solo uno encuentra la suscripción aún abierta (el otro recibe `false`). Si la nueva no se puede abrir, la vieja sigue activa.

### Tests (72)

- **Dominio (41):** `LimiteTest` (5), `PlanTest` (12), `SuscripcionTest` (13: los instantes exactos del vencimiento y del fin de la gracia), `PlanesDeCuentaTest` (11: sin suscripciones, todas terminadas y dos activas se rechazan; el cambio cierra la activa y abre la nueva en el mismo instante, no muta el original y nunca deja más de una activa).
- **Esquema, con Postgres real (`EsquemaDePlanesTest`, 14):** los cuatro planes con todos sus valores; Gratis es el único por defecto; una cuenta nueva nace con Gratis activo y cada cuenta con la suya; actualizar la cuenta (el upsert del repositorio) no abre otra; dos activas → error; cerrada la activa se puede abrir otra; nombre único sin importar mayúsculas; un solo plan por defecto, que no puede estar inactivo; límites, precio, fechas y gracia inválidos; sin plan por defecto crear una cuenta falla entera.
- **Migración de verdad (`MigracionDePlanesTest`, 2):** con su propia base detenida en la versión 36 se crean cuentas «como existían» y recién después se aplica la V37: todas quedan en Gratis, con su fecha de creación, sin vencimiento. Sin cuentas no se crea ninguna suscripción.
- **Adaptadores (`JdbcPlanRepositoryTest` 7, `JdbcSuscripcionRepositoryTest` 8):** `NULL` vuelve como «sin límite»; orden por precio y luego por nombre; el cambio completo con fechas, vencimiento y gracia; historial en orden; **el segundo de dos cambios simultáneos no hace nada**; si la nueva falla, la cuenta conserva su plan; no toca a otras cuentas; una nueva de otra cuenta no se acepta.

### Verificación por mutación — 71/71 mueren

| Capa | Mutaciones | Cuántas |
|---|---|---|
| `Limite` | el cero es válido / lo finito es ilimitado / «sin límite» lleva una cifra | 3 |
| `Plan` | nombre sin recortar o con otro tope / gratis no puede costar cero / precio negativo / tres decimales / precio sin normalizar / cero RUC o años / el plan por defecto nace inactivo o se desactiva / desactivar no desactiva / un inactivo figura activo / sin estado o sin límites | 14 |
| `Suscripcion` | gracia negativa / vence al empezar / termina antes de empezar / un día de gracia de más / vencimiento o fin de gracia inclusivos / una reemplazada sigue vigente / se termina dos veces / sin vencimiento vence / sin cuenta o sin inicio | 11 |
| `PlanesDeCuenta` | suscripción ajena / sin plan / dos activas / cambiar no cierra la activa o cierra en otro instante / la nueva empieza en otro instante, pierde la gracia o el plan / la lista se altera por fuera | 9 |
| Adaptadores | orden de los planes (2) / un null no es sin límite / el plan por defecto es cualquiera / cambiar pisa una ya cerrada / acepta una nueva de otra cuenta / la vieja termina en otro instante / historial al revés / la nueva pierde vencimiento, gracia o plan / cambiar siempre dice que sí / una cuenta inexistente tiene planes / se leen las de todas las cuentas | 14 |
| Esquema (V37) | sin trigger / las existentes no migran o migran con la fecha de hoy / un valor de un plan mal / Pro con tope de keys / Gratis no es el defecto / unicidad de la activa abarca el historial o falta / dos planes por defecto / nombre sensible a mayúsculas / defecto inactivo / límite cero, cero RUC, precio negativo, fechas invertidas, gracia negativa, estado desconocido / el trigger no falla sin plan por defecto / el trigger usa otra fecha | 20 |

**Un hueco real, encontrado antes de las mutaciones:** `Plan` normalizaba el precio a dos decimales pero ningún test lo notaba (los míos comparaban con `isEqualByComparingTo`, que ignora la escala). Ahora hay un test que mira el texto (`29.00`, `29.50`, `100.00`).

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (7 min 51 s; incluye `ArchitectureTest` y todos los E2E de Spring con Postgres real, que ahora crean sus cuentas con el trigger de plan).
- Portal: sin cambios (esta PR solo toca el backend).

### Corrección de la revisión de #237

- **H1 (menor): dos tests de `JdbcPlanRepositoryTest` borraban su plan de más solo si pasaban.** `plan` no se vacía entre tests (los cuatro vienen de la
  migración), así que un fallo dejaba «Viejo» o «Abeja» y hacía fallar, por otra razón, a los tests que cuentan los cuatro planes. Ahora un `@AfterEach` borra
  todo plan que no sea de la migración.
- **H2 (menor): el historial podía salir al revés.** `deLaCuenta` ordenaba por `inicia_en, created_at`; un cambio en el mismo instante en que nace la cuenta
  (misma transacción, mismo `now()`) dejaba el orden entre la cerrada y la nueva al azar. Ahora desempata `termina_en NULLS LAST`: a igual inicio, la que terminó
  va antes. Test `aIgualInicioLaQueTerminoVaAntesQueLaActiva`, en rojo antes del cambio (que es también su mutación: quitar el desempate lo vuelve rojo).
- Después: los tests de planes y suscripciones de `out-persistence`, **BUILD SUCCESSFUL**.

### Límites conocidos

- **Todavía nada hace valer el plan.** `LIMITE_PLAN` sigue siendo un texto: aplicar los límites es #192. Esta PR solo hace que el plan exista, se pueda asignar y nunca falte.
- **No hay endpoints ni pantalla**: asignar y cambiar el plan es #191, el CRUD de planes #190. Los adaptadores no están cableados en `AppConfig` todavía (no los usa nadie).
- **El trigger esconde una regla en la base.** Es deliberado (ver arriba) y está comentado en la migración, pero quien lea solo el código Java no verá de dónde sale la suscripción de una cuenta nueva.
- **Retención de Negocio y Pro**: suposición (5 años), la página de precios no la dice.
- **«Cambiar un límite afecta al ciclo siguiente»** (#190) necesitará que la suscripción recuerde los límites con los que empezó el ciclo; este modelo no lo hace aún y no se anticipó para no inventar lo que #190 va a decidir.
- **La migración no es reversible por sí sola** (no hay `undo` de Flyway en este repo): revertir es borrar el trigger, `suscripcion` y `plan`.

## #190 · Planes: CRUD de planes en el backoffice

**Estado: ✅ revisión de la PR (#238) corregida, 189/189 mutaciones verificadas (185 y 4 de la corrección; 1 equivalente documentada).** Backend (`/v1/admin/planes`) y portal (`/admin/planes`).

### Diseño

- **El nombre y el precio cambian al instante; los límites, al ciclo siguiente.** Es el criterio que más pesa del issue: «subir un límite a mitad de mes no debe regalar documentos del ciclo corriente ni cortarle a nadie». `Plan.editar` **no toca** los límites vigentes: si los nuevos difieren, quedan como `programado` (`CambioDeLimites`, tabla `plan_cambio_programado`, V38) con `aplica_desde` = la medianoche del día 1 del mes siguiente **en America/Lima** (`CicloMensual`: el mismo ciclo que #192 definirá para el consumo). Subir o bajar un tope se trata igual.
  - Como mucho **un** cambio por plan: un segundo cambio antes de que llegue la fecha reemplaza al primero; poner otra vez los límites vigentes **cancela** el programado (y queda en la bitácora).
  - Cuando la fecha llega, `Plan.vigenteEn(ahora)` lo da por vigente (sin tarea programada que se pueda olvidar: quien lea los límites para hacerlos valer, #192, pasa por ahí) y la siguiente edición lo pasa a las columnas de `plan`. En el instante exacto de `aplica_desde` ya manda.
  - Desactivar y activar **conservan** el cambio programado.
- **Desactivar no toca a las cuentas.** Solo marca el plan fuera de la oferta; las suscripciones no se tocan (el E2E compara las filas de `suscripcion` antes y después). La bitácora dice con cuántas cuentas se quedó. Se puede volver a ofrecer (`activar`; el issue pedía desactivar, pero sin volver atrás sería un callejón sin salida).
- **«Un plan con cuentas activas no se puede borrar, solo desactivar»**, y un poco más: tampoco si **alguna vez** tuvo una cuenta (el historial de suscripciones apunta al plan y borrarlo lo rompería). `409 PLAN_EN_USO` con un mensaje que dice cuál de las dos cosas es y qué hacer. El plan de las cuentas nuevas no se borra ni se desactiva (`409 PLAN_POR_DEFECTO`). El borrado es condicional en SQL (`NOT EXISTS` de suscripciones): si entre mirar y borrar una cuenta lo toma, la base no lo borra y es `PLAN_EN_USO`, no un 500. Su cambio programado se va con él (`ON DELETE CASCADE`).
- **Nombre único sin importar mayúsculas ni espacios, decidido por la base** (`ux_plan_nombre`): dos altas simultáneas con el mismo nombre dejan un solo plan y un `409 NOMBRE_DUPLICADO` limpio para la otra (el E2E lo prueba con dos hilos), no un 500 ni un «mirar y luego guardar».
- **Dos administradores a la vez se serializan:** el plan se lee con `FOR UPDATE` dentro de la transacción de la acción (un test de persistencia lo comprueba con una segunda conexión `NOWAIT`).
- **«Sin límite» nunca se asume.** La API recibe cada tope como `{maximo}` o `{ilimitado: true}`; un límite omitido, uno vacío o uno con las dos cosas es `422 LIMITE_INVALIDO`. Así un campo que se olvidó mandar jamás se vuelve «ilimitado» en silencio.
- **Bitácora**, en la misma transacción que el cambio: `CREAR_PLAN` (con el plan completo), `EDITAR_PLAN` (qué cambió, de qué a qué, y `limites_desde=` la fecha), `DESACTIVAR_PLAN` (con las cuentas que lo siguen teniendo), `ACTIVAR_PLAN`, `ELIMINAR_PLAN`. Una edición que no cambia nada no escribe ni deja registro.
- **Listado:** del más barato al más caro, con los límites vigentes, el precio y **cuántas cuentas lo tienen como suscripción vigente** (el plan que nadie usa figura con 0).
- **Portal:** tabla con precio, límites, retención, cuentas y acciones por fila; el cambio programado se muestra **aparte** de lo vigente («Desde el 1 Nov 2026: Documentos / mes: 1,500 → 2,000»). El formulario (crear y editar) dice el efecto antes de guardar —incluida la fecha en que entran los límites— y, si ya hay un cambio programado, muestra esos límites y avisa que volver a poner los de hoy lo cancela. Solo se ofrece lo que el plan permite (el de las cuentas nuevas solo se edita; con cuentas, no se ofrece borrar), pero que se ofrezca no lo autoriza: el backend decide.
- **`DialogoDeAccion`** aprendió `DELETE` (por defecto sigue siendo `POST`).
- **Enlace «Planes»** en el menú (ya no «Pronto») y miga «Comercial / Planes».

### Tests

- **Dominio:** `LimitesTest` (4), `CicloMensualTest` (5: la medianoche de Lima, no la de UTC; el instante exacto de inicio ya es el ciclo nuevo; diciembre; febrero bisiesto), `PlanTest` (27, +15: editar programa, bajar también, un segundo cambio reemplaza, cancelar, `vigenteEn` antes y en el instante exacto, desactivar y activar conservan el programado).
- **Servicio** (`GestionarPlanesServiceTest`, 36): listar con cuentas y con el cambio que ya llegó, crear/editar/desactivar/activar/borrar con su bitácora dentro de la transacción, los rechazos sin registro, el texto exacto de la bitácora, la lectura con bloqueo.
- **REST** (`AdminPlanControllerTest`, 17): cada forma de límite inválido, 201 al crear, los 409, sin credencial no se ejecuta nada, id mal formado es 400.
- **Persistencia:** `JdbcPlanRepositoryTest` (26, +19: guardar, cambio programado, reemplazar y cancelar, nombre repetido, borrar con cuentas / con historial / el por defecto, cuentas por plan, el bloqueo) y `EsquemaDePlanesTest` (+2: los topes del programado y un solo programado por plan).
- **E2E real** (`PlanesAdminE2ETest`, 25, Spring completo + Postgres + filtros reales): listado con cuentas; crear con bitácora (clave de plataforma y administrador real); los datos inválidos no crean ni registran nada; nombre repetido; **dos altas a la vez**; el precio cambia al instante y **los límites quedan programados con la fecha exacta del ciclo siguiente** (en la base el plan sigue con 800 y 2000 espera); segundo cambio y cancelación; editar y desactivar **no tocan** las suscripciones; borrar con cuentas / con historial / el por defecto; y nadie más puede (sin credencial, JWT del dueño, API key, clave errónea).
- **Portal, Vitest (101):** cliente (15) y formatos del ciclo (+6), validación del formulario (13), BFF (`comun` 6, crear 5, editar y borrar 8, activar y desactivar 2), formulario (17), acciones de fila (12), tabla (12), página (3), diálogo (+1) y migas (+1).
- **Portal, Playwright** (`admin-planes.spec.ts`, 16): el listado, el menú y la miga, el cambio programado sembrado, crear, el formulario vacío, nombre repetido, ilimitado, editar (precio al instante, límites programados), cancelar el cambio, desactivar y reactivar, borrar, lo que no se ofrece, el rechazo de un plan con historial, y el BFF (sin sesión, ids inválidos, JSON inválido, cookie `httpOnly`).

### Verificación por mutación — 185/185 mueren (1 equivalente)

| Capa | Mutaciones | Cuántas |
|---|---|---|
| `Limites`, `CicloMensual`, `CambioDeLimites` | cero RUC o años; sin límites; el ciclo siguiente es el actual / se cuenta en UTC / empieza el día 2; un cambio sin límites o sin fecha | 8 |
| `Plan` | sin id; desactivar uno inactivo; activar uno activo; el cambio entra tarde o nunca; editar programa aunque no cambie nada / parte de los límites viejos / aplica al instante / pierde el por defecto o reactiva; desactivar o activar pierden el programado; editar sin límites | 15 |
| Servicio | listar sin aplicar lo que ya llegó o con 0 cuentas; crear inactivo, por defecto, sin registro o con otra acción; editar sin el no-op, sin aplicar lo vigente, sin guardar, sin registro; desactivar/activar sin guardar ni registrar; borrar el por defecto, con cuentas o con historial, sin mirar si la base lo dejó; leer sin bloqueo; sin datos; la bitácora sin fecha, sin «cancelados», con lo que no cambió, sin límites, `ilimitado` como `null`, precios sin dos decimales; plural | 30 |
| Persistencia | nombre repetido no reconocido; cancelar no borra; borrar el por defecto o con suscripciones; cuentas por plan cuenta el historial; suscripciones solo las vigentes; leer sin bloqueo; estado o precio sin actualizar; el programado lee los RUC, usuarios o retención del plan; un segundo cambio no reemplaza; fecha perdida | 14 |
| Esquema V38 | sin `ON DELETE CASCADE`; cero documentos, RUC, usuarios, keys o años en el programado; varios cambios por plan | 7 |
| REST | ilimitado con máximo; sin máximo ni ilimitado; límite omitido; sin RUC, retención o límites; la respuesta pierde el programado, las cuentas, el por defecto o el máximo; crear con 200; los 409 de plan en uso, nombre repetido y ya inactivo; el actor o el id | 16 |
| Cliente y formatos (Vitest) | lo ilimitado con cifra; sin máximo; sin separador de miles *(equivalente, ver abajo)*; gratis; plural de años; los cambios incluyen lo que no cambia; usuarios compara las keys; los métodos y rutas; sin JWT ni IP; el ciclo en UTC / el mes anterior / medianoche UTC | 18 |
| Validación del formulario | nombre sin recortar o con otro tope; tres decimales; cero entero; el tope de un entero; ilimitado manda el máximo; los errores de nombre, precio y límites; prellenar con los vigentes en vez de los programados | 12 |
| BFF | sin sesión; un id cualquiera; sin caché; sin IP; un arreglo o un nulo como cuerpo; crear con 200 o ignorando el cuerpo; editar sin sesión o con id cualquiera; borrar que llama a editar; activar y desactivar cruzados | 17 |
| Formulario | doble clic; cerrar mientras envía; método o ruta equivocados; nombre repetido sin marcar; 404 sin recargar; corte de red reintentado; lo escrito sobrevive; sin recargar ni cerrar tras guardar; sin aviso del programado ni fecha del ciclo; ilimitado sin deshabilitar o mostrando el número; corregir no quita el error; sin `aria-invalid`; un error de validación envía igual; el botón sin «Guardando…» | 19 |
| Acciones de fila y diálogo | el por defecto se desactiva o se borra; activar uno activo; borrar con cuentas; borrar como POST; el texto de las cuentas (cero, una, varias, sin la cifra); sin el nombre; rutas cruzadas; sin refresco si el estado estaba viejo; el diálogo siempre POST | 13 |
| Tabla, página y navegación | sin marca de por defecto o de inactivo; precio sin formato; cuentas en 0; sin aviso del programado, o con uno vacío, o sin fecha; sin estado en la fila; sin botón de nuevo ni estado vacío; la retención sin palabras; la página sin redirección, que revienta si no carga, sin JWT, con otro enlace; la miga bajo «Clientes»; **el menú sin enlace (Playwright)** | 17 |

Lo que sobrevivía en la primera tanda y se arregló con su test:

- **`desactivar()` y `activar()` descartaban el cambio de límites programado** sin que ningún test lo viera: desactivar un plan con un cambio pendiente lo perdía en silencio. Ahora hay un test que lo exige.
- **El servicio de `activar` no guardaba** (devolvía el plan activado pero no lo persistía) y mi test solo miraba la respuesta. Ahora comprueba lo guardado.
- **El campo «ilimitado» mostraba un número viejo** que ya no valía; **un cambio programado idéntico a lo vigente** se anunciaba como un cambio vacío; **`cambiosDeLimites` listaba también lo que no cambiaba**.
- **Equivalente:** pasar `"en-US"` a `"es-PE"` en el separador de miles (`limiteEnPalabras`): ambos escriben «1,500». El código usa `en-US` por la convención de miles del resto del portal.

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (8 min 53 s; incluye `ArchitectureTest` y todos los E2E de Spring con Postgres real).
- Portal: `tsc` y ESLint limpios; Vitest **733/733** (87 archivos); Playwright completo (`--workers=2`) **253/253**.

### Corrección de la revisión de #238

- **H1 (menor): un precio de 100 000 000 o más respondía 500 INTERNO.** La columna es `NUMERIC(10,2)` y nadie ponía tope: el desborde llegaba como
  `DataIntegrityViolationException`, sin handler. Ahora `Plan.PRECIO_MAX` (99 999 999.99) lo rechaza con `PRECIO_INVALIDO`, y el formulario y el mock usan el
  mismo tope. Tests `PlanTest.elPrecioCabeEnLaColumna` y el de `planes-formulario`, ambos en rojo antes.
- **H2 (menor): desactivar o activar después de que llegó un cambio programado respondía los límites viejos** (y el cambio ya vencido como pendiente), en contra
  del contrato de `PlanResponse`. Ahora ambos parten de `vigenteEn(ahora)`, como editar y listar. Test
  `desactivarYActivarDespuesDeQueElCambioEntroRespondenLosLimitesQueMandan`, en rojo antes; quitar cualquiera de los dos `vigenteEn` lo vuelve rojo.
- **H3 (menor): el mock respondía `400 VALIDACION`** a un id que no es UUID en los cuatro handlers de un plan; ahora `parametroInvalido("id")`, como el backend.
- Después: dominio y servicio de planes en verde; `tsc` y ESLint limpios; Vitest **736/736** (88 archivos); Playwright `admin-planes` **16/16**.

### Límites conocidos

- **El cambio programado se aplica «al leer», no con una tarea:** `vigenteEn` lo da por vigente en el instante exacto, y la fila de `plan` se actualiza en la siguiente edición. Mientras nadie edite el plan, la fila conserva los límites viejos y el cambio sigue en su tabla; quien haga valer los límites (#192) **debe** pasar por `Plan.vigenteEn` y no leer las columnas de `plan` por su cuenta.
- **El ciclo es el mes calendario de Lima para todas las cuentas**, no el día en que cada una contrató. Es lo que dice #192; si el plan comercial cambiara a ciclos por suscripción, hay que revisar `CicloMensual`.
- **Los límites que se ven en la tabla son los vigentes en el momento de cargar la página** (el servidor aplica `vigenteEn(ahora)`); una página abierta de un día para otro no se actualiza sola al llegar el ciclo.
- **El precio de un plan no tiene «ciclo siguiente»**: cambia al instante porque todavía no hay cobro automático (#194 es manual). Cuando lo haya, un cambio de precio a mitad de ciclo tendrá que decidir si afecta a quien ya pagó.
- **La mensajería de «cuentas» cuenta suscripciones vigentes**, incluidas las de cuentas dadas de baja (siguen apuntando al plan).
- **El borrado no se prueba contra una carrera real** (una cuenta que toma el plan justo entre mirar y borrar): lo cubre la condición SQL y un test del servicio con un repositorio que se niega a borrar, no un test concurrente.

## #192 · Planes: contador de consumo mensual por cuenta y empresa

**Estado: ✅ revisión de la PR (#239) corregida, 40/40 mutaciones verificadas (39 y 1 de la corrección).** Es código de dinero: se verificó sobre todo lo que **no** cuenta, que es donde se equivoca el cálculo. Solo backend (la pantalla de consumo es #193; #191 lo usa para avisar antes de cambiar un plan). Sin esta PR el límite de documentos de un plan no tiene contra qué compararse.

> ⚠️ **No pude leer `docs/plan/plan.md`**, que el issue cita como la definición de «documento consumido»: `docs/` es local y no existe en esta máquina. Seguí al pie lo que el propio issue dice (aceptados, sin rechazados / reintentos / errores / bajas, mes calendario en Lima). **Conviene que quien tenga ese documento compare las tres decisiones de abajo antes de mergear.**

### Qué cuenta

- **Solo los comprobantes que SUNAT aceptó**: `ACEPTADO` o `ACEPTADO_CON_OBS`, **y también `ANULADO`** (a él solo se llega desde un aceptado: lo aceptado ya consumió, y dar de baja no lo devuelve al cupo ni cambia el consumo de un mes cerrado; decisión de la revisión de #239). La regla vive en **un solo sitio**, `EstadoDocumento.cuentaParaElConsumo()`, y el SQL arma su lista de estados desde ahí (no hay un texto copiado que pueda desacordarse). Un test recorre los once estados y obliga a clasificar cada uno a propósito: un estado nuevo no puede colarse en el cobro, ni quedarse fuera, sin que ese test falle.
- **No cuentan:** `RECHAZADO`, `ERROR_ENVIO`, `FUERA_DE_PLAZO` (no llegaron), `RECIBIDO`, `INVALIDO`, `FIRMADO`, `PENDIENTE_AGRUPACION`, `ENVIADO` (todavía no hay respuesta de SUNAT).
- **Los reintentos no multiplican.** Un comprobante reintentado hasta que lo aceptan es **una fila** de `documento` con `intentos = 7`, no siete: se cuentan filas. Hay un test con un aceptado de 7 intentos y otro con rechazados y errores de envío muy reintentados.
- **Un resumen diario cuenta 1:** hoy el sistema no guarda resúmenes diarios como documentos (el estado `PENDIENTE_AGRUPACION` existe pero ningún servicio lo usa), así que no hay nada que excluir. Si algún día se guardan, **ese día habrá que decidir** cómo cuentan; está anotado en el código.
- **La comunicación de baja** vive en otra tabla (`comunicacion_baja`), no en `documento`: no cuenta por construcción.
- **Los cuatro tipos** (factura, boleta, nota de crédito y de débito) cuentan como un documento cada uno.

### El mes y los alcances

- **Mes calendario en America/Lima**, por la **fecha de emisión** (`fecha_emision`, un `DATE` ya en hora de Lima): del día 1 inclusive al día 1 siguiente exclusive. Los bordes están probados (30 de septiembre, 1 y 31 de octubre, 1 de noviembre; diciembre y enero; el mismo mes de otro año). **Sin `mes`, es el mes en curso de Lima, no el de UTC** (a las 03:00 UTC del 1 de noviembre todavía es octubre).
- `GET /v1/admin/empresas/{id}/consumo?mes=AAAA-MM` y `GET /v1/admin/cuentas/{id}/consumo?mes=AAAA-MM`. La cuenta devuelve el total —la suma de sus empresas— y el detalle por empresa **aunque alguna no haya emitido nada**, ordenadas por RUC. Una cuenta de otra no se mezcla; una empresa sin cuenta no figura en ninguna.
- `mes` es `AAAA-MM` y nada más (`400 PARAMETRO_INVALIDO` con `2026-13`, `26-10`, `2026-10-15`, `+12026-10`…); vacío o ausente es el mes en curso. `404` si la cuenta o la empresa no existen; `400` si el id no es un UUID.
- **Solo del administrador** (clave de plataforma o JWT de administrador): ni el dueño de la cuenta, ni una API key, ni una clave errónea lo ven. **Solo lectura**: no escribe nada ni deja registro.

### Tests (48)

- **Dominio** (`EstadoDocumentoTest`, +1): los once estados, uno por uno.
- **Persistencia, Postgres real** (`JdbcConsumoRepositoryTest`, 17): aceptados y con observaciones; los cuatro tipos; **un documento en cada uno de los demás estados**; rechazados y errores reintentados; un aceptado con 6 reintentos cuenta 1; la baja (comunicación y anulado); los bordes del mes; diciembre/enero; otro año; mes vacío; empresa inexistente; cada empresa lo suyo; la cuenta lista cada empresa por RUC aunque tenga 0; cuentas ajenas; empresa sin cuenta; cuenta sin empresas; y que la lista de la cuenta aplique **las mismas reglas** que la de la empresa.
- **Servicio** (`ConsultarConsumoServiceTest`, 8): el mes en curso es el de Lima (tres bordes); con y sin mes; el total de la cuenta es la suma; cuenta sin empresas; 404.
- **REST** (`AdminConsumoControllerTest`, 7) y **E2E real** (`ConsumoMensualE2ETest`, 15, Spring completo + Postgres + filtros reales): el recorrido completo de «lo que no cuenta» por HTTP, los meses mal escritos, 404/400, que consultar no escribe nada, y que nadie más puede.

### Verificación por mutación — 39/39 mueren

| Capa | Mutaciones | Cuántas |
|---|---|---|
| Regla del dominio | contar los enviados, los anulados, los rechazados o los errores de envío; contar solo `ACEPTADO` o solo `ACEPTADO_CON_OBS`; no contar nada | 7 |
| Contador en la base | la lista de estados con todos o con uno solo; el primer día del mes no cuenta; el primer día del mes siguiente cuenta (en la consulta de la empresa y en la de la cuenta); el mes empieza el día 2 o dura dos meses; contar series o sumar intentos en vez de documentos; las empresas sin documentos desaparecen; la cuenta lista todas las empresas o no las ordena por RUC; la empresa cuenta las de todos; el estado no se mira en la cuenta | 14 |
| Servicio | el mes en curso en UTC; la cuenta suma mal; una cuenta inexistente se consulta; el detalle pierde los documentos o el mes; la empresa pierde su razón social; el mes pedido se ignora | 7 |
| REST | cualquier mes vale; el mes 13 o el 00 valen; un mes vacío es un error; la cuenta o la empresa ignoran el mes; la respuesta pierde el mes, el total, el detalle, el RUC o los documentos | 11 |

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (8 min 51 s; incluye `ArchitectureTest` y todos los E2E de Spring con Postgres real).
- Portal: sin cambios (esta PR solo toca el backend).

### Corrección de la revisión de #239

- **H1 (importante, decisión de negocio): un comprobante aceptado y luego dado de baja dejaba de consumir.** El consumo de un mes ya cerrado bajaba después
  de cobrarlo, y emitir y anular permitía no gastar el límite. Decidido: `ANULADO` cuenta (`cuentaParaElConsumo`); la comunicación de baja sigue sin sumar
  otro documento. Tests del dominio, de persistencia y E2E cambiados primero (en rojo antes del cambio, que es también su mutación: quitar `ANULADO` de la
  regla los vuelve rojos). La documentación de la API y del puerto dicen ahora lo mismo.
- Después: tests de consumo de dominio, servicio, persistencia, REST y E2E **BUILD SUCCESSFUL**.

### Decisiones que conviene contrastar con el plan comercial

1. **`ANULADO` cuenta** (decidido en la revisión de #239). «Las bajas no cuentan» se lee como la comunicación de baja, que no suma otro documento —igual que «un resumen diario cuenta 1» habla de documentos de SUNAT—. Si el anulado no contara, el consumo de un mes ya cobrado bajaría después y un cliente podría emitir y anular para no gastar su límite.
2. **El mes es el de la fecha de emisión, no el de la aceptación.** Un comprobante emitido el 31 de octubre y aceptado el 1 de noviembre consume en octubre. Es lo que ya usa el listado de empresas («comprobantes del mes») y evita depender de una marca de tiempo que cambia con cada actualización de la fila.
3. **La consulta por id de una cuenta dada de baja devuelve sus números reales.** El issue de la baja (#201) dice que la cuenta de baja sale del cálculo de consumo y cobro: cualquier **listado o total agregado** (#193) debe excluirlas con `BajasEnListado`; esta consulta es la de una cuenta concreta que el administrador pidió.

### Límites conocidos

- **El «comprobantes del mes» que ya muestra el listado de empresas es otra cosa:** cuenta **todos** los documentos del mes, aceptados o no. No se tocó; el consumo de esta PR es el que se cobra, y la pantalla de #193 debe llamarlos distinto para no confundir.
- **No hay todavía una pantalla** ni el cruce con el límite del plan (#193). Tampoco se aplica ningún límite: hacerlo valer es otro trabajo, y cuando se haga debe leer los límites con `Plan.vigenteEn` (#190).
- **Sin caché ni tabla de agregados:** cada consulta cuenta filas de `documento` con el índice `(tenant_id, fecha_emision)`. Para una cuenta con millones de documentos al mes habría que medirlo; hoy no hay ese volumen.

## #191 · Planes: asignar y cambiar el plan de una cuenta

**Estado: ✅ revisión de la PR (#240) corregida, 150/150 mutaciones verificadas (148 y 2 de la corrección; 1 equivalente documentada).** Backend (`/v1/admin/cuentas/{id}/plan`) y portal (la ficha de la cuenta). Va **después de #192** en la pila (lo adelanté): el issue pide mostrar el consumo del mes antes de confirmar, y ese contador es de #192.

### Diseño

- **Subir de plan entra ya; bajar, al ciclo siguiente.** Lo que es «subir» o «bajar» lo decide el **precio** (`DireccionDeCambio`): más caro, subida; más barato, bajada; el mismo plan es una **renovación** (otro vencimiento u otra gracia) y entra ya. Dos planes distintos al mismo precio **no** son una bajada: nadie paga menos, así que no se hace esperar al cliente.
- **Subida o renovación: inmediata y condicional.** Se cierra la suscripción activa y se abre la nueva **en una sola sentencia**, condicionada a que la activa siga siendo la que el administrador vio (`409 CAMBIO_CONCURRENTE` si otro administrador la cambió en el medio; el E2E lo prueba con dos hilos: pase lo que pase queda **una** activa y nadie recibe un 500). En la misma sentencia se borra la bajada que esperaba: un cambio inmediato la cancela.
- **Bajada: programada, no aplicada.** La cuenta sigue con el plan de hoy hasta la medianoche del día 1 en Lima (`CicloMensual`, el mismo ciclo de #190 y #192). Queda en `suscripcion_cambio_programado` (V39): como mucho **una por cuenta** (una segunda bajada reemplaza a la primera), con el vencimiento y la gracia con que empezará la suscripción nueva.
- **Aplicar lo vencido: un trabajo programado cada diez minutos** (`AplicarCambiosDePlanWorker`). La suscripción nueva **empieza en la fecha programada**, no cuando el trabajo corrió, y la anterior termina en ese mismo instante: el historial dice cuándo cambió el plan de verdad y no hay hueco ni solape. Cada cuenta va en su transacción: una que falla no frena a las demás y se reintenta. Entre la búsqueda y la transacción se vuelve a mirar si sigue vencido (alguien pudo reprogramarlo).
- **Vencimiento y gracia.** Un plan de pago exige el vencimiento (`422 VENCIMIENTO_REQUERIDO`); uno gratis no. El vencimiento es **exclusivo** (en ese instante empieza la gracia) y debe ser posterior al inicio. La gracia va de **0 a 90 días** (el tope de 90 es mío: el issue no lo fija). Un plan fuera de la oferta no se asigna (`409 PLAN_INACTIVO`); la cuenta que ya lo tiene lo conserva.
- **Previsualización antes de confirmar** (`GET …/plan/previsualizacion?plan_id=`): si sube, baja o renueva, cuándo entra y el consumo de la cuenta **este mes** (solo comprobantes aceptados, de #192) frente al tope de documentos del plan nuevo, con una advertencia si ya lo supera. No escribe nada. El modal **no deja confirmar hasta tenerla**.
- **Bitácora** `CAMBIAR_PLAN`, en la misma transacción y con la cuenta en `cuenta_id` (aparece en el detalle de la cuenta): `desde=Negocio hacia=Emprende direccion=BAJADA efecto=CICLO_SIGUIENTE aplica_desde=2026-10-01 vence=2026-12-01 gracia=3`. Aplicar lo programado **no** deja otro registro: no es una acción de nadie y el de cuando se programó ya dice quién.
- **Un plan al que una cuenta va a pasar no se puede borrar** (#190 lo cuenta como en uso).
- **Portal:** en la ficha de la cuenta, una tarjeta con el plan, su estado de pago (**Al día / En gracia / Vencido**), «Pagado hasta el …» (el último día cubierto: el vencimiento es exclusivo), los días de gracia, hasta cuándo se la sirve, los límites vigentes y, aparte, la bajada que espera. El modal de cambio pide «Pagado hasta (inclusive)» y manda como vencimiento la medianoche de Lima del día siguiente.

### Tests

- **Dominio:** `DireccionDeCambioTest` (5) y `PlanesDeCuentaProgramadoTest` (16: programar, reemplazar, cancelar; un cambio inmediato cancela lo programado; el plan vigente antes y **en el instante exacto** de la fecha; aplicar empieza en la fecha programada con su vencimiento y su gracia).
- **Servicio** (`CambiarPlanDeCuentaServiceTest`, 36): las dos direcciones del cambio (subir entra ya, bajar queda programado y no toca la suscripción), renovar, la previsualización (consumo frente al tope, ilimitado nunca se supera), todas las validaciones, el conflicto concurrente, la bitácora dentro de la transacción, un cambio vencido sin aplicar, y aplicar lo vencido (fechas, una cuenta que falla, lo que ya no estaba vencido, sin bitácora).
- **Persistencia:** `JdbcSuscripcionRepositoryTest` (+14: el cambio programado, un cambio inmediato lo borra en la misma sentencia y uno que no se hizo no lo borra, los vencidos por orden, el límite) y los topes de V39 en `EsquemaDePlanesTest`.
- **REST** (`AdminPlanDeCuentaControllerTest`, 12) y **trabajo programado** (`AplicarCambiosDePlanWorkerTest`, 3).
- **E2E real** (`CambioDePlanE2ETest`, Spring completo + Postgres + filtros reales): subir entra ya con vencimiento y gracia y se cierra la anterior sin hueco; el listado de planes cuenta la cuenta en el plan nuevo; la bitácora (clave de plataforma y administrador real); **bajar deja programado y las filas de suscripción no cambian**; una segunda bajada reemplaza, una subida y una renovación cancelan; **llegada la fecha el trabajo la pasa a vigente desde la fecha programada** y una segunda pasada no vuelve a aplicar nada; la previsualización con consumo real (solo aceptados) y sin escribir nada; los rechazos; dos cambios a la vez; y nadie más puede.
- **Portal, Vitest (74):** formato de fechas (+9), cliente (6), validación (12), BFF (12), modal (21), tarjeta (9) y página (5).
- **Portal, Playwright** (`admin-plan-de-cuenta.spec.ts`, 15): la tarjeta en cada estado de pago, la previsualización de subida / bajada / renovación / advertencia, que no se puede confirmar sin ella, subir, bajar y cancelar renovando, las validaciones y el BFF.

### Verificación por mutación — 148/148 mueren (1 equivalente)

| Capa | Mutaciones | Cuántas |
|---|---|---|
| Dirección del cambio | el mismo plan no es renovación; a igual precio es bajada; la dirección invertida; la renovación o la bajada con efecto cambiado | 5 |
| `CambioDePlan` y el agregado | sin plan; vence al mismo instante; la gracia negativa; programar antes del inicio; un cambio inmediato no cancela lo programado; el plan vigente manda antes de tiempo o nunca; aplicar antes de la fecha, hoy en vez de la fecha programada, sin vencimiento o sin gracia; cancelar o programar que no hacen nada | 13 |
| Servicio | gracia de 90 / negativa; un plan gratis exige vencimiento o uno de pago no; la bajada entra hoy o se aplica al instante; un conflicto pasa; sin registro, sin la cuenta o con otra acción; el tope justo se supera; lo ilimitado se supera; la dirección desde el plan equivocado; efecto invertido; el consumo de otro mes; un plan inactivo se asigna; aplicar sin mirar la fecha; los fallidos no se cuentan; la bitácora sin vencimiento, sin fecha o con el texto cambiado; vista sin lo programado, con otro estado o sin «hasta cuándo»; la bajada no se programa o programa otro plan; lo vencido no se aplica antes de cambiar | 28 |
| Persistencia y esquema | un cambio inmediato no borra lo programado, o borra el de todas las cuentas; lo que vence hoy no cuenta; orden y límite de los vencidos; reprogramar sin actualizar vencimiento o gracia; leer sin vencimiento; cancelar que borra todo; un plan esperado se borra o no se cuenta; las restricciones de V39 | 14 |
| REST y trabajo | un cambio sin plan; la vista pierde lo programado, «hasta cuándo» o la gracia; la previsualización confunde dirección y efecto o pierde el consumo o la advertencia; los 409; el cambio pierde el vencimiento o la gracia; la previsualización pide otro plan; el trabajo no aplica o un fallo lo tumba | 14 |
| Fechas, validación y cliente | «pagado hasta» sin sumar un día / a medianoche UTC / en UTC; el último día cubierto; un mes 13; un día que no existe; una fecha con sufijo; fecha de hoy; un plan gratis exige fecha; la gracia (90, negativa, con letras, vacía); el cuerpo; la URL, el método, el JWT y la IP | 24 |
| BFF | sin sesión; un id cualquiera; el cuerpo inválido; sin IP; sin `no-store`; la previsualización con plan cualquiera o sin plan | 11 |
| Modal | doble clic; confirmar sin la previsualización; el botón sin esperarla; una respuesta atrasada pisa a la nueva; cerrar mientras envía; un corte de red reintentado; un conflicto sin recargar; lo escrito sobrevive; sin recargar tras confirmar; efecto o advertencia invertidos o sin advertir; lo ilimitado dice una cifra; el mes sin formato; cuerpo vacío; otro plan; el precio equivocado; el plan actual sin marca; elegir nada pide la previsualización; sin «Cambiando…»; un error de validación envía igual; elegir plan limpia errores ajenos | 22 |
| Tarjeta, página y Playwright | sin vencimiento siempre; «pagado hasta» con el día del vencimiento; la gracia sin vencimiento; sin «hasta cuándo»; sin la bajada; planes fuera de la oferta; plural de la gracia; los límites sin documentos; sin «desde»; el modal sin su plan actual; la página que se cae si el plan falla, sin JWT, con otro enlace o con la cuenta equivocada; **la previsualización pide otro plan (Playwright)** | 18 |

Lo que sobrevivía en la primera tanda y se arregló con su test (o con menos código):

- **Aplicar un cambio ya no vencido** (el trabajo lo busca y entre la búsqueda y la transacción alguien lo reprogramó) no estaba probado: ahora hay un test con un repositorio desfasado.
- **El orden de las validaciones** (la gracia antes de buscar el plan) tampoco; ahora está fijado.
- **Fechas imposibles en el futuro** (`2026-11-31`, `2027-02-29`): mis casos eran del pasado, así que los atrapaba «la fecha ya pasó» por casualidad. Ahora hay casos futuros, y la validez se reconoce solo por el mes (comparar el año y el día sobraba: un día o un mes imposibles siempre desbordan a otro mes). Un sufijo (`2026-10-04-5`) pasaba.
- **Elegir otro plan borraba también el error de la gracia**, que sigue mal: ahora no.
- **El servicio ya no depende del repositorio de cuentas:** toda cuenta tiene siempre una suscripción, así que «no existe» y «no tiene» eran lo mismo (dos comprobaciones que ningún test podía distinguir).
- **Equivalente:** quitar de `confirmar()` la guarda «solo con la previsualización lista». El botón ya está deshabilitado en ese estado y no hay otro camino al envío; la guarda queda por el tipo (`previa.datos`).

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (9 min 13 s; incluye `ArchitectureTest` y todos los E2E de Spring con Postgres real).
- Portal: `tsc` y ESLint limpios; Vitest **805/805** (94 archivos); Playwright completo (`--workers=2`) **268/268**.

### Corrección de la revisión de #240

- **H1 (importante): un cambio descartaba en silencio la bajada que esperaba.** Una subida o una renovación la cancelan y otra bajada la reemplaza, pero la
  previsualización no lo decía: renovar Negocio a quien ya había pedido bajar a Emprende le seguía cobrando Negocio sin que el administrador lo supiera. Ahora la
  previsualización trae `programado_que_se_descarta` (el plan y la fecha; ausente si no hay, o si ya llegó su fecha, porque entonces se aplica primero) y el modal
  lo dice: «Cancela el paso a Emprende programado para el 1 Nov 2026» o «Reemplaza el paso a…». Tests: servicio (2, con dos mutaciones que mueren: no
  devolverlo, o devolverlo aunque ya llegó), controlador (2), modal (3, en rojo antes) y Playwright sobre la cuenta sembrada con una bajada.
- **H2 (menor): el mock respondía `400 VALIDACION`** a un id de cuenta o un `plan_id` que no son UUID; ahora `parametroInvalido("id")` /
  `parametroInvalido("plan_id")`, como el backend.
- Después: servicio y controlador en verde; `tsc` y ESLint limpios; Vitest **811/811** (95 archivos); Playwright `admin-plan-de-cuenta` + `admin-planes` **32/32**.

### Límites conocidos

- **Que una suscripción venza no hace nada por sí solo.** El estado pasa a «En gracia» y luego a «Vencido» y se ve en la ficha, pero no hay corte ni baja automática de plan al vencer: eso lo muestra #193 (planes vencidos o en gracia) y hacerlo valer es otro trabajo.
- **Entre la fecha de una bajada y la pasada del trabajo hay hasta diez minutos** en que la fila sigue en el plan viejo: el listado de planes (#190) todavía cuenta a la cuenta en el anterior. Un cambio manual en ese lapso aplica primero lo vencido.
- **La gracia máxima de 90 días es una decisión mía**, igual que que renovar sea el mismo plan con otro vencimiento (no hay una acción de «extender» aparte: #194, el pago manual, podrá extender el vencimiento).
- **No se bloquea cambiarle el plan a una cuenta de baja o suspendida:** es una acción del administrador y a veces es justo lo que hace falta.
- **El modal no se refresca si otro administrador cambia el plan mientras está abierto:** se entera al confirmar (`CAMBIO_CONCURRENTE`, que además recarga la página).

## #193 · Planes: consumo contra el límite, alertas y exportación

**Estado: ✅ revisión de la PR (#241) corregida, 162/163 mutaciones verificadas (161 y 1 de la corrección; 1 equivalente documentada).** Backend (`GET /v1/admin/consumo` y `/v1/admin/consumo/exportacion`) y portal (`/admin/consumo`). Va después de #191: usa el plan vigente de cada cuenta y el contador de #192.

### Diseño

- **Una sola definición de «cerca del límite» y de «plan vencido».** `UsoDeLimite` (dominio) calcula el porcentaje —**hacia abajo**: 79,9 % se ve como 79 % y no alerta— y la alerta («el porcentaje llegó al umbral»), así nunca se contradicen; `Suscripcion.estadoDeLaVigente` es la regla de pago que ya usa la ficha de la cuenta. El SQL de los filtros reproduce las mismas fórmulas y hay tests que comparan base y dominio.
- **Umbral 80 % inclusive.** El issue dice «por encima del 80 %»; elegí que el 80 justo ya alerte (240 de 300). Se cambia en un solo sitio (`UMBRAL_DE_ALERTA`) y el portal lo muestra tal como lo manda el backend.
- **Se cuenta como #192** (solo aceptados, por fecha de emisión, mes calendario de Lima) y **se compara con el plan de hoy** de cada cuenta, con los límites que rigen hoy (un cambio de límites programado cuenta desde su fecha). Un mes pasado se compara con el plan actual, no con el que tenía entonces: limitación explícita, abajo.
- **Quién sale:** una fila por cuenta que no está de baja (#201); las suspendidas sí.
- **Vistas:** `TODAS`, `CERCA_DEL_LIMITE` (`en_alerta`) y `PLAN_VENCIDO` (`vence_en <= ahora`: en gracia o vencida). Orden por porcentaje (de mayor a menor, **los planes sin tope al final**) o por documentos; el desempate es siempre nombre y luego id, así la paginación no repite ni pierde cuentas. El total (`X-Total-Count`) refleja el filtro.
- **Exportación CSV** completa y sin paginar, de lo que se ve (mes, vista y orden), como `consumo-AAAA-MM.csv`: UTF-8 **con marca de orden de bytes** (Excel lee las tildes), registros con CRLF también el último, RFC 4180 (comas, comillas y saltos entre comillas) y **neutralización de fórmulas**: un nombre de cliente que empiece por `= + - @`, tabulador o retorno lleva una comilla delante. Un plan sin tope dice `ilimitado`.
- **BFF de la descarga:** el CSV pasa como bytes (`Response.text()` quita la marca UTF-8), valida mes/vista/orden (400 si están mal escritos: no se exporta otra cosa que lo que se ve) y nunca se cachea. El JWT sigue en la cookie `httpOnly`.
- **La columna «Comprobantes del mes» de Empresas** contaba **todo** lo emitido y se parecía demasiado al consumo: ahora dice «Emitidos en el mes» con una ayuda que remite a Consumo.
- Solo lectura: no escribe nada ni deja bitácora (el E2E lo comprueba).

### Tests

- **Dominio:** `UsoDeLimiteTest` (11: redondeo hacia abajo, umbral inclusive, sin tope, más de 100 %, desbordes) y `SuscripcionTest` (+2: la regla de pago estática y sus bordes).
- **Servicio** (`ConsultarConsumoDeCuentasServiceTest`, 12): valores por defecto, mes de Lima y no de UTC, porcentaje y alerta, los bordes exactos del estado de pago, «hasta cuándo se la sirve».
- **Persistencia** (`JdbcConsumoPorCuentaRepositoryTest`, 24, Postgres real): solo aceptados y el mes con sus bordes (día 1 y último día), una suscripción por cuenta, límites que mandan hoy, bajas fuera y suspendidas dentro, orden y desempates, 79/80 %, vencido en el instante exacto y coincidencia con la regla del dominio, paginación estable.
- **REST:** `AdminConsumoDeCuentasControllerTest` (10) y `CsvDeConsumoTest` (12: BOM, CRLF, comillas, fórmulas una por una, retorno suelto).
- **E2E real** (`ConsumoPorCuentasE2ETest`, 14; Spring completo + Postgres + filtros reales, con los planes sembrados): fila completa, 79/80 %, sin tope, orden, filtros con su total, estado de cobro con gracia, bajas, mes pedido, paginación, CSV completo, CSV con filtro, fórmula en el nombre, solo lectura, parámetros inválidos y que nadie más (dueño, API key, clave errónea) pueda verlo ni bajarlo.
- **Portal, Vitest (+54):** cliente (20), BFF de la exportación (6), tabla (22), página (5) y miga (1).
- **Portal, Playwright** (`admin-consumo.spec.ts`, 20): la lista en cada estado, orden, bajas fuera, enlace al detalle, las tres vistas con sus totales, estado en la URL, mes y su vacío, paginación y página fuera de rango, **la descarga real** (nombre, BOM, filas, filtro), el BFF sin sesión y con parámetros malos, el menú y la miga, la columna renombrada de Empresas y la página sin sesión.

### Verificación por mutación — 161/162 mueren (1 equivalente)

| Capa | Mutaciones | Cuántas |
|---|---|---|
| `UsoDeLimite` y regla de pago | umbral 79/81, alerta exclusiva, redondeo (a la mitad / hacia arriba), sin tope que alerta o tiene 0 %, el porcentaje se corta en 100 o desborda, el producto desborda en silencio; vencimiento y gracia inclusivos, sin gracia nunca vence, sin vencimiento vencida | 14 |
| Repositorio | cambio de límites un instante tarde o nunca, suscripciones viejas mezcladas, días 1 y siguiente del mes, todos los estados, vencido sin el instante exacto, umbral exclusivo, cuentas sin documentos desaparecen, sin tope primero, orden invertido, desempates (nombre, id) por orden, bajas salen, página mal calculada, orden por documentos que ordena por porcentaje, mes de dos meses, contar sin filtro, límite siempre del plan | 19 |
| Servicio | mes de UTC, mes pedido ignorado, filtro/orden/umbral por defecto otros, «hasta cuándo» sin gracia, estado sin gracia, nunca alerta, tope nulo que no es ilimitado, `todas` y `contar` sin filtro, porcentaje perdido | 12 |
| REST y CSV | página 0, tamaño sin acotar, total sin filtro, nombre del archivo, mes pedido, exportación sin filtro o sin orden, umbral, lista sin orden o filtro, sin UTF-8, sin descarga, DTO (estado, límite, «se sirve hasta»), validación del mes; BOM, LF en vez de CRLF, sin fin de línea final, cada carácter de fórmula (`= + - @` tab retorno), comilla, comas, saltos y retornos, comillas sin duplicar, `ilimitado`, alerta invertida, porcentaje vacío, estado, cabecera, fecha vacía | 38 |
| Cliente y BFF (portal) | mes 13 / sin ancla / cualquiera, valores por defecto, página 0 o fraccionaria, exportación sin orden / filtro / mes, consulta sin tamaño o página, la URL con los valores por defecto, fuera de rango, total, JWT, caché, error ignorado o sin código o sin mensaje, disposición; BFF: sin sesión, mes / filtro / orden mal escritos, charset, descarga, caché, parámetros perdidos, código y status del rechazo, error no propagado | 40 |
| Tabla, página y miga | tope exacto, barra pasada de 100 y colores, tonos del estado de pago, alerta sin etiqueta o con la equivocada, sin tope, fecha pagada, páginas que no se conservan (cambiar, vista, filas), exportación sin el mes medido o sin `download`, vista actual sin marcar, rango, vacío por vista, marca de alerta, umbral fijo, mes mostrado, enlace al detalle, paginación que pierde el filtro; página: sin sesión, sin corregir, reintentar, total, params | 39 |

Lo que sobrevivía en la primera tanda y se arregló con su test:

- **Porcentaje y producto con cifras imposibles** (`Math.min` al entero máximo, `multiplyExact`): no estaban probados; ahora sí (un conteo imposible falla a la vista en vez de dar la vuelta).
- **El primer día del mes** no tenía documentos de prueba (solo el 30 de septiembre y el 1 de noviembre): ahora el día 1 y el último cuentan y sus vecinos no.
- **Dos cuentas con el mismo nombre**: el desempate por id no se probaba; ahora, en los dos órdenes.
- **`todas` ignoraba el filtro** sin que ningún test lo viera (solo se miraba el orden).
- **Un retorno de carro suelto** en un nombre no obligaba a comillas.
- **Portal:** el 100 % exacto («En el límite»), el clic en una página que perdía el filtro.
- **Equivalente:** `cambiar({ mes: mesValido(e.target.value) })` → `e.target.value`. jsdom (y Chromium) sanean un `<input type="month">` a `""` antes de llegar al código, así que no hay valor inválido que probar; la guarda queda por navegadores que dejan escribir texto libre en ese campo.

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (9 min 46 s; incluye `ArchitectureTest`).
- Portal: `tsc` y ESLint limpios; Vitest **859/859** (98 archivos); Playwright completo (`--workers=2`) **288/288**: en la primera corrida pasaron 285 y fallaron 3 tests de la spec nueva (filas que caían en la página 2 con el tamaño por defecto); corregida la spec, esa spec pasa 20/20.

### Corrección de la revisión de #241

- **H1 (menor): el CSV decía un día más de servicio pagado que la pantalla.** Escribía `pagado_hasta` y `se_sirve_hasta` como el instante de vencimiento, que es
  exclusivo (`2026-10-20T05:00:00Z` es la medianoche del 20 en Lima), mientras la pantalla muestra el último día cubierto («Pagado hasta el 19 Oct 2026»). Ahora
  el CSV escribe también el último día cubierto en hora de Lima (`2026-10-19`), en el backend y en el mock; la documentación de la API lo dice. Tests:
  `CsvDeConsumoTest.unaFilaLlevaTodosSusDatosConLasFechasComoUltimoDiaCubierto` (en rojo antes) y la spec de Playwright, que exige fechas `AAAA-MM-DD` al final de
  la fila. Se probó en la revisión, además, que el filtro «cerca del límite» de la base (`documentos * 100 >= limite * 80`) equivale al porcentaje hacia abajo
  del dominio, y que el estado del plan del listado y el de la ficha salen de la misma función.
- Después: tests de consumo de `in-rest` en verde; `tsc` y ESLint limpios; Playwright `admin-consumo` **20/20**.

### Límites conocidos

- **Un mes pasado se compara con el plan de hoy**, no con el que tenía la cuenta ese mes (no hay historial de límites por mes). Lo dice la propia pantalla.
- **Umbral de 80 % inclusivo y fijo:** decisión mía sobre el «por encima del 80 %» del issue; no es configurable por cuenta ni por plan.
- **Sin avisos al cliente ni al operador:** la pantalla es de consulta y exportación; enviar la alerta por correo no estaba en los criterios.
- **La exportación no pagina:** con decenas de miles de cuentas habría que pasarla a flujo; hoy el volumen no lo pide.
- **El plan vencido no corta el servicio:** solo se ve (hacerlo valer sigue siendo otro trabajo, como dice #191).

## #194 · Planes: registro manual de pagos e historial

**Estado: ✅ revisión de la PR (#242) corregida, 177/180 mutaciones verificadas (2 equivalentes documentadas, 1 código muerto eliminado).** Backend (`POST` y `GET /v1/admin/cuentas/{id}/pagos`, migración V40) y portal (la sección «Pagos» de la ficha de la cuenta). Va después de #193 en la pila.

### Diseño

- **Un pago es un apunte que solo se agrega.** No se edita ni se borra: es el rastro de plata que entró; una equivocación se aclara con otro apunte. Guarda la cuenta, la suscripción vigente en ese momento, el periodo (`periodo_desde`…`periodo_hasta`, ambos inclusive, hasta un año), el monto en soles (mayor que cero, hasta dos decimales, hasta 9 999 999,99), el medio (`TRANSFERENCIA`, `DEPOSITO`, `YAPE`, `PLIN`, `TARJETA`, `EFECTIVO`, `OTRO`), la fecha de pago (no futura, en hora de Lima), una referencia y una nota opcionales. **Sin pasarela de pago, a propósito.**
- **Extender el vencimiento es opcional y condicional.** Con `extender_vencimiento` el vencimiento de la suscripción vigente pasa a ser la medianoche (Lima) del día siguiente a `periodo_hasta`, el mismo criterio exclusivo de #191. Solo si **adelanta** el vencimiento (`409 EXTENSION_SIN_EFECTO`: pagar un mes que ya estaba pagado no extiende nada) y solo en un plan que **vence** (`409 PLAN_SIN_VENCIMIENTO`: el gratis no). No toca el plan, la gracia ni la bajada programada. La extensión es una sentencia condicionada a que el vencimiento siga siendo el que se vio (`409 CAMBIO_CONCURRENTE` si otro administrador lo movió).
- **El pago, la extensión y la bitácora son una sola transacción.** Si la bitácora no puede escribir, no queda el pago ni el vencimiento movido (el E2E lo prueba rompiendo la tabla de bitácora a propósito). `REGISTRAR_PAGO` dice el pago, el periodo, el monto, el medio, la fecha y el nuevo vencimiento (o `sin_cambio`); **no lleva la referencia ni la nota**, que son texto libre del administrador y ya están en el pago.
- **El mismo apunte no se anota dos veces.** Con referencia, una cuenta no repite medio y referencia (sin distinguir mayúsculas): `409 PAGO_DUPLICADO`, por un índice único parcial; el insert pasa a «no hacer nada» y el repositorio lo cuenta como repetido, sin excepción que aborte la transacción. Así un doble clic o un reintento no duplican el pago. **Sin referencia no hay con qué comparar**, y dos pagos iguales se pueden anotar (límite conocido).
- **El historial** va del más reciente al más antiguo (fecha de pago, luego registro, luego id para que dos páginas no se pisen), paginado, con el total en `X-Total-Count`.
- **Portal:** la ficha de la cuenta muestra los **diez más recientes** (y dice cuántos hay si son más) con periodo, monto, medio, referencia y hasta dónde dejaron pagada la cuenta. El modal «Registrar pago» sugiere el periodo desde el día siguiente a donde está pagada la cuenta, y la casilla **«Extender el vencimiento»** viene marcada cuando se puede (el plan vence y el periodo adelanta), diciendo de qué día a qué día se mueve; si no se puede, se deshabilita y explica por qué. Los pagos y el plan se piden por separado: si el plan no carga, los pagos se ven igual y solo se pierde la opción de extender.
- **Un corte de red no reintenta a ciegas:** si no se sabe si el pago llegó, el modal manda a recargar para ver si quedó registrado (un reintento podría anotarlo dos veces).

### Tests

- **Dominio:** `PagoTest` (17: periodo, monto con decimales y tope, medio, fecha, referencia y nota recortadas y con límite, ids, y el vencimiento que daría).
- **Servicio** (`PagosDeCuentaServiceTest`, 26): registrar con y sin extender, todos los rechazos (plan que no vence, sin efecto, concurrente, duplicado, datos inválidos, cuenta inexistente), «hoy» de Lima y no de UTC, la bitácora dentro de la transacción y sin la referencia ni la nota, y el historial.
- **Persistencia** (`JdbcPagoRepositoryTest`, 19, Postgres real): lo guardado vuelve igual, el orden y las páginas, el apunte repetido (sin distinguir mayúsculas, por medio, por cuenta), las restricciones de la base y los tamaños máximos, y `extenderVencimiento` (condicional, solo la activa, sin tocar lo demás).
- **REST:** `AdminPagoControllerTest` (11).
- **E2E real** (`PagosManualesE2ETest`, 23; Spring completo + Postgres + filtros reales): registrar sin extender, extender (movimiento exacto del vencimiento, plan y gracia intactos, estado «al día»), no cancela la bajada programada, plan sin vencimiento, sin efecto, duplicado (deshace la extensión), **bitácora rota → no queda nada**, **dos administradores a la vez → un 201 y un 409, nunca un 500**, datos inválidos, bitácora (clave de plataforma y administrador real), historial y páginas, los pagos sobreviven a un cambio de plan, y que nadie más pueda verlos ni anotarlos.
- **Portal, Vitest (+76, 935 en total):** el cliente de la API, la validación del formulario, el BFF, el modal, la sección de pagos y la ficha de la cuenta.
- **Portal, Playwright** (`admin-pagos.spec.ts`, 19): la ficha con sus pagos, el orden, «sin referencia», el recorte de diez, la cuenta sin pagos, registrar sin y con extensión (el plan de la ficha cambia), el duplicado, la misma referencia por otro medio, las validaciones del formulario, la casilla en cada estado y el BFF (sin sesión, id, cuerpo, rechazos del backend).

### Verificación por mutación — 177/180 mueren (2 equivalentes, 1 código muerto eliminado)

| Capa | Mutaciones | Cuántas |
|---|---|---|
| `Pago` | periodo de un día / al revés / de más de un año (y su borde) / sin fechas; monto cero, tres decimales, ceros de más, tope exclusivo o ausente, sin normalizar, sin monto; sin medio ni fecha; texto sin recortar, vacío que no es nulo, límites exclusivos o cambiados; cada id obligatorio; el vencimiento que daría (mismo día, UTC); la extensión que se pierde | 27 |
| Servicio | fecha futura (pasa, UTC, hoy ya es futuro); nunca o siempre extiende; el mismo vencimiento extiende; plan sin vencimiento o un vencimiento que retrocede pasan; duplicado y conflicto pasan; la extensión pide otro vencimiento; bitácora ausente, de otra acción, sin la cuenta, sin el id del pago, que dice siempre «sin_cambio» o lleva la referencia; el historial de una cuenta inexistente; el total; sin comando; el pago pierde su suscripción, su nota, su referencia o su instante | 24 |
| Persistencia y esquema | orden (fecha, registro, id, cada uno), página mal calculada, `registrar` al revés, historial o total que mezclan cuentas, nota o extensión perdidas, extender sin exigir la activa o el vencimiento visto o tocando otra suscripción; la base acepta monto cero, periodo al revés o de más de un año, medio desconocido, referencia que distingue mayúsculas, monto, referencia o nota que no caben, pago sin cuenta o sin suscripción | 22 |
| REST | página 0, tamaño sin acotar, total perdido, 200 en vez de 201, siempre / nunca extiende, periodo y referencia / nota cruzados, el DTO pierde medio, monto, fecha o extensión, los tres 409 | 16 |
| Validación y cliente (portal) | cada regla del formulario (periodo, un año y su 29 de febrero, monto con coma y tope, medio, fecha futura y hoy, referencia y nota con sus límites y recortes, extender sin plan que venza o sin adelantar) y el cuerpo que arma; el cliente (medios, tamaño, página, total, JWT, método, IP); el BFF (sesión, id, cuerpo, 201, caché, IP, error) | 46 |
| Modal, sección y ficha | periodo sugerido, fecha por defecto, plan que vence, adelanta, casilla marcada / habilitada / que respeta la elección, doble clic, cierre mientras envía (botón, cruz, Escape), corte de red y respuesta inválida, conflicto y no encontrado que recargan, reinicio, validación previa, contexto, casilla, ruta; monto, fecha, medio, extensión, guion, nota, aviso de recorte, vacío, botón; la ficha (pide los pagos, los separa del plan, su error y su «Reintentar») | 45 |

Lo que sobrevivía en la primera tanda:

- **Un test de la clase equivocada** (`extenderVencimiento` ignorando que la suscripción ya terminó): la mutación corría contra `JdbcSuscripcionRepositoryTest` y la regla la prueba `JdbcPagoRepositoryTest`; corrida contra la correcta, muere.
- **Cerrar con Escape mientras se envía:** la guarda de `cambiarAbierto` no tenía test (el botón y la cruz ya no están, pero Escape pasa por el mismo camino). Ahora hay dos tests, uno de cada lado.
- **El guion de «sin referencia»** podía estar oculto sin que ningún test lo viera.
- **Equivalente — `ORDER BY … id`:** el índice `ix_pago_cuenta` termina en `id` y ya entrega ese orden en los empates; la cláusula queda para garantizarlo aunque el planificador no use el índice.
- **Equivalente — valor inicial de la fecha de pago y del «desde»:** abrir el modal siempre llama a `reiniciar()`, así que el valor del `useState` no se llega a ver.
- **Código muerto eliminado — error de la casilla «extender»:** el modal deshabilita y desmarca la casilla cuando no se puede extender, así que ese error nunca se mostraba; se quitó del modal y la regla queda en `validarPago`, probada.

### Suites

- Backend: `./gradlew test` completo, **BUILD SUCCESSFUL** (9 min 58 s; incluye `ArchitectureTest`).
- Portal: `tsc` y ESLint limpios; Vitest **935/935** (103 archivos); Playwright completo (`--workers=2`) **307/307**.

### Corrección de la revisión de #242

- **H1 (menor): el mock no seguía el contrato del backend en dos rechazos.** Un id de cuenta que no es UUID respondía `400 VALIDACION` (el backend:
  `400 PARAMETRO_INVALIDO`), y un pago sin `medio` respondía `400 JSON_INVALIDO` cuando el backend lo rechaza en el dominio con `422 MEDIO_INVALIDO` (solo un medio que
  no existe es un 400, porque no se convierte del JSON). Ninguna spec dependía de esos códigos (el formulario exige el medio); después, `tsc` limpio y Playwright
  `admin-pagos` **19/19**.
- En la revisión se comprobó, además, que el pago, la extensión condicional del vencimiento y la bitácora van en una sola transacción, que el `ON CONFLICT` usa el
  índice único parcial de la referencia y que el tope del monto (`9 999 999,99`) coincide con la columna `NUMERIC(9,2)`.

### Límites conocidos

- **Sin referencia, dos pagos idénticos se pueden anotar dos veces:** no hay con qué reconocer un reintento. Con referencia, no.
- **No se edita ni se anula un pago.** Una equivocación se aclara con otro apunte; si hace falta anular, es otro issue (y otra acción en la bitácora).
- **Un pago no valida que el periodo siga al anterior:** el administrador decide; la casilla solo comprueba que el vencimiento avance.
- **El monto es solo en soles** y no se concilia con el precio del plan (puede haber descuentos, pagos adelantados o parciales).
- **La ficha muestra los diez pagos más recientes:** el historial completo está en la API (paginado); una pantalla de historial con páginas no estaba en los criterios.
- **No emite comprobante de pago:** emitir la factura de la plataforma con el propio khipu queda para después, como dice la épica.

## #200 · Decisión: cómo se atienden los tickets de soporte

**Estado: ✅ decidido y revisado (#243); el issue de implementación es #250.** Es un issue de decisión, no de código: esta sección es el entregable (el issue pide dejarlo en `docs/superpowers/specs/`, que está en `.gitignore` y es solo local; se escribió allí **y** acá, que es lo que viaja con la PR).

### Decisión

**Los tickets viven en un servicio externo; khipu no construye un sistema de tickets.** Lo único que se construye es lo mínimo para llegar a él: un enlace y un correo de soporte, configurables, visibles en el portal del cliente y en el backoffice (issue de implementación propuesto abajo). Se revisa si se cumple alguna condición de la última sección.

### La comparación

| Criterio | Tickets propios (asunto, empresa, estado, notas internas) | Servicio externo |
|---|---|---|
| **Costo** | Sin licencia, pero se paga en tiempo de desarrollo y de mantenimiento, para siempre. | Una cuota mensual por agente o un plan gratuito, según el servicio (**precios a verificar**: no se consultaron). Con uno o dos agentes es poco. |
| **Esfuerzo** | Lo más grande de todo el backoffice. Mínimo: tablas de ticket, mensaje y nota interna; estados y asignación; API; lista y detalle en el backoffice; **pantallas del cliente** para abrir y seguir un ticket; correos de aviso en las dos direcciones; adjuntos; permisos y bitácora; protección contra spam. Mi estimación: entre dos y cuatro issues del tamaño de #191, y **recibir correos** (que el cliente responda por mail) no existe hoy en la plataforma: es otro componente. | Elegir el servicio, crear una cuenta, una dirección de correo y una página; el resto lo da hecho (respuestas por correo, estados, notas internas, adjuntos, plantillas). Una integración más profunda (widget, enlace desde la ficha de la cuenta) es opcional y posterior. |
| **¿El cliente lo ve desde su portal?** | Sí, pero solo si se construye (es la mitad del esfuerzo de arriba). | Lo ve en el propio servicio (hilo por correo o su portal de clientes) y desde el portal de khipu llega por un enlace o un widget. No hay una vista de tickets *dentro* de khipu. |
| **¿Qué pasa con los datos?** | Quedan en nuestra base: nosotros decidimos retención, copia y borrado, y no hay un tercero más que tratar datos personales. | Las conversaciones viven en un tercero (encargado del tratamiento): hay que revisar su contrato de tratamiento de datos, dónde guarda la información y cómo se exporta si algún día cambiamos. A cambio, la plataforma no guarda más datos personales de los que ya tiene. |

### Por qué externo

1. **El volumen de hoy no justifica construirlo:** son clientes que se dan de alta uno a uno, con soporte asistido. Un sistema de tickets propio sería lo más caro del backoffice para resolver algo que ya está resuelto, y mal hecho es peor que no tenerlo («un sistema de tickets a medias», como dice el issue).
2. **Lo que el soporte necesita ya existe en el backoffice:** la ficha de la cuenta, sus empresas, su plan y sus pagos, la impersonación de solo lectura (#184) y el restablecimiento de acceso (#183). El ticket es una conversación; el contexto está acá, y se une con un enlace, no con una tabla.
3. **El costo de equivocarse es bajo y reversible:** un servicio externo se cambia por otro (o por uno propio) sin migrar nada de la plataforma; un sistema propio, no.
4. **Los datos sensibles no entran:** se le pide al cliente no pegar claves (API keys, contraseñas, certificados) en un ticket; el soporte nunca las necesita (hay acciones del administrador que las reemplazan).

### Lo que sí se construye (issue de implementación propuesto)

**Soporte: enlace y correo de soporte configurables.** Criterios propuestos:

- Dos variables de entorno opcionales, `SUPPORT_URL` (la página o el portal de ayuda) y `SUPPORT_EMAIL`, con valores por defecto vacíos y una validación clara si traen un valor mal formado (URL `https`, correo con formato).
- El portal del cliente muestra «¿Necesitas ayuda?» con ese enlace y ese correo, en el pie y en las pantallas de error; sin configurar, no muestra nada.
- El backoffice muestra, en la ficha de una cuenta, un enlace «Escribir a soporte» con el correo ya redactado (asunto con el nombre de la cuenta y su id; sin datos sensibles).
- Se documenta en `README.md` y `.env.example`.
- Fuera de alcance: tablas de tickets, recibir correos, widget embebido.

El issue de implementación es **#250** (abierto en la revisión de la PR, #243, con estos mismos criterios).

### Cuándo volver a decidir

Se reabre si pasa alguna de estas cosas:

- **El soporte necesita el ticket dentro de la ficha de la cuenta** (verlo sin salir de khipu) y el enlace deja de alcanzar.
- **El volumen crece** hasta que la cuota por agente o la falta de integración cuesten más que construir lo mínimo.
- **El soporte pasa a depender del plan** (tiempos de respuesta acordados por plan, #189): el plan tendría que viajar al ticket.
- **Un cliente exige que los datos del soporte no salgan de la plataforma** (contrato o regulación).
- **El servicio elegido sube de precio o cambia sus condiciones** de tratamiento de datos.

### Límites de esta decisión

- **No se eligió un servicio concreto:** el issue pide decidir *si* son propios o externos; elegir producto (y verificar precios y contrato de datos) es parte del trabajo de implementación o de quien administra la plataforma.
- **Las estimaciones de esfuerzo son mías**, no medidas.
- **No se consultaron precios** de ningún servicio; la comparación de costo es cualitativa.

## #198 · Backoffice: verificación de integridad del almacenamiento

**Estado: ✅ revisado (#244), 68/70 mutaciones verificadas (1 equivalente documentada, 1 línea redundante eliminada); el enlace al comprobante queda en #251.** Es solo la pantalla, como dice el issue: el endpoint `POST /v1/admin/integridad` ya existía. Lo único nuevo en el backend es un **test de contrato** del controlador; no cambió código de producción. Va después de #200 en la pila.

### Diseño

- **Pantalla `/admin/integridad`** (menú «Integridad», miga «Operación / Integridad»): un formulario con el rango de fechas de emisión (`Desde`, `Hasta` inclusive; por defecto los últimos siete días) y, debajo, el resultado: cuántos comprobantes se verificaron, qué se encontró y, si hay problemas, una tabla con el tipo, el comprobante (`RUC-tipo-serie-número`), el detalle y un enlace «Ver empresa». Solo se barre cuando el administrador lo pide: nada corre al abrir la página ni se repite sola.
- **Los cuatro tipos** (`XML_FALTANTE`, `XML_CORRUPTO`, `CDR_FALTANTE`, `STORAGE_INACCESIBLE`) se explican en una leyenda que solo muestra los que aparecieron. Perder o alterar un objeto es grave (rojo); **no poder leer el almacenamiento** puede ser un fallo pasajero y no un objeto perdido, así que sale como aviso y la leyenda aconseja repetir.
- **El resumen cuenta comprobantes, no problemas:** un comprobante con el XML corrupto y el CDR faltante es un comprobante con dos problemas («Se encontraron 2 problemas en 1 comprobante»).
- **Tope de 92 días por vez** (decisión mía: el backend no lo limita). Cada comprobante firmado del rango se lee del almacenamiento; un rango de años por error sería un barrido enorme sobre producción. Lo aplican el formulario y el BFF, que arma con esas fechas la URL del backend (existen, `desde` ≤ `hasta`, y no pasan del tope): de ahí solo salen fechas `AAAA-MM-DD` válidas. El barrido de todos los días sigue corriendo a diario sobre los últimos.
- **«Enlace al comprobante» → enlace a la empresa.** El backoffice **no tiene una página por comprobante** (la ficha de la empresa muestra los diez más recientes) y la API de comprobantes es de la empresa, no del administrador. Cada fallo enlaza a la ficha de **su empresa** (`tenant_id`) y muestra la identidad completa del comprobante; una página de detalle de un comprobante sería otro issue.
- **Solo lee:** no repara nada, no deja bitácora (el endpoint no escribe) y la respuesta del BFF no se guarda en caché. El JWT del administrador sigue en su cookie `httpOnly`.

### Tests

- **Backend:** `AdminIntegridadControllerTest` (6): la **forma real** del JSON (snake_case, `problemas` vacío y no ausente, sin campos de más), el rango pasado tal cual, el rango al revés (`400 RANGO_INVALIDO`) y los parámetros ausentes o mal escritos (`400`, sin llamar al caso de uso).
- **Portal, Vitest (+39, 974 en total):** la validación del rango (incluido el borde exacto de 92 días), el cliente, el BFF (sesión, cuerpo, fechas, parámetro colado, caché, error), el componente (16: resumen en singular y plural, tabla, tonos, leyenda, enlaces, validación, estados, doble clic, fallos) y la página.
- **Portal, Playwright** (`admin-integridad.spec.ts`, 14): el menú y la miga, el rango por defecto, un barrido limpio, uno con problemas (tabla y leyenda), el enlace a la empresa (navega y llega), el almacenamiento inaccesible, reemplazar un resultado por otro, los rechazos del formulario, justo 92 días, el BFF (sin sesión, cuerpos inválidos, informe tal cual, sin caché) y la página sin sesión.

### Verificación por mutación — 68/70 mueren (1 equivalente, 1 línea redundante eliminada)

| Capa | Mutaciones | Cuántas |
|---|---|---|
| Rango | el tope en 91 o 93; los extremos que no cuentan; el tope exclusivo o ausente; el rango al revés (o de un día) que pasa; fecha ausente o inexistente que pasa en `desde` o en `hasta`; fechas sin recortar; comparar aunque una no exista; el mensaje sin el máximo | 16 |
| Cliente y BFF | método, codificación de cada fecha, JWT, ruta, fechas al revés; BFF: sin sesión, cuerpo roto, fechas que no son texto, solo una exigida, rango sin validar, texto crudo en la URL, caché, código y estado del rechazo, mensaje que pierde un error, error que no se propaga | 18 |
| Componente | rango inicial (±1 día) y «hasta» sin hoy; doble clic; sin validar; ruta; éxito sin datos; fallo sin mensaje; botón sin bloquear; aviso y fallo que no se muestran; limpio con tabla / con problemas que es limpio; singular y plural de cada frase; comprobantes sin agrupar; el tono de cada tipo; enlace a otra empresa; detalle sin guion; leyenda repetida o con todos los tipos; el comprobante o el tipo mal mostrados | 31 |
| Página y miga | sin sesión, «hoy» de UTC, título; la integridad bajo «Comercial» o con otro nombre | 5 |

Lo que sobrevivía:

- **Línea redundante eliminada — `setErrores({})` antes de enviar:** cada cambio de un campo ya limpia los errores, así que al llegar un envío válido no queda ninguno que limpiar; ningún camino alcanzaba esa línea.
- **Equivalente — mandar `{desde, hasta}` en vez de las fechas recortadas por `validarRango`:** un campo de fecha nunca trae espacios (el navegador y jsdom lo sanean), así que no hay un valor sin recortar que probar desde la pantalla; el BFF recorta igual y eso sí está probado.

**Hallazgo, fuera de este issue:** un `GET` a una ruta que solo acepta `POST` (p. ej. `/v1/admin/integridad`) responde **`500 INTERNO`** en vez de `405`: `GlobalExceptionHandler` no cubre `HttpRequestMethodNotSupportedException` y cae en el manejador general. Es de toda la API, no de esta ruta; no se tocó acá.

### Suites

- Backend: los módulos `:adapters:in-rest`, `:application` y `:domain` (**BUILD SUCCESSFUL**); no cambió código de producción ni hay migración, así que no se repitió el `./gradlew test` completo de 10 minutos.
- Portal: `tsc` y ESLint limpios; Vitest **974/974** (108 archivos); Playwright completo (`--workers=2`) **321/321 (en la primera corrida pasaron 320 y falló un test de la spec de #193 por una carrera de hidratación del selector de mes: corregida en la rama de #193, y esa spec pasa 20/20)**.

### Límites conocidos

- **No hay página de detalle de un comprobante:** el enlace va a la empresa (ver arriba). Lo que falta del criterio «enlace al comprobante» quedó en **#251**
  (ficha de un comprobante en el backoffice, enlazada desde esta pantalla y desde la cola de errores); por eso la PR (#244) **referencia** #198 en vez de cerrarlo.
- **El barrido es síncrono:** la página espera la respuesta; con un rango de 92 días en producción podría tardar. Mientras tanto avisa que puede tardar y no deja lanzar otro. Si en la práctica tarda demasiado, habría que pasarlo a un trabajo con su resultado guardado (otro issue).
- **No repara nada y no guarda los resultados:** cada barrido es una consulta; el historial de barridos no existe.
- **El tope de 92 días es una decisión mía** y solo lo aplica la pantalla (el endpoint sigue aceptando cualquier rango).

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
