# ssuAI

> AI assistant for Soongsil University students — a single conversational
> front door to scattered campus systems (cafeteria, dorm meals, campus
> facilities; later: LMS, u-SAINT, library).

[![CI](https://github.com/hoeongj/ssuAI/actions/workflows/ci.yml/badge.svg)](https://github.com/hoeongj/ssuAI/actions/workflows/ci.yml)

**Status:** MVP live. 4 read-only REST endpoints, the MCP server, and the
Next.js dashboard are deployed over public HTTPS. The chatbot slice is in
review on `feat/chatbot-slice`.

Live endpoints:
- **Demo:** <https://ssuai.vercel.app/>
- **Chat:** <https://ssuai.vercel.app/chat>
- **Backend:** <https://ssumcp.duckdns.org>
- **MCP SSE:** <https://ssumcp.duckdns.org/sse>

---

## What it does today

| Surface | What you can do |
|---|---|
| **REST API** | `GET /api/meals/today`, `GET /api/meals/weekly`, `GET /api/dorm/meals/this-week`, `GET /api/campus/facilities?query=…` — all return the standard `ApiResponse<T>` envelope with `data` / `error` / `traceId`. |
| **Web dashboard** | Next.js 16 App Router with 4 cards (today's cafeteria, weekly cafeteria, dorm weekly, facility search). Per-card loading / error / empty states. |
| **Chatbot** | `/chat` page plus `POST /api/chat`. Default local/test/prod manifest mode is deterministic mock; production can explicitly enable bounded multi-provider LLM fallback for public campus questions only. |
| **MCP server** | 4 tools (`get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`, `search_campus_facilities`) over SSE. Same Spring Boot process; usable from Claude Desktop, Claude Code, Cursor. See [`docs/mcp-tools.md`](docs/mcp-tools.md). |

No login, no PII, no personalization yet — the MVP is intentionally
read-only and public-data-only. See [`docs/product.md`](docs/product.md)
for the full product brief.

---

## Architecture (one-paragraph version)

Single Spring Boot process serves both **REST** and **MCP** from the same
`Service` layer. **Connectors** (under `domain/<x>/connector/`) are the
only code that knows the shape of school websites; everything above the
connector boundary speaks internal DTOs. Each connector has a `mock` and
a `real` implementation, swappable per-profile via
`@ConditionalOnProperty` — a fresh checkout boots with mocks so no school
site is ever hit during CI. Frontend is Next.js + Tailwind + shadcn +
TanStack Query, talking directly to the backend (CORS-allowlisted in
dev). Full design docs:

- [`docs/architecture.md`](docs/architecture.md) — system overview,
  package layout, response/error contract, connector pattern.
- [`docs/security.md`](docs/security.md) — secrets, logging, CORS,
  outbound HTML treatment, dependency policy.
- [`docs/adr/`](docs/adr/) — every load-bearing decision (currently
  ADRs 0001-0009).

---

## Tech stack

**Backend** — Java 21, Spring Boot 4.x, Spring AI MCP Server (SSE),
Jsoup for cafeteria scraping, Gradle.

**Frontend** — Next.js 16 App Router, TypeScript (strict), Tailwind CSS,
shadcn/ui, TanStack Query v5, pnpm.

**Infra (Task 06+)** — Single-node k3s on Oracle Cloud Free Tier ARM
Ampere A1, Traefik ingress, cert-manager + Let's Encrypt,
GitHub Container Registry. Frontend on Vercel.

Why this stack? — see ADRs
[0006](docs/adr/0006-frontend-stack.md) (frontend) and
[0007](docs/adr/0007-prod-deploy-oracle-k3s.md) (infrastructure).

---

## Local development

### Prerequisites

- JDK 21 (Temurin recommended)
- Node 20+ and pnpm 9+
- Git

### Local pre-commit hook (optional)

CI runs `gitleaks` on every PR and push to `main`. For local checks before
committing, install `lefthook` and `gitleaks`, then run `lefthook install`.

```bash
# macOS
brew install lefthook gitleaks
# Windows
scoop install lefthook gitleaks
# Linux
go install github.com/evilmartians/lefthook@latest
go install github.com/gitleaks/gitleaks/v8@latest

# in the repo
lefthook install
```

### Backend

```bash
# from repo root
gradlew.bat bootRun           # Windows
./gradlew bootRun             # macOS / Linux
```

Defaults to the `dev` profile with `mock` connectors — no school sites
are contacted. Listens on `:8080`.

```bash
curl http://localhost:8080/api/meals/today
```

Local OpenAPI JSON is available at <http://localhost:8080/v3/api-docs>,
and Swagger UI is available at <http://localhost:8080/swagger-ui.html>.

### Frontend

```bash
cp frontend/.env.example frontend/.env.local
pnpm --dir frontend install
pnpm --dir frontend dev
```

Open <http://localhost:3000>.

### Run with real connectors

```bash
SPRING_PROFILES_ACTIVE=dev \
SSUAI_CONNECTOR_MEAL=real \
SSUAI_CONNECTOR_DORM_MEAL=real \
./gradlew bootRun
```

### MCP server

The MCP server exposes 4 tools at `http://localhost:8080/sse`. See
[`docs/mcp-tools.md`](docs/mcp-tools.md) for Claude Desktop / Cursor /
Claude Code wiring (direct SSE or via `mcp-proxy`).

### Chatbot

`/chat` is available in local dev. Backend defaults to `mock` chat mode, so
the page works without any LLM API key.

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"오늘 학식 뭐야?\"}"
```

---

## Project status

| Task | What | Status |
|---|---|---|
| 01 | Backend skeleton | ✅ done |
| 02 | Meal mock API | ✅ done |
| 03 | Real cafeteria connector | ✅ done |
| 04 | Dorm meal connector | ✅ done |
| 05 | Frontend MVP (Next.js dashboard, 4 cards) | done |
| 06 | Production deploy artifacts (k3s + Vercel + cert-manager) | live |
| 07 | ArgoCD GitOps + Helm chart refactor | done |
| 08 | OpenAPI / Swagger UI | done |
| 09 | Secret scanning | done |
| 10 | Frontend test infrastructure | done |
| 11 | Dependabot | done |
| chat | Chatbot slice (POST /api/chat + /chat page) | done |

Specs live in [`docs/tasks/`](docs/tasks/). Per-task narrative is
appended to [`docs/dev-log.md`](docs/dev-log.md) and load-bearing
decisions to [`docs/adr/`](docs/adr/).

---

## Documentation map

- [`docs/product.md`](docs/product.md) — what the product is and isn't.
- [`docs/architecture.md`](docs/architecture.md) — system design.
- [`docs/security.md`](docs/security.md) — security policy and threat
  model.
- [`docs/mcp-tools.md`](docs/mcp-tools.md) — MCP server usage and
  integration recipes.
- [`docs/adr/`](docs/adr/) — Architecture Decision Records (one per
  load-bearing call).
- [`docs/tasks/`](docs/tasks/) — implementation specs per task.
- [`docs/dev-log.md`](docs/dev-log.md) — chronological build log.
- [`docs/troubleshooting/`](docs/troubleshooting/) — postmortems and
  root-cause writeups.
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — top-level troubleshooting
  log shared by Claude and Codex.

---

## Known limitations

- **No auth, no personalization, no DB yet.** All endpoints serve public
  data. Auth + persistence land with the LMS / u-SAINT integrations.
- **Single-region scraping.** Connectors target Soongsil's public sites
  directly; behavior can break if those sites change. The connector
  boundary is designed to absorb that — a connector swap is the only
  fix needed.
- **MVP UI is desktop-first.** Mobile works but isn't polished.
- **Live deployment is still one small VM.** The public demo is suitable for
  portfolio review and low traffic, not high-volume production use.

---

## Contributing / project conventions

This is a one-developer portfolio project, but the conventions are
documented because they matter for how the codebase reads:

- **Dependency updates** are handled by Dependabot for Gradle, npm, and
  GitHub Actions.
- **Conventional Commits** for the message header. See `git log` for
  the prevailing style.
- **One concept per commit, one concept per PR.** See ADR 0007 for the
  reasoning.
- **CLAUDE.md / AGENTS.md** describe the AI-collaboration workflow
  used to build the project (Claude as architect/reviewer, Codex CLI
  as implementer). This is part of the portfolio narrative.

---

## License

MIT — see LICENSE
