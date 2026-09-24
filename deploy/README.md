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
| `MASTER_KEY` | `openssl rand -base64 32` | **Nunca rotar** una vez emitido el primer comprobante: descifra certificados/credenciales SOL almacenados |
| `API_KEY_PEPPER` | `openssl rand -base64 32` | **Nunca rotar**: invalida todas las API keys emitidas |
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

El portal se puede desplegar **antes que el backend**: sin `API_BASE_URL` el landing, `/login` y la
documentación funcionan, y `/developers` muestra un aviso en vez de fallar.

## 5. Orden de despliegue sugerido

1. Postgres (plugin).
2. `backend` — esperar que el healthcheck `/health/liveness` pase (corre las migraciones Flyway al arrancar).
3. `portal` — depende de que `backend` ya tenga una URL asignada para `API_BASE_URL`/`API_PUBLIC_URL`.
