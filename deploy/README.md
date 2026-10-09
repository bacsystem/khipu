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
| `MASTER_KEY` | `openssl rand -base64 32` | **No reemplazar a secas**: descifra certificados, credenciales SOL y secretos del 2FA guardados. Para rotarla, §9 |
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

### Qué se sabe de Railway, y qué no

**No hay documentación oficial de este comportamiento y las fuentes se contradicen.** En hilos del foro de Railway (Central
Station) un empleado dice que el borde *borra* el `X-Forwarded-For` del cliente y sobrescribe `X-Real-IP`; en otro, que
`X-Forwarded-For` es lo más fiable y que `X-Real-IP` trae la IP del borde (lo llama un bug); usuarios dicen que el borde *agrega*
sin borrar, que la red interna es `100.0.0.0/8`, y alguno vio solo IPs de Railway y Cloudflare. Por eso el número de saltos no está
fijado en el código: **se mide en tu despliegue**, y puede cambiar si Railway cambia su borde. Repetir la calibración si cambia
algo en el borde, el dominio o Cloudflare.

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

## 9. Rotar `MASTER_KEY` y `API_KEY_PEPPER`

Hace falta si una de las dos pudo filtrarse, o por política. Ninguna se reemplaza a secas: `MASTER_KEY` cifra lo guardado (certificados y su clave,
credenciales SOL, secretos del segundo factor de los administradores, respuestas guardadas del alta asistida) y `API_KEY_PEPPER` es parte del hash
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

