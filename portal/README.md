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

## Textos e idiomas (#25)

Los textos de la UI viven en `src/messages/<locale>.json` (`es` es la fuente; `en` es el segundo locale), no en los componentes.
Librería: [next-intl](https://next-intl.dev) 4, **sin rutas por idioma**: no hay `/en/...`; el locale sale de la cookie
`khipu_locale` (`es` | `en`) en `src/i18n/request.ts`, y sin cookie (o con otro valor) es `es`. No se mira `Accept-Language`
a propósito: los e2e corren en un Chromium en inglés y el portal no debe cambiar de idioma solo por eso. Se eligió next-intl porque
resuelve lo que el App Router complica (Server Components síncronos y async, `generateMetadata`, Client Components vía
`NextIntlClientProvider` en `app/layout.tsx`) sin tocar el `middleware.ts` de borde, el BFF (`/api/*`) ni las URLs.
Va fijada en `4.4.0` (y `use-intl`, que usan las pruebas, en la misma): desde 4.5 depende de `@swc/core` y su plugin carga el binario
nativo al leer `next.config.ts`, que en Windows falla con `ERR_SWC_NATIVE_CACHE` y tumba `next dev`; no se usa nada de lo que agregó.

Cómo se leen (detalle en `src/i18n/mensajes.ts`):

- Componente (cliente o servidor no async): `const messages = useMessages();` → `messages.auth.login.titulo` (tipado contra es.json).
- Server Component async / `generateMetadata`: `const messages = await getMessages();` (`next-intl/server`).
- `lib/` sin React: recibe `messages: Mensajes` (y `locale`) por parámetro; por defecto castellano.
- Datos en el texto: `{nombre}` en el JSON e `interpolar(texto, { nombre })`; texto con negrita o enlace: `useTranslations(...).rich(...)`.
- Errores de la API: `useMensajeError()`. Fechas y montos: `lib/formato.ts` recibe el locale (`es` conserva el formato de siempre:
  hora de Lima, montos como SUNAT, meses de Perú); la zona horaria es siempre `America/Lima`.

Guardas:

- `npm run lint` corre la regla `khipu/no-literal-de-ui` (`eslint-rules/no-literal-de-ui.mjs`) sobre `src/app`, `src/components` y `src/lib`.
  Lo que todavía no se migró está en la línea base `eslint-suppressions.json` (supresiones masivas de ESLint), que **solo puede bajar**:
  un literal nuevo falla el lint, y al migrar uno hay que podar la línea base (`npx eslint --prune-suppressions`), si no el lint también falla.
  Nunca se regenera con `--suppress-all` para tapar un literal nuevo.
- `npm run test` incluye `src/messages/paridad.test.ts` (mismas claves, `{datos}` y etiquetas en cada locale, y ICU válido) y
  `src/i18n/pantallas-en-ingles.test.tsx` (las pantallas migradas se ven en inglés sin restos en castellano). En Vitest, next-intl se
  reemplaza en `vitest.setup.ts` por traductores reales sobre el locale de prueba (`fijarLocaleDePrueba("en")`, ver `src/i18n/prueba.ts`).
- Para ver el portal en inglés: cookie `khipu_locale=en` (p. ej. desde las devtools). `e2e/idioma.spec.ts` lo comprueba de punta a punta.

Agregar un texto: la clave en `es.json` y en `en.json` (si falta en `en`, `paridad.test.ts` falla) y leerla como arriba.

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
