# Deploy del portal (frontend) a Railway

Servicio `portal` del proyecto de Railway. La guía general de los tres servicios está en
[`../README.md`](../README.md); acá están los pasos completos del portal, incluida la **fase 0**: salir con el
landing público y el autoservicio cerrado, sin depender de que el backend esté desplegado.

## 0. Qué se despliega

Next.js 15 (App Router) en modo `standalone`, imagen Docker propia. El navegador nunca recibe el JWT: el portal
habla con la API desde el servidor (BFF) y guarda la sesión en cookies `httpOnly`.

| | |
|---|---|
| Root Directory | `portal` |
| Dockerfile Path | `Dockerfile` (el de `portal/Dockerfile`) |
| Puerto | no fijar `PORT`: Railway lo inyecta y el server `standalone` lo respeta (la imagen ya trae `HOSTNAME=0.0.0.0`) |
| Healthcheck path | `/` |
| Watch paths | `portal/**` — así un push que solo toca el backend no redespliega el portal |

## 1. Fase 0: landing público, autoservicio cerrado

Crear una cuenta arrastra registro → onboarding → carga del certificado PKCS#12 → credenciales SOL, que **todavía
no están certificados** (ver [`../../qa/README.md`](../../qa/README.md)). Hasta que lo estén, el portal sale con el
registro cerrado: el landing y la documentación son públicos y las cuentas se dan de alta a mano.

Con `REGISTRO_ABIERTO=false`:

- el landing no ofrece «Crear cuenta gratis»; con `CONTACTO_URL` definida muestra **«Solicitar acceso»**, y sin ella
  no muestra ningún botón de alta;
- `/registro` responde **404**;
- `POST /api/auth/registro` responde **403 `REGISTRO_CERRADO`** — cerrar solo la página dejaría el endpoint abierto;
- `/login` sigue funcionando, para las cuentas que se dan de alta a mano.

Las tres páginas que leen estas variables se renderizan en cada request (`force-dynamic`), así que **cambiar el flag
en Railway y redeployar basta: no hay que reconstruir la imagen**.

## 2. Variables de entorno

| Variable | Fase 0 (landing) | Con backend | Notas |
|---|---|---|---|
| `REGISTRO_ABIERTO` | `false` | `true` cuando el autoservicio esté certificado | Sin la variable, el default en producción ya es cerrado; se declara explícita para que el estado sea visible en Railway |
| `CONTACTO_URL` | `mailto:...` o la URL de un formulario | — | A dónde lleva «Solicitar acceso». Sin ella el landing no muestra botón de alta |
| `API_BASE_URL` | se puede omitir | `http://backend.railway.internal:$PORT` | URL interna que usa el BFF. Sin backend, `/developers` y `/developers/catalogos` muestran un aviso en vez de fallar |
| `API_PUBLIC_URL` | se puede omitir | dominio público del backend | Es la URL que ve el navegador en los ejemplos de `/developers` |
| `PORTAL_DEV_ORIGINS` | no usar | no usar | Solo para el `dev` local por IP de red |

`NODE_ENV=production` ya viene en la imagen; no hace falta declararla.

## 3. Antes de publicar: verificación local

Con el `Makefile` de la raíz:

```bash
make verificar      # tsc + eslint + Vitest + Playwright (e2e con mocks)
make docker-portal  # construye la MISMA imagen que Railway va a construir
```

Para probar la imagen como saldría en producción, sin backend y con el registro cerrado:

```bash
docker run --rm -p 3200:3000 \
  -e REGISTRO_ABIERTO=false -e CONTACTO_URL="mailto:hola@tu-dominio.pe" \
  khipu-portal
```

## 4. Pasos en Railway

1. **New Service → GitHub Repo** → `bacsystem/khipu` (Railway necesita acceso al repo).
2. **Settings → Source**: Root Directory `portal`, Dockerfile Path `Dockerfile`, Watch Paths `portal/**`.
3. **Variables**: las de la tabla de arriba (en fase 0 alcanza con `REGISTRO_ABIERTO=false` y `CONTACTO_URL`).
4. **Settings → Networking → Generate Domain** (o dominio propio + CNAME al que da Railway).
5. **Deploy**. El build tarda unos minutos (`npm ci` + `next build` dentro de la imagen).
6. Verificar con el checklist de abajo.

## 5. Verificación después del deploy

Reemplazando `$URL` por el dominio del servicio:

```bash
curl -s -o /dev/null -w "%{http_code}\n" $URL/                      # 200
curl -s $URL/ | grep -o "Solicitar acceso"                          # aparece (si definiste CONTACTO_URL)
curl -s -o /dev/null -w "%{http_code}\n" $URL/registro              # 404  ← autoservicio cerrado
curl -s -X POST $URL/api/auth/registro -H "content-type: application/json" -d '{}'   # 403 REGISTRO_CERRADO
curl -s -o /dev/null -w "%{http_code}\n" $URL/login                 # 200
curl -s -o /dev/null -w "%{http_code}\n" $URL/developers            # 200 (con aviso si no hay backend)
curl -s -o /dev/null -w "%{http_code}\n" $URL/comprobantes          # 307 → /login
```

Todo esto quedó comprobado corriendo la imagen localmente antes de publicar estos pasos.

## 6. Abrir el autoservicio más adelante

Cuando registro + onboarding + empresa/certificado/SOL pasen su certificación (`qa/`), basta con poner
`REGISTRO_ABIERTO=true` en Railway y redeployar. No hay cambios de código ni rebuild de la imagen.

## 7. Rollback

Railway conserva los deploys anteriores: **Deployments → el deploy previo → Redeploy**. Como el portal no tiene
estado propio (la sesión vive en cookies y los datos en el backend), volver atrás no pierde nada.
