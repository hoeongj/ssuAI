# Task 16 — u-SAINT realtime data tools (Phase 3 third deliverable)

> Created 2026-05-16 after Task 14 (u-SAINT identity SSO) merged. Task 14
> proves we can confirm *who* the user is; Task 16 turns that confirmation
> into actual academic data tools (`get_my_schedule`, `get_my_grades`) by
> retaining the saint portal cookies that Task 14 currently throws away.

## 1. Goal / Scope / Non-goals

### Goal

Ship the first two **authenticated, per-user MCP tools** on top of the
existing Task 14 SSO loop:

- `get_my_schedule` — 시간표 (current term)
- `get_my_grades` — 성적 (cumulative + per-term)

The deliverable validates the **portal-cookie retention pattern** that
every future authenticated u-SAINT tool will reuse (`get_my_attendance`,
`get_my_assignments` if LMS endpoints prove similar, …).

### In scope

- Encrypted, short-TTL server-side store of saint portal cookies
  (`SaintSessionStore`), keyed by ssuAI student id.
- `SaintSsoService` extension — after Phase 2 succeeds, save the portal
  cookies to the store instead of discarding them.
- Two new connectors hitting saint.ssu.ac.kr authenticated pages with
  the stored cookies. Jsoup parse → typed DTOs.
- Two new services (`SaintScheduleService`, `SaintGradesService`) that
  read the connectors and surface a `SaintSessionExpiredException`
  when the stored cookies no longer authenticate.
- Two new MCP tools (`get_my_schedule`, `get_my_grades`) and two new
  REST endpoints (`GET /api/saint/schedule`, `GET /api/saint/grades`).
  Both require the access JWT (`JwtAuthFilter` already drops the
  student id onto the request).
- Cookie-expired → return a structured error code (`SAINT_SESSION_EXPIRED`)
  so the frontend can route the user back to `/auth/login`.

### Non-goals

- ❌ **No action tools.** This task is read-only. 수강신청 / 출결 정정
  같은 write tools 은 Phase 4 별도 task.
- ❌ **No LMS tools.** LMS lives at its own host with its own auth
  (Task 17 후보). `get_my_assignments` 는 LMS 가 와야 됨.
- ❌ **No long-lived session retention.** Portal cookies live ≤ 30
  minutes in the store. After that the user must re-do the SSO loop —
  same trade-off ssutoday accepts.
- ❌ **No persistent storage of school passwords.** SmartID still holds
  them. The store only keeps **post-auth portal cookies**, which are
  themselves short-lived upstream.
- ❌ **No frontend page yet.** The MCP tools + REST endpoints land in
  Task 16; the chatbot (Layer 3) already discovers MCP tools dynamically,
  so it can call `get_my_schedule` the moment the tool is registered.
  A dedicated dashboard card for schedule/grades is Task 18 candidate.

## 2. Why this is different from Task 14

Task 14 was **identity-only**: confirm the user, mint ssuAI JWTs, discard
the saint portal cookies. The discard was intentional — Task 14 spec §1
explicitly says realtime data is deferred.

Task 16 is **the realtime data half**. The single architectural change
needed is "do not discard the Phase 2 portal cookies — store them
encrypted, key by `student_id`, expire after a short TTL." Everything
else (connector pattern, MCP tool pattern, service layer, JWT auth) is
already established.

| Dimension                  | Task 14                       | Task 16                                                       |
|----------------------------|-------------------------------|---------------------------------------------------------------|
| What's stored after auth   | Nothing                       | Portal cookies (encrypted)                                    |
| Where session "lives"      | ssuAI JWT only                | ssuAI JWT + `SaintSessionStore` entry                          |
| TTL                        | 15m access / 14d refresh      | ≤ 30 minutes (portal cookies expire fast upstream anyway)     |
| What expires force-relogs  | Refresh cookie 14d            | Portal cookies — much sooner; user must re-do SSO loop        |
| Data class touched         | Personal (identity)           | **Sensitive** (`docs/security.md` §2 — grades/schedule)       |

## 3. Confirmed/assumed endpoints (spike during PR 16a)

The exact saint URLs for schedule / grades pages are **not yet
spike-confirmed.** Pin them in PR 16a before any parsing code lands.
Reference points to start from:

- ssutoday's `AuthServiceImpl` Phase 2 lands on `/irj/portal` and parses
  identity off `.main_box09` cells — that page does **not** contain the
  full schedule/grade tables. There are deeper pages reachable from the
  portal nav.
- u-SAINT historically routes academic queries through ITS-style URLs
  under `/sap/bc/webdynpro/sap/`. These need a real session capture to
  enumerate.

**Update 2026-05-16 (post Task 14 prod-live)**: `/irj/portal` is a
**SAP NetWeaver frameset wrapper**. Only `<span class="top_user">`
greeting + JS `LogonUid` live on the wrapper. 학적/소속/시간표/성적
모두 `<iframe id="contentAreaFrame">` 안쪽에서 lazy load. This means:

- PR 16a's spike must **drill into the iframe** before capturing URLs.
  In a logged-in browser, open DevTools → Application → Frames, locate
  the contentAreaFrame, and use **Ctrl+U on the iframe URL directly**
  (not on `/irj/portal`). See `MEMORY.md` /
  `learning-browser-view-source-vs-devtools.md` for the F12-vs-Ctrl+U
  pitfall — F12 Elements panel composites all frames into one tree,
  obscuring the wrapper-vs-iframe boundary.
- Expect URLs of shape `/irj/servlet/prt/portal/prtroot/...` or
  `/sap/bc/webdynpro/sap/...` for the deep endpoints. The fixture
  capture must record both the exact URL **and** the iframe src that
  loaded it, since the iframe URL itself encodes navigation state.
- Cookies needed for the deep fetch are the same set Phase 1 already
  collects (the `SaintSessionStore` in PR 16a stores precisely those).
  No new auth round-trip per tool call.

PR 16a includes a **30-min manual spike**: log into u-SAINT with a test
account, navigate to 시간표 + 성적 **through the portal nav into the
iframe**, capture iframe URLs + page HTML, scrub PII (replace 학번/이름/
실 성적 with `20999999`/홍길동/placeholder), commit fixtures under
`backend/src/test/resources/fixtures/saint/`. Capture the **real
response**, not a synthetic shape — Task 14 §13 retrospective documents
why synthetic fixtures cost three rewrites at prod-verify time.

## 4. Architecture

```text
[Chat or REST caller]
  │ (with Authorization: Bearer ssuAI-access-jwt)
  ▼
[JwtAuthFilter] populates ssuai.studentId attribute
  │
  ▼
[SaintScheduleController] reads student id from request attributes
  │ (UnauthorizedException if missing)
  ▼
[SaintScheduleService.fetchSchedule(studentId)]
  │
  │ 1. SaintSessionStore.cookies(studentId) → Optional<PortalCookies>
  │    └─ absent → throw SaintSessionExpiredException → 401 SAINT_SESSION_EXPIRED
  │
  │ 2. SaintScheduleConnector.fetch(portalCookies)
  │    └─ saint returns "login required" HTML → throw SaintSessionExpiredException
  │    └─ saint returns 200 → parse → ScheduleResponse DTO
  │
  ▼
[ApiResponse<ScheduleResponse>]
```

Cookies path on the SSO side stays the same except for the *post-Phase 2*
step:

```text
SaintSsoCallbackController.sso-callback
  ↓
SaintSsoService.authenticate(sToken, sIdno)
  ├─ phase 1 → cookie list
  ├─ phase 2 → portal HTML → UsaintAuthResult
  └─ (new in Task 16) SaintSessionStore.put(studentId, phase1Cookies, now + 30m)
  ↓
StudentService.upsertOnLogin + JwtProvider.issue*
  ↓
302 to /auth/return?ok=1
```

The cookies are saved **only after** identity confirmation succeeds.
There is no scenario where the store holds cookies for a student we
haven't verified.

## 5. Package additions

```
backend/src/main/java/com/ssuai/
├── domain/auth/saint/
│   ├── SaintSsoService.java            # MODIFY — save cookies after phase 2
│   ├── SaintSessionStore.java          # NEW — encrypted in-memory map (Redis later)
│   ├── SaintSessionEntry.java          # NEW — record (encryptedCookies, expiresAt)
│   └── PortalCookies.java              # NEW — record (raw cookie header value)
├── domain/saint/                       # NEW — realtime u-SAINT data
│   ├── controller/
│   │   ├── SaintScheduleController.java
│   │   └── SaintGradesController.java
│   ├── connector/
│   │   ├── SaintScheduleConnector.java
│   │   ├── RealSaintScheduleConnector.java
│   │   ├── MockSaintScheduleConnector.java
│   │   ├── SaintGradesConnector.java
│   │   ├── RealSaintGradesConnector.java
│   │   └── MockSaintGradesConnector.java
│   ├── dto/
│   │   ├── ScheduleResponse.java       # record (term, days[ScheduleEntry])
│   │   ├── ScheduleEntry.java          # record (day, period, course, professor, room)
│   │   ├── GradesResponse.java         # record (terms[TermGrades], cumulativeGpa)
│   │   ├── TermGrades.java             # record (term, courses[CourseGrade], termGpa)
│   │   └── CourseGrade.java            # record (course, credits, grade)
│   └── service/
│       ├── SaintScheduleService.java
│       └── SaintGradesService.java
├── domain/mcp/tool/
│   ├── SaintScheduleMcpTool.java       # NEW — `get_my_schedule`
│   └── SaintGradesMcpTool.java         # NEW — `get_my_grades`
└── global/exception/
    └── SaintSessionExpiredException.java  # NEW — maps to 401 SAINT_SESSION_EXPIRED
```

### `SaintSessionStore` shape

MVP: in-process `ConcurrentMap<String, SaintSessionEntry>` with an LRU
cap (same pattern as `LibrarySessionStore`). Cookies encrypted at rest
with AES-GCM under `SSUAI_CREDENTIAL_ENCRYPTION_KEY` (per-record IV).
Eventual swap to Redis is a single-class refactor — the store interface
exposes only `put(studentId, cookies)` / `cookies(studentId)` /
`invalidate(studentId)`.

Encryption key bootstrap: same pattern as `JwtProvider.buildSigningKey`
— empty env in dev/test = ephemeral random per JVM restart, ≥ 32 bytes
required in prod.

## 6. Open questions

1. **Encryption-key bootstrap in dev** — JwtProvider already supports
   ephemeral fallback. Same fallback for `SSUAI_CREDENTIAL_ENCRYPTION_KEY`
   in dev means a JVM restart silently invalidates stored sessions.
   Acceptable for MVP; document in §7 dev-log entry.
2. **TTL exact value** — 30 minutes is the spec default; we don't know
   how long saint actually keeps portal cookies alive. PR 16a spike
   should measure (capture cookies, wait, replay request, see how long
   until 401). If saint TTL < 30m, the store TTL should match — no point
   keeping a useless entry.
3. **Concurrent SSO from two devices** — second SSO overwrites the
   first device's cookies, but the first device's ssuAI JWT is still
   valid. First-device requests will hit cookies the *second* device
   captured (still works) until those expire too. Document as
   "intended" — single store entry per `student_id`, last writer wins.
4. **Korean major / 한자 / encoding** — saint HTML historically uses
   EUC-KR on some pages. Confirm during the fixture-capture spike;
   `RealDormMealConnector` already has the EUC-KR pattern we'd reuse.
5. **Per-tool rate limit** — chat will call `get_my_schedule` on every
   "내일 1교시 뭐야?" question. Cache (Service-layer, same pattern as
   `WeeklyMealCache`) is the answer. TTL ~ 1 hour for schedule,
   ~ 24 hours for grades (grades only change at term end).
6. **Privacy in chat completion prompts** — the schedule / grades DTOs
   include sensitive content. Tool result compaction
   (`LlmChatService.compactToolResponse`) must scrub or budget
   carefully — Sensitive data crosses ssuAI's trust boundary into the
   LLM provider's prompt, which is a class-level concern. **Decision
   (locked-in)**:
   - **Schedule**: allowed in LLM prompt with compact row format ("월
     1교시 알고리즘 / 정보과학관 401"). 비교적 non-sensitive, 사용자가
     "내일 1교시 뭐야?" 같은 질문에 직접 답을 받아야 가치 있음.
   - **Grades**: **NEVER** in LLM prompt. Period. The chatbot answers
     grade questions via tool **citations** only — "성적 페이지에서
     N과목 확인 가능합니다" + a link, without the content itself.
     `LlmChatService.compactToolResponse` 분기에서 `get_my_grades`
     응답은 `{count: int, link: string}` 만 LLM 에 전달, 본문은 REST
     controller path 로만 노출. 어떤 fallback / debug log / error
     payload 에도 성적 값이 LLM 토큰 스트림에 들어가지 않도록 단위
     테스트로 고정.

## 7. Implementation plan

Three PRs, sequenced.

### PR 16a — Spec + encrypted session store + capture pinning

- 30-min spike: hit u-SAINT logged-in, capture schedule + grades page
  URLs + HTML, scrub PII, commit fixtures.
- `SaintSessionStore` + `SaintSessionEntry` + AES-GCM encryption helper.
- `SaintSsoService.authenticate` modified to call
  `sessionStore.put(studentId, phase1Cookies)` right before returning
  the `UsaintAuthResult`.
- `SaintSessionExpiredException` + `ErrorCode.SAINT_SESSION_EXPIRED` (HTTP 401).
- Unit tests:
  - `SaintSessionStoreTests` — encrypt-decrypt round-trip, TTL expiry,
    LRU cap, invalidate.
  - `SaintSsoServiceTests` happy-path test gets a new assertion: cookies
    landed in store.
- No new MCP tool, no new controller yet. **Lays the foundation.**

### PR 16b — `get_my_schedule`

- `SaintScheduleConnector` (interface), `MockSaintScheduleConnector`,
  `RealSaintScheduleConnector` (Jsoup against the pinned fixture URL).
- `SaintScheduleService.fetchSchedule(studentId)`:
  - reads cookies from `SaintSessionStore`
  - calls connector
  - throws `SaintSessionExpiredException` if either cookies missing or
    connector says "needLogin"
  - returns `ScheduleResponse`
- `SaintScheduleController.GET /api/saint/schedule` (authenticated;
  reads student id from request attrs).
- `SaintScheduleMcpTool.get_my_schedule` (no args; student id from MCP
  client identity once the MCP layer learns to pass it through; see §9
  for the interim).
- Tests:
  - `SaintScheduleServiceTests` — happy path, expired session, missing
    cookies.
  - `SaintScheduleControllerTests` — 200 with envelope, 401 when no
    attribute, 401 with `SAINT_SESSION_EXPIRED` when the session lapsed.
  - `RealSaintScheduleConnectorTests` — fixture-based parse.
  - `MockSaintScheduleConnectorTests`.
- Default config `ssuai.connector.saint-schedule: mock` so prod and CI
  don't hit saint unintentionally.

### PR 16c — `get_my_grades`

Same shape as PR 16b. Default config `ssuai.connector.saint-grades: mock`.

After 16c lands, schedule + grades flip to `real` together via a
follow-up `application-prod.yml` PR — same sequenced-flip pattern Task
15 used for the library book search.

## 8. Security checklist

- [ ] `SSUAI_CREDENTIAL_ENCRYPTION_KEY` env required in prod. Dev/test
      ephemeral fallback acceptable.
- [ ] Portal cookies encrypted at rest (AES-GCM, per-record IV).
- [ ] Cookie store TTL ≤ 30 minutes. Expired entries pruned on read.
- [ ] **Never log** the cookie value, the schedule rows, the grade rows.
      Log only counts/shape: `connector=saint-schedule status=ok rows=12`.
- [ ] `LlmChatService.compactToolResponse` budgets schedule rows
      conservatively; **grades are never included in LLM prompts** —
      tool calls return content to the *controller* path only.
- [ ] Tool-call audit on grades — when chat invokes `get_my_grades`,
      log only "user X requested grades", not what it returned.
- [ ] Test fixtures contain only `20999999` / 홍길동 / placeholder
      grades. Never a real student row.
- [ ] `gitleaks` rule already catches Anthropic keys; verify it doesn't
      false-positive on encrypted-cookie hex blobs in fixtures.

## 9. Stop and flag

Stop and surface to the user if any of the following happens:

- The portal-cookie TTL upstream is much shorter than expected (< 5
  minutes). 30-min store TTL becomes pointless; we'd need either a
  refresh-the-cookies side-channel (does saint expose one?) or accept
  per-tool-call SSO redirects.
- saint's schedule/grades pages require an extra CSRF token / form
  POST that we don't currently send. The 2-phase auth already handles
  the basic session; if a third request shape appears, we need a
  separate spike.
- u-SAINT HTML is delivered via WebDynpro and is structurally
  unparsable without simulating ViewState round-trips. In that case
  Playwright-based scraping replaces Jsoup; reassess scope.
  **Updated likelihood 2026-05-16**: medium-to-high. Task 14 prod-live
  confirmed `/irj/portal` is a SAP NetWeaver frameset, which is
  WebDynpro-adjacent. The deep iframe endpoints may indeed require
  ViewState round-trips. PR 16a spike is the gate — if the captured
  HTML contains `wdr:client_data` / `<input name="sap-..." />` hidden
  state fields, escalate to user before writing parsing code.
- MCP tool argument passing for "current user id" is not actually
  supported by Spring AI's `@Tool` annotations the way Task 14 §6
  assumed. Interim: tool can only be called via the chat path that
  already knows the student id (chat controller sets a thread-local
  before invoking the tool registry). Document this clearly.

## 10. Out-of-scope work this enables

- **Task 17 — LMS connector + `get_my_assignments`**. Different upstream
  (LMS), different auth, but same SaintSessionStore-style pattern.
- **Task 18 — Dashboard cards for schedule / grades**. Once the API is
  there, a "내 시간표" card + a "내 성적" card become trivial.
- **Phase 4 prep — credential-backed action tools**. The cookie-storage
  primitive Task 16 ships is reused by `reserve_library_seat` (Task 13
  follow-up) and any future write tool. The action-tool guardrails
  (dry-run, audit, confirmation) layer on top.

## 11. Relationship to Task 14

Task 14 left a single explicit hook for Task 16: the comment in
`SaintSsoService` that says "Phase 1 cookies are also method-scoped and
discarded after phase 2. Realtime u-SAINT data tools (Task 15+)
re-issue a fresh SSO flow." Task 16 changes that line — cookies are no
longer discarded; they go into the encrypted store.

ADR 0014's "no Spring Security" position still holds: the new endpoints
read the student id off the request attributes that `JwtAuthFilter`
already populates. No new auth layer.

The biggest *behavioral* change is that a successful SSO now leaves
server-side state for up to 30 minutes per student, where Task 14 left
zero. The security model gains a category — sensitive cookies at rest —
that `docs/security.md` §5 was already written for; Task 16 is the
first feature to actually exercise that policy.
