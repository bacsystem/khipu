# Portal

Portal web de autoservicio (Next.js 15 + React 19 + TypeScript). Etapa 1: registro/login, empresas, API keys, comprobantes. Ver `docs/superpowers/specs/10-portal.md`.

## Desarrollo

```bash
npm install
npm run dev        # http://localhost:3000
npm run test        # Vitest
npm run e2e          # Playwright (levanta el server de dev automáticamente)
npm run build
```

## Docker

```bash
docker build -t portal .
docker run -p 3000:3000 -e API_BASE_URL=http://host.docker.internal:8080 portal
```

O desde la raíz del repo: `docker compose up portal`.
