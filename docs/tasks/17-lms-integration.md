# Task 17 — LMS integration (Phase 3 fourth deliverable)

> Created 2026-05-16 as the natural follow-up to Task 16 (u-SAINT
> realtime data). Task 16 proves the encrypted-session-store pattern
> against saint; Task 17 reuses it against the LMS host so ssuAI can
> answer "이번 주 과제 뭐 있어?" with real data. LMS lives at its own
> host with its own auth — that auth shape is the central unknown this
> task pins down.

## 1. Goal / Scope / Non-goals

### Goal

Ship the first **authenticated LMS MCP tool**:

- `get_my_assignments` — 내 LMS 과제 (과목 · 제목 · 마감일 · 제출 여부)

This is the Phase 3 deliverable named in
[`docs/vision.md`](../vision.md) §3 Layer 1 and
[`docs/mcp-tools.md`](../mcp-tools.md) §2 "예정 도구". It is also the
last piece before Phase 4 can begin — once LMS data is reachable, the
chatbot can answer combined "내일 1교시 + 이번 주 과제 마감" questions
without any school-site context switching.

### In scope

- Spike: identify the LMS host, the auth mechanism, the assignments
  page URL, and the parse target. Capture fixtures with PII scrubbed.
- `LmsSessionStore` — encrypted, short-TTL, in-process map. Same shape
  as the `SaintSessionStore` from Task 16; key by ssuAI student id.
- `LmsAuthService` — performs the LMS login (whichever shape the spike
  reveals: SmartID SSO callback, school-account form login, or other)
  and parks the resulting session cookies in the store.
- `LmsAssignmentsConnector` (interface + Mock + Real). Real impl reads
  session cookies, fetches assignment list, parses into typed DTOs.
- `LmsAssignmentsService` — reads connector, surfaces
  `LmsSessionExpiredException` on cookie expiry.
- `LmsAssignmentsController` — `GET /api/lms/assignments`. Requires
  the access JWT; `JwtAuthFilter` populates student id.
- `LmsAssignmentsMcpTool` — `get_my_assignments`.
- New error codes: `LMS_AUTH_FAILED`, `LMS_SESSION_EXPIRED` (HTTP 401).
- Config flag `ssuai.connector.lms-assignments: mock` default.

### Non-goals

- ❌ **No LMS write tools.** No `submit_assignment`, no
  `mark_announcement_read`, nothing. Submission is explicitly forbidden
  by [`docs/vision.md`](../vision.md) §5 ("자동 LMS 과제 제출").
- ❌ **No lecture materials or announcements yet.** `get_my_materials`
  and `get_my_lms_announcements` are obvious follow-ups but they each
  imply a different page scrape; they get their own PRs once
  `get_my_assignments` is stable.
- ❌ **No password proxy for "convenience".** If the LMS auth shape
  turns out to be school-account form login, ssuAI **uses the password
  once** to obtain a session cookie and then discards it — not because
  it's nice-to-have but because [`docs/security.md`](../security.md) §5
  forbids the alternative. See §6 open question on session vs
  long-lived credential.
- ❌ **No long-lived sessions.** Same ≤30-minute TTL as Task 16. After
  expiry the user re-does whichever login flow the spike pinned.
- ❌ **No frontend page yet.** Chatbot already discovers MCP tools
  dynamically; a dashboard card for assignments is a Task 18 candidate
  ride-along.

## 2. Why this is different from Task 14 and Task 16

| Dimension                  | Task 14 (u-SAINT identity)    | Task 16 (u-SAINT data)              | Task 17 (LMS)                                   |
|----------------------------|-------------------------------|-------------------------------------|-------------------------------------------------|
| Upstream host              | smartid + saint.ssu.ac.kr     | saint.ssu.ac.kr                     | **unknown — spike output**                      |
| Auth shape                 | SmartID SSO redirect-callback | reuses Task 14 cookies              | **unknown — could be SSO, form, or OAuth-ish**  |
| What's stored after auth   | nothing (identity-only)       | portal cookies (encrypted)          | LMS session cookies (encrypted)                 |
| Connector dependency       | none                          | none                                | possibly Playwright (if JS-heavy login)         |
| Data class                 | Personal (identity)           | Sensitive (schedule, grades)        | Sensitive (assignment titles, due dates, body)  |

The architectural payoff: Task 14 built the SSO-callback machinery,
Task 16 added the encrypted-session-store. Task 17 either reuses
exactly that machinery (if LMS is also SmartID-fronted) or proves the
pattern generalises to a different auth mechanism. Either outcome is
load-bearing for Phase 4 — every future authenticated tool will pick
one of these two shapes.

## 3. The spike (PR 17a)

The exact LMS host, auth mechanism, and assignments page URL are **not
spike-confirmed.** PR 17a is mostly a research PR that ends with
committed evidence.

Candidates to investigate, in rough order of likelihood:

- **SmartLEAD-style LMS on `smartlead.ssu.ac.kr`** — informal name; the
  actual host needs confirmation against the student's own LMS login.
- **`eclass.ssu.ac.kr` or `lms.ssu.ac.kr`** — alternative URL shapes
  that some Korean university LMSs use.
- A SmartID-fronted variant — the LMS login button might bounce through
  `smartid.ssu.ac.kr/Symtra_sso/smln.asp` with a different
  `apiReturnUrl`. If so, Task 14's `SaintSsoCallbackController` pattern
  is reusable; only the `apiReturnUrl` and the post-redirect target host
  change.

Spike deliverables, committed under
`backend/src/test/resources/fixtures/lms/`:

1. The actual login URL (or SSO entry URL).
2. One captured response that proves whether the LMS is HTML + form
   (Jsoup-parseable) or JS-rendered (Playwright-required).
3. The assignments page URL once logged in.
4. One captured assignments-page HTML, PII-scrubbed
   (`20999999`/홍길동/placeholder course names + assignment titles).
5. Header / cookie shape of an authenticated request (so the connector
   knows what to send).

The spike does **not** require any code beyond the empty class skeletons
that PR 17a stages.

## 4. Architecture (sketch — final shape depends on §3)

```text
[Chat or REST caller]
  │ (Authorization: Bearer ssuAI-access-jwt)
  ▼
[JwtAuthFilter] populates ssuai.studentId attribute
  │
  ▼
[LmsAssignmentsController.GET /api/lms/assignments]
  │
  ▼
[LmsAssignmentsService.fetchAssignments(studentId)]
  │
  │ 1. LmsSessionStore.cookies(studentId) → Optional<LmsCookies>
  │    └─ absent → throw LmsSessionExpiredException → 401 LMS_SESSION_EXPIRED
  │
  │ 2. LmsAssignmentsConnector.fetch(lmsCookies)
  │    └─ LMS says "login required" → LmsSessionExpiredException
  │    └─ 200 → parse → AssignmentsResponse DTO
  │
  ▼
[ApiResponse<AssignmentsResponse>]
```

LMS login path (one of):

```text
A. SmartID SSO fronted:
   /api/auth/lms/sso-init → 302 to smartid?apiReturnUrl=/api/auth/lms/sso-callback
   /api/auth/lms/sso-callback (sToken, sIdno)
     → LmsAuthService.authenticate(sToken, sIdno)
         phase 1: SmartID → LMS landing page (reuses Task 14 phase shape)
         phase 2: LMS portal (login confirmed, cookies captured)
     → LmsSessionStore.put(studentId, lmsCookies, now + 30m)

B. School-account form login:
   /api/auth/lms/login (POST: schoolId, schoolPassword)
     → LmsAuthService.formLogin(schoolId, schoolPassword)
         POST LMS login form
         capture session cookies from response
         password becomes local-variable garbage immediately
     → LmsSessionStore.put(studentId, lmsCookies, now + 30m)
```

The store-key remains `ssuai_studentId` (same as `SaintSessionStore`),
not the school id, because the access JWT carries the ssuAI student id
and we want one cookie set per ssuAI user.

## 5. Package additions

```
backend/src/main/java/com/ssuai/
├── domain/auth/lms/
│   ├── LmsAuthService.java               # NEW — login (SSO or form, per §3 spike)
│   ├── LmsSessionStore.java              # NEW — mirrors SaintSessionStore
│   ├── LmsSessionEntry.java              # NEW — record (encryptedCookies, expiresAt)
│   ├── LmsCookies.java                   # NEW — record (raw cookie header)
│   ├── LmsAuthProperties.java            # NEW — base URL, login URL
│   └── (if SSO) LmsSsoCallbackController.java
├── domain/lms/
│   ├── controller/LmsAssignmentsController.java
│   ├── connector/
│   │   ├── LmsAssignmentsConnector.java
│   │   ├── RealLmsAssignmentsConnector.java
│   │   └── MockLmsAssignmentsConnector.java
│   ├── dto/
│   │   ├── AssignmentsResponse.java      # record (courses[CourseAssignments])
│   │   ├── CourseAssignments.java        # record (course, items[AssignmentItem])
│   │   └── AssignmentItem.java           # record (id, title, dueAt, submitted, urlOpaqueId)
│   └── service/LmsAssignmentsService.java
├── domain/mcp/tool/
│   └── LmsAssignmentsMcpTool.java        # NEW — get_my_assignments
└── global/exception/
    ├── LmsAuthFailedException.java       # NEW — 401 LMS_AUTH_FAILED
    └── LmsSessionExpiredException.java   # NEW — 401 LMS_SESSION_EXPIRED
```

`AssignmentItem.urlOpaqueId` is intentionally opaque — the LMS uses
some internal id for assignment URLs. We pass it through to the
frontend so a "원문 보기" link works, but we never log or LLM-prompt
it (it can leak course/section info).

## 6. Open questions (spike resolves most)

1. **SSO vs form login.** If SmartID-fronted, the spec aligns with
   Task 14's callback pattern and ssuAI never sees the school password.
   If form-login, we briefly handle the school password — see
   [`docs/security.md`](../security.md) §5 "Don't proxy". The spec's
   position: even in the form-login branch, the password lives only
   inside `LmsAuthService.formLogin` for the duration of the outbound
   call and is never stored. The user re-enters it after the 30-minute
   session lapses. Trade-off: UX friction vs blast radius. UX wins
   only if the school provides a long-lived session token; otherwise
   the trade-off is the same as Task 16.
2. **Playwright dependency.** Architecture §14 forecasts Playwright
   for LMS. If the LMS is form + HTML response, Jsoup is sufficient
   and we avoid the ~200 MB Playwright install. Decide after the
   spike capture. Default plan: **Jsoup first**, escalate to
   Playwright only on a concrete blocker.
3. **Korean LMS encoding.** Some pages are EUC-KR. Reuse
   `RealDormMealConnector`'s encoding pattern.
4. **Per-course iframe.** Some LMSs scatter assignments across
   per-course iframes (one HTTP request per enrolled course). If true,
   the connector fans out (parallel) and merges; cache TTL becomes
   important. Decide during spike — capture two or three courses, see.
5. **Assignment body in LLM prompts.** `AssignmentItem.title` (e.g.,
   "1주차 과제 — 정렬 알고리즘 비교") is safe-ish in LLM prompts. Full
   assignment **body** (the question text) is **Sensitive** per
   [`docs/security.md`](../security.md) §2 and §4. Decision for
   Task 17: the `get_my_assignments` tool returns titles + dueAt +
   submitted-flag only, never bodies. A future `get_assignment_detail`
   tool can return the body but must use the tool-citation pattern
   (frontend renders it, LLM does not see it).
6. **Encryption key reuse.** `SSUAI_CREDENTIAL_ENCRYPTION_KEY` is
   already introduced by Task 16. Reuse it for `LmsSessionStore`. One
   key per environment; per-record IV.
7. **Per-tool rate limit.** Chat will call `get_my_assignments` often
   ("이번 주 마감 뭐야?", "과제 다 했어?"). Service-layer cache, key by
   student id, TTL ~ 10–15 minutes. Reuses the same cache-aside shape
   as `WeeklyMealCache`.

## 7. Implementation plan

Two PRs, sequenced.

### PR 17a — Spike + scaffolding + LmsSessionStore

- 30-min manual spike (user-driven, same as Task 16's u-SAINT spike).
- Commit fixtures under `backend/src/test/resources/fixtures/lms/`,
  PII-scrubbed.
- `LmsSessionStore` + `LmsSessionEntry` + `LmsCookies` records.
- `LmsAuthProperties` (base URL, login URL — values pinned by spike).
- `LmsAuthFailedException` + `LmsSessionExpiredException` + the two
  `ErrorCode` entries.
- Empty `LmsAuthService` stub with the right method signature for
  whichever auth branch the spike chose (SSO or form). No real network
  call yet.
- Unit tests:
  - `LmsSessionStoreTests` — encrypt-decrypt round-trip, TTL expiry,
    LRU cap, invalidate. Pattern-identical to `SaintSessionStoreTests`.
- No new MCP tool, no new controller. Lays the foundation.

### PR 17b — `get_my_assignments`

- `LmsAuthService.authenticate(...)` real implementation against the
  pinned auth shape.
- `LmsAssignmentsConnector` (interface), `MockLmsAssignmentsConnector`,
  `RealLmsAssignmentsConnector` (Jsoup against fixture-pinned URL).
- `LmsAssignmentsService.fetchAssignments(studentId)`:
  - reads cookies from `LmsSessionStore`
  - calls connector
  - throws `LmsSessionExpiredException` if cookies missing or stale
  - returns `AssignmentsResponse`
  - 10–15 min Service-layer cache, key by student id
- `LmsAssignmentsController.GET /api/lms/assignments` (authenticated).
- `LmsAssignmentsMcpTool.get_my_assignments` (no args; student id
  from request attributes via the same mechanism Task 16 PR 16b uses
  — finalise that mechanism in Task 16 first; see §11 below).
- Tests:
  - `LmsAssignmentsServiceTests` — happy path, expired session, missing
    cookies, cache hit / miss.
  - `LmsAssignmentsControllerTests` — 200 envelope, 401 when no
    attribute, 401 `LMS_SESSION_EXPIRED` when stale.
  - `RealLmsAssignmentsConnectorTests` — fixture-based parse.
  - `MockLmsAssignmentsConnectorTests`.
- Default config `ssuai.connector.lms-assignments: mock`.

After 17b lands, a follow-up `application-prod.yml` PR flips to
`real` — same sequenced-flip pattern Task 15 used for the library
book search.

## 8. Security checklist

- [ ] `SSUAI_CREDENTIAL_ENCRYPTION_KEY` reused (already required in
      prod from Task 16). Dev/test ephemeral fallback OK.
- [ ] LMS session cookies encrypted at rest (AES-GCM, per-record IV).
- [ ] Store TTL ≤ 30 minutes. Expired entries pruned on read.
- [ ] **Never log** cookie values, assignment titles, course names,
      assignment bodies. Log only counts/shape:
      `connector=lms-assignments status=ok courses=4 items=12`.
- [ ] If the spike picks form-login: the school password lives only
      inside `LmsAuthService.formLogin` for the outbound call duration.
      No DB, no log, no audit row contains it.
- [ ] `LlmChatService.compactToolResponse` budget for assignments:
      titles + dueAt + submitted-flag only. **No assignment body** in
      LLM prompts.
- [ ] Tool-call audit on assignments — log "user X requested
      assignments", not what was returned.
- [ ] Test fixtures contain only `20999999` / 홍길동 / placeholder
      course names + assignment titles. Never a real student row.
- [ ] gitleaks rule already catches keys; verify it doesn't
      false-positive on AES-encrypted cookie hex blobs in fixtures.

## 9. Stop and flag

Stop and surface to the user if any of the following happens:

- LMS login requires a CAPTCHA. Headless login is then off-limits;
  fallback is manual cookie paste (same shape as Task 13 PR 13c).
  Reassess scope.
- LMS auth turns out to be a third shape neither SSO nor form login
  (rare but possible — OTP, IP-binding, certificate). Stop and discuss
  before any login code lands.
- LMS session TTL upstream is < 5 minutes. 30-minute store TTL becomes
  pointless. Either find a refresh side-channel or accept per-tool-call
  re-login (heavy UX cost — discuss).
- LMS HTML requires JS execution to populate the assignments list
  (single-page-app style). Escalate to Playwright; add the
  dependency-weight cost to the decision.
- The course-iframe fan-out turns out to need > 10 parallel requests
  per page load. Crawling-etiquette concern
  ([`docs/security.md`](../security.md) §11). Add server-side cache
  with longer TTL.

## 10. Out-of-scope work this enables

- **Task 18 candidate — dashboard card for assignments** (chat already
  works the moment 17b lands).
- **`get_my_materials`, `get_my_lms_announcements`** — same auth, same
  store, different parse target. Each becomes a small follow-up PR.
- **Phase 4 — `set_assignment_reminder`.** Pure ssuAI-side notification
  (cron + push), not an LMS write. Trivial once assignment metadata is
  reachable.
- **Combined chat answers** — "내일 1교시 + 이번 주 과제 마감" becomes
  a single chat turn that fans out across `get_my_schedule` (Task 16)
  and `get_my_assignments` (Task 17).

## 11. Relationship to Task 14 / Task 16

- **Task 14** built the SmartID SSO callback machinery. If the spike
  reveals LMS is SmartID-fronted, Task 17 PR 17a's `LmsAuthService`
  reuses the same `RestClient` shape as `SaintSsoService.authenticate`
  — only the post-SSO landing host changes.
- **Task 16** built `SaintSessionStore` and the
  `ssuai.studentId`-request-attribute convention. Task 17's
  `LmsSessionStore` is structurally identical (same encryption helper,
  same TTL handling, same LRU cap). The two stores stay separate
  classes — different domain, different lifecycle, different
  expiration policy if upstream TTL diverges. A future refactor can
  extract a `CredentialSessionStore<T extends Cookies>` once a third
  store appears (e.g., library auth in Task 13 PR 13c follow-up).
- **MCP student-id passing** — Task 16 PR 16b will finalise how MCP
  `@Tool` methods receive the current student id (Task 16 §9 stop-and-
  flag #4 calls this out as possibly needing a thread-local). Task 17
  PR 17b inherits whatever Task 16 settles. **Do not start PR 17b until
  Task 16 PR 16b's MCP-id mechanism is merged.**

This task does not change any of Task 14's auth surface. It does not
re-issue ssuAI access JWTs; it relies on the JWT auth already in
place. The only architectural delta is one more session-store class
and one more domain package.
