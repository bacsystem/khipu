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

`develop` is the repo's **default branch** on GitHub, so closing keywords (`Closes #N`, `Cierra #N`) close the issue when the PR merges into `develop`: use them only when every acceptance criterion is met, and `Refs #N` otherwise (see «Equipo de agentes», process rules). Only «Create a merge commit» is used; squash and rebase are turned off in the repo settings (Settings → General → Pull Requests).

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

`bootstrap/src/test/.../ArchitectureTest.java` enforces this at build time: `domain` cannot depend on `application`/`adapters`/Spring/SQL; `application` cannot depend on `adapters`; adapter packages cannot depend on each other. `domain` has three packages: `tenant`, `cuenta`, `documento`. `application` has `port.in` (use cases), `port.out` (repository/gateway interfaces), `service` (implementations). Each adapter implements one or more `port.out` interfaces or exposes a `port.in` use case (REST, scheduler). `bootstrap` is the only module allowed to know about all of them — it's where Spring wiring and `application.yml` live. Flyway migrations live in `adapters/out-persistence/src/main/resources/db/migration` (classpath `db/migration`; see «Equipo de agentes», migrations).

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

The team uses the global agents from `bacsystem/skills` (`~/.claude/agents/`); there are no project agents in `.claude/agents/` on purpose (one with the same name would replace the global entirely). This section is what those agents read for khipu; if it contradicts an agent, this section wins. Start the session with `claude --agent tech-lead`. To install the agents, clone `bacsystem/skills` and run its `scripts/instalar-agentes.ps1` (Windows) or `scripts/instalar-agentes.sh` (macOS/Linux); this replaces the old `setup-team.sh`.

**Roles and instances**

| Name | Global agent | Model | Notes |
|---|---|---|---|
| tech-lead | `tech-lead` | opus | main session; does not open code PRs, only reviews them (it may open docs/CLAUDE.md/release PRs) |
| architect | `architect` | opus | |
| po | `po` | sonnet (medium effort) | |
| qa | `qa` | sonnet (medium effort) | |
| devops | `devops` | sonnet (medium effort) | |
| backend-dev, backend-dev-2 | `java-dev` | sonnet | launched as `java-dev` named `backend-dev` / `backend-dev-2` |
| frontend-dev | `nextjs-dev` | sonnet | |

A third backend is another `java-dev` instance (`backend-dev-3`); add it to the ownership table below first. Every developer working in parallel has its own worktree and branch.

**File ownership** (nobody edits another role's files without telling them)

- backend-dev / backend-dev-2: `domain/`, `application/`, `adapters/`; `bootstrap/` only for the wiring a task needs (`AppConfig`, `application.yml`, tests under `bootstrap/src/test`). Default split, which the tech-lead confirms or changes per task and states when assigning (two instances never share a module at the same time):
  - backend-dev: the core and the emission flow: `domain`, `application`, `adapters/in-rest`.
  - backend-dev-2: SUNAT integration and infrastructure: `adapters/out-ubl`, `out-signing`, `out-sunat-soap`, `out-storage`, `out-persistence`, `out-crypto`, `out-mail`, `out-pdf`, `in-scheduler`.
  - Shared, announce to the other backend before opening the PR: `bootstrap` (`AppConfig`, `application.yml`), any `application/port.in`/`port.out` used by more than one adapter, and migrations.
- frontend-dev: `portal/`, except `portal/Dockerfile`.
- devops: `.github/`, `docker-compose*.yml`, `deploy/` (Dockerfiles, `deploy/README.md` and the other runbooks), `portal/Dockerfile`, `.env.example`, `k6/`, `CHANGELOG.md` for releases. Never the application code or migrations.
- architect: `docs/adr/` and `docs/architecture/` (local) and issue/PR comments only.
- po: GitHub issues and local notes in `docs/`. qa: evidence in `qa/<topic>.md` (versioned; pattern `qa/backoffice.md`), never the code or its tests.
- Root `CLAUDE.md`, `portal/CLAUDE.md`, `.claude/`: tech-lead, with the user's approval.

**Flow.** po writes the issue → architect reviews its impact and posts the API/data contract as an issue comment (the binding contract is the committed `portal/src/lib/api/openapi.json`, see «Portal») → tech-lead splits it, reserves migration numbers, and states modules and merge order → developers work in parallel and each opens their own PR to `develop` (`gh pr create --base develop` or `gh stack submit`) → review loop of «Every PR goes through `/pr-review`» → qa validates against the acceptance criteria → the user approves the merge. No task closes without a clean review.

**Process rules (stricter than the global agents)**

- Merge method: always «Create a merge commit», never squash or rebase (both are turned off in the repo settings). When reporting a PR as ready: number, link, merge order, method.
- No push to `develop`/`main`, merge, tag or deploy without the user's explicit approval; pushing your own branch and opening its PR is part of the task. Creating a `v*` tag publishes the images and deploys to production (`imagenes.yml`).
- Issue links: `develop` is the default branch, so a `Closes #N` in a PR into `develop` closes the issue on merge. Write `Closes #N` only when every acceptance criterion is met; otherwise `Refs #N`, saying which criteria stay open. After the merge, the tech-lead validates the criteria (with qa when it applies); an issue still open because of `Refs` is closed by hand (`gh issue close --comment`, citing the PR) once its last criterion is met.
- Evidence in every PR: backend, the full `./gradlew test` output with `BUILD SUCCESSFUL` visible (never `| tail`; read the line); portal, `npm run typecheck`, `npm run lint` and `npm run test` (Vitest) exiting 0. Playwright (`npm run e2e`) runs only before a release, not per issue (see `portal/CLAUDE.md` for how). `pruebas.yml` runs it on PRs to `main`.
- CI as it stands: `ci.yml` on every PR (backend `./gradlew test`; portal typecheck, lint, Vitest; path filter and a `ci` aggregator, #650); `pruebas.yml` on PRs to `main` and on demand (adds e2e and `npm run audit`, #671); `imagenes.yml` on `v*` tags; `k6.yml` and `homologacion.yml` on demand only. Changing what runs on PRs or adding a schedule needs the user's approval.
- Issues (po): Spanish, in the repo's format (see #175, #176, #179): `## Historia de usuario` (Como <tenant/cliente | administrador de la plataforma>…), `## Criterios de aceptación`, `## INVEST` (table, verdict per letter), `## Notas`, and «Parte de la épica #N, sección X». Roadmap in `docs/spec/spec.md`, status in `docs/pending.md`. Priority: impact on issuing and delivering comprobantes and on tenant onboarding first. Any user-visible text covers `es.json` and `en.json`.
- Backend errors: domain exceptions (`DomainException` and kin), translated to HTTP only in `GlobalExceptionHandler` (in-rest).
- Test data: `k6/preparar-tenant.sh` (isolated tenant, self-signed cert), the versioned `adapters/out-signing/src/test/resources/test-cert.p12`, or `docs/cert/certificado-prueba.p12` (local, main checkout only). Never a real tenant's data or certificate, not even in a prompt to an AI.

**Migrations.** Flyway (community: no undo; reverting is a new compensating migration, never an edit to a merged one), in `adapters/out-persistence/src/main/resources/db/migration`, named `Vxx__snake_case.sql`. The tech-lead reserves `Vxx` when assigning the task and states the merge order; the developer does not change it. Last one on 2026-11-13: `V76__indice_sesion_por_vencimiento.sql` (check the folder). Flyway applies them in order when `bootstrap` starts; devops lists them in the release PR.

**Docs.** `docs/` is gitignored: ADRs in `docs/adr/NNNN-title.md` (context, options, decision, consequences, status, date) and diagrams in `docs/architecture/*.mmd`, local; anything the team needs goes also as an issue/PR comment, since `docs/` does not travel to other worktrees. QA evidence in `qa/` is versioned.

**Load and deployment**

- Targets (from `docs/spec/spec.md`, results in `k6/README.md`): emission (facturas and boletas) 50 doc/s with p95 < 500 ms without SUNAT; downloads p95 < 300 ms; boleta voids in the daily summary p95 ≤ 100 ms, ≥ 50/s; portal login and pages p95 < 2000 ms (k6 thresholds). Expected concurrent users in production: TBD: ask the owner.
- Tool: k6 (`k6/*.js`, `k6/preparar-tenant.sh`, `k6.yml` on demand). Every run goes to the single dashboard (`k6/dashboard/agregar.mjs` → `k6/resultados/`), never a standalone report; `k6/README.md` keeps the current results.
- Topology (`deploy/README.md`): Cloudflare (proxy on the own domain) → Railway edge → `portal` (Next.js) → Railway private network (`backend.railway.internal`) → `backend` (Spring Boot/Tomcat) → Railway Postgres plugin, plus the `respaldo` cron (§8). Images on Docker Hub, built by CI; Railway compiles nothing. Replicas: one with `STORAGE_TYPE=fs` (local Volume), several possible with `s3`; scheduled work is already multi-replica safe (outbox `FOR UPDATE SKIP LOCKED`, single alert reservation). How many replicas run in production: TBD: ask the owner. Trusted proxies and keep-alive: §4–§5.

**Secrets.** Never in a commit, PR, issue, message or log: `MASTER_KEY`, `API_KEY_PEPPER` (and their `_ANTERIOR`), `PLATFORM_ADMIN_KEY`, `JWT_SECRET`, SMTP/SUNAT credentials, `RAILWAY_TOKEN`, `HOMOLOGACION_*`, real `.env` files and certificates. They live in Railway's variables (app) and GitHub Environments (CI). Rotation: see «Commands» and `deploy/README.md` §9; it is the user's or devops' job, never a developer's.

**Publication.** The repo is public. Issues, PRs and comments carry no content from `docs/` (pricing and business plan, audits, specs), no customer data or real certificates, and no internal strategy.

**Memory.** See «Project memory: `khipu_memory`»; the tech-lead consults it at the start and logs each closed block.

**Communication.** As in the global agents: announce start, finish, blockers and any change that affects another member, directly to that member.
