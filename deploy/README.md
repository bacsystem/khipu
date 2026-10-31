# Deploy a Railway

Dos servicios en el mismo proyecto de Railway (`backend` y `portal`) más el plugin de Postgres. Cada
servicio construye desde este mismo repo (monorepo), variando el **Root Directory** y el **Dockerfile
Path** en su configuración de Railway. No se usa Nixpacks ni `railway.toml`/`railway.json`: cada
servicio apunta directo a su Dockerfile.

## 1. Postgres

Agregar el plugin **Postgres** de Railway al proyecto. Provee las variables `PGHOST`, `PGPORT`,
`PGUSER`, `PGPASSWORD`, `PGDATABASE` que se referencian desde el servicio `backend` (ver abajo).

## 2. Servicio `backend`

- **Root Directory**: `.` (raíz del repo — es un proyecto Gradle multi-módulo, necesita ver todos los
  módulos para compilar `:bootstrap:bootJar`)
- **Dockerfile Path**: `deploy/backend/Dockerfile`
- **Healthcheck path**: `/health/liveness` — grupo con base de datos y proceso, o sea lo que vuelve
  inservible al servicio. No incluye el correo a propósito: un SMTP caído no debería tumbar el
  despliegue. Para diagnóstico está `/health` completo, que sí incluye el correo **cuando está
  habilitado** (`MAIL_HABILITADO=true`); con el correo apagado su indicador no se registra, así que
  `/health` no queda en rojo permanente por algo que nadie usa
- **Puerto**: no fijar `PORT` manualmente — Railway lo inyecta y el backend ya lo respeta
  (`server.port: ${PORT:8001}`)

### Variables de entorno

| Variable | Valor | Notas |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | Reference variable al plugin de Postgres |
| `DB_USER` | `${{Postgres.PGUSER}}` | |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` | |
| `MASTER_KEY` | `openssl rand -base64 32` | **No reemplazar a secas**: descifra certificados, credenciales SOL y API SUNAT y secretos del 2FA guardados. Para rotarla, §9 |
| `API_KEY_PEPPER` | `openssl rand -base64 32` | **No reemplazar a secas**: es parte del hash de cada API key. Para rotarlo, §9 |
| `MASTER_KEY_ANTERIOR`, `API_KEY_PEPPER_ANTERIOR` | el valor de antes | **Solo durante una rotación** (§9); vacías el resto del tiempo |
| `PLATFORM_ADMIN_KEY` | `openssl rand -base64 32` | |
| `JWT_SECRET` | ≥32 bytes, p. ej. `openssl rand -base64 32` | Auth del portal |
| `PORTAL_URL` | URL pública del servicio `portal` en Railway | Usado en emails de recuperación |
| `APP_MODE` | `multi` | |
| `STORAGE_TYPE` | `fs` o `s3` | `fs` necesita un Volume (paso 3); `s3` no y además permite más de una réplica — ver abajo |
| `STORAGE_FS_ROOT` | `/data` | Solo con `STORAGE_TYPE=fs`; debe coincidir con el mount path del Volume (paso 3) |
| `MAIL_HABILITADO` | `true` | **Requerido en producción.** Su default es `false`, que escribe los correos en el log en vez de enviarlos — ver aviso abajo |
| `MAIL_HOST` | host SMTP del proveedor | Obligatorio con `MAIL_HABILITADO=true`: sin él la app aborta al arrancar (`no hay SMTP configurado (define MAIL_HOST)`), en vez de levantar y fallar en cada correo |
| `MAIL_PORT` | `587` | Opcional, es el default |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | credenciales SMTP | Según el proveedor |
| `MAIL_REMITENTE` | `no-responder@tu-dominio.pe` | De dónde salen los correos; default `no-responder@khipu.pe` |
| `REGISTRO_ABIERTO` | `false` mientras el autoservicio no esté certificado | Cierra `POST /v1/auth/registro` con `403 REGISTRO_CERRADO` (issue #174). Su default ya es `false`: es la misma variable que usa el servicio `portal` (ver abajo), y **hay que ponerla en los dos servicios** — el portal cierra su propia ruta, el backend cierra la suya, y son independientes |
| `TRUSTED_PROXIES` | expresión regular de las IPs de los proxies de confianza (el servicio `portal`) | Opcional, **vacío por defecto: no se confía en `X-Forwarded-For`** y la bitácora registra la IP de la conexión. Se calibra con `GET /v1/admin/origen`; ver §5 antes de fijarla. Una expresión inválida impide arrancar |
| `ADMIN_SESION_MINUTOS` | `30` | Opcional. Vida de la sesión del administrador del backoffice (#177), **entre 5 y 60**: fuera de ese rango el backend no arranca. Sin refresh: al vencer se vuelve a entrar con contraseña y segundo factor. El portal toma la vida de la cookie de la respuesta del login, no hace falta configurarla allá |
| `LOG_DIR` | `/data/logs` (dentro del Volume) | Opcional. Carpeta de `khipu.log` (INFO y superiores) y `khipu-error.log` (WARN y ERROR con la traza completa). Default `./logs`, que en la imagen es `/app/logs` (el Dockerfile la crea con permisos para el usuario `khipu`) y se pierde al redeployar. Para conservarlos, apuntar `LOG_DIR` a una carpeta del Volume, con el mismo requisito que `STORAGE_FS_ROOT`: que el usuario `khipu` pueda escribir ahí. Con `STORAGE_TYPE=s3` no hay Volume y `/data` no existe: no fijar `LOG_DIR` en ese caso. **Un `LOG_DIR` que `khipu` no pueda crear o escribir impide que el backend arranque** (Spring Boot aborta con `Logback configuration error detected: Failed to create parent directories`): es una configuración que falla rápido, igual que `MASTER_KEY` o `MAIL_HOST`. En Railway el log de referencia es la consola, que ya muestra cada error en una línea con su causa raíz |

> **Dejar `MAIL_HABILITADO` en `false` en producción falla en silencio.** No hay error ni alerta: la
> recuperación de contraseña y el envío de comprobantes al cliente se escriben en el log del servicio y
> el destinatario nunca recibe nada. Es la razón por la que `PORTAL_URL` (arriba) importa: sin correo
> habilitado, ese enlace de recuperación no llega a ninguna parte.

**Avisos automáticos de boletas sin informar (#356).** Una boleta —o una nota que la modifica— que sigue sin informarse a SUNAT (emitida con `enviar_automatico: false`, o con el envío
fallando) llega al 5.º día con el envío individual por vencer, y el resumen diario cierra el 7.º. Un trabajo (`AvisarBoletasSinInformarWorker`) corre cada hora, a los 2 minutos de arrancar,
y le escribe a la cuenta dueña de cada empresa: el 5.º día, y otra vez cuando la más antigua llega al 7.º. Solo mira boletas emitidas en los últimos 30 días: lo abandonado no genera un correo cada semana para siempre (la cola y el monitor del backoffice lo siguen mostrando). Como mucho un correo por empresa y por motivo cada 7 días (se registran en
`aviso_a_cliente`, como los avisos que manda un administrador). Sin `MAIL_HABILITADO=true` no hace nada ni deja rastro: reservar el aviso lo daría por mandado una semana sin que el cliente
se enterara. Los textos son dos plantillas más, editables en Configuración. Se puede ajustar el ritmo con `app.avisos.boletas.intervalo-ms` y `app.avisos.boletas.inicial-ms`
(`APP_AVISOS_BOLETAS_INTERVALO_MS` y `APP_AVISOS_BOLETAS_INICIAL_MS`); el default sirve. Lo que no es un correo —la cola de errores y el monitor del backoffice— no depende del correo.

**Padrón reducido de SUNAT (#361).** `GET /v1/consultas/ruc/{ruc}` responde con razón social, estado, condición y domicilio fiscal de un contribuyente, leídos de la tabla `padron_ruc`
(~18 millones de filas: unos 2 a 3 GB entre tabla e índice). La tabla se llena con una carga diaria que **está apagada por defecto** (baja ~400 MB y escribe la base): hay que encenderla en el
servicio `backend`, y hasta que corra la primera la consulta responde `503 PADRON_NO_CARGADO` (no se afirma que un RUC no existe sin tener el padrón).

| Variable | Valor | Notas |
|---|---|---|
| `PADRON_RUC_HABILITADO` | `true` | Enciende la carga. Default `false` |
| `PADRON_RUC_ORIGEN` | default `https://www2.sunat.gob.pe/padron_reducido_ruc.zip` | URL de la descarga pública de SUNAT, o la ruta de un archivo local (`.zip` o `.txt`, ISO-8859-1) |
| `PADRON_RUC_CRON` | default `0 30 4 * * *` | Cron de Spring, hora de Lima. SUNAT publica el archivo cada día de madrugada |
| `PADRON_RUC_LIMITE_DESCARGA_MINUTOS` | default `30` | Cuánto puede tardar la descarga |
| `PADRON_RUC_MINIMO_PRIMERA_CARGA` | default `1000000` | Filas mínimas de la primera carga; después se exige el 90 % de las de la carga anterior, así que un archivo cortado no reemplaza al padrón bueno |
| `PADRON_RUC_MAXIMO_DIAS_DE_ATRASO` | default `3` | Hasta cuántos días de antigüedad de la última carga se confía en el padrón para rechazar una emisión (#378) |

**La emisión usa el padrón** (#378): si el comprador tiene RUC, un RUC que el padrón no tiene se rechaza con `422 RECEPTOR_INVALIDO` (1083) antes de numerar, y uno de baja (4013) o no habido
(4014) se emite con `avisos` en la respuesta, porque SUNAT solo lo observa. Esto vale solo mientras la última carga tenga a lo sumo `PADRON_RUC_MAXIMO_DIAS_DE_ATRASO` días: sin padrón cargado
(la instalación por defecto, con `PADRON_RUC_HABILITADO=false`) o con la carga atrasada no se rechaza nada y el log avisa `El padrón de RUC …` (una vez por hora, no en cada emisión). Un RUC
recién inscrito puede tardar un día en figurar en el padrón: la emisión a ese comprador se rechaza hasta la siguiente carga. e-beta no aplica esta regla (el 2026-10-16 aceptó con CDR `0` y sin
observaciones un RUC inexistente, uno de baja y uno no habido), así que no sirve para probarla; en producción la aplica SUNAT.

Para la **primera** carga, cambiar `PADRON_RUC_CRON` a un minuto cercano de un solo disparo (hora de Lima, p. ej. `0 38 21 * * *` para las 21:38; no `*/5`: la carga tarda ~8 minutos y se repetiría apenas termine), esperar a que el log diga `Padrón de RUC cargado desde …` y volver al valor normal. Cada
intento, bueno o malo, queda en `carga_padron_ruc` (origen, filas, descartadas, nuevas, cambiadas, borradas y el error si falló); si falla, sigue sirviendo el padrón anterior. La carga necesita
disco libre para el .zip temporal (~400 MB, se borra al terminar) y para la tabla de paso, que se vacía al terminar. La carga corre en un hilo propio (`padron-ruc`), no en el del planificador, así que no detiene el outbox ni los demás trabajos. Si SUNAT redujera de verdad el padrón más del 10 %, la carga
falla con `PADRON_INCOMPLETO` en cada intento (el error registrado dice cuántas filas trajo): para aceptar el archivo, bajar a cero las filas que se le exigen: `UPDATE carga_padron_ruc SET filas = 0 WHERE id = (SELECT max(id) FROM carga_padron_ruc WHERE resultado = 'EXITOSA')`. La siguiente carga entra sin mínimo y deja registradas las filas reales, que son las que se exigen después. Mientras tanto la consulta sigue respondiendo con el padrón vigente. Cuota de consultas: 60 por minuto y 5 000 por día por empresa (`429 DEMASIADAS_CONSULTAS`).

Las demás opcionales (timeouts SUNAT, outbox, integridad): ver `.env.example` en la raíz del repo —
usan default razonable si se omiten. `PORT` no hace falta: Railway lo inyecta.

## 3. Dónde se guardan los XML, CDR y PDF

Hay dos adapters y la elección condiciona si el servicio puede escalar:

| | `STORAGE_TYPE=fs` | `STORAGE_TYPE=s3` |
|---|---|---|
| Qué hace falta | un Volume de Railway montado en `/data` | un bucket S3 o compatible (AWS, Cloudflare R2, Backblaze B2, MinIO) |
| Réplicas | **una sola**: el Volume es local al contenedor, cada réplica tendría su propio storage | varias, todas ven el mismo bucket |
| Variables | `STORAGE_FS_ROOT=/data` | `STORAGE_S3_BUCKET`, `STORAGE_S3_REGION`, `STORAGE_S3_ENDPOINT`, `STORAGE_S3_ACCESS_KEY`/`SECRET_KEY`, `STORAGE_S3_PATH_STYLE` |

En cualquiera de los dos, **sin storage persistente cada redeploy borra los XML/CDR/PDF ya emitidos**
(evidencia ante SUNAT). Con `fs` eso significa crear el Volume sí o sí:

1. En el servicio `backend` → **Volumes** → crear uno nuevo.
2. Mount path: `/data`.
3. Setear `STORAGE_FS_ROOT=/data` (ver tabla arriba).

Con `s3` no hace falta Volume. Las claves y la política de retención recomendada (versionado +
Object Lock ≥ 5 años + réplica), además de cómo migrar de disco a S3 sin perder lo ya emitido,
están en el `README.md` de la raíz (§Storage).

## 4. Servicio `portal`

Pasos completos, verificación y fase 0 (landing público con el autoservicio cerrado):
[`frontend/README.md`](frontend/README.md). Resumen:

- **Root Directory**: `portal`
- **Dockerfile Path**: `Dockerfile` (el que ya existe en `portal/Dockerfile`)
- **Puerto**: tampoco fijar `PORT` — el output `standalone` de Next.js ya lo respeta

### Variables de entorno

| Variable | Valor | Notas |
|---|---|---|
| `API_BASE_URL` | dominio interno de Railway del servicio `backend`, en el puerto que ese servicio escucha (`http://backend.railway.internal:$PORT`) | Usado server-side (Server Components, route handlers) — tráfico dentro de la red privada de Railway. Si preferís un puerto interno fijo, seteá `PORT=8001` en el servicio `backend` y usá ese valor acá |
| `API_PUBLIC_URL` | dominio público del servicio `backend` | Es lo que ve el navegador (snippets de integración, "Try it" de `/developers`) |
| `REGISTRO_ABIERTO` | `false` mientras el autoservicio no esté certificado | Con `false`: `/registro` da 404, `POST /api/auth/registro` da 403 y el landing ofrece «Solicitar acceso». Su default en producción ya es `false` |
| `CONTACTO_URL` | `mailto:` o URL de un formulario | A dónde lleva «Solicitar acceso» con el registro cerrado |
| `SUPPORT_URL` | página o portal de ayuda, `https://…` | Opcional (#250), vacía por defecto. El portal muestra «¿Necesitas ayuda?» con este enlace en el pie, en las pantallas de error y de acceso. Si no es `https`, el log del portal nombra la variable y no se muestra ninguna ayuda hasta corregirla; el resto del portal funciona igual |
| `SUPPORT_EMAIL` | correo de soporte | Opcional (#250), vacía por defecto. Va junto al enlace y en «Escribir a soporte» de la ficha de cada cuenta del backoffice (asunto con el nombre y el id de la cuenta). Un correo mal formado se denuncia igual que un enlace que no es `https` |
| `TRUSTED_PROXY_HOPS` | cuántos proxies de confianza hay **delante del portal** | Opcional, **`0` por defecto: el portal no reenvía ninguna IP** y el backend ve la del servidor de Next. Se calibra con `/api/admin/origen`; ver §5. No poner más de `0` si el portal está expuesto directamente, sin proxy delante |

El portal se puede desplegar **antes que el backend**: sin `API_BASE_URL` el landing, `/login` y la
documentación funcionan, y `/developers` muestra un aviso en vez de fallar.

## 5. IP real del administrador en la bitácora (#208)

La bitácora de auditoría (#178) guarda de dónde actuó el administrador. Detrás del portal, esa IP es la del servidor de Next,
igual para todos, salvo que el portal la reenvíe y el backend la acepte. Las dos mitades van juntas y **ninguna confía en nadie por
defecto**:

- **Portal** (`TRUSTED_PROXY_HOPS`, `0` por defecto): cuántos proxies de confianza hay delante. Lee `X-Forwarded-For` **desde la
  derecha** (los proxies agregan a la derecha; lo que el navegador antepone a la izquierda nunca se mira) y manda al backend **una
  sola IP ya resuelta**, nunca la cadena que llegó. Con `0`, o si la cadena es más corta que los saltos, no manda nada.
- **Backend** (`TRUSTED_PROXIES`, vacío por defecto): regex de las IPs de las que acepta esa IP. Solo si el par que llama casa con ella,
  Tomcat reescribe la IP de la conexión, también leyendo desde la derecha. Una petición directa con un `X-Forwarded-For` falso
  se ignora. Solo se toca la IP: el esquema (`X-Forwarded-Proto`) no.

**El límite de intentos de login por IP (#261) también depende de esto.** El límite por correo (5 contraseñas erróneas en 15 minutos
bloquean ese correo 15 minutos) funciona siempre. El de IP (20 fallos en 15 minutos desde la misma IP) **solo se activa con
`TRUSTED_PROXIES` configurado**, y solo para las peticiones cuya IP Tomcat resolvió: sin él, todos los clientes llegan con la IP del
portal y contar por ella dejaría que veinte fallos de cualquiera bloquearan el login de todos. Calibrar las dos variables (abajo)
lo enciende sin tocar nada más.

### La cadena en Railway, salto por salto

```
navegador ──HTTPS──▶ borde de Railway ──HTTP──▶ portal (Next) ──red privada──▶ backend (Tomcat) ──▶ auditoria_admin.ip
   (1)                     (2)                       (3)                             (4)
```

| Salto | Quién escribe la IP | ¿De confianza? | Qué lo garantiza en khipu |
|---|---|---|---|
| (1) navegador → borde | el navegador puede mandar cualquier `X-Forwarded-For` o `X-Real-IP` | **No**, nunca | Nada de lo que el navegador antepone se lee: el portal cuenta desde la derecha |
| (2) borde → portal | el borde de Railway, que termina el TLS y conoce la IP real de la conexión | **Sí**, si el portal solo es alcanzable por el borde (su dominio público de Railway o uno propio apuntado a él) | `TRUSTED_PROXY_HOPS` = cuántas entradas de la derecha del `X-Forwarded-For` escribió el borde (o el borde y Cloudflare, si hay Cloudflare delante). El portal no comprueba la IP del borde: Railway no la publica. Ojo: cualquier otro servicio del mismo proyecto de Railway puede llegar a `portal.railway.internal` con un `X-Forwarded-For` inventado y el portal lo tomaría; el riesgo exige controlar un servicio del propio proyecto, pero conviene no desplegar ahí código ajeno |
| (3) portal → backend | el portal, que manda **una sola IP ya resuelta** en `X-Forwarded-For` por `API_BASE_URL` (`backend.railway.internal`) | **Sí**, solo desde la dirección privada del portal | `TRUSTED_PROXIES` en el backend: Tomcat acepta la cabecera solo si el par de la conexión casa con la regex |
| (4) backend | Tomcat reescribe `getRemoteAddr()` y la bitácora la guarda | — | — |

El backend **también** tiene dominio público (`API_PUBLIC_URL`, integradores y el «Try it» de `/developers`). Esas peticiones llegan
desde el borde, no desde el portal: `TRUSTED_PROXIES` no debe cubrir las direcciones del borde, solo la del portal en la red
privada. Así, un `X-Forwarded-For` que alguien mande directo al backend nunca cambia la IP registrada: se registra la dirección
del borde. Consecuencia para el límite de intentos por IP de #261: con `TRUSTED_PROXIES` configurado, un login hecho **directo** al
dominio público del backend se cuenta por la dirección del borde, compartida por todos los que entran así (los logins del portal se
cuentan por la IP real que resolvió el portal).

### Qué dice Railway, y qué hay que verificar en el despliegue

Lo que está **documentado** (consultado el 2026-10-20):

- La página de especificaciones de la red pública lista, entre las cabeceras de la petición, `X-Real-IP` «for identifying client's
  remote IP», `X-Forwarded-Proto` (siempre `https`) y `X-Forwarded-Host`. **No menciona `X-Forwarded-For`**, no dice si el borde
  reemplaza o agrega a lo que mandó el cliente y no publica las IPs de origen del borde
  ([Public Networking · Specs & Limits](https://docs.railway.com/networking/public-networking/specs-and-limits)).
- La red privada da a cada servicio un nombre `<servicio>.railway.internal`; en los entornos creados desde el 2025-10-16 resuelve a
  IPv4 e IPv6, en los anteriores solo a IPv6, y el tráfico «never leaves Railway's infrastructure and is not exposed to the public
  internet». **No dice qué rango de direcciones usa ni si una dirección se mantiene entre despliegues**
  ([Private Networking · How it works](https://docs.railway.com/networking/private-networking/how-it-works)).

Lo que **no** está documentado y solo aparece en el foro (Central Station), con respuestas que no coinciden:

- Un moderador afirma que el borde sobrescribe `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` y `X-Forwarded-Host` en cada
  petición ([hilo del 2026-09-29](https://station.railway.com/questions/which-client-address-headers-does-the-ra-fb9e36d7));
  en otro hilo un usuario reporta que un `X-Real-IP` puesto por el cliente sí llegó a su aplicación, y un empleado dice que
  `100.0.0.0/8` (el rango del ejemplo de Caddy) «is not guaranteed to be stable»
  ([hilo del 2026-09-09](https://station.railway.com/questions/supported-trust-model-for-client-ip-head-aafb68bc)).

**A verificar por el dueño en su despliegue** (no se puede deducir desde local, y puede cambiar si Railway cambia su borde):

1. Si el borde **reemplaza** el `X-Forwarded-For` del navegador o **agrega** a la derecha, y cuántas entradas escribe: eso fija
   `TRUSTED_PROXY_HOPS`. Si el borde escribe una sola entrada (la IP de quien se le conectó), en los dos casos es la de más a la
   derecha y `1` es el valor esperado sin Cloudflare; con Cloudflare delante puede ser `2`. Es una expectativa, no un dato: se mide.
2. La dirección del portal en la red privada, que fija `TRUSTED_PROXIES`, y si cambia al redesplegar el portal. Railway no publica
   el rango: si cambia, hay que cubrir el rango observado (lo más estrecho posible), no «toda la red privada».
3. Que la dirección con que el borde llega al backend **no** case con `TRUSTED_PROXIES`.

khipu lee solo `X-Forwarded-For`, no `X-Real-IP`, aunque esta última sea la que Railway documenta. Si la verificación mostrara que
`X-Forwarded-For` no sirve (ningún valor de `TRUSTED_PROXY_HOPS` da la IP pública propia), leer `X-Real-IP` sería un cambio de
código en `portal/src/lib/origen.ts`, no de configuración.

#### Procedimiento de verificación

Hace falta la IP pública propia (`curl https://ifconfig.me`), una sesión de administrador en el portal y la cookie
`khipu_admin_access` de esa sesión (DevTools → Application → Cookies) para las pruebas con `curl`.

1. **Cadena tal como llega.** Con `TRUSTED_PROXY_HOPS=0` y `TRUSTED_PROXIES` vacío, abrir `https://<portal>/api/admin/origen`.
   Anotar `cadena_recibida` (lo que escribió el borde) e `ip_backend` (la dirección privada del portal: es lo que va en
   `TRUSTED_PROXIES`).
2. **¿Reemplaza o agrega?** Repetir con una cabecera falsa:
   `curl -s -H "X-Forwarded-For: 6.6.6.6" -H "Cookie: khipu_admin_access=<valor>" https://<portal>/api/admin/origen`.
   Si `cadena_recibida` es solo tu IP, el borde reemplaza; si es `6.6.6.6, <tu IP>`, agrega. En los dos casos se sigue con el paso 3.
3. **Fijar las variables** como dice «Calibrar» (abajo) y repetir el `curl` del paso 2: `ip_resuelta` e `ip_backend` deben ser tu IP
   pública, **nunca `6.6.6.6`**.
4. **El borde no es de confianza en el backend.** Llamar al dominio público del backend con la clave de plataforma:
   `curl -s -H "X-Platform-Key: <clave>" -H "X-Forwarded-For: 6.6.6.6" https://<backend-publico>/v1/admin/origen`.
   `datos.ip` debe ser una dirección del borde de Railway: ni `6.6.6.6` ni tu IP pública. Si sale `6.6.6.6` o tu IP, Tomcat aceptó el
   `X-Forwarded-For` del borde: `TRUSTED_PROXIES` es demasiado amplio y cubre también al borde.
5. **Estabilidad.** Redesplegar el portal y repetir el paso 1: si `ip_backend` cambió y ya no casa con `TRUSTED_PROXIES`, la bitácora
   vuelve a registrar la dirección del portal (falla segura, no falsificable) hasta ampliar la regex al rango observado.

Anotar en este README los valores medidos y la fecha. Repetir la verificación si cambia el borde, el dominio o se agrega Cloudflare.

### Calibrar

Con la sesión de administrador abierta, visitar `https://<portal>/api/admin/origen`. Devuelve `cadena_recibida` (el
`X-Forwarded-For` que llegó al portal), `saltos` (`TRUSTED_PROXY_HOPS`), `ip_resuelta` (la que el portal sacó de la cadena) e
`ip_backend` (la que el backend registraría). Se compara con tu IP pública real (p. ej. la que muestra `ifconfig.me`).

1. **Primero `TRUSTED_PROXIES`.** Con la variable vacía, `ip_backend` es la dirección del **portal dentro de la red privada**: es
   justo lo que la regex debe cubrir. Ponerla en el backend con la menor cobertura posible (el rango de esa red, no «todo»).
2. **Después `TRUSTED_PROXY_HOPS`.** Empezar en `1`:
   - `ip_resuelta` es tu IP pública → correcto.
   - `ip_resuelta` es una IP de Railway o Cloudflare (la cadena es `cliente, borde, …`) → falta un salto: subir en `1`.
   - `ip_resuelta` es `null` con saltos > 0 → la cadena es más corta que los saltos: bajar en `1`, o el portal no tiene proxy delante (`0`).
3. Comprobar que `ip_backend` es la misma dirección que `ip_resuelta`. Si `ip_backend` sigue siendo la del portal, `TRUSTED_PROXIES` no casa con él.
   Con una IPv6 se compara la **dirección**, no el texto: `ip_resuelta` sale comprimida (`2001:db8::1`) y `ip_backend` sin comprimir
   (`2001:db8:0:0:0:0:0:1`), y son la misma. La bitácora guarda siempre la forma sin comprimir, así que para buscar una IPv6 en
   `auditoria_admin` se usa esa.

**`TRUSTED_PROXY_HOPS` mayor que `0` sin un proxy real delante del portal permite falsificar la IP**: Next no la sobrescribe si el
navegador ya mandó `X-Forwarded-For` (solo la rellena si falta), y esa cabecera sería lo que se lee. Probado en local: con `1` y sin
proxy, un `X-Forwarded-For: 6.6.6.6` del navegador queda como IP registrada. Con un borde que agrega a la derecha, lo falso queda a
la izquierda y no se mira.

## 6. Orden de despliegue sugerido

1. Postgres (plugin).
2. `backend` — esperar que el healthcheck `/health/liveness` pase (corre las migraciones Flyway al arrancar).
3. `portal` — depende de que `backend` ya tenga una URL asignada para `API_BASE_URL`/`API_PUBLIC_URL`.
4. **Cada administrador del backoffice entra enseguida y configura su segundo factor** (ver §7).
5. `respaldo` — el respaldo diario de la base (ver §8). **Antes del primer cliente real.**

## 7. Segundo factor del administrador (#177): el primer ingreso

El backoffice exige contraseña **y** un código de una app de autenticación (TOTP). El segundo factor se configura en el primer inicio de sesión: el
administrador escanea el QR y confirma con el primer código. Eso tiene una consecuencia operativa que conviene conocer:

- **Hasta ese primer ingreso, la contraseña sola alcanza para entrar.** Quien la tenga puede hacer el primer inicio de sesión, configurar *su* teléfono,
  recibir los diez códigos de recuperación y dejar al administrador legítimo fuera (`409 SEGUNDO_FACTOR_YA_CONFIGURADO`). Los administradores que ya
  existían antes de este despliegue están en ese estado hasta que entren.
- **Qué hacer:** al desplegar esta versión, que cada administrador existente inicie sesión y configure su app **de inmediato**, y crear los administradores
  nuevos solo cuando quien los va a usar pueda enrolarse en el momento. Si una contraseña pudo exponerse antes de enrolar, reemplazar al administrador
  con el procedimiento de abajo antes de que entre.
- **El producto no cambia la contraseña de un administrador** ni lo desactiva: solo los crea. Reemplazarlo (contraseña expuesta, o alguien se enroló sin
  ser él) se hace así, **en este orden**:
  1. Borrarlo en la base: `DELETE FROM administrador WHERE email = 'ana@tu-dominio.pe';` (el correo en minúsculas, como lo guarda el alta). Las filas de
     su segundo factor y sus códigos de recuperación se borran en cascada, y un login a medias deja de valer: el desafío se comprueba contra la base.
     La bitácora conserva sus registros con el id viejo (no tiene clave foránea).
  2. Volver a crearlo con una contraseña nueva: `POST /v1/admin/administradores` con `X-Platform-Key` y `{"email": "...", "password": "..."}`.
  3. Que la persona entre y configure su app **de inmediato**: hasta entonces vuelve a valer lo de arriba.

  Borrar solo la fila de `administrador_segundo_factor` **no alcanza**: con la contraseña de siempre, quien la tenga puede volver a enrolarse antes que el
  administrador. Y una sesión ya emitida **no se revoca**: el backoffice solo comprueba la firma y el vencimiento del token, así que la de un atacante dura
  hasta `ADMIN_SESION_MINUTOS` (30 por defecto, 60 como máximo) aunque el administrador ya no exista. Un reinicio desde el backoffice queda para #183.

## 8. Respaldos de la base

Sin respaldo, perder la base de Postgres es perder cuentas, planes, pagos, la bitácora y **la numeración de los comprobantes**, que SUNAT exige
correlativa. Los XML y CDR no van aquí: van en el storage (§3), y conviene que sea `STORAGE_TYPE=s3` con versionado y retención, porque un Volume de
Railway no tiene copia propia.

`deploy/respaldo/` es una imagen con `pg_dump`, `openssl` y el cliente de S3:

- **`respaldar.sh`** (lo que corre el servicio): `pg_dump` en formato custom → cifrado AES-256 con `BACKUP_PASSPHRASE` → objeto
  `postgres/khipu-<fecha UTC>.dump.enc` en el bucket. Comprueba que el volcado tenga datos y que el objeto subido mida lo mismo que el local. Ante
  cualquier fallo termina con error, así que Railway marca la ejecución como fallida.
- **`restaurar.sh <clave> | --ultimo`**: baja, descifra y restaura en `RESTAURAR_EN`, una variable aparte de `DATABASE_URL` a propósito, para no
  restaurar encima de producción por un descuido. Si la base destino ya tiene tablas, exige `CONFIRMAR=si`.
- **`probar.sh`** (`make probar-respaldo`): la prueba de punta a punta en contenedores propios y temporales. Crea una base con las migraciones reales y
  la respalda a un MinIO. Después restaura en una base vacía, compara tabla por tabla y comprueba que una clave equivocada no descifra y que una base
  con datos no se pisa sin confirmar.

### Servicio `respaldo` en Railway

0. **Misma versión mayor de Postgres que la base.** La imagen trae `pg_dump` 16 y `pg_dump` aborta ante un servidor más nuevo («server version
   mismatch»): el respaldo fallaría todos los días. Comprobarlo en la base de Railway con `SELECT version();`; si es otra, cambiar el `FROM` de
   `deploy/respaldo/Dockerfile` a esa versión.
1. **New → GitHub Repo** (el mismo repo) → **Settings → Build**: Dockerfile `deploy/respaldo/Dockerfile`, root `deploy/respaldo`.
2. **Settings → Cron Schedule**: `0 8 * * *` (todos los días a las 3:00 de Lima, que es UTC-5). Railway arranca el contenedor a esa hora, corre
   `respaldar.sh` y lo apaga.
3. Variables:

| Variable | Valor |
|---|---|
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` (la del plugin; la red privada alcanza) |
| `BACKUP_PASSPHRASE` | 20 caracteres o más, al azar. **Guardarla también fuera de Railway** (gestor de contraseñas): si se pierde junto con el proyecto, los respaldos no se pueden leer |
| `BACKUP_S3_BUCKET` | un bucket **distinto** del de los XML y CDR, con versionado |
| `BACKUP_S3_ENDPOINT` | solo para S3 compatibles: `https://<cuenta>.r2.cloudflarestorage.com` (R2), `https://s3.<región>.backblazeb2.com` (B2) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | una llave del bucket **solo con permiso de escribir y leer** ese bucket, no de borrar |
| `AWS_DEFAULT_REGION` | la del bucket (`auto` en R2) |
| `BACKUP_S3_PREFIJO` | opcional, por defecto `postgres` |

4. **Retención en el bucket, no en el script:** una regla de ciclo de vida que borre los objetos de `postgres/` a los 35 días. Así nadie con la
   llave del servicio puede borrar los respaldos viejos, y un atacante que entre a Railway no se lleva la historia. Si el proveedor lo permite, Object
   Lock en modo *governance* por 30 días. **Con versionado activo, la regla tiene que incluir también las versiones no actuales**
   (`NoncurrentVersionExpiration`, p. ej. 35 días) **y la limpieza de los marcadores de borrado que quedan solos**: expirar la versión actual solo
   deja un marcador, y sin esas dos partes las versiones viejas se quedan para siempre (el costo crece y la retención no es la que se dice).
5. **Probar la restauración una vez al desplegar, y después cada mes.** Un respaldo que nunca se restauró no es un respaldo. Desde una máquina con
   Docker, con las mismas variables del bucket:

```bash
docker build -t khipu-respaldo deploy/respaldo
docker run --rm -e RESTAURAR_EN=postgres://usuario:clave@host:5432/base_de_prueba -e BACKUP_PASSPHRASE=… -e BACKUP_S3_BUCKET=… \
  -e BACKUP_S3_ENDPOINT=… -e AWS_ACCESS_KEY_ID=… -e AWS_SECRET_ACCESS_KEY=… -e AWS_DEFAULT_REGION=… khipu-respaldo restaurar.sh --ultimo
# BACKUP_S3_ENDPOINT solo con R2, B2 o MinIO; con AWS S3 se omite.
```

Restaurar **en una base de prueba**, nunca en la de producción, y abrirla para comprobar que están las cuentas y los comprobantes recientes.

**Además**, si el plan de Railway incluye respaldos del Volume de Postgres, conviene activarlos también: es una segunda copia, en otro lugar. No
reemplaza a esta, porque queda dentro del mismo proyecto de Railway.

## 9. Rotar `MASTER_KEY` y `API_KEY_PEPPER`

Hace falta si una de las dos pudo filtrarse, o por política. Ninguna se reemplaza a secas: `MASTER_KEY` cifra lo guardado (certificados y su clave,
credenciales SOL, secretos del segundo factor de los administradores, respuestas guardadas del alta asistida, secretos de firma de los webhooks, credenciales API SUNAT de cada empresa —#318—) y `API_KEY_PEPPER` es parte del hash
de cada API key. El backend admite **la de antes junto con la nueva** mientras dura la rotación. **Antes de empezar, un respaldo de la base (§8).**

### `MASTER_KEY`

1. Generar la nueva: `openssl rand -base64 32`. Guardarla fuera de Railway, igual que la anterior.
2. En el servicio `backend`: `MASTER_KEY_ANTERIOR` = el valor actual de `MASTER_KEY`, y `MASTER_KEY` = la nueva. Redeploy.
3. Al arrancar, el backend vuelve a cifrar con la nueva todo lo que estaba con la anterior y lo dice en el log:
   - `Rotación de MASTER_KEY: N valor(es) recifrado(s) con la clave vigente, 0 pendientes. Ya se puede quitar MASTER_KEY_ANTERIOR.`
   - Si dice que **quedan pendientes**, no seguir: algo no se pudo recifrar y depende de la anterior. El log de arriba dice qué tabla y qué fila no
     abren con ninguna de las dos claves.
4. **Reiniciar una vez más, todavía con `MASTER_KEY_ANTERIOR` puesta**, cuando ya no quede ninguna instancia con la configuración vieja (en un
   redeploy la anterior sigue atendiendo hasta que la nueva está lista, y lo que guarde en ese rato —credenciales SOL, un certificado, un segundo
   factor— sale con la clave vieja, después del recifrado). Ese arranque recifra lo que quedó y tiene que volver a decir «0 pendientes».
5. Con «0 pendientes» en ese segundo arranque, borrar `MASTER_KEY_ANTERIOR` y redeploy. Desde ese momento la clave vieja ya no abre nada de la base
   (salvo los respaldos anteriores a la rotación, que siguen cifrados con `BACKUP_PASSPHRASE` y contienen los datos con la clave vieja: por eso la
   vieja se guarda hasta que esos respaldos venzan). Si aun así quedó algo con la vieja, ese arranque lo dice en el log (`MASTER_KEY: N valor(es)
   guardado(s) no abren con la clave vigente…`): volver a poner `MASTER_KEY_ANTERIOR` y reiniciar lo recifra.

Mientras `MASTER_KEY_ANTERIOR` esté puesta, todo sigue funcionando: lo que no se recifró se lee con la anterior. Dos réplicas recifrando a la vez no
se pisan: cada fila se actualiza solo si nadie la cambió en el medio.

### `API_KEY_PEPPER`

Las API keys no se guardan, solo su hash con el pepper: no se pueden recalcular todas de una vez. Cada key pasa al pepper nuevo **la primera vez que
se usa** durante la rotación.

1. Generar el nuevo: `openssl rand -base64 32`.
2. `API_KEY_PEPPER_ANTERIOR` = el valor actual de `API_KEY_PEPPER`, y `API_KEY_PEPPER` = el nuevo. Redeploy. Las keys emitidas siguen autenticando.
3. El log de cada arranque dice cuántas keys activas siguen dependiendo del pepper anterior:
   `Rotación de API_KEY_PEPPER: N API key(s) activa(s) todavía dependen del pepper anterior`.
4. Cuando diga **0**, quitar `API_KEY_PEPPER_ANTERIOR` y redeploy. Si quedan keys que nadie usa (un integrador de baja, una key olvidada), decidir:
   esperar, revocarlas, o avisar a esos clientes que creen otra. **Si se quita antes, esas keys dejan de autenticar**, y el log lo dice al arrancar.

Si el pepper viejo **se filtró**, no alcanza con rotarlo: quien tenga una key ya sabe la key, no el pepper. Revocar y reemplazar las keys
comprometidas es aparte.

`JWT_SECRET` no necesita este procedimiento: cambiarlo cierra todas las sesiones del portal y del backoffice, y la gente vuelve a entrar.


## 10. Mover el reloj del backend para probar plazos (#363) — solo en el ambiente de pruebas

Algunas reglas de SUNAT dependen de la fecha y no se pueden reproducir con el reloj real: la boleta informada pasado el 7.º día (la observación **4443** que prevé la norma y que e-beta no devuelve, #331).
`RELOJ_DESPLAZAMIENTO` suma una duración al reloj del backend para probarla hoy. **Nunca va en producción** (Railway): no se define allí.

| Variable | Valor | Notas |
|---|---|---|
| `RELOJ_DESPLAZAMIENTO` | Duración ISO-8601: `-P8D` (8 días antes), `P79DT3H`, `PT6912345S` (esos segundos después) | Vacía o ausente = hora real, comportamiento idéntico al de siempre. Con un valor que no es una duración (`8 dias`, `-8`, `P1Y`) o que mueve el reloj más de 3650 días el backend **no arranca** y dice cuál |

### Qué hace el backend para que no pase inadvertido en producción

- **No arranca** con el reloj movido si hay empresas con `entorno = PRODUCCION` en la base (el mensaje dice cuántas). En producción siempre las hay.
- Aunque arranque (base sin empresas todavía, o una empresa pasada a PRODUCCION después), para una empresa en PRODUCCION responde `422 RELOJ_DESPLAZADO` y **no hace nada con la
  fecha falsa**: no emite (sin gastar número), no envía (ni la cierra como `FUERA_DE_PLAZO`, que es un estado terminal), no genera comunicaciones de baja ni resúmenes diarios (sin
  gastar correlativo ni guardar un XML con esa fecha), y no le habla a SUNAT: ni envíos ni consultas (`getStatus`, `getStatusCdr`, validez). El barrido horario que cierra por plazo
  no corre mientras haya una empresa en PRODUCCION (y lo dice en el log). Lo que distingue pruebas de producción no es la URL (la configuración lleva siempre las dos) sino el entorno
  de cada empresa, que es lo que elige la URL a la que se habla. Lo único que sigue yendo a las URL de producción es el sondeo anónimo de disponibilidad del monitor del backoffice
  (una petición HTTP sin comprobantes ni credenciales). Leer (listar, descargar, `GET` de un comprobante) sigue igual.
- Cada arranque deja un `WARN` de cinco líneas con `!!! RELOJ DESPLAZADO (RELOJ_DESPLAZAMIENTO=…)`, el desplazamiento legible y la hora que el backend cree que es. Búscalo con
  `docker compose … logs backend | grep -A3 "RELOJ DESPLAZADO"`. El reloj movido también se ve en el campo `generado_en` del monitor del backoffice y en la fecha de generación de cada RC/RA (`fecha_generacion`).

### Qué se mueve y qué no

| Sigue al reloj movido (usa el `Clock` de la aplicación) | NO lo sigue |
|---|---|
| La fecha y hora de emisión, y la validación «no futura» de `fecha_emision` | `now()` de PostgreSQL: `created_at`, `updated_at` y la **cola de envíos** (`outbox.siguiente_intento <= now()`) |
| Los plazos de envío (factura 3 días; boleta 5 individual y 7 en resumen) y el cierre `FUERA_DE_PLAZO`, también el del barrido horario (si no hay empresas en PRODUCCION) | Los `iat`/`exp` de los JWT del portal y del backoffice, y las cookies del navegador (hora real) |
| La fecha de generación, el correlativo del día y el formato (05 o 06–08/9996) de los RC y RA | La hora de SUNAT: valida lo que recibe contra **su** fecha real |
| La comprobación de vigencia del certificado en el backend (`CERTIFICADO_VENCIDO`), el plan de la cuenta (`PLAN_VENCIDO`), el tope y el consumo del mes | La caducidad real del certificado dentro de la firma XML-DSig, que SUNAT mira con su fecha |
| El vencimiento de enlaces de verificación y recuperación, y de las sesiones de refresh del portal | La fecha de modificación de los archivos del storage |

Consecuencias que conviene tener presentes:

- **El reintento automático no es fiable con el reloj movido.** El backoff se calcula con el reloj movido y se compara con `now()` de PostgreSQL: con el reloj atrasado el reintento
  ya «venció» y el worker lo repite cada 10 s hasta agotar los intentos; con el reloj adelantado no vence hasta que llegue esa fecha. Por eso los procedimientos de abajo emiten con
  `enviar_automatico: false` y provocan los fallos de forma controlada en vez de esperar al worker.
- Con el reloj adelantado, usa una empresa **sin cuenta** (la que crea `POST /v1/admin/tenants`) o una cuenta con plan sin vencimiento: un plan con fecha de fin se vence y emitir
  responde `402 PLAN_VENCIDO`. También las sesiones de refresh del portal creadas antes pueden figurar vencidas: vuelve a iniciar sesión (con una API key no pasa).
- Usa una empresa de pruebas dedicada (`BETA`): lo que se emite con fechas movidas no es un dato que quieras conservar.

### Cómo usarlo en develop (Docker)

`RELOJ_DESPLAZAMIENTO` ya se pasa al contenedor `backend` desde el `docker-compose.yml` (vacío por defecto). Para cambiarlo cómodamente sin tocar el repo, el archivo local
`../khipu-develop.override.yml` (fuera del repo) lleva, además de lo que ya tenga:

```yaml
services:
  backend:
    environment:
      RELOJ_DESPLAZAMIENTO: "${RELOJ_DESPLAZAMIENTO:-}"
      # Solo para el procedimiento B (hace fallar el envío a SUNAT). El valor por defecto debe ser el `app.sunat.beta-url` de application.yml.
      APP_SUNAT_BETAURL: "${SUNAT_BETA_URL_PRUEBA:-https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService}"
```

Funciones de ayuda (Git Bash, desde la raíz del checkout de khipu; `dev` son los parámetros de `COMPOSE_DEVELOP` del Makefile más el override):

```bash
dev() { POSTGRES_PORT=5433 BACKEND_PORT=18001 PORTAL_PORT=13000 MAILPIT_UI_PORT=8026 \
        PORTAL_API_BASE_URL=http://backend:8001 PORTAL_API_PUBLIC_URL=http://localhost:18001 \
        PORTAL_URL=http://localhost:13000 PORTAL_COOKIE_SECURE=false \
        docker compose -p khipu-develop --project-directory ../khipu-wt-develop -f ../khipu-wt-develop/docker-compose.yml \
        -f ../khipu-develop.override.yml --env-file ../khipu-develop.env "$@"; }

# reloj <desplazamiento>: recrea solo el backend con ese reloj ("" = hora real), espera a que responda y muestra el WARN.
reloj() { RELOJ_DESPLAZAMIENTO="$1" dev --profile app up -d --no-deps --force-recreate backend
          until curl -sf http://localhost:18001/health >/dev/null; do sleep 2; done
          dev --profile app logs backend 2>&1 | grep -A3 "RELOJ DESPLAZADO" | tail -4; }

# hasta <instante ISO con zona>: el desplazamiento que deja al backend en ese instante (ahora), p. ej. hasta 2026-12-31T23:45:00-05:00
hasta() { echo "PT$(( $(date -d "$1" +%s) - $(date +%s) ))S"; }
```

El reloj movido sigue andando desde que arranca el backend: el desplazamiento es fijo, no congela la hora. Puesto a las 23:45 del 31-12, llega al 01-01 a los 15 minutos.

Datos comunes (una vez): una empresa `BETA` de pruebas con certificado y credenciales SOL de beta, y su API key. Una empresa sin cuenta no puede crear su API key
desde el portal (`POST /v1/empresa/api-keys` exige sesión del portal): se da de alta con `POST /v1/admin/tenants` (cabecera `X-Platform-Key`) y esa respuesta devuelve
`datos.api_key`. Con esa key (`X-Api-Key`) funcionan `POST /v1/empresa/certificado` (multipart `archivo` y `clave`; certificado autofirmado con el RUC en el campo `OU`),
`PUT /v1/empresa/credenciales-sol` (en beta, `MODDATOS` / `moddatos`) y `POST /v1/series`.

En **Git Bash de Windows**, antes de los `dev exec -T backend …` con rutas del contenedor (`/app/storage/...`) hace falta `export MSYS_NO_PATHCONV=1`: sin él, MSYS
convierte `/app/...` en `C:/Program Files/Git/app/...` y el comando falla.

```bash
export MSYS_NO_PATHCONV=1
BASE=http://localhost:18001
API_KEY=fk_…     # la de la empresa de pruebas
H=(-H "X-Api-Key: $API_KEY" -H "Content-Type: application/json")
# campo <ruta>: lee un campo del JSON que llega por stdin, p. ej. … | campo datos.0.estado
campo() { node -pe "const r=JSON.parse(require('fs').readFileSync(0)); '$1'.split('.').reduce((o,k)=>o?.[k], r)"; }
curl -s -X POST "$BASE/v1/series" "${H[@]}" -d '{"tipo":"03","serie":"B001","correlativo_inicial":0}'
```

### A. Observación 4443: boleta informada pasado el 7.º día (#331)

Se emite con el reloj 8 días atrás (con el reloj real no se puede emitir una fecha así: la boleta queda `FIRMADO` con fecha de hace 8 días) y se envía con la hora real.

```bash
# 1. Reloj 8 días atrás
reloj -P8D
FECHA=$(TZ=UTC+5 date -d '8 days ago' +%F)        # el «hoy» del backend, en hora de Lima

# 2. Emitir la boleta sin enviarla
ID=$(curl -s -X POST "$BASE/v1/facturas" "${H[@]}" -d '{
  "serie":"B001","fecha_emision":"'$FECHA'","moneda":"PEN","enviar_automatico":false,
  "cliente":{"tipo_doc":"1","num_doc":"12345678","razon_social":"CLIENTE DE PRUEBA"},
  "items":[{"descripcion":"Producto de prueba","unidad":"NIU","cantidad":1,"precio_unitario":118.00,"tipo_afectacion_igv":"10"}]}' | campo datos.id)
curl -s "$BASE/v1/facturas/$ID" "${H[@]}" | campo datos.estado_documento       # FIRMADO
curl -s "$BASE/v1/facturas/$ID" "${H[@]}" | campo datos.fecha_emision          # = $FECHA

# 3. Hora real
reloj ""

# 4. Enviarla: pasó el 7.º día, va en un resumen diario fuera de plazo
curl -s -X POST "$BASE/v1/facturas/$ID/enviar" "${H[@]}" | campo datos.baja.fuera_de_plazo   # true
curl -s "$BASE/v1/facturas/$ID" "${H[@]}" | campo datos.estado_documento       # ACEPTADO en SUNAT beta (si dice ENVIADO, SUNAT aún procesa el ticket: el worker lo consulta cada 30 s; repite el comando)
curl -s "$BASE/v1/facturas/$ID" "${H[@]}" | node -pe "JSON.stringify(JSON.parse(require('fs').readFileSync(0)).datos.cdr, null, 1)"
```

Resultado verificado el 2026-10-13 contra SUNAT beta, con 8 y con 30 días de atraso: el RC sale con `ConditionCode` 1, la boleta pasa a `FIRMADO → ENVIADO → ACEPTADO`,
`cdr.codigo` es `0` y `cdr.observaciones` está **vacía**: **la beta no devuelve la 4443** (la regla está en la hoja Resumen Diario de SUNAT, pero la beta no la aplica). Lo que
sí comprueba este procedimiento es lo que depende de khipu: que la boleta pasada del 7.º día **va en un resumen diario propio con la fecha de generación de hoy** (`baja.fuera_de_plazo`
`true`) en vez de quedar cerrada como `FUERA_DE_PLAZO`, y que SUNAT lo acepta. Si algún día la beta empieza a observar, el estado será `ACEPTADO_CON_OBS` y `cdr.observaciones`
traerá `4443 - …`. En el portal, el detalle del comprobante muestra el aviso de informado fuera de plazo.

### Al terminar

```bash
unset SUNAT_BETA_URL_PRUEBA
reloj ""                                  # hora real; el WARN no vuelve a aparecer
dev exec -T postgres psql -U factura -d factura -c "delete from outbox where tenant_id = (select id from tenant where ruc = '<RUC de la empresa de pruebas>')"
```

El `delete` quita los reintentos que quedaron programados con fecha del reloj movido (en el procedimiento B quedan para el 31-12 real): sin él, ese día el worker enviaría a SUNAT beta
resúmenes de pruebas. Los comprobantes de pruebas se dejan como están.

## 11. Alertas al operador (#341)

Hasta ahora producción solo tenía el healthcheck de Railway: si algo fallaba sin tumbar el proceso, nadie se enteraba hasta que un cliente reclamaba. `VigilarOperacionWorker` corre cada 5
minutos (2 minutos después de arrancar) y avisa de lo que sigue.

| Alerta (`clave`) | Cuándo salta | Qué hacer |
|---|---|---|
| `OUTBOX_ATASCADO` | Hay envíos vencidos en el outbox hace más de 5 min: el trabajo que los vacía no corre. No cuenta los de empresas con las credenciales SOL rechazadas (esos esperan a propósito; los cubre `CREDENCIALES_SOL`) | Revisar el log del backend; reiniciar el servicio |
| `ENVIOS_CON_ERROR` | Al menos `ALERTAS_ERROR_ENVIO_CANTIDAD` (5) comprobantes en `ERROR_ENVIO` se crearon hace más de `ALERTAS_ERROR_ENVIO_MINUTOS` (30): llevan ese tiempo sin poder enviarse | SUNAT caída o credenciales SOL; el detalle por empresa está en la cola de errores del backoffice |
| `FUERA_DE_PLAZO` | Algún comprobante pasó a `FUERA_DE_PLAZO` en las últimas `ALERTAS_FUERA_DE_PLAZO_HORAS` (24). Avisa por episodio: otros que lleguen mientras sigue activa no avisan hasta la repetición, y `RESUELTO` solo dice que pasaron esas horas sin nuevos | El cliente tiene que volver a emitir: avisarle |
| `RESUMENES_SIN_RESPUESTA` | Un resumen diario o baja con ticket lleva más de `MONITOR_RESUMEN_SIN_RESPUESTA_HORAS` (6) sin respuesta | Mirar el monitor del backoffice |
| `BOLETAS_POR_VENCER` | Hay boletas en su 7.º día (o pasado) sin informarse a SUNAT | El aviso al cliente ya salió el 5.º día; revisar si el correo funciona |
| `RESPALDO_ATRASADO` | Pasaron más de `ALERTAS_RESPALDO_HORAS` sin respaldo bueno (o nunca hubo uno) | Revisar el cron del servicio `respaldo` (§8) |
| `CORREO_DESHABILITADO` | `MAIL_HABILITADO=false` o sin SMTP | Ver el aviso de §2: los correos se escriben en el log y no salen. Un SMTP configurado pero caído **no** dispara esta alerta: se ve en `/health` y, si `ALERTAS_EMAIL` está puesto, en el log (`El canal de alertas correo no pudo entregar`) |
| `CERTIFICADOS_EN_RIESGO` | Hay empresas con el certificado vencido o por vencer | El portal ya se lo avisa al cliente; la lista está en Avisos del backoffice |
| `CREDENCIALES_SOL` | Hay empresas con envíos fallando porque SUNAT rechaza su usuario SOL | El cliente tiene que actualizar el usuario SOL |

Cada problema avisa **una vez** cuando aparece, se repite cada `ALERTAS_REPETIR_HORAS` (24) mientras siga, y avisa `RESUELTO` cuando deja de cumplirse. Lo avisado queda en `alerta_operador`:
si ningún canal pudo entregar el mensaje, no se registra y la siguiente pasada lo reintenta; si un canal falla pero otro entrega, el aviso se da por entregado y el canal caído no lo recibe (queda `WARN`/`ERROR` en el log). Con varias réplicas del backend, una sola reserva y manda cada aviso. Siempre sale también por el log (nivel ERROR), con o sin canal configurado; si no hay ningún canal, el backend lo dice al arrancar. Un canal que se configura después no recibe las alertas que ya estaban activas hasta la siguiente repetición.

### Variables (servicio `backend`)

| Variable | Ejemplo | Notas |
|---|---|---|
| `ALERTAS_EMAIL` | `ops@tu-dominio.pe,guardia@tu-dominio.pe` | Direcciones separadas por coma. Solo cuenta si el correo entrega de verdad (`MAIL_HABILITADO=true`) |
| `ALERTAS_WEBHOOK_URL` | `https://hooks.slack.com/services/...` | Un `POST` JSON con `text` (Slack, Mattermost, Teams), `content` (Discord), `asunto` y `cuerpo`. Una URL que no sea http(s) impide arrancar |
| `ALERTAS_RESPALDO_HORAS` | `26` | **Recomendado en producción** (el cron es diario). `0` (default) apaga la alerta del respaldo |
| `ALERTAS_REPETIR_HORAS` | `24` | 1 a 168 |
| `ALERTAS_ERROR_ENVIO_MINUTOS` / `ALERTAS_ERROR_ENVIO_CANTIDAD` | `30` / `5` | Umbral de `ENVIOS_CON_ERROR` |
| `ALERTAS_FUERA_DE_PLAZO_HORAS` | `24` | Ventana de `FUERA_DE_PLAZO` (1 a 168) |
| `ALERTAS_INTERVALO_MS` | `300000` | Cada cuánto corre el vigilante (5 min) |
| `SCHEDULER_HILOS` | `4` | Hilos de los trabajos programados (envío a SUNAT, avisos, vigilante, webhooks). Antes era 1: una llamada lenta frenaba a todos |
| `MAIL_TIMEOUT_MS` | `10000` | Tope de conexión, lectura y escritura con el SMTP |

Con `CORREO_DESHABILITADO` el canal de correo no puede avisar de su propio problema: configure también el webhook, o esa alerta solo quedará en el log.

### El respaldo avisa que corrió

`respaldar.sh` anota al terminar bien un latido (`INSERT` en `latido_operativo`, clave `respaldo`) con el mismo `DATABASE_URL` con que hizo el volcado. Si la base todavía no tiene la migración V57, el
respaldo se sube igual y el script solo escribe una advertencia. Con `ALERTAS_RESPALDO_HORAS=26`, un día sin respaldo bueno dispara `RESPALDO_ATRASADO`.

### Probar que llega

1. Configure `ALERTAS_WEBHOOK_URL` y/o `ALERTAS_EMAIL` y reinicie el backend.
2. Para provocar una alerta sin dañar nada, con `ALERTAS_RESPALDO_HORAS=26` borre el latido y el registro de esa alerta: `delete from latido_operativo where clave = 'respaldo'; delete from alerta_operador where clave = 'RESPALDO_ATRASADO'`. En la siguiente pasada (máximo 5 minutos) llega `ALERTA · El respaldo diario de Postgres no corrió`.
3. Corra el servicio `respaldo` (o inserte el latido a mano) y en la pasada siguiente llega `RESUELTO`.
4. Si no llega nada, busque en el log `El canal de alertas webhook no pudo entregar` o `correo`: dice por qué falló.

## 12. Webhooks de cambio de estado (#19)

Los integradores registran URL a las que khipu avisa, con un `POST` firmado, cuando un comprobante cambia de estado (guía de desarrolladores, sección *Webhooks*). Lo mueve el
trabajo `EntregarWebhooksWorker` (cada 5 s). Las entregas se encolan en `webhook_entrega` **en la misma transacción** que el cambio de estado.

- **Varias réplicas**: cada entrega se toma con `FOR UPDATE SKIP LOCKED` y una marca de bloqueo de 5 min, y el resultado solo se anota si la entrega sigue tomada con esa marca: si una
  instancia tarda más que el bloqueo, otra puede volver a mandar la entrega (como mucho una vez más) pero la lenta no pisa el resultado de la otra.
- **Un receptor lento no frena a los demás**: un lote toma hasta 5 entregas por endpoint y 50 en total; los endpoints se atienden en paralelo (`WEBHOOKS_HILOS`, 8), cada uno en orden, y antes
  de cada envío se vuelve a mirar que siga encendido. El peor caso de un endpoint es 5 × 15 s. El trabajo usa su propio pool, no los hilos del scheduler.
- **El secreto de firma de cada endpoint se guarda cifrado con `MASTER_KEY`** (como las credenciales SOL) y entra en la rotación de §9. **Reemplazar la clave de golpe** deja los
  secretos ilegibles: cada entrega falla con «No se pudo leer el secreto de firma» y, a los 10 intentos seguidos, el endpoint se pausa. Al rotar el secreto de un endpoint (desde el portal),
  el anterior deja de valer al instante y las entregas en curso se firman con el nuevo: el integrador tiene que actualizar su servidor antes.
- **Destinos**: solo `https` y solo direcciones públicas. Antes de cada entrega se resuelve el nombre y se rechaza loopback, redes privadas, enlace local (incluida la metadata de la nube,
  `169.254.169.254`), CGNAT, `192.0.0.0/24`, `198.18.0.0/15`, `240.0.0.0/4`, IPv6 ULA y NAT64 hacia una de esas; no se siguen redirecciones y nunca se usa un proxy. Los mensajes de error
  no revelan la dirección resuelta. **Límite conocido**: el cliente HTTP vuelve a resolver el nombre al conectar, así que un DNS que cambie justo entre la comprobación y la conexión no se
  detiene; lo acota la caché de DNS de la JVM (`networkaddress.cache.ttl` = 30 s si nadie la fijó) y no hay forma de verlo más que en la red del destino. Cualquier puerto es válido.
- **Reintentos**: 8 intentos con espera de 1 min, 5 min, 15 min, 1 h, 3 h, 6 h y 12 h; con 10 intentos fallidos seguidos el endpoint se apaga y se avisa por correo a la cuenta (necesita
  `MAIL_HABILITADO=true`; si no, queda en el log). Un endpoint **pausado por khipu** sigue guardando en cola los eventos nuevos y los manda al reactivarlo; uno que **apagó una persona** no
  recibe nada. El log de entregas cerradas y las pendientes de un endpoint apagado se conservan 30 días (limpieza diaria).
- **Monitoreo**: las entregas fallidas no generan una alerta del operador; el integrador las ve en el portal. Si el trabajo dejara de correr, las entregas pendientes se acumulan en
  `webhook_entrega` (`select count(*) from webhook_entrega where estado = 'PENDIENTE' and siguiente_intento < now() - interval '10 minutes'`). En los logs, solo aparece el servidor de la
  URL (nunca su ruta ni su consulta, que suelen llevar un token del integrador).

| Variable | Valor | Notas |
|---|---|---|
| `WEBHOOKS_INTERVALO_MS` | `5000` | Cada cuánto corre el trabajo de entrega |
| `WEBHOOKS_HILOS` | `8` | Endpoints atendidos a la vez (1 a 64) |
| `WEBHOOKS_PERMITIR_HTTP` | `false` | **Solo desarrollo y pruebas.** Acepta destinos `http://` |
| `WEBHOOKS_PERMITIR_REDES_PRIVADAS` | `false` | **Solo desarrollo y pruebas** (un receptor en `localhost`). El backend lo avisa con un WARN al arrancar si está en `true`; en producción, siempre `false` |

## 13. Consulta de validez por la API REST de SUNAT (#318)

`GET /v1/consultas/validez` usa la «consulta integrada de comprobante de pago» de SUNAT (API REST con OAuth2) cuando la empresa cargó sus credenciales API SUNAT
(`PUT /v1/empresa/credenciales-api-sunat`, o la pestaña «Credenciales SOL» del portal). Las genera el contribuyente en SOL: Empresas › Comprobantes de pago ›
Consulta de Validez de Comprobantes de Pago › Credenciales de API SUNAT (registra una aplicación y copia su «Id» y su «Clave»). Se guardan cifradas con
`MASTER_KEY` y entran en la rotación de §9. Si la API no responde o rechaza las credenciales y la empresa tiene clave SOL, la consulta cae a `billValidService`
(SOAP, solo producción) y la respuesta lo dice en `aviso`.

**Fallback provisional ante un rechazo de la consulta.** Hoy también cae a SOAP (si hay clave SOL) cuando la API responde que la consulta es inválida (400/422 o
`success: false`), con el `aviso` «La API de SUNAT rechazó la consulta (…): se usó billValidService con la clave SOL». Existe solo porque el contrato con la
API (rutas, scope, `numero`/`monto` como texto, la forma de `success`/`data`) sale del manual y no se verificó contra SUNAT real: sin él, una empresa en
producción que valida por SOAP y carga credenciales API recibiría `422 CONSULTA_RECHAZADA` si SUNAT no aceptara ese formato. **Se quita tras la corrida
controlada de abajo** (en `ConsultarValidezService`): un rechazo de los datos es la respuesta de SUNAT y preguntarlo por otro canal no la cambia. Sin clave
SOL, el rechazo ya se devuelve como `422 CONSULTA_RECHAZADA`.

**Cambio de contrato para integradores** en `GET /v1/consultas/validez`: sin ninguna credencial el código pasó de `422 CREDENCIALES_SOL_NO_CARGADAS` a
`422 CREDENCIALES_SUNAT_NO_CARGADAS` (sirven las API SUNAT o las SOL). Los fallos de SUNAT, que antes salían como `500 INTERNO`, ahora son `503 SUNAT_NO_DISPONIBLE`,
`422 CREDENCIALES_SOL_RECHAZADAS`, `422 CREDENCIALES_API_SUNAT_RECHAZADAS` o `422 CONSULTA_RECHAZADA`. La respuesta suma `fuente`, `estado_ruc`,
`condicion_domicilio`, `observaciones`, `aviso` y los estados `AUTORIZADO` / `NO_AUTORIZADO`. `PUT /v1/empresa/credenciales-api-sunat` responde `422 VALIDACION`
si falta un campo y `422 CREDENCIALES_API_SUNAT_INVALIDAS` si el `client_id` no tiene forma de identificador.

| Variable | Por defecto | Para qué |
|---|---|---|
| `SUNAT_API_SEGURIDAD_URL` / `SUNAT_API_SEGURIDAD_BETA_URL` | `https://api-seguridad.sunat.gob.pe` | Servicio de token (`/v1/clientesextranet/<client_id>/oauth2/token/`) |
| `SUNAT_API_CONSULTA_URL` / `SUNAT_API_CONSULTA_BETA_URL` | `https://api.sunat.gob.pe` | Consulta (`/v1/contribuyente/contribuyentes/<ruc>/validarcomprobante`). SUNAT no publica una instancia de pruebas: beta usa la misma |
| `SUNAT_API_TIMEOUT_SECONDS` | `10` | Tope de cada llamada (1 a 120) |

**Pendiente: prueba controlada con credenciales reales.** Las pruebas automáticas usan un SUNAT simulado con las rutas y campos del manual de SUNAT; el envío
de `numero` y `monto` como texto sigue el ejemplo del manual y no está confirmado contra SUNAT con una empresa. Se cierra con un solo comando que pregunta a SUNAT
de verdad, sin backend, base de datos ni Docker, y solo lee (no escribe nada en SUNAT):

```bash
VALIDEZ_RUC=<RUC de la empresa dueña de las credenciales> VALIDEZ_CLIENT_ID=<Id de la aplicación> VALIDEZ_CLIENT_SECRET=<Clave> VALIDEZ_SERIE=F001 VALIDEZ_NUMERO=<n> VALIDEZ_FECHA=<AAAA-MM-DD> VALIDEZ_MONTO=<total> ./gradlew :adapters:out-sunat-rest:validezReal
```

En PowerShell las variables se fijan antes, en la misma sesión, y se borran al terminar:

```powershell
$env:VALIDEZ_RUC="<RUC>"; $env:VALIDEZ_CLIENT_ID="<Id>"; $env:VALIDEZ_CLIENT_SECRET="<Clave>"
$env:VALIDEZ_SERIE="F001"; $env:VALIDEZ_NUMERO="<n>"; $env:VALIDEZ_FECHA="<AAAA-MM-DD>"; $env:VALIDEZ_MONTO="<total>"
./gradlew :adapters:out-sunat-rest:validezReal
Remove-Item Env:VALIDEZ_*
```

Las credenciales se generan en SOL: Empresas › Comprobantes de pago › Consulta de Validez de Comprobantes de Pago › Credenciales de API SUNAT (registrar una
aplicación y copiar su «Id» y su «Clave»). El comprobante tiene que ser **uno propio ya aceptado** (`VALIDEZ_TIPO` es `01` por defecto; `VALIDEZ_RUC_EMISOR`, el RUC
de la empresa por defecto). Las credenciales van en variables de entorno de ese comando y no se guardan en ningún archivo; la evidencia no las lleva.

La corrida hace seis preguntas y deja el resultado en `adapters/out-sunat-rest/build/validez-real/RESUMEN.md`:

| Paso | Lo esperado | Si no se cumple |
|---|---|---|
| Credenciales inventadas | SUNAT las rechaza como credenciales (HTTP 400, `unauthorized_client`). No necesita ninguna variable | La URL del token o el trato de sus errores no son los de SUNAT |
| El comprobante existente | `estadoCp` 1 (aceptado), con `estadoRuc` y `condDomiRuc` | **El contrato del manual no es el que SUNAT acepta**: mirar el `SunatRechazoException` que muestra y seguir el punto 2 de abajo |
| Un correlativo que no existe | `estadoCp` 0, o un rechazo de SUNAT que diga que no existe (las dos formas valen) | Solo falla si SUNAT lo da por aceptado |
| Fecha equivocada, sin monto | Solo informan lo que SUNAT contesta (con o sin observación) | No hace fallar la corrida |
| `numero` y `monto` como números JSON | Solo informa si SUNAT acepta también ese formato | No hace fallar la corrida |

Sin las variables, la corrida solo hace el primer paso y deja los demás como «omitido». Ese primer paso ya se hizo contra SUNAT real el 2026-10-31: SUNAT
contestó `400 unauthorized_client` («cliente no autorizado») a un cliente inventado, que es lo que `ClienteOAuthSunat` traduce a «credenciales rechazadas».

Después de la corrida con credenciales:

1. Pegar `RESUMEN.md` en el issue #318. Si el paso «existente» cumple, el contrato de las rutas, el scope y el formato de `numero`/`monto` queda confirmado.
2. Si SUNAT rechaza la consulta por el formato de `numero`/`monto`, ajustar `RestValidezGateway` (mandarlos como número, que es lo que el último paso informa) y su prueba, y repetir.
3. Verificado el contrato, quitar el fallback ante `SunatRechazoException` en `ConsultarValidezService` y volver la prueba de la matriz a «un rechazo no cae a SOAP».
4. Comprobar de punta a punta por la aplicación, con una empresa que tenga esas credenciales:
   `curl "$API/v1/consultas/validez?ruc=<RUC emisor>&tipo=01&serie=F001&numero=<n>&fecha=<AAAA-MM-DD>&monto=<total>" -H "X-Api-Key: $KEY"`. Lo esperado:
   `fuente: API_REST`, `estado: ACEPTADO`, sin `aviso`. Mientras dure el fallback provisional, un rechazo de la API no se ve como error: hay que mirar que la respuesta
   diga `fuente: API_REST`; para probar sin fallback, hacerlo con una empresa sin clave SOL.

## 14. Guía de remisión electrónica por la API REST de SUNAT (#39) — EXPERIMENTAL

`POST /v1/guias-remision` emite la guía de remisión remitente (09, serie `T###`) y la transportista (31, serie `V###`). La GRE no va por SOAP: khipu firma el
`DespatchAdvice`, lo envía en un ZIP a la API REST de SUNAT (`<gre>/v1/contribuyente/gem/comprobantes/{RUC}-{tipo}-{serie}-{número}`), recibe un ticket y lo
consulta (`<gre>/v1/contribuyente/gem/comprobantes/envios/{ticket}`) hasta tener el CDR. El token es OAuth2 con el grant `password`
(`<seguridad>/v1/clientessol/<client_id>/oauth2/token/`, scope `https://api-cpe.sunat.gob.pe`): pide las **credenciales API SUNAT** de la empresa (las de §13)
**y** su usuario y clave SOL. Fuente: «Manual de servicios web plataforma nueva GRE» y su anexo de URLs (`docs/sunat/ref/manual_servicios_gre_rest.pdf` y
`manual_url_gre_rest.xlsx`, locales).

**El envío viene apagado (`SUNAT_API_GRE_HABILITADA=false`).** El contrato con SUNAT no se probó (ver «Pendiente» abajo). Con el interruptor apagado, emitir con
envío y `POST /v1/guias-remision/{id}/enviar` responden `422 GRE_NO_DISPONIBLE` **antes de numerar** y sin llamar a SUNAT (y el outbox no envía nada); emitir con
`enviar_automatico: false` sigue permitido, para revisar el XML y el PDF. **El dueño lo enciende solo después de la corrida controlada** de abajo, y la API se
documenta como EXPERIMENTAL hasta entonces. De las dos guías, la **transportista (31) es la menos respaldada**: su remitente en `cac:DespatchParty`, el registro
MTC del emisor en `cac:DespatchSupplierParty/.../cbc:CompanyID` y la ausencia de modalidad no tienen fuente en el repositorio (la remitente 09 coincide con las
implementaciones conocidas de la GRE 2022).

**Reintentos y envíos duplicados.** Antes de llamar a SUNAT la guía pasa a `ENVIANDO` con una actualización condicional: dos envíos a la vez (el outbox y uno a
mano) no mandan el mismo número dos veces. El outbox reintenta un envío al que SUNAT no respondió (backoff de los comprobantes, hasta `OUTBOX_MAX_INTENTOS`;
agotados, la guía queda `ERROR_ENVIO` con `reintento_automatico: false` y el motivo) y consulta el ticket mientras SUNAT diga «en proceso» (30 s, 1 min, … hasta
5 min, con tope de 300 consultas, unas 24 h). Una guía que quedó `ENVIANDO` (el proceso murió después de llamar a SUNAT) es **ambigua**: SUNAT pudo recibirla. No se
reenvía sola; pasados 10 min se retoma con `POST /v1/guias-remision/{id}/enviar?forzar=true`, **solo después de revisar en SOL que no llegó**. Si SUNAT responde
que el número ya está registrado (1032, 1033, 2223, 4000), la guía queda `ERROR_ENVIO` con el aviso de revisarla en SOL, no de reenviarla.

**Credenciales rechazadas.** Si el token responde 400/401/403 (credenciales API o clave SOL), la guía queda `ERROR_ENVIO` sin reintento y la empresa queda
marcada: mientras sus credenciales sean las mismas y no pase una hora, emitir con envío, reenviar y consultar tickets responden `422 CREDENCIALES_GRE_RECHAZADAS`
**sin pedir token** (golpear el token con una clave SOL equivocada puede bloquearla), y el outbox espera. Guardar otras credenciales lo libera en el acto.

**Beta.** SUNAT no publica una API GRE de pruebas, y la de producción emite guías con validez tributaria. `SUNAT_API_GRE_BETA_URL` va vacía por defecto y una
empresa en `BETA` no envía guías. La única beta práctica es una **API de pruebas de terceros** (p. ej. la de un OSE o un simulador): esa API recibe el ZIP de la
guía y el bearer del token, y su servicio de token (`SUNAT_API_GRE_SEGURIDAD_BETA_URL`, obligatorio con la beta) recibe el `client_id`, el `client_secret`, el
usuario y la **clave SOL** de cada empresa en BETA: use credenciales de prueba, nunca las de una empresa real. El backend **no arranca** si la beta apunta a la API
GRE de producción o al `api-seguridad` real de SUNAT (las empresas en BETA mandarían guías reales, o su bearer real a un tercero).

| Variable | Por defecto | Para qué |
|---|---|---|
| `SUNAT_API_GRE_HABILITADA` | `false` | Enciende el envío de guías a SUNAT. Solo tras la corrida controlada |
| `SUNAT_API_GRE_URL` | `https://api-cpe.sunat.gob.pe` | Envío y ticket de la GRE de las empresas en `PRODUCCION` |
| `SUNAT_API_GRE_BETA_URL` | (vacía) | API GRE de pruebas de terceros para las empresas en `BETA`; vacía, no envían |
| `SUNAT_API_GRE_SEGURIDAD_BETA_URL` | (vacía) | Token de esa API de pruebas; obligatorio con `SUNAT_API_GRE_BETA_URL` |
| `SUNAT_API_GRE_TIMEOUT_SECONDS` | `30` | Tope de cada llamada de la GRE (1 a 120); conectar, a lo sumo 5 s |
| `SUNAT_API_SEGURIDAD_URL` | `https://api-seguridad.sunat.gob.pe` | El token de las empresas en `PRODUCCION` (compartido con §13, otra ruta y otro grant) |

**Pendiente: nunca se probó contra SUNAT.** No hay credenciales API SUNAT reales ni API GRE de pruebas: todas las pruebas automáticas (unidad, WireMock y
`GuiaRemisionE2ETest`) usan un SUNAT simulado que responde lo que dicen el manual y su anexo. Lo que el manual no fija y khipu supone sin haberlo visto
funcionar: (a) el `hashZip` es el SHA-256 del ZIP en hexadecimal minúsculo; (b) `arcCdr` es un ZIP con el `ApplicationResponse`, como el CDR del SOAP;
(c) la URL del QR está en un `cbc:DocumentDescription` del CDR; (d) la estructura del `DespatchAdvice` (nodos de vehículos, conductores, partida, llegada y,
en la 31, remitente y MTC) sigue el XSD UBL 2.1, los nodos que nombran los códigos de «CódigosRetorno» y las implementaciones conocidas de la GRE 2022: el
repositorio no tiene la guía de elaboración del XML de la GRE ni su hoja de validaciones. Corrida controlada, con una empresa real **en PRODUCCION** (la guía tiene
validez tributaria: use un traslado real y documentado), en un servidor con `SUNAT_API_GRE_HABILITADA=true` solo para ella:

1. En SOL, registrar la aplicación en «Credenciales de API SUNAT» con acceso a la **GRE** (el alta de la aplicación para la consulta de validez puede no
   bastar: SUNAT distingue los servicios) y copiar «Id» y «Clave».
2. Guardar las credenciales API (`PUT /v1/empresa/credenciales-api-sunat`, ver §13) y las SOL (`PUT /v1/empresa/credenciales-sol`, usuario secundario con
   permiso para emitir la GRE).
3. Crear las series: `POST /v1/series` con `{"tipo":"09","serie":"T001"}` (y `31`/`V001` si es transportista).
4. Emitir primero **sin enviar** (`"enviar_automatico": false`), revisar el XML (`GET /v1/guias-remision/{id}/xml`) y, si todo cuadra, enviarla con
   `POST /v1/guias-remision/{id}/enviar`. Lo esperado: `estado` `ENVIADO_PENDIENTE_TICKET` o `ACEPTADO`, y `ticket` con un UUID.
5. Si SUNAT responde 422 al envío, `ultimo_error` trae su código (`504` nombre del archivo, `507` hash, `159`/`161` nombre del XML): ajustar
   `RestGreGateway` (p. ej. el hash en otro formato) y su prueba. Si el ticket devuelve 99, el código del CDR o de `error.numError` dice qué regla falla:
   ajustar la plantilla `despatch-advice.ftl` o la regla del dominio, y su prueba.
6. Verificar que `GET /v1/guias-remision/{id}/pdf` lleve el QR (si no, el CDR trae la URL en otro nodo: ajustar `RestGreGateway.enlace`).
7. Repetir con una guía transportista (31) si la empresa lo es: es la menos respaldada.
8. Recién entonces encender `SUNAT_API_GRE_HABILITADA` para todos y quitar la marca EXPERIMENTAL de la API y de la guía de desarrolladores.

**Lo que falta del issue #39** (la PR es «Refs #39», no «Closes»):
- La UI de emisión y el listado de guías en el portal (la API está; el portal solo crea las series 09/31 y nombra los tipos).
- La guía aceptada en una beta (no hay API GRE de pruebas de SUNAT) y la corrida controlada en producción de arriba.
- Los motivos 08 importación, 09 exportación y 18 emisor itinerante (piden la DAM, puertos o depósitos y no tienen punto de llegada). Con el 08 vuelve el número de
  **bultos o pallets** (`TotalTransportHandlingUnitQuantity`, reglas 2771/4195): hoy el campo `bultos` de la API se rechaza y la tabla `guia_remision` no tiene columna
  para él; agregarla en esa migración.
- Webhooks e historial de eventos de las guías, y si cuentan para el tope del plan.

## 15. Registro de ventas del SIRE (RVIE) (#322) — EXPERIMENTAL

`GET /v1/sire/rvie?periodo=AAAAMM` descarga un archivo de reemplazo de la propuesta del RVIE armado con los comprobantes de la empresa, y
`POST /v1/sire/rvie/conciliacion?periodo=AAAAMM` (cuerpo: la propuesta descargada del SIRE, `.txt` o `.zip`) lo compara con la propuesta de SUNAT. khipu **nunca escribe
en el SIRE**: el archivo de reemplazo lo sube el contribuyente (o su contador) en SOL. La propuesta puede subirla a mano, o khipu puede pedírsela a SUNAT por la API del
SIRE (fase 2, más abajo; apagada por defecto). No hay migración: todo se calcula de las tablas de comprobantes.

**EXPERIMENTAL: formato reconstruido. No usarlo para reemplazar una propuesta real antes de validarlo con el PVSIRE** (corrida controlada al final). Nada de esto se
verificó contra el SIRE ni contra el validador de SUNAT.

**Cubre solo lo emitido en khipu.** La propuesta de SUNAT trae todos los comprobantes de la empresa; reemplazarla con este archivo **saca** las ventas emitidas por
otros medios (SEE-SOL, comprobantes físicos, otro proveedor). Antes de reemplazar, conciliar y exigir `solo_en_sunat` vacío. La respuesta lo recuerda con la
cabecera `X-Rvie-Alcance: solo-khipu`; la guía y el OpenAPI lo dicen en las dos operaciones.

**Qué entra.** Una fila por comprobante emitido en el mes que SUNAT tiene: `ACEPTADO`, `ACEPTADO_CON_OBS` y `ANULADO`. Lo que va en camino (`FIRMADO`, `ENVIADO`,
`ERROR_ENVIO`, `PENDIENTE_AGRUPACION`) no entra y se cuenta en `X-Rvie-Pendientes-Sunat`. La lista vive en `FilaRvie.INCLUIDOS` / `FilaRvie.PENDIENTES`. Tope: 100 000
comprobantes por periodo (`422 RVIE_EXCEDE_EL_TOPE`); el archivo se escribe por tramos de 500 sin cargar el mes en memoria. `X-Rvie-Registros` se cuenta al
preparar la respuesta: si un comprobante cambia de estado durante la descarga (un `ENVIADO` que SUNAT acepta), el archivo puede traer uno más o uno menos.

### Fuentes

- **Anexo III de la RS 000138-2023/SUNAT**, «Estructura y reglas para elaborar el archivo plano que permita la comparación con la propuesta RVIE o reemplazar esa
  última» (https://www.sunat.gob.pe/legislacion/superin/2023/anexoIII-000138-2023.pdf). Es una modificación parcial del Anexo 3: trae solo los campos 2, 3, 7–13 y
  29–32 y las notas 1 y 3–6. Lo que dice y khipu aplica:
  - Campo 2 hasta 1500; campo 3 `AAAAMM`; campo 7 por la tabla 3; campos 8 y 9 alfanuméricos de hasta 20.
  - Campo 10 (número final) solo para consolidados (`03` y otros), y la boleta de S/ 700 o más va detallada. khipu escribe una fila por boleta; la conciliación
    no lee como boleta una fila consolidada de la propuesta: la cuenta en `filas_consolidadas_no_conciliadas`.
  - Campos 11–13 obligatorios. Sus excepciones (exportación, campo 14 > 0; boleta de menos de S/ 700; consolidados) «solo serán aplicables cuando los comprobantes
    de pago y las notas vinculadas sean emitidos de manera física» (nota 5): khipu emite electrónicos y escribe siempre el cliente del comprobante.
  - Campos 29–32: fecha (dd/mm/aaaa, no posterior al periodo), tipo, serie y número del comprobante que modifica la nota.
  - Nota 3: los comprobantes electrónicos con baja comunicada se anotan en cero.
  - Nota 4: los campos 34 a 40 que no se informan los completa SUNAT.
- **Control de cambios del Aplicativo Cliente SIRE**
  (https://www.sunat.gob.pe/orientacion/librosRegistros-Electronicos/descarga2/migeLibros/controlCambios/controlCambios-sire.pdf):
  - separador «palotes "|"» y cantidad de campos de la estructura (validación 246, versión 3.37.0);
  - tipo de cambio de las operaciones en la moneda de la contabilidad «se homologa con valor de "0.000"» (3.17.0, nota sobre la visualización);
  - el campo 25 acepta positivos y negativos para todo tipo (3.38.0);
  - los campos 15–17 en `0`, `0.0` o `0.00` cuando no corresponden (3.25.0);
  - la fecha de vencimiento debe corresponder al periodo informado, salvo el tipo 14 (validación 210, 3.37.0 y 3.39.0).
- **Manual del servicio API del Registro de Ventas e Información Electrónica, v31** (septiembre de 2026,
  https://cpe.sunat.gob.pe/sites/default/files/2026-09/Manual%20del%20servicio%20API%20del%20Registro%20de%20Ventas%20e%20Informaci%C3%B3n%20Electr%C3%B3nica.%20v31_1.pdf).
  Es la fuente de la fase 2 (token, ticket, descarga) y de lo que fija del archivo de reemplazo: se sube comprimido (`.zip`, error 1348 si no), el nombre sigue
  `LE<RUC><AAAAMM>0014040002OIM2` y su ejemplo es `LE2010017645020230200140400020112.zip` (§5.3).
- **Estructura del nombre del archivo** (posiciones 1–35: `LE`, RUC, año, mes, `00`, libro `140400`, código de oportunidad, y los indicadores de operaciones, de
  contenido, de moneda y de generado por el SIRE), que SUNAT publica en la RS 112-2021 modificada por la RS 040-2022; no se leyó el texto de la resolución sino un
  resumen de una revista contable (https://misha.pe/?p=16554), que coincide con el patrón y el ejemplo del manual: oportunidad `02` reemplaza la propuesta;
  operaciones `1` empresa operativa; contenido `1` con información; moneda `1` soles; generado `2` por el nuevo sistema (fijo). El `03` de ajustes posteriores lleva
  además un correlativo de 2 dígitos (posiciones 34–35), por eso hay fuentes que hablan de 35 caracteres: el reemplazo no lo lleva.
- **Programa Validador SIRE (PVSIRE)** (https://cpe.sunat.gob.pe/programa-validador-sire-pvsire): valida sin conexión archivos de reemplazo del RVIE en txt o zip. No
  se usó (es un instalable externo): es el paso de la corrida controlada.

**Sin fuente en el repositorio**: los campos 1, 4–6, 14–28 y 33–40 (salvo lo citado del control de cambios), la codificación, el fin de línea, el separador al final de
la fila y los valores del campo 35. (El anexo que SUNAT publica en la RS 000138-2023 y el 2 de la propuesta salen con «(…)» donde faltan los demás campos: la tabla
completa está en el Anexo 3 de la RS 112-2021 que no se pudo leer; la corrida de la fase 2 dirá si la propuesta real de SUNAT se lee con estas columnas.)

### Decisiones y supuestos

Cada una está en un solo lugar (`FormatoRvie` y `FilaRvie`, paquete `pe.factura.domain.sire`):

| Decisión | Valor actual | Fuente | Dónde |
|---|---|---|---|
| Nombre | `LE<RUC><AAAAMM>00140400021112.txt` (33 caracteres) dentro de un `.zip` del mismo nombre | manual v31 §5.3 (patrón, ejemplo y `.zip`); los indicadores `1112`, la estructura de posiciones (arriba); que el `.txt` interno se llame igual, supuesto | `FormatoRvie.nombreArchivo` |
| Separador | `\|` entre campos | control de cambios (246) | `FormatoRvie.linea` |
| Fin de fila | `\|` también al final; CRLF; sin fila de títulos | supuesto | `FormatoRvie.linea` |
| Codificación | UTF-8 sin BOM | supuesto | `FormatoRvie.CODIFICACION` |
| Texto | `\|`, saltos de línea y caracteres de control pasan a ser un espacio (no hay escape); razón social hasta 1500 | anexo (2, 13: hasta 1500) | `FormatoRvie.texto` |
| Importes y fechas | 2 decimales con punto, sin miles; `dd/mm/aaaa`; tipo de cambio con 3 decimales | anexo (29: dd/mm/aaaa); el resto, supuesto | `FormatoRvie` |
| CAR (campo 4) | RUC + tipo + serie + número en 10 dígitos | supuesto | `FilaRvie.car` |
| Vencimiento (6) | vacío si cae fuera del periodo | control de cambios (210); que se acepte vacío, supuesto | `FilaRvie.vencimientoDelPeriodo` |
| Cliente (11–13) | siempre el del comprobante, `-` incluido en la boleta sin documento | anexo, nota 5 | `FilaRvie.de` |
| Nota de crédito | importes en negativo | supuesto | `FilaRvie.signo` |
| Notas (29–32, 34) | comprobante modificado; el 34 con el motivo (catálogo 09/10) | anexo (29–32); 34, supuesto | `FilaRvie.de` |
| Anulado | importes en cero; estado `2` (vigente `1`) | anexo, nota 3 (cero); estado, supuesto | `FilaRvie.ESTADO_*` |
| Moneda (27, 28) | importes en la moneda del comprobante; en soles `0.000`; fuera de soles el tipo de cambio del comprobante (#316) | control de cambios (3.17.0) para `0.000`; el resto, supuesto | `FilaRvie.TIPO_CAMBIO_EN_SOLES` |
| Boletas | una fila por boleta, campo 10 vacío | anexo (10: la consolidación es opcional) | `FilaRvie.campos` |
| Campo 25 | lo que falta para que 14–25 sumen el 26: otros tributos + cargos − descuentos 01/03 + redondeo (± ajuste de anticipos), con signo | control de cambios (3.38.0) para el signo; la fórmula, supuesto | `FilaRvie.de` |
| Vacíos | 10, 33, 36, 39 y 40; 16 y 18 en `0.00` | anexo (nota 4: SUNAT completa 34–40); control de cambios (3.25.0) | `FilaRvie.campos` |

Los importes salen del cálculo del dominio sobre los ítems guardados (el mismo que muestran la API y el PDF), no del XML firmado: si una regla de cálculo cambió
después de emitir, la conciliación lo muestra como diferencia.

Un comprobante fuera de soles sin tipo de cambio (emitido antes del #316) hace responder `422 RVIE_SIN_TIPO_CAMBIO` con los primeros 10; con
`permitir_sin_tipo_cambio=true` sale con el campo 28 vacío (cuenta en `X-Rvie-Sin-Tipo-Cambio`) y hay que completarlo a mano antes de subirlo.

**Dudas abiertas, a confirmar con el PVSIRE**:
- El nombre generado tiene 33 caracteres. Los 35 son del archivo de ajustes posteriores (lleva un correlativo de 2 dígitos), no del reemplazo. El ejemplo del manual
  usa el indicador de operaciones `0` (cierre de operaciones) y khipu escribe `1` (empresa operativa): se confirma con el PVSIRE.
- Un periodo sin comprobantes responde `404 NO_ENCONTRADO`. SUNAT admite, según su página de estructura de archivos, un archivo «en blanco» cuando no hubo operaciones
  (indicador de contenido `0` en la posición 31 del nombre), pero khipu no lo genera: queda el 404.
- Si el campo 6 vacío se acepta en una factura al crédito que vence otro mes.

**Conciliación.** Compara por tipo, serie y número (el número de dígitos sin ceros a la izquierda): fecha, moneda, estado (si la propuesta trae el campo 35),
base gravada, IGV, exonerado, inafecto y total. Lee la propuesta con el mismo layout (tolera una fila de títulos, el `|` final ausente y LF). Rechaza con
`422 RVIE_ARCHIVO_INVALIDO`, citando la línea y el campo:
- lo que no puede leer, una fila de otro RUC o de otro periodo, un comprobante repetido;
- un cuerpo vacío o sin filas de datos, un ZIP dañado, una línea de más de 16 KB.

Una propuesta enviada como formulario es `400 PARAMETRO_INVALIDO`. Topes: 20 MB tal como llega y 64 MB descomprimido (`422 RVIE_PROPUESTA_DEMASIADO_GRANDE`). No
guarda nada.

### Fase 2: la propuesta de SUNAT por la API del SIRE

Con el manual v31, khipu puede pedirle a SUNAT la propuesta y conciliar sin que nadie la descargue ni la suba. **Solo lee**: pedir la propuesta hace que SUNAT arme un
archivo (un ticket), no cambia el registro. Aceptar o reemplazar la propuesta desde khipu sigue fuera, como dice el issue.

| Ruta | Qué hace |
|---|---|
| `POST /v1/sire/rvie/propuesta?periodo=AAAAMM` | Pide la propuesta (manual 5.18) y responde `202` con el `ticket` |
| `GET /v1/sire/rvie/propuesta/{ticket}?periodo=AAAAMM` | Estado del ticket (5.16): `EN_PROCESO`, `TERMINADO` o `CON_ERRORES`, con el código y la descripción de SUNAT |
| `GET /v1/sire/rvie/propuesta/{ticket}/conciliacion?periodo=AAAAMM` | Baja los archivos (5.17) y los concilia como la ruta con cuerpo; mismo reporte |

Son lecturas para todos los roles (`PermisoDeRuta`). Es asíncrono a propósito: ninguna petición espera a SUNAT más de lo que tarda una llamada (30 s de tope por
llamada, `SUNAT_API_SIRE_TIMEOUT_SECONDS`); quien llama repite el estado hasta `TERMINADO`. Si el periodo llega partido en varios archivos, se concilian como uno
solo (hasta 50 y 20 MB en suma) y un error de lectura dice cuál archivo fue.

**Qué necesita la empresa**: las credenciales API SUNAT (`client_id` y `client_secret`) de una aplicación registrada en SOL como **«MIGE RCE y RVIE - SIRE»** (el
ámbito `https://api-sire.sunat.gob.pe`), más las credenciales SOL; el token es el del grant `password` con `username` = RUC + usuario SOL, igual que la GRE
(`PedidoDeToken.password`). Sin ellas: `422 CREDENCIALES_API_SUNAT_NO_CARGADAS` / `CREDENCIALES_SOL_NO_CARGADAS`; si SUNAT las rechaza, `422
CREDENCIALES_SIRE_RECHAZADAS`; si rechaza el pedido por sus datos, `422 SIRE_RECHAZO` con su código (1070, que no hay comprobantes en el periodo, es `404
NO_ENCONTRADO`); si no responde, `503 SUNAT_NO_DISPONIBLE`. Un ticket que sigue en proceso es `409 PROPUESTA_NO_LISTA`; uno con errores, `422 PROPUESTA_CON_ERRORES`.

**Apagado por defecto** (`422 SIRE_NO_DISPONIBLE`), por lo mismo que la GRE: el contrato no se verificó contra SUNAT.

| Variable | Por defecto |
|---|---|
| `SUNAT_API_SIRE_HABILITADA` | `false` |
| `SUNAT_API_SIRE_URL`, `SUNAT_API_SIRE_BETA_URL` | `https://api-sire.sunat.gob.pe` (SUNAT no publica una instancia de pruebas: lee datos reales de la empresa, también en BETA) |
| `SUNAT_API_SIRE_TIMEOUT_SECONDS` | `30` (1 a 120) |
| `SUNAT_API_SEGURIDAD_URL` | el de la validez y la GRE (`https://api-seguridad.sunat.gob.pe`) |

**Lo que el manual deja ambiguo y khipu supone** (la corrida de abajo lo confirma o lo corrige en `RestSireGateway`):
- que `codLibro=140000` y `codOrigenEnvio=2` van también en la consulta del ticket (la tabla de parámetros los pide como obligatorios; su ejemplo no los trae);
- que los estados 04 y 06 del Anexo III son aquellos en que ya se baja el archivo, que 03 es el error y que 01, 02 y 05 siguen en proceso (un código que no es
  ninguno de estos se trata como un fallo pasajero y no como terminado);
- que el tipo de archivo del ticket viene como `codTipoAchivoReporte` (con esa errata, como la escribe el manual) o `codTipoArchivoReporte`, y que si es nulo la
  descarga lo manda como el texto `null` (el manual lo pide así);
- que la propuesta de texto (`codTipoArchivo=0`) llega como un ZIP con un `.txt` de las mismas columnas que lee `PropuestaRvie`.

### Corrida controlada (pendiente)

Con una empresa en beta o producción que tenga comprobantes aceptados:

1. **Validar la fase 2 con el SIRE real**, con un solo comando, sin backend, base de datos ni Docker (el dueño de la cuenta, con la aplicación «MIGE RCE y RVIE - SIRE»
   registrada en SOL). En PowerShell:
   ```powershell
   $env:SIRE_RUC="<RUC>"; $env:SIRE_USUARIO_SOL="<usuario SOL>"; $env:SIRE_CLAVE_SOL="<clave SOL>"
   $env:SIRE_CLIENT_ID="<Id de la aplicación>"; $env:SIRE_CLIENT_SECRET="<Clave de la aplicación>"; $env:SIRE_PERIODO="<AAAAMM de un mes con ventas>"
   ./gradlew :adapters:out-sunat-rest:sireReal
   ```
   Sin variables corre un solo paso, el de unas credenciales inventadas, que ya se corrió contra SUNAT: responde `400 access_denied` y la corrida lo reconoce como un
   rechazo de credenciales. Con ellas pide la propuesta, sigue el ticket hasta `TERMINADO` (`SIRE_ESPERA_SEGUNDOS`, 180 por defecto), baja los archivos y los lee con
   el lector de la conciliación. La evidencia queda en `adapters/out-sunat-rest/build/sire-real/RESUMEN.md`, sin secretos, sin filas de la propuesta y con los valores
   que no se pudieron leer tachados. Qué mirar:
   - el paso de la consulta sin `codLibro` ni `codOrigenEnvio` (informativo): si SUNAT la acepta o la rechaza;
   - los estados que vio el ticket y los nombres, tipos y cantidad de archivos;
   - la lectura: cuántas filas, si trae una fila de títulos y cuántos campos tiene la primera. **Si no se lee, el formato real difiere del supuesto de 40 columnas**:
     corregir `PropuestaRvie` y `FormatoRvie` con lo que diga el error (línea y campo).
   Si todo cumple: `SUNAT_API_SIRE_HABILITADA=true` en el servidor, y quitar la marca EXPERIMENTAL de las tres rutas nuevas.


2. **Validar el formato del reemplazo con el PVSIRE.**
   - Generar con `GET /v1/sire/rvie` archivos de periodos que tengan, entre todos: una factura gravada en soles, una nota de crédito sobre ella, una boleta (con y
     sin documento del comprador), un comprobante anulado (baja aceptada), una factura en USD con tipo de cambio y una exportación.
   - Pasarlos por el PVSIRE (instalarlo y correrlo el dueño de la cuenta, no un agente) y revisar:
     - el nombre del archivo (33 caracteres; el indicador de operaciones `1`);
     - el separador final y la codificación;
     - el CAR (campo 4);
     - el signo de la nota de crédito;
     - el campo 35 del anulado;
     - el `0.000` en soles;
     - el campo 6 vacío;
     - que el campo 25 cierre la suma.
   - Corregir `FormatoRvie` / `FilaRvie` y sus pruebas (`FilaRvieTest` fija la fila completa de una factura).
3. Descargar el archivo: `curl -o rvie.zip "$API/v1/sire/rvie?periodo=<AAAAMM>" -H "X-Api-Key: $KEY" -D -` y revisar las cabeceras `X-Rvie-*`.
4. En SOL › SIRE › Registro de Ventas, descargar la propuesta del mismo periodo y conciliar (o, con la fase 2 encendida, usar las rutas de `propuesta`):
   `curl -X POST "$API/v1/sire/rvie/conciliacion?periodo=<AAAAMM>" -H "X-Api-Key: $KEY" -H "Content-Type: application/zip" --data-binary @propuesta.zip`.
   Lo esperado: `cuadra: true`, o al menos `solo_en_sunat` vacío. Una diferencia sistemática (todas las notas de crédito con el signo cambiado, todos los totales
   distintos) es un supuesto del formato que no coincide, no una venta mal emitida. Lo de `solo_en_sunat` son ventas que khipu no emitió: con ellas no se reemplaza.
5. **No reemplazar la propuesta de un periodo real** hasta haber cerrado el punto 2: probar primero el reemplazo en un periodo sin movimientos o con el contador.
6. Cerrado el punto 2, quitar la marca EXPERIMENTAL del OpenAPI (`SireController`), de la guía de `/developers` y de esta sección.

## 16. Retenciones y percepciones (#40)

Los comprobantes de retención (20) y de percepción (40) y sus resúmenes de reversiones (`RR-…`) no van al `billService` de facturas sino al de
retenciones y percepciones de SUNAT, que rechaza lo demás (y el de facturas rechaza estos: «codigo cpe: 20 no es un cpe valido»):

| Propiedad (variable) | Por defecto |
|---|---|
| `app.sunat.otros-beta-url` (`APP_SUNAT_OTROSBETAURL`) | `https://e-beta.sunat.gob.pe/ol-ti-itemision-otroscpe-gem-beta/billService` |
| `app.sunat.otros-prod-url` (`APP_SUNAT_OTROSPRODURL`) | `https://e-factura.sunat.gob.pe/ol-ti-itemision-otroscpe-gem/billService` |

**Designación.** Solo emite una empresa que SUNAT designó agente de retención o de percepción. khipu no consulta los padrones de agentes: el
administrador verifica la resolución y la registra con `POST /v1/admin/empresas/{id}/agente` (`{"retencion": true, "percepcion": false}`), que queda en la
bitácora. Sin ella, crear la serie `R###`/`P###` o emitir responde `422 AGENTE_NO_DESIGNADO`.

**Homologado en e-beta (2026-10-20)**, escenarios 100–107 de `HomologacionBetaTest` (`HOMOLOGACION_ESCENARIOS="10[0-5]-.*"`), todos con CDR `0` y sin
observaciones: retención en soles; en soles y dólares; nota de débito, ticket y dos cuotas; percepción 2 %, 0.5 % (boleta a DNI) y 1 % en dólares; y la
reversión RR de una retención y de una percepción.

**Lo que e-beta no valida y producción sí** (khipu no tiene los datos para comprobarlo antes): que la empresa sea agente (2617 en la retención, error; 4285
en la percepción, observación); que el RUC del proveedor o del cliente exista (2621/2605, salvo que el padrón de RUC esté cargado); que el comprobante cobrado
en una percepción exista con esa fecha (2609/2610), sea al crédito (3329) y no lleve ya la percepción (3312); y el plazo de envío (2600). khipu aplica 7 días
calendario desde el día siguiente a la emisión (RS 274-2015; fuentes secundarias, ver `PlazoEnvio`). e-beta tampoco lee el redondeo del total
(`PayableRoundingAmount`: responde 0306), por eso khipu no lo emite. La reversión no tiene un plazo que SUNAT valide contra la emisión; la norma exige
presentarla dentro de 7 días calendario desde que se comunicó la reversión al proveedor o al cliente, una fecha que khipu no conoce.

**Hueco de numeración.** Si SUNAT rechaza un 20 o un 40, para SUNAT el número no quedó usado (manual del programador), pero khipu lo da por consumido
(`RECHAZADO`) y el comprobante corregido sale con el siguiente: la serie queda con un hueco ante SUNAT.

**Pendiente** (no hecho en #40): reutilizar el número de un 20/40 rechazado; sondear el servicio de otros CPE en el monitor del backoffice; la nota de crédito y
el propio 20/40 como documento relacionado, la emisión excepcional de la percepción y el redondeo del total.

## 17. Términos y privacidad (#342)

El portal publica los términos del servicio en `/terminos` y la política de privacidad en `/privacidad` (públicas, enlazadas desde el pie del landing,
el login y el registro). El registro exige aceptarlos (`acepta_terminos: true` en `POST /v1/auth/registro`; sin eso, `400 TERMINOS_NO_ACEPTADOS`), y el
backend guarda por usuario la versión aceptada y la fecha (tabla `aceptacion_terminos`, migración V64: una fila por usuario y versión, nunca se borra la
anterior, y la clave foránea a `usuario` no borra en cascada: un `DELETE` de un usuario con aceptaciones falla). Los usuarios que existían antes de V64 no tienen ninguna fila: **la primera vez que entren después de este despliegue el portal les pide
aceptar** antes de dejarlos usar el panel. Lo mismo pasa con los usuarios que crea el alta asistida: el administrador no acepta por el cliente.

**Los textos actuales son un borrador sin revisión legal.** Mientras `REVISADO_POR_LEGAL` sea `false`, las dos páginas lo dicen arriba de todo. Los datos
entre corchetes (`[RUC por completar]`, domicilio, correo de contacto, inscripción del banco de datos ante la ANPD) también son del dueño.

**Cómo publicar una versión nueva** (todo en el mismo commit, y desplegar los dos servicios):

1. Editar el texto en `portal/src/content/legal/terminos.ts` o `privacidad.ts`. Es el único lugar donde viven.
2. En `portal/src/content/legal/version.ts`, poner la fecha del cambio (AAAA-MM-DD) en `VERSION_TERMINOS` o `VERSION_PRIVACIDAD`, y en
   `VERSION_TEXTOS_LEGALES` la más reciente de las dos (`version.test.ts` lo comprueba).
3. Poner esa misma fecha en `app.terminos-version` de `bootstrap/src/main/resources/application.yml` (`TerminosVersionTest` del backend falla si no
   coinciden). No es una variable de entorno a propósito: la versión acompaña al texto, que vive en el código.
4. Al desplegar el backend, **todos los usuarios quedan pendientes** y aceptan la versión nueva la próxima vez que entren (el login, el refresh y
   `GET /v1/auth/me` traen `terminos_pendientes`; aceptar es `POST /v1/auth/terminos`). Una errata que no cambia lo que se acepta no sube la versión.
5. Cuando un abogado revise los textos, `REVISADO_POR_LEGAL = true` en `version.ts` quita el aviso de borrador.

Mientras un usuario tenga los términos pendientes, el backend le niega toda escritura con la sesión del portal fuera de `/v1/auth/**`
(`403 TERMINOS_PENDIENTES`, en `JwtFilter`, igual que `CORREO_SIN_VERIFICAR`): puede mirar y aceptar, no emitir. El portal le muestra la pantalla
de aceptación en lugar del panel, y si una escritura llega a recibir ese 403 el mensaje le pide recargar, que es lo que muestra esa pantalla.

Qué **no** cambia: las API keys de los integradores siguen funcionando con los términos pendientes (el filtro JWT no las mira), y una sesión
de soporte del backoffice mira el panel sin la pantalla de aceptación y no puede aceptar por el cliente (`403 SOPORTE_SOLO_LECTURA`).

**Orden de despliegue al subir la versión.** Desplegar **primero el portal y después el backend**. Con el orden inverso, durante el rato
entre los dos despliegues el backend ya pide aceptar la versión nueva y el portal todavía muestra el texto viejo: se registraría como aceptada
una versión que el usuario no pudo leer. Con el portal primero, lo único que pasa en ese rato es que el texto nuevo se publica antes de
pedirlo. El primer despliegue de #342 es la excepción: el portal necesita el backend con `terminos_pendientes` y `POST /v1/auth/terminos`, así
que va el backend primero (las páginas nuevas del portal no existen hasta entonces, pero tampoco hay usuarios que las hayan aceptado).

**Antes de producción hay que completar** en `version.ts` los datos entre corchetes: RUC y domicilio fiscal del responsable, correo de contacto
para privacidad y derechos ARCO, y la frase de la inscripción del banco de datos ante la Autoridad Nacional de Protección de Datos Personales
(con su código, o retirarla si se decide que no corresponde). Y la revisión legal de los dos textos (#283), que es la que permite poner
`REVISADO_POR_LEGAL = true`. Los textos describen lo que el sistema hace hoy (por ejemplo, que no borra datos de forma automática): si eso
cambia, el texto tiene que cambiar con él.

La constancia de quién aceptó qué y cuándo se consulta en la base:

```sql
SELECT u.email, a.version, a.aceptada_at FROM aceptacion_terminos a JOIN usuario u ON u.id = a.usuario_id ORDER BY a.aceptada_at DESC;
```
