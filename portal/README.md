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

**Advisories conocidos de la 4.4.0** (`npm audit`, ambos moderados) y por qué hoy no aplican:

- [GHSA-8f24-v5vv-gm5j](https://github.com/advisories/GHSA-8f24-v5vv-gm5j), open redirect (corregido en 4.9.1): está en el middleware
  de next-intl (`createMiddleware`) y su routing por locale. El portal no los usa: el locale sale de una cookie y `middleware.ts` es
  el nuestro, sin nada de next-intl.
- [GHSA-4c35-wcg5-mm9h](https://github.com/advisories/GHSA-4c35-wcg5-mm9h), prototype pollution: solo con
  `experimental.messages.precompile` en el plugin. `next.config.ts` llama `createNextIntlPlugin` sin esa opción.

Mientras siga fijada en 4.4.0: **no adoptar el middleware, el routing ni la navegación de next-intl** (`createMiddleware`,
`defineRouting`, `createNavigation`) ni `precompile`. Para usar cualquiera de ellos, primero subir a ≥ 4.9.1 (y resolver lo de
`@swc/core` en Windows).

Cómo se leen (detalle en `src/i18n/mensajes.ts`):

- Componente (cliente o servidor no async): `const messages = useMessages();` → `messages.auth.login.titulo` (tipado contra es.json).
- Server Component async / `generateMetadata`: `const messages = await getMessages();` (`next-intl/server`).
- `lib/` sin React: recibe `messages: Mensajes` (y `locale`) por parámetro, sin valor por defecto (olvidarlo no compila) y sin
  importar `es.json` (en código de cliente metería el catálogo entero en el bundle). Los tests pasan `es` explícito.
- Datos en el texto: `{nombre}` en el JSON e `interpolar(texto, { nombre })`; texto con negrita o enlace: `useTranslations(...).rich(...)`.
- Errores de la API: `useMensajeError()`. Fechas y montos: `lib/formato.ts` recibe el locale (`es` conserva el formato de siempre:
  hora de Lima, montos como SUNAT, meses de Perú); la zona horaria es siempre `America/Lima`.

Qué viaja al navegador: el `NextIntlClientProvider` serializa en el HTML lo que recibe, así que no recibe el catálogo entero.
`src/i18n/textos-de-cliente.tsx` define por segmento los namespaces que usan los Client Components (`base` en el layout raíz;
`landing` en la portada; `privado`, `admin` y `developers` en sus layouts) y `<TextosDeCliente segmento="…">` los envía. Con eso
`/login` pasó de 318 KB a 39 KB de HTML y la portada de 402 KB a 132 KB, y `developers`/`admin` solo llegan a sus secciones. Los
Server Components siguen leyendo todo con `getMessages()`. Si un Client Component empieza a leer un namespace nuevo, súmalo al
segmento donde se usa: `src/i18n/textos-de-cliente.test.ts` recorre los imports desde cada página y falla si falta.

Guardas:

- `npm run lint` corre la regla `khipu/no-literal-de-ui` (`eslint-rules/no-literal-de-ui.mjs`) sobre `src/app`, `src/components` y `src/lib`.
  Lo que todavía no se migró está en la línea base `eslint-suppressions.json` (supresiones masivas de ESLint), que **solo puede bajar**:
  un literal nuevo falla el lint, y al migrar uno hay que podar la línea base (`npx eslint --prune-suppressions`), si no el lint también falla.
  Nunca se regenera con `--suppress-all` para tapar un literal nuevo: `src/lib/linea-base-de-literales.test.ts` tiene el tope
  (hoy 9 literales en 3 archivos) y falla si la línea base lo pasa. Al migrar literales se baja el tope en el mismo cambio; nunca se sube.
- La regla tiene pruebas propias (`eslint-rules/no-literal-de-ui.test.mjs`, con `RuleTester`, en `npm run test`).
- `npm run build` corre `eslint` antes de `next build` (y así también el `Dockerfile`): `next build` lintea con la API de ESLint,
  que no aplica la línea base, por eso tiene `eslint.ignoreDuringBuilds` y el lint va por la CLI en el mismo script.
- El lint también rechaza importar `@/messages/*.json` desde `src/app`, `src/components` y `src/lib` (fuera de tests): los textos llegan
  por `useMessages()`/`getMessages()` o por parámetro.
- `npm run test` incluye `src/messages/paridad.test.ts` (mismas claves, `{datos}` y etiquetas en cada locale, y ICU válido) y
  `src/i18n/pantallas-en-ingles.test.tsx` (las pantallas migradas se ven en inglés sin restos en castellano). En Vitest, next-intl se
  reemplaza en `vitest.setup.ts` por traductores reales sobre el locale de prueba (`fijarLocaleDePrueba("en")`, ver `src/i18n/prueba.ts`).
- Para ver el portal en inglés: cookie `khipu_locale=en` (p. ej. desde las devtools). `e2e/idioma.spec.ts` lo comprueba de punta a punta.

Los componentes genéricos vienen del paquete `khipu-design-system` (#16), fuera de `src/`: la regla no los cubre y sus textos propios
(el `aria-label` del spinner, «(opcional)» de `Campo`, «Paso n de m», los botones de `StepperNumerico`, el selector de tema…) siguen
en castellano con `en` hasta que el paquete los reciba por props o los traduzca. Lo que el portal les pasa (títulos, etiquetas) sí sale de messages.

Agregar un texto: la clave en `es.json` y en `en.json` (si falta en `en`, `paridad.test.ts` falla) y leerla como arriba.

## Integración continua y vulnerabilidades (CAL-02)

`.github/workflows/pruebas.yml` corre en cada PR y en cada push a `develop`/`main` dos jobs pensados como *required status checks*: **Pruebas / backend**
(`./gradlew test --continue`, con Testcontainers) y **Pruebas / portal** (`npm ci`, `typecheck`, `lint`, Vitest, `npm run audit` y los e2e de Playwright con
un solo worker). Dependabot (`.github/dependabot.yml`) abre una PR semanal por ecosistema (Gradle, npm, GitHub Actions, el Dockerfile del portal), con las
actualizaciones menores agrupadas.

`npm run audit` (`scripts/auditar-dependencias.mjs`) falla con cualquier aviso high/critical de las dependencias de producción, salvo los que
`audit-excepciones.json` acepta con motivo y fecha de vencimiento; una excepción vencida vuelve a fallar. Hoy las cuatro excepciones son los avisos del
`postcss` 8.4.31 que Next 15.5 lleva anidado: solo aplican al procesar CSS ajeno en build y no tienen fix dentro de Next 15. Para aceptar un aviso nuevo,
añádelo con su URL de GitHub Advisory, el porqué y una fecha; para quitar una excepción basta con borrarla.

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

La referencia interactiva de `/developers` (Scalar) llama a la API **directamente desde el navegador**, sin pasar
por el proxy de Scalar: `api-reference.tsx` fija `proxyUrl: ""`, `withDefaultFonts: false` y `telemetry: false`,
porque con `proxyUrl` indefinido Scalar manda cada "Test Request" por `https://proxy.scalar.com` con la API key
real y el cuerpo del ejemplo (DEPS-01 de la auditoría 2026-10-22). Para que esas llamadas funcionen el backend
debe aceptar CORS desde el origen público del portal: `PORTAL_URL` en el backend tiene que ser exactamente la URL
por la que entra el navegador (esquema, host y puerto). `api-reference.test.tsx` falla si alguna de las tres
opciones vuelve a su default.

O desde la raíz del repo: `docker compose up portal`.
