# khipu · Portal

Portal web de autoservicio (Next.js 15 + React 19 + TypeScript). Etapa 1: registro/login, empresas, API keys, comprobantes. Ver `docs/superpowers/specs/10-portal.md`.

## Desarrollo

```bash
npm install
npm run dev        # http://localhost:3000
npm run test        # Vitest
npm run e2e          # Playwright (levanta el server de dev automáticamente)
npm run build
```

## Acceso desde otro equipo de la red local

Los servers de dev escuchan en todas las interfaces, así que basta con entrar por la IP de esta máquina
(`ipconfig getifaddr en0`), p. ej. `http://192.168.18.134:3000`. Para que Next acepte ese origen y para que
`/developers` (Scalar corre en el navegador) y los ejemplos de integración apunten a una API alcanzable desde
el otro equipo:

```bash
# backend (raíz del repo): CORS y enlaces de recuperación con la URL por la que entra el navegador
PORTAL_URL=http://192.168.18.134:3000 ./gradlew :bootstrap:bootRun

# portal
PORTAL_DEV_ORIGINS=192.168.18.134 API_BASE_URL=http://192.168.18.134:8001 npm run dev
```

Las cookies de sesión solo llevan `secure` en producción (`next start` / Docker), donde hace falta HTTPS;
en dev funcionan por HTTP plano.

```bash
docker build -t portal .
docker run -p 3000:3000 -e API_BASE_URL=http://host.docker.internal:8001 -e API_PUBLIC_URL=http://localhost:8001 portal
```

`API_BASE_URL` es la URL con la que el contenedor habla con la API; `API_PUBLIC_URL` la que se muestra al usuario
(ejemplos de integración de API keys y "Try it" de `/developers`), que debe ser alcanzable desde su navegador.

O desde la raíz del repo: `docker compose up portal`.
