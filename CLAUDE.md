# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`khipu` (repo `bacsystem/khipu`; Java package `pe.factura`, Gradle root project and Postgres database still named `factura` for historical reasons) is a multi-tenant SUNAT electronic invoicing (facturación electrónica) system for Peru: a Java/Spring Boot API that builds, signs (XML-DSig), validates (XSD) and submits UBL 2.1 invoices to SUNAT over SOAP, plus a Next.js self-service portal (`portal/`) for tenants to onboard and manage their own account. Two ways to authenticate against the API: `X-Api-Key` (integrators) or JWT (the portal, via `Authorization: Bearer` + `X-Empresa`) — both are accepted on the same endpoints, see `JwtFilter`/`ApiKeyFilter`, with one exception: managing API keys (`POST/GET/DELETE /v1/empresa/api-keys`) requires a portal session (`CuentaActual.exigirSesion`, 403 `REQUIERE_SESION` with an API key), so a leaked key cannot mint or revoke keys.

Design docs live in `docs/` (gitignored, local-only): `docs/spec/spec.md` (architecture + prioritized roadmap), `docs/plan/plan.md` (pricing/business plan), `docs/pending.md` (running status/pending-work report), `docs/superpowers/specs/` and `docs/superpowers/plans/` (per-phase specs and plans, e.g. `10-portal.md` for the portal). Read these for anything beyond what's below — this file intentionally doesn't restate them.

## Project memory: `khipu_memory`

**Before starting the day's work or any development, consult `khipu_memory`**, the project's NotebookLM notebook, about the project's status and the topic at hand. Do it through the `gemini-notebook-mcp` MCP server or the `nlm` CLI, and say in one line what was consulted and what came back.

- The notebook belongs to the repo's owner, so this applies where that server is set up. Elsewhere, skip it.
- If the query fails (expired Google session, server down), say so and do not carry on as if it had been consulted. `nlm login` renews the session.
- It holds copies of this repo's docs, so it can lag behind or drop steps. The repo, GitHub and production win; for key rotation or restores, read `deploy/README.md` itself.
- Only public content goes in: never `docs/`, secrets or customer data.
- **Log each work session in the notebook.** When a block of work closes (a merged PR, a release, a decision, the end of the session), add a text source titled `sesion-YYYY-MM-DD`. Use a source, not a note: queries read sources. Later work the same day goes in a new source (`sesion-YYYY-MM-DD-2`), not over the old one. Keep it under ~1000 characters, in this shape:

  ```
  sesion-YYYY-MM-DD[-n] · <topic in a few words>
  Hecho: #PR → #issue, one line each (what changed, not how it was reviewed)
  Decisiones: the decision and why (only what no issue, PR or ADR records)
  Pendiente: what · who · blocked by
  Lecciones: a trap in the environment or the workflow worth remembering (optional)
  ```

  Leave out what GitHub already keeps (test counts, review details, mutations, measurements in the PR body): the PR number is enough.
- **Consolidate weekly.** In the first session of a new week, merge the previous week's `sesion-*` sources into one `semana-YYYY-Www` (ISO week) with what is still true: decisions, pending items still open, lessons. Drop what was closed later, then delete those `sesion-*` sources. Fewer, current sources keep queries from answering with stale pending items.

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

Required env vars beyond `.env.example`: `JWT_SECRET` (≥32 bytes, portal auth) and `PORTAL_URL` (default `http://localhost:3000`, used in recovery emails). **Never replace `MASTER_KEY`** (decrypts stored PKCS#12 certs / SOL credentials / admin 2FA secrets) or `API_KEY_PEPPER` (part of every API key hash) **outright** — rotating them is only safe with the `MASTER_KEY_ANTERIOR` / `API_KEY_PEPPER_ANTERIOR` procedure in `deploy/README.md` §9.

### Portal (Next.js, `portal/`)

Commands, the BFF pattern, the OpenAPI contract guard, i18n, test mocking and the design-system rules are in `portal/CLAUDE.md`, which Claude Code loads when a file under `portal/` is opened. Read it before touching the portal, and read it explicitly before reviewing a portal PR: `gh pr diff` does not open files, so it does not load it.

**Git hooks (husky).** `npm install`/`npm ci` in `portal/` runs `prepare` (`cd .. && husky || true`, so an install without devDependencies, e.g. `npm ci --omit=dev`, does not fail), which points `core.hooksPath` at `.husky/_` in the repo root. That setting lives in the shared `.git/config`, so it applies to every worktree that has run an install. `.husky/pre-commit` only acts when the commit includes `portal/**/*.{ts,tsx,js,mjs}`: it runs eslint on those files and `tsc --noEmit` on the project (~10 s). Vitest and e2e are not part of it. `HUSKY=0 git commit …` skips the hooks. Without `.git` (the portal Docker build) `prepare` prints a notice and exits 0.

### Branching model: GitFlow (simplified)

Two long-lived branches, both protected on GitHub (1 required approving review, no force-push, no deletion):

- **`main`** — production. Only receives merges from `develop` (a release) or from a `hotfix/*` branch. Every merge into `main` gets tagged `vX.Y.Z` afterward (see `git-flow` skill, step 10).
- **`develop`** — integration branch. Default base for all feature/fix work, including `gh stack` stacks (see below). Runs ahead of `main` with merged-but-unreleased work.

No formal `release/*` branches — a release is just a PR with base `main` and head `develop` (or a fast-forward) once `develop` is stable enough to ship. `hotfix/*` branches off `main` for urgent prod-only fixes, PRs back into `main` (tag after merge), and then get merged into `develop` too so it doesn't regress.

The `git-flow` skill detects `develop` automatically and uses it as the PR base instead of `main` — no need to pass it explicitly.

Closing keywords (`Closes #N`, `Cierra #N`) only auto-close an issue when the PR merges into the **default branch** (`main`). A PR merged into `develop` leaves its issue open until the release PR reaches `main` — close it by hand if it shouldn't wait for the release.

### Every PR goes through `/pr-review`

**Rule: right after a PR is opened, run `/pr-review <number>` on it**, with no exceptions: code, docs, config, a one-line change or a PR you opened yourself. Do not tell the user a PR is ready, or ask them to merge it, before that review is clean. Working alone, you review and fix. With the agent team (see «Equipo de agentes»), the loop is:

1. The developer opens the PR and asks the tech-lead for a review.
2. The tech-lead runs `/pr-review <number> --comment` and **publishes every report (review and re-review) as a comment on the GitHub PR**. This rule is the owner's standing consent to post reviews on this repo's PRs, so no confirmation is asked per comment.
3. The tech-lead messages the developer who owns the PR with the PR link and the finding IDs. The tech-lead does not fix them.
4. The developer fixes every finding in the same branch, answers each one, and **asks the tech-lead for a re-review** once all of them are fixed. The tech-lead does not re-review on its own initiative.
5. Repeat from 2 until the verdict is clean. Only then does the tech-lead tell the user the PR is ready to merge.

- Fix every finding (MENOR included) in the same branch, right away and without asking for permission, then the review runs again until the verdict is APROBADO with no findings (in the team, the tech-lead runs it when the developer asks, never the developer on their own PR). Out-of-scope follow-ups are not fixed there: open an issue for them.
- Report the findings, how each was fixed and the final verdict, and list the PRs left open and ready to merge (number and link), in merge order.
- A PR still open without review gets reviewed as soon as it is noticed, and its findings are fixed in its own branch like any other. A PR already merged without review gets reviewed afterwards, and its findings go in a follow-up PR.
- If the `/pr-review` skill is not available in the session, stop and say so; do not replace it with a manual review.

### Pull requests: stacked, via `gh stack`

Work that spans several PRs is stacked, not chained by hand. GitHub's official extension manages it (`gh extension install github/gh-stack` — per machine, not a repo dependency):

```bash
gh stack init feat/base                 # new stack on top of develop; adopts the branch if it already exists
gh stack add feat/encima -m "mensaje"   # next layer, based on the previous one
gh stack view                           # where each branch sits
gh stack submit                         # push every branch and open/update its PR, each based on the one below
gh stack sync --prune                   # after a merge: rebase the rest onto develop, retarget bases, drop merged branches
gh stack merge                          # atomic bottom-up merge of the whole stack (or up to a chosen PR)
```

The point is `sync`: when the bottom PR merges, it cascade-rebases the branches above it and retargets their bases. Doing that by hand is what made the #102 → #103 → #104 chain expensive — every merge meant retargeting the next PR and resolving conflicts against a stale branch.

Two things worth knowing before running it:

- **`sync` force-pushes** every branch in the stack (`--force-with-lease --atomic`). Commit or stash first; it rewrites the branches you have checked out.
- **The stack lives in `.git/gh-stack`**, local and uncommitted — it is not shared through the repo. On another machine or a fresh clone, rebuild it with `gh stack checkout <PR#>` instead of `init` (which would create a second stack over the same branches).

Merging the bottom PR from the GitHub UI still works; just run `gh stack sync --prune` afterwards so the rest of the stack catches up. Each PR in a stack keeps the usual convention: one issue, reviewed before merge.

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
- **Auth filters** run in a specific order (`JwtFilter` order 8) and defer to each other: `JwtFilter` skips public/admin routes and anything already carrying `X-Api-Key` (`RutaRequest.rutaNormalizada` normalizes `;`/`%xx` bypass attempts before deciding). Routes under `/v1/admin/**` use `AdminAuthFilter` (order 5), not the tenant `JwtFilter`/`ApiKeyFilter`: it accepts either `X-Platform-Key` (ops tooling) or the backoffice's own `Authorization: Bearer` JWT (`PLATFORM_ADMIN`, minted by `JwtAdministradorTokenEmisor` — a distinct token type from the tenant one, with no `cuenta`/`rol` claims, so a client JWT can never pass as an admin session or vice versa). `POST /v1/admin/auth/login` is the one admin route with no credential requirement, since the administrator has neither yet.
- Both `X-Api-Key` (tenant-scoped) and JWT+`X-Empresa` (account-scoped, portal) resolve to the same `TenantActual`/tenant context, so most controllers (`FacturaController`, `EmpresaController`, `EmpresasController`) don't need to know which one authenticated the request — the API key management endpoints in `EmpresaController` are the exception (see above).
- **Roles (#21)**: a portal user is `ADMIN`, `EMISOR` or `LECTURA`. `JwtFilter` reads the user's current role from the database on every non-`/v1/auth/**` request and checks it against `PermisoDeRuta` (method + normalized route → `Permiso`; any unlisted write is ADMIN-only), answering `403 ROL_INSUFICIENTE`. A new portal endpoint must be classified in `PermisoDeRutaTest.ESPERADO` or that test fails. API keys are role-free (they belong to the company and only an ADMIN can mint them).

### Portal

See `portal/CLAUDE.md`. One rule crosses into the backend: the committed `portal/src/lib/api/openapi.json` must match what the API serves (`DeveloperPortalE2ETest` fails otherwise), so a backend change that moves the contract regenerates it in the same PR (`npm run generate:api` in `portal/`, backend running with `OPENAPI_RUTAS_OCULTAS=` empty) and fixes what `npm run typecheck` flags; two nested records with the same simple name collapse into one OpenAPI schema (`NombresDeEsquemasOpenApiTest`), fixed with `@Schema(name = "…")`.

## Load testing

`k6/` has load-test scripts for `docs/spec/spec.md`'s throughput targets (`k6/emision.js`, `k6/descargas.js`) plus `k6/preparar-tenant.sh` to spin up an isolated test tenant (self-signed cert with the RUC in its `OU`, which is all the backend validates) without touching real data. See `k6/README.md` for current results and the known emission bottleneck above.

## Equipo de agentes
Roles (definidos en .claude/agents/): po, architect, tech-lead (sesión principal), backend-dev, backend-dev-2, frontend-dev, devops, qa.
Flujo: po escribe issues de GitHub (historia de usuario + criterios de aceptación, el formato que ya usa el repo) -> architect revisa su impacto y publica el contrato de API/datos como comentario en el issue, además de sus ADR en docs/adr/ (local) ->
tech-lead reparte tareas -> backend-dev y backend-dev-2 (domain/application/adapters, módulos distintos), frontend-dev (portal/) y devops (CI, Docker, entornos y releases) trabajan en paralelo, cada uno dueño de sus archivos ->
qa valida contra los criterios de aceptación -> cada developer crea su PR a develop -> tech-lead revisa todo PR (sin excepción) con el skill /pr-review y publica el reporte en el PR ->
si hay hallazgos, los corrige el developer autor y el tech-lead vuelve a revisar, hasta que quede limpio.
Ninguna tarea se cierra sin ese review limpio. El merge requiere aprobación del usuario. Un PR mergeado a develop no autocierra su issue (GitHub solo lo hace contra main): el tech-lead lo cierra a mano tras validar.
Comunicación siempre activa: cada integrante avisa al empezar, al terminar, al bloquearse y cuando su cambio afecta a otro.
Respetar las convenciones de este archivo (GitFlow, Conventional Commits, arquitectura hexagonal, khipu-design-system). Sin push ni deploy sin aprobación del usuario.
