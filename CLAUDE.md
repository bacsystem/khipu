# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`khipu` (repo `bacsystem/khipu`; Java package `pe.factura`, Gradle root project and Postgres database still named `factura` for historical reasons) is a multi-tenant SUNAT electronic invoicing (facturación electrónica) system for Peru: a Java/Spring Boot API that builds, signs (XML-DSig), validates (XSD) and submits UBL 2.1 invoices to SUNAT over SOAP, plus a Next.js self-service portal (`portal/`) for tenants to onboard and manage their own account. Two ways to authenticate against the API: `X-Api-Key` (integrators) or JWT (the portal, via `Authorization: Bearer` + `X-Empresa`) — both are accepted on the same endpoints, see `JwtFilter`/`ApiKeyFilter`, with one exception: managing API keys (`POST/GET/DELETE /v1/empresa/api-keys`) requires a portal session (`CuentaActual.exigirSesion`, 403 `REQUIERE_SESION` with an API key), so a leaked key cannot mint or revoke keys.

Design docs live in `docs/` (gitignored, local-only): `docs/spec/spec.md` (architecture + prioritized roadmap), `docs/plan/plan.md` (pricing/business plan), `docs/pending.md` (running status/pending-work report), `docs/superpowers/specs/` and `docs/superpowers/plans/` (per-phase specs and plans, e.g. `10-portal.md` for the portal). Read these for anything beyond what's below — this file intentionally doesn't restate them.

## Commands

### Backend (Java, root of repo)

JDK 21 required; `gradle.properties` does not pin `java.home`, so `JAVA_HOME` must point at a JDK 21 before invoking `./gradlew` (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS).

```bash
docker compose up -d postgres                     # only Postgres is containerized; the app runs via Gradle
./gradlew :bootstrap:bootRun                       # needs MASTER_KEY, API_KEY_PEPPER, PLATFORM_ADMIN_KEY, JWT_SECRET in env (see .env.example)
./gradlew test                                     # all modules; requires Docker (Testcontainers)
./gradlew :adapters:out-signing:test               # single module
./gradlew :bootstrap:test --tests "*ArchitectureTest*"   # single test class (any module)
```

Required env vars beyond `.env.example`: `JWT_SECRET` (≥32 bytes, portal auth) and `PORTAL_URL` (default `http://localhost:3000`, used in recovery emails). **Never rotate `MASTER_KEY`** (decrypts stored PKCS#12 certs / SOL credentials) or `API_KEY_PEPPER` (invalidates all issued API keys) — see `README.md`.

### Portal (Next.js, `portal/`)

```bash
cd portal
npm run dev                                        # http://localhost:3000, needs API_BASE_URL (default http://localhost:8080)
npm run build                                       # do not run concurrently with `dev` against the same .next/ — corrupts the dev server
npm run test                                        # Vitest
npx vitest run src/lib/session.test.ts              # single test file
npm run e2e                                         # Playwright; auto-starts its own dev server on :3100 with mocked backend (see below)
npx playwright test e2e/login.spec.ts               # single spec
npm run lint
npm run generate:api                                # regenerate src/lib/api/openapi.d.ts from a running backend's /openapi.json — NOT currently wired into the app (see below)
```

## Architecture

### Backend: hexagonal, enforced by ArchUnit

Module dependency direction (`settings.gradle.kts`), one-way only:

```
domain  ←  application  ←  adapters/{in-rest, in-scheduler, out-ubl, out-signing,
                                      out-sunat-soap, out-storage, out-persistence,
                                      out-crypto, out-mail, out-pdf}  ←  bootstrap
```

`bootstrap/src/test/.../ArchitectureTest.java` enforces this at build time: `domain` cannot depend on `application`/`adapters`/Spring/SQL; `application` cannot depend on `adapters`; adapter packages cannot depend on each other. `domain` has three packages: `tenant`, `cuenta`, `documento`. `application` has `port.in` (use cases), `port.out` (repository/gateway interfaces), `service` (implementations). Each adapter implements one or more `port.out` interfaces or exposes a `port.in` use case (REST, scheduler). `bootstrap` is the only module allowed to know about all of them — it's where Spring wiring, `application.yml`, and Flyway migrations (`bootstrap/src/main/resources/db/migration`) live.

Key flows to know before touching invoice emission:
- **Numbering**: `JdbcSerieRepository.siguienteNumero` does `SELECT ... FOR UPDATE` on the series row, and the lock is held for the entire rest of the transaction in `EmitirComprobanteService` (UBL generation, XML-DSig signing, XSD validation, storage write) — this is intentional, to avoid a gap in SUNAT's required-sequential numbering if signing/storage fails after a number is assigned. It also means concurrent emission against the *same* series serializes (see `k6/README.md` for a measured example: p95 3.6s at 50 req/s vs 500ms target).
- **Async delivery**: comprobantes that don't send synchronously land in the `outbox` table; `OutboxWorker` (in `adapters/in-scheduler`) retries with exponential backoff.
- **Auth filters** run in a specific order (`JwtFilter` order 8) and defer to each other: `JwtFilter` skips public/admin routes and anything already carrying `X-Api-Key` (`RutaRequest.rutaNormalizada` normalizes `;`/`%xx` bypass attempts before deciding). Routes under `/v1/admin/**` use `PlatformKeyFilter` (`X-Platform-Key`), not JWT or API key.
- Both `X-Api-Key` (tenant-scoped) and JWT+`X-Empresa` (account-scoped, portal) resolve to the same `TenantActual`/tenant context, so most controllers (`FacturaController`, `EmpresaController`, `EmpresasController`) don't need to know which one authenticated the request — the API key management endpoints in `EmpresaController` are the exception (see above).

### Portal: BFF pattern over the same API

Next.js App Router (`portal/src/app`). The browser never receives the JWT:

- `src/app/api/auth/*` route handlers call the Java API server-side and set `factura_access`/`factura_refresh`/`factura_empresa` as `httpOnly` cookies (`src/lib/session.ts`).
- `src/app/api/proxy/[...path]/route.ts` forwards any other `/v1/**` call, attaching `Authorization`/`X-Empresa` from cookies, and retries once on 401 by refreshing (a module-level single-flight guard, `refrescarUnaVez`, avoids two concurrent requests both rotating/invalidating the same refresh token — see the route's own comment for why).
- `middleware.ts` (edge runtime) proactively refreshes an expired access token (decoding its `exp` client-side via `src/lib/jwt.ts`, no signature check — the backend is the actual authority) before Server Components render, since Server Components hit the backend directly with `src/lib/api/*.ts` (not through the proxy) and have no way to react to a 401 mid-render.
- `src/lib/api/*.ts` are hand-written, snake_case-typed clients matching the API's actual runtime JSON shape. **`npm run generate:api`'s output is not adopted**: `springdoc` currently documents `ComprobanteResponse`/etc. fields in camelCase while `jackson.property-naming-strategy=SNAKE_CASE` actually serializes snake_case at runtime — the generated types would be wrong until that's fixed backend-side.
- Test mocking: `src/mocks/` (MSW) is wired in via `instrumentation.ts`, active only when `API_MOCKING=enabled` (set by `playwright.config.ts`'s `webServer.env`) — Playwright e2e runs against a fully in-memory fake of the auth/empresas/series/facturas endpoints, not a live backend.

## Load testing

`k6/` has load-test scripts for `docs/spec/spec.md`'s throughput targets (`k6/emision.js`, `k6/descargas.js`) plus `k6/preparar-tenant.sh` to spin up an isolated test tenant (self-signed cert with the RUC in its `OU`, which is all the backend validates) without touching real data. See `k6/README.md` for current results and the known emission bottleneck above.
