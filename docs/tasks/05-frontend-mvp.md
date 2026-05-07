# Task 05 — Frontend MVP (Next.js dashboard, local integration)

> Hand-off spec for the implementer (Codex CLI). Read `docs/architecture.md`
> §6, §12, §13 and `docs/security.md` first. This task must not contradict
> them. Reply using the Required Output Format in `AGENTS.md`.

## Goal

Build the first frontend slice — a Next.js dashboard with **four cards**
that consume the existing backend REST API. After this task is merged, a
fresh clone should be able to:

1. Run the backend with `gradlew.bat bootRun` (port 8080, default `dev`
   profile, `mock` connectors) — already works.
2. Run the frontend with `pnpm --dir frontend dev` (port 3000) and see
   four working cards backed by real HTTP calls to the local backend.
3. Pass `pnpm --dir frontend lint` and `pnpm --dir frontend typecheck`.
4. Build for production with `pnpm --dir frontend build`.

This task is **local-only**. Production deployment (Vercel + Railway,
CORS, prod env vars) is **Task 06** — do not start it here.

## Why this slice now

- Backend MVP has 4 stable read endpoints; the architecture decision in
  `docs/architecture.md §12` is locked. Time to prove the contract
  end-to-end across the stack.
- Same pattern as Task 02 / 03: thin happy-path slice first, then layer
  real concerns (deploy, CORS, error UX polish) in follow-ups.
- Establishes the frontend project structure, API client pattern, and
  `ApiResponse<T>` envelope handling — every later UI feature copies this.

## Scope — in

1. New top-level `frontend/` workspace (Next.js 15 App Router + TS,
   pnpm).
2. Tailwind CSS + shadcn/ui initialized.
3. TanStack Query v5 wired through a root `<Providers>` client component.
4. Type-safe API client (`lib/api/`) that mirrors the backend records
   exactly and unwraps `ApiResponse<T>` consistently.
5. Four dashboard cards on a single page (`/`):
   - **Today's cafeteria meal** — `GET /api/meals/today`
   - **Weekly cafeteria meals** — `GET /api/meals/weekly`
   - **Dorm weekly meals** — `GET /api/dorm/meals/this-week`
   - **Campus facility search** — `GET /api/campus/facilities?query=`
6. Loading skeletons, empty states, and error states for every card.
7. `traceId` rendered in a small monospace footer per error (portfolio:
   shows you understand observability) — see §6.
8. Backend CORS config allowing `http://localhost:3000` so the frontend
   can fetch directly without a Next.js proxy. **In dev profile only.**
9. `frontend/README.md` with run / build / lint commands.

## Scope — out (do NOT do here)

- **Vercel deploy** — Task 06. No `vercel.json`, no Vercel project setup.
- **Backend deploy (Railway/Render/Fly)** — Task 06.
- **Production CORS config** — Task 06. Dev-only allowlist for now.
- Authentication, login screens, user profile.
- Chatbot UI — backend has no chat endpoint yet.
- Library cards (book search / seat status) — backend has no
  library endpoints.
- Mobile layout polish beyond "doesn't break on a phone width".
- Dark mode toggle (Tailwind defaults are fine; no theme switcher).
- E2E tests (Playwright). Unit tests for the API client only — see §8.
- Storybook, design tokens beyond Tailwind defaults, custom fonts.
- i18n framework — copy is Korean strings inlined; English mixed where
  natural.

If a requirement seems to need any of the above, stop and flag rather than
expanding scope.

## Stack & rationale

| Choice                | Why                                                                                  |
|-----------------------|--------------------------------------------------------------------------------------|
| Next.js 15 App Router | `docs/architecture.md §12` lock; industry default for React in 2026; SSR optional.   |
| TypeScript (strict)   | Mirrors backend Java records as TS types; catches contract drift at compile time.    |
| Tailwind CSS          | Architecture lock; fastest path to acceptable UI without custom CSS.                 |
| shadcn/ui             | Copy-in components (no runtime dep); portfolio-friendly; standard in 2025–26.        |
| TanStack Query v5     | Architecture lock; standard server-state lib; built-in caching/retry/loading states. |
| pnpm                  | Faster than npm, smaller `node_modules`, common in modern Next.js setups.            |

No Redux, no Zustand, no SWR. Server state lives in TanStack Query;
client state (search input, etc.) is local `useState`. If we ever need
shared client state, revisit then — not now (YAGNI).

## Files to create / modify

```
frontend/
├── app/
│   ├── layout.tsx                  // root layout, fonts, metadata
│   ├── page.tsx                    // dashboard (server component) wrapping client cards
│   ├── providers.tsx               // "use client" — QueryClientProvider
│   └── globals.css                 // Tailwind base/components/utilities
├── components/
│   ├── ui/                         // shadcn primitives (button, card, input, skeleton, badge)
│   ├── meal/
│   │   ├── TodayMealCard.tsx
│   │   └── WeeklyMealCard.tsx
│   ├── dorm/
│   │   └── DormWeeklyCard.tsx
│   ├── facility/
│   │   ├── FacilitySearchCard.tsx
│   │   └── FacilityResultItem.tsx
│   └── shared/
│       ├── ErrorState.tsx          // takes { code, message, traceId }
│       └── EmptyState.tsx
├── lib/
│   ├── api/
│   │   ├── client.ts               // fetchJson<T>() — env base + envelope unwrap
│   │   ├── types.ts                // ApiResponse, ErrorResponse, ApiError
│   │   ├── meal.ts                 // getTodayMeal, getWeeklyMeals
│   │   ├── dorm.ts                 // getDormThisWeekMeal
│   │   └── facility.ts             // searchFacilities
│   └── utils.ts                    // shadcn cn() helper, date helpers
├── hooks/
│   ├── useTodayMeal.ts
│   ├── useWeeklyMeals.ts
│   ├── useDormWeeklyMeal.ts
│   └── useFacilitySearch.ts
├── public/                         // empty for now
├── .env.example                    // NEXT_PUBLIC_SSUAI_API_BASE=http://localhost:8080
├── .env.local                      // gitignored — same content as example
├── .eslintrc.json                  // next/core-web-vitals + typescript
├── .gitignore                      // standard Next.js gitignore
├── components.json                 // shadcn config
├── next.config.ts
├── package.json
├── pnpm-lock.yaml
├── postcss.config.mjs
├── tailwind.config.ts
├── tsconfig.json                   // strict: true
└── README.md

backend/src/main/java/com/ssuai/global/config/
└── WebCorsConfig.java              // dev-only CORS, allows localhost:3000

backend/src/main/resources/
└── application-dev.yml             // (modified if needed) document CORS scope
```

Root `.gitignore` already covers `node_modules/`, `.next/`, etc. — verify
and extend only if missing. Do **not** add `frontend/.env.local` to git.

## Contracts (these TypeScript shapes must match backend records exactly)

`lib/api/types.ts`:

```ts
export interface ApiResponse<T> {
  data: T | null;
  error: ApiErrorBody | null;
  traceId: string;
}

export interface ApiErrorBody {
  code: string;       // e.g. "CONNECTOR_UNAVAILABLE", "CONNECTOR_TIMEOUT", "VALIDATION_FAILED", "INTERNAL_ERROR"
  message: string;
}

// Thrown by fetchJson on non-success or envelope.error !== null.
export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public traceId: string,
    public httpStatus: number,
  ) { super(message); }
}
```

Domain types (mirroring `backend/.../domain/**/dto/*.java`):

```ts
// meal
export type MealType = "BREAKFAST" | "LUNCH" | "DINNER";

export interface MealItem {
  restaurant: string;     // e.g. "학생식당"
  type: MealType;
  corner: string;
  menu: string[];
}

export interface MealClosure {
  restaurant: string;
  reason: string;
}

export interface MealResponse {
  date: string;           // ISO date "YYYY-MM-DD"
  meals: MealItem[];
  closures: MealClosure[];
}

export interface WeeklyMealResponse {
  startDate: string;
  endDate: string;
  days: MealResponse[];
}

// campus
export type CampusFacilityCategory =
  | "CAFETERIA" | "CONVENIENCE_STORE" | "CAFE" | "BOOKSTORE_STATIONERY"
  | "SNACK" | "BAKERY" | "GIFT_SHOP" | "PRINT_SHOP";

export interface CampusFacility {
  id: string;
  name: string;
  category: CampusFacilityCategory;
  categoryLabel: string;
  location: string;
  phone: string;
  extension: string;
  fax: string;
  weekdayHours: string[];
  weekendHours: string[];
  notes: string[];
  aliases: string[];
}

export interface CampusFacilityListResponse {
  facilities: CampusFacility[];
}
```

If a backend record changes, the Java side is the source of truth; update
these TS types in the same PR that changes Java.

### `fetchJson<T>` contract (`lib/api/client.ts`)

- Read base URL from `process.env.NEXT_PUBLIC_SSUAI_API_BASE`. Throw a
  clear error at module load if missing.
- Compose URL as `${base}${path}` where `path` always starts with `/api/...`.
- `Content-Type: application/json` and `Accept: application/json`.
- Parse the JSON envelope:
  - If `response.ok === false` AND body is a valid envelope, throw
    `ApiError(error.code, error.message, traceId, response.status)`.
  - If `response.ok === false` AND body is not a valid envelope, throw
    `ApiError("HTTP_" + status, response.statusText, "", status)`.
  - If `response.ok === true` and `error !== null`, throw
    `ApiError(error.code, error.message, traceId, response.status)`.
  - Otherwise return `data` as `T`.
- No retries here — TanStack Query handles retry policy.

### TanStack Query keys

```ts
["meal", "today"]
["meal", "weekly", startDate ?? "current-week"]
["dorm", "weekly"]
["facility", "search", normalizedQuery]   // " 학생식당 " → "학생식당", lowercase
```

`staleTime`:
- meal/today: until next midnight Asia/Seoul (compute ms from now).
- meal/weekly + dorm/weekly: 5 minutes.
- facility/search: 5 minutes.

`retry: 1` for connector errors. `retry: false` for `VALIDATION_FAILED`
(client bug, retrying won't help) — implement via `retry: (failureCount, error) => { ... }`.

## Component design

### Page layout (`app/page.tsx`)

```
┌─────────────────────────────────────────────────────────────┐
│  ssuAI                                       (top brand)    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌─────────────────────────────┐  │
│  │ 오늘의 학식          │  │ 시설 검색                     │  │
│  │ (TodayMealCard)     │  │ (FacilitySearchCard)        │  │
│  └─────────────────────┘  └─────────────────────────────┘  │
│  ┌─────────────────────┐  ┌─────────────────────────────┐  │
│  │ 학식 주간 식단       │  │ 기숙사 주간 식단              │  │
│  │ (WeeklyMealCard)    │  │ (DormWeeklyCard)            │  │
│  └─────────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

- Two-column grid on `md:` and up, single column on mobile.
- Each card is `<Card>` (shadcn) with header (title + subtitle) and content.
- Cards are independent client components — one card erroring doesn't
  break others.

### Cards — minimum behavior each

- **TodayMealCard** — groups items by `restaurant` then `type`. Closures
  shown as a small badge ("운영 휴무: 사유"). Empty meals list → empty state.
- **WeeklyMealCard** — 5-day strip (Mon–Fri); each day shows meal type
  pills with hover/click revealing menu. Default selected day = today.
- **DormWeeklyCard** — same shape as WeeklyMealCard but reads from
  `getDormThisWeekMeal()`; reuse the inner day-strip presentational
  component (split into `<WeeklyMealStrip days={...} />` if it falls out
  cleanly — no over-engineering).
- **FacilitySearchCard** — debounced text input (300 ms), result list
  with category badge, location, phone, hours. Empty query → empty state
  prompting "검색어를 입력하세요". Empty results → "결과가 없습니다".

## Error & loading UX

- Loading: shadcn `<Skeleton />` matching the card's content shape.
- Error: `<ErrorState code message traceId />`. Renders:
  - Korean message derived from `code` (small switch — see below) plus a
    "다시 시도" button that calls TanStack Query's `refetch()`.
  - `traceId` in `text-xs font-mono text-muted-foreground` at the bottom.
- Code → message map (Korean):
  - `CONNECTOR_TIMEOUT` → "응답이 너무 오래 걸려요. 잠시 후 다시 시도해주세요."
  - `CONNECTOR_UNAVAILABLE` → "외부 서비스가 일시적으로 닿지 않습니다."
  - `VALIDATION_FAILED` → "입력값을 확인해주세요." (no retry button)
  - default → message from server, fallback "알 수 없는 오류가 발생했습니다."
- Empty state: `<EmptyState />` with neutral icon + Korean copy.

`docs/adr/0005-error-ux-by-surface.md` already documents the
backend-side error UX policy. Reuse the same `code` taxonomy here so the
two layers stay consistent.

## Backend changes (CORS)

Create `backend/src/main/java/com/ssuai/global/config/WebCorsConfig.java`:

- `@Configuration`
- `@Profile("dev")` — **dev only**. Production CORS is Task 06's job and
  must be a controlled allowlist, not `*`.
- Implements `WebMvcConfigurer.addCorsMappings`:
  - Path: `/api/**`
  - Allowed origins: `http://localhost:3000` (only)
  - Allowed methods: `GET, POST, OPTIONS` (POST/PUT/DELETE not used yet
    but harmless; if you prefer, restrict to `GET, OPTIONS`)
  - Allowed headers: `*`
  - Allow credentials: `false` (no cookies in MVP)
  - Max age: 3600

Do **not** put CORS config in `application.yml`. Keep it in code so the
profile gating is explicit.

Add a one-line comment in `application-dev.yml` (or this class's javadoc)
pointing to the policy: "production CORS — Task 06".

## Configuration

`frontend/.env.example` (committed):

```
NEXT_PUBLIC_SSUAI_API_BASE=http://localhost:8080
```

`frontend/.env.local` (gitignored, copied from `.env.example` on first
clone). Document this step in `frontend/README.md`.

`next.config.ts`: no rewrites, no proxy. Direct fetch → backend with CORS.

## Test plan

Keep tests minimal and high-signal — this is UI scaffolding.

1. **API client unit tests** (`lib/api/client.test.ts`, vitest):
   - 200 + valid envelope → returns `data`.
   - 200 + `error !== null` → throws `ApiError` with code/message/traceId.
   - 5xx + valid envelope → throws `ApiError` with envelope's code.
   - 5xx + non-JSON body → throws `ApiError("HTTP_500", ...)`.
   - Missing `NEXT_PUBLIC_SSUAI_API_BASE` at module load → clear error.
2. **Type-level smoke test**: a single `.test.ts` that imports each domain
   type and asserts a sample fixture compiles. Catches drift early.
3. **No component tests, no E2E** for this task. Codex must run a manual
   smoke against the running backend before reporting done — see §
   "Verification" below.

Tooling: vitest + @testing-library/react if Codex finds it cheap to wire,
otherwise vitest alone is fine. Prefer vitest over Jest in 2026 — faster,
zero config with Vite.

## Subtask breakdown (for separate commits)

Codex should commit in **this order**, one logical change per commit. The
goal is a portfolio-friendly commit history, not a single "frontend MVP"
mega-commit.

1. `chore(frontend): scaffold Next.js 15 + TS + Tailwind + shadcn`
   - `create-next-app`, init Tailwind, init shadcn, install button/card/
     input/skeleton/badge primitives. Add `.env.example`. README.
2. `feat(frontend): add typed API client and TanStack Query provider`
   - `lib/api/types.ts`, `lib/api/client.ts`, `app/providers.tsx`,
     wire provider in `app/layout.tsx`. Tests for `fetchJson`.
3. `feat(backend): add dev-profile CORS config for localhost:3000`
   - `WebCorsConfig.java`. One-liner test or manual verification note.
4. `feat(frontend): TodayMealCard with loading/error/empty states`
   - First card end-to-end. Establishes the card pattern.
5. `feat(frontend): WeeklyMealCard + DormWeeklyCard sharing day strip`
   - Two cards, factor the shared inner strip if it's clean.
6. `feat(frontend): FacilitySearchCard with debounced query`
   - Last card. Dashboard now full.
7. `docs: add ADR 0006 frontend stack + dev-log entry for Task 05`
   - ADR captures Next.js + Tailwind + shadcn + TanStack Query choice
     with the alternatives considered (Vite SPA, SWR). One dev-log line.

If any subtask is too large to land cleanly, split further — never
combine.

## Verification before reporting done

Codex must do all of these:

1. `gradlew.bat bootRun` (backend, default profile) — verify
   `curl http://localhost:8080/api/meals/today` returns JSON.
2. `pnpm --dir frontend dev` — open `http://localhost:3000`, confirm
   all 4 cards render with real data (mock connectors → deterministic).
3. `pnpm --dir frontend lint` and `pnpm --dir frontend typecheck` clean.
4. `pnpm --dir frontend build` succeeds.
5. `pnpm --dir frontend test` passes (API client + type smoke).
6. Manually check: stop the backend, refresh — every card shows the
   `ErrorState` with a 다시 시도 button (not a blank screen, not a console
   stack trace bubbling to the user).

If any of these fail, fix before reporting done.

## Security notes (from `docs/security.md`)

- All 4 endpoints serve **public** data — no PII, no credentials, no
  cookies. Frontend stores nothing in localStorage / cookies for this task.
- Do not log request/response bodies in the frontend beyond the
  TanStack Query devtools defaults (which are dev-only anyway).
- `.env.local` must stay gitignored. `NEXT_PUBLIC_*` is fine to commit
  in `.env.example` — that's the public API base, not a secret.
- shadcn copies components into the repo. Read what you copy before
  committing — no surprise telemetry, no eval.

## PR description draft (for Codex / the developer to use)

Title: `feat(frontend): MVP dashboard with 4 cards (local integration)`

Body (use as-is or trim):

```markdown
## What
Adds the first frontend slice — a Next.js 15 dashboard with four cards
backed by the existing backend REST API:

- 오늘의 학식 (`/api/meals/today`)
- 학식 주간 식단 (`/api/meals/weekly`)
- 기숙사 주간 식단 (`/api/dorm/meals/this-week`)
- 시설 검색 (`/api/campus/facilities?query=`)

Stack: Next.js 15 App Router, TypeScript (strict), Tailwind, shadcn/ui,
TanStack Query v5, pnpm. Locked in `docs/architecture.md §12`; ADR 0006
captures the rationale.

## Why
Backend MVP has 4 stable read endpoints; this task proves the contract
end-to-end across the stack and establishes the API client pattern,
`ApiResponse<T>` envelope handling, and the per-card loading/error/empty
state pattern that every later UI feature copies.

## Notable decisions
- **Direct fetch + CORS over Next.js rewrites**: explicit CORS config
  (dev-only, `localhost:3000` allowlist) makes the same setup work for
  Task 06's prod deploy without a code change. Rewrites would have hidden
  the CORS layer and added a Vercel-edge proxy hop in prod.
- **TanStack Query over SWR**: architecture lock; richer query
  invalidation primitives we'll want for personalization later.
- **Local-only in this PR**. Vercel + Railway deploy is Task 06 — kept
  out to keep this PR reviewable and avoid bundling unrelated CORS-policy
  decisions.

## Troubleshooting log
- (fill in 1–2 real issues you hit; e.g., "shadcn `init` defaulted to
  `app/globals.css` path that conflicted with the existing one — fixed by
  …", or "TanStack Query v5 needs `<QueryClientProvider>` in a `'use
  client'` boundary; moved into `app/providers.tsx`")

## Test plan
- [ ] Backend running on :8080, frontend on :3000 — all 4 cards load
- [ ] Stop backend, refresh — all 4 cards show ErrorState (not blank)
- [ ] `pnpm --dir frontend lint` ✓
- [ ] `pnpm --dir frontend typecheck` ✓
- [ ] `pnpm --dir frontend build` ✓
- [ ] `pnpm --dir frontend test` ✓ (API client + type smoke)

## Next
- Task 06 — Production deploy (Vercel + Railway/Render, prod CORS, env
  wiring, live demo URL).
```

## Commit guidance

- Commit per subtask in §"Subtask breakdown" — small, focused, each one
  pushable on its own.
- Conventional Commits style (matches existing history).
- `chore(frontend): scaffold ...` for the bootstrap commit; `feat(...)`
  for behavior; `docs: ...` for ADR / dev-log.
- Push frequently — do not stack 7 local commits before the first push.

## ADR & dev-log

- Add `docs/adr/0006-frontend-stack.md` capturing: Next.js App Router
  vs Vite SPA, TanStack Query vs SWR, shadcn vs MUI/Chakra. Follow the
  format of `docs/adr/0001-meal-response-closures.md`.
- Append one line to `docs/dev-log.md`:
  `2026-05-07: Task 05 frontend MVP — Next.js dashboard + 4 cards, local
  integration. <한 줄 인상깊은 트러블슈팅>`.
