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
- **Healthcheck path**: `/health` (actuator, ya expuesto en `application.yml`). Devuelve `200 UP` sin SMTP
  configurado: el indicador de salud de correo está desactivado a propósito, porque el correo es opcional
  (`MAIL_HABILITADO=false` por defecto, con fallback a log)
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
| `STORAGE_TYPE` | `fs` | Único adapter de storage disponible hoy (`FileSystemDocumentStorage`) |
| `STORAGE_FS_ROOT` | `/data` | Debe coincidir con el mount path del Volume (paso 3) |

Variables opcionales (correo, timeouts SUNAT, outbox): ver `.env.example` en la raíz del repo —
usan default razonable si se omiten.

## 3. Volume para `STORAGE_FS_ROOT`

El adapter de storage es solo filesystem local; sin un Volume, cada redeploy borra los XML/CDR/PDF
generados (evidencia SUNAT). Pasos:

1. En el servicio `backend` → **Volumes** → crear uno nuevo.
2. Mount path: `/data`.
3. Setear `STORAGE_FS_ROOT=/data` (ver tabla arriba).

Limitación conocida: un Volume de Railway es local al contenedor — si el servicio escala a más de
una réplica, cada una tendría su propio storage. No es un problema mientras el backend corra en una
sola réplica.

## 4. Servicio `portal`

- **Root Directory**: `portal`
- **Dockerfile Path**: `Dockerfile` (el que ya existe en `portal/Dockerfile`)
- **Puerto**: tampoco fijar `PORT` — el output `standalone` de Next.js ya lo respeta

### Variables de entorno

| Variable | Valor | Notas |
|---|---|---|
| `API_BASE_URL` | dominio interno de Railway del servicio `backend` (p. ej. `http://backend.railway.internal:8001`) | Usado server-side (Server Components, route handlers) — tráfico dentro de la red privada de Railway |
| `API_PUBLIC_URL` | dominio público del servicio `backend` | Es lo que ve el navegador (snippets de integración, "Try it" de `/developers`) |

## 5. Orden de despliegue sugerido

1. Postgres (plugin).
2. `backend` — esperar que el healthcheck `/health` pase (corre las migraciones Flyway al arrancar).
3. `portal` — depende de que `backend` ya tenga una URL asignada para `API_BASE_URL`/`API_PUBLIC_URL`.
