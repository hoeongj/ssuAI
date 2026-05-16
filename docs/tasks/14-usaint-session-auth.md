# Task 14 — u-SAINT session auth (Phase 3 second deliverable)

> Created 2026-05-15 alongside Task 13. Where Task 13 wires the library
> auth path (oasis / Pyxis, session-cookie based), Task 14 wires the
> u-SAINT path (saint.ssu.ac.kr through smartid.ssu.ac.kr SSO,
> one-shot-token based). Together they unlock Phase 3's personal-data
> tool plane.

## 1. Goal / Scope / Non-goals

### Goal

Capture an authenticated u-SAINT identity by porting **ssutoday**'s
React Native SSO-redirect pattern to a pure web frontend. The ssuAI
backend becomes the redirect target itself, so the SmartID flow ends
**on our origin** with `sToken` + `sIdno` in the query string —
removing the entire same-origin-policy class of problems that killed
Task 13's popup approach.

The deliverable is ssuAI's first **authenticated user system**:
after this task, ssuAI has its own JWT-issued users keyed by
SSU student id, and downstream tools can branch on "who is the current
ssuAI user."

### In scope

- Frontend "유세인트로 로그인" CTA. On click, the browser navigates to
  `smartid.ssu.ac.kr/Symtra_sso/smln.asp?apiReturnUrl=<our backend
  callback>`.
- Backend `GET /api/auth/saint/sso-callback?sToken=...&sIdno=...` —
  same-origin to us, no cross-origin reads, no SOP issue.
- Backend `SaintSsoService` does ssutoday's documented 2-phase auth:
  - **Phase 1**: GET `saint.ssu.ac.kr/webSSO/sso.jsp?sToken=...&sIdno=...`
    with `Cookie: sToken=...; sIdno=...`. Verify response body contains
    `location.href = "/irj/portal"`. Collect every `set-cookie` from the
    response — those are the real portal session cookies.
  - **Phase 2**: GET `saint.ssu.ac.kr` portal with the cookies from
    phase 1. Jsoup parse 학번 / 이름 / 소속 / 학적상태.
- `Student` JPA entity (first non-mock data row in ssuAI) and a
  `StudentRepository`. H2 for dev, deferred-to-Postgres for prod when
  user count grows. Keys: `studentId` (primary), `name`, `major`,
  `enrollmentStatus`, `createdAt`, `lastLoginAt`.
- `JwtProvider` — issues ssuAI's own short-lived access JWT + refresh
  token. The frontend stores access JWT in memory, refresh in
  HttpOnly Secure SameSite=Lax cookie. (We finally need real auth
  infra; this task is where it lands.)
- After SSO success, backend `302 → ssuai.vercel.app/auth/return?ok=1`;
  refresh cookie set. Frontend rehydrates session, shows the user's
  name on the dashboard.
- ADR 0014 documenting the redirect-callback pattern and why it
  works on web (we control `apiReturnUrl`, so there is no second
  origin in the loop after SmartID).

### Non-goals

- ❌ **Realtime u-SAINT data fetch** — 성적 / 시간표 / 출결 / 수강신청 etc.
  Identity-only here. Realtime data tools are Task 15+ and require
  separate session-handling decisions (the portal cookies expire fast
  and we are NOT going to persist sToken/sIdno; see §7 #2).
- ❌ Library data — that is Task 13's domain. The two systems share
  *nothing* on the auth path (Pyxis is its own auth island).
- ❌ Server-side credential storage. Passwords never enter ssuAI.
  Same property as ssutoday — only short-lived SSO tokens cross our
  trust boundary, and even those we throw away after Phase 2.
- ❌ Persistent SSO token storage. `sToken` / `sIdno` are one-shot;
  used to fetch identity in a single backend call, then discarded.
- ❌ Phase 4 write tools (수강신청, …). Different problem space.

## 2. Why this is different from Task 13

| Dimension | Task 13 (library / Pyxis) | Task 14 (u-SAINT / SmartID) |
|---|---|---|
| **Auth target** | `oasis.ssu.ac.kr/login` direct username/password | `smartid.ssu.ac.kr` central SSO |
| **Token shape** | Long-lived `ssotoken` session cookie | Short-lived `sToken` + `sIdno` one-shot tokens |
| **Token usage** | Sent on every upstream API call (data access) | Used **once** to confirm identity, then discarded |
| **Where token lives in ssuAI** | `LibrarySessionStore` (encrypted, persistent) | Nowhere — used inline during the SSO callback, never stored |
| **Capture mechanism** | Manual paste (SOP blocks popup capture) | SSO redirect callback to our backend (we control apiReturnUrl, no SOP issue) |
| **What we end up with** | Stored ssotoken → real seat data fetches | ssuAI JWT for the user → ssuAI-side user system |
| **Reference impl** | None (we are the first SSU project to try this on web) | `jonghokim27/ssutoday`'s `LoginScreen` + `AuthServiceImpl.uSaintAuth` |

## 3. Reference — `jonghokim27/ssutoday`

ssutoday is React Native, but the auth dance is web-protocol-shaped
and ports directly. The relevant files:

- `packages/app/src/screens/LoginScreen.js` — RN WebView intercepts
  the redirect at `https://saint.ssu.ac.kr/webSSO/sso.jsp?sToken=...&sIdno=...`
  via `onLoadStart`, extracts the two query params, POSTs them to its
  Spring backend's `/student/login`.
- `packages/server/spring-backend/src/main/java/.../service/AuthServiceImpl.java` —
  the 2-phase server-side flow. Reproduce verbatim in our backend
  (Jsoup, RestClient, same Cookie shape).
- `packages/server/spring-backend/src/main/java/.../controller/StudentController.java#login` —
  the controller endpoint that orchestrates auth → user upsert → JWT.

**Crucial adaptation**: ssutoday's `apiReturnUrl` is
`https://saint.ssu.ac.kr/webSSO/sso.jsp` because RN WebView can read any
URL its `onLoadStart` fires on, regardless of origin. Web cannot —
SOP blocks reading the query string of an `oasis`/`saint` URL from
`ssuai.vercel.app`. The web-port trick is to make **our backend** the
`apiReturnUrl`. SmartID then 302-redirects the browser to
`https://api.ssumcp.duckdns.org/api/auth/saint/sso-callback?sToken=...&sIdno=...`
and our Spring controller reads the query params directly. No SOP, no
interceptor needed, no native code.

**Key property preserved**: the user's SSU password never crosses
ssuAI's trust boundary. SmartID handles it on its own login page. We
see only short-lived tokens, and even those we discard after the 2-phase
identity confirmation.

## 4. Confirmed endpoints (verify in spike before implementing)

| Path | Status | Notes |
|---|---|---|
| `https://smartid.ssu.ac.kr/Symtra_sso/smln.asp` | GET | SmartID SSO login entry. Query param `apiReturnUrl` controls where it redirects post-success. **Spike**: confirm whether arbitrary apiReturnUrl is accepted or whitelisted. |
| `https://saint.ssu.ac.kr/webSSO/sso.jsp?sToken=...&sIdno=...` | GET (server-side) | Phase 1 endpoint. Response body must contain `location.href = "/irj/portal"` for success. Verbatim from ssutoday `AuthServiceImpl`. |
| `https://saint.ssu.ac.kr/irj/portal` (or whatever `uSaintPortalUrl` resolves to) | GET (server-side) | Phase 2 endpoint, with cookies from phase 1. Returns the HTML with `main_box09` / `main_box09_con` elements ssutoday parses. |

We also need:

- **Our** `GET /api/auth/saint/sso-callback?sToken=...&sIdno=...` —
  served from `api.ssumcp.duckdns.org`. CORS not relevant (SmartID
  does a 302, not a CORS-fetch). HTTPS mandatory because SmartID will
  refuse to redirect to a plaintext URL.

## 5. Architecture

```text
Browser                          ssuAI backend              SmartID                          saint.ssu.ac.kr
───────                          ─────────────              ───────                          ─────────────
[User clicks 유세인트 로그인]
  │
  │ window.location.href =
  │   smartid…/smln.asp?
  │   apiReturnUrl=https%3A%2F%2F
  │     api.ssumcp.duckdns.org%2F
  │     api%2Fauth%2Fsaint%2F
  │     sso-callback
  ▼
[SmartID 로그인 페이지]
[User enters SSU id + pw]
   │
   │  (SmartID processes — ssuAI never sees pw)
   ▼
   302 →
   https://api.ssumcp.duckdns.org/api/auth/saint/sso-callback
       ?sToken=...&sIdno=...
   ▼
                                 [SsoCallbackController]
                                  reads sToken, sIdno
                                  from @RequestParam
                                  │
                                  │ Phase 1: GET saint.ssu.ac.kr/webSSO/sso.jsp
                                  │          Cookie: sToken=...; sIdno=...
                                  └──────────────────────────────────────────► [saint validates]
                                                                                returns Set-Cookie:
                                                                                portal session
                                  ◄──────────────────────────────────────────
                                  │ body contains location.href=/irj/portal ?
                                  │
                                  │ Phase 2: GET saint…/irj/portal
                                  │          Cookie: <portal session>
                                  └──────────────────────────────────────────► [saint serves portal HTML]
                                  ◄──────────────────────────────────────────
                                  │ Jsoup parse 학번 / 이름 / 소속
                                  │
                                  │ Student upsert + JWT issue
                                  │
                                  │ 302 → https://ssuai.vercel.app/auth/return?ok=1
                                  │       Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax
                                  ▼
[ssuai.vercel.app/auth/return]
   │
   │ POST /api/auth/refresh
   │ → access JWT in memory
   ▼
[Dashboard shows "안녕하세요 OOO 학생"]
```

`sToken` and `sIdno` are alive only inside the
`SsoCallbackController` invocation. Once Phase 2 returns and we have
the student row, **both tokens are local variables that go out of scope
and become GC eligible**. They are never written to disk, logs, or
session storage.

## 6. Package additions

```
backend/src/main/java/com/ssuai/
├── domain/auth/                               # already created in Task 13
│   ├── controller/
│   │   ├── LibrarySessionController.java      # existing (Task 13 PR 13a)
│   │   └── SaintSsoCallbackController.java    # NEW — Task 14
│   ├── dto/
│   │   ├── LibrarySessionCaptureRequest.java  # existing
│   │   └── (no DTO needed; sToken/sIdno are @RequestParam)
│   └── service/
│       ├── SaintSsoService.java               # NEW — 2-phase auth, mirrors ssutoday AuthServiceImpl
│       └── SaintSsoProperties.java            # NEW — saint SSO + portal URLs, configurable per env
├── domain/user/                               # NEW package — ssuAI's first user system
│   ├── entity/
│   │   └── Student.java                       # JPA @Entity
│   ├── repository/
│   │   └── StudentRepository.java
│   └── service/
│       └── StudentService.java                # upsert by studentId, lastLoginAt bump
└── global/auth/                               # NEW package — JWT infra
    ├── JwtProvider.java                       # access + refresh issue / validate
    ├── JwtProperties.java                     # secret, ttl, issuer
    ├── JwtAuthFilter.java                     # OncePerRequestFilter, reads Authorization Bearer
    └── SecurityContextHelper.java             # current ssuAI user lookup for controllers

frontend/
├── app/auth/
│   ├── login/
│   │   └── page.tsx                           # NEW — "유세인트로 로그인" CTA
│   └── return/
│       └── page.tsx                           # NEW — landing after SSO 302 back
├── components/auth/
│   └── SaintLoginButton.tsx                   # NEW
├── hooks/
│   └── useSaintAuth.ts                        # NEW — refresh token handshake + access JWT in memory
└── lib/api/
    └── auth.ts                                # NEW — /api/auth/refresh + /me
```

**Why `domain/auth/`**: Task 13 PR 13a already created this package. We
extend it. Library-side session capture and SmartID-side SSO callback
are auth-adjacent siblings even though their token semantics differ.

**Why `domain/user/`**: this is the first task where ssuAI has an actual
user table. Previous work was all anonymous-public.

## 7. Open questions (resolve during implementation)

1. **`apiReturnUrl` whitelist** — **RESOLVED POSITIVE 2026-05-16.**
   Real-user spike confirmed SmartID accepts arbitrary `apiReturnUrl`
   values. Hitting
   `https://smartid.ssu.ac.kr/Symtra_sso/smln.asp?apiReturnUrl=https%3A%2F%2Fexample.com`
   in a logged-in browser produced a SmartID 302 to
   `https://example.com/?sToken=<redacted>&sIdno=<redacted>` — exactly
   the shape `SaintSsoCallbackController` reads. The web-port pattern
   is therefore safe to depend on; §10 stop-and-flag #1 does not fire.
   The PR series that built on this assumption (PRs #94 / #99 / #100 /
   #101 / #102) stands without rework.
2. **Portal cookie TTL after phase 2** — we throw the portal cookies
   away after parsing identity, so this only matters for *future*
   realtime-data tools (Task 15+). Document but do not solve here.
   **Update 2026-05-16 (post prod live)**: the assumption that Phase 2
   HTML carries 학번/이름/소속/학적 in one inline page turned out wrong.
   `/irj/portal` is a **SAP NetWeaver frameset wrapper**: only `<span
   class="top_user">{이름}님 접속을 환영합니다.</span>` greeting + JS
   `LogonUid` live on the wrapper itself. 소속/학적/학년 카드는 모두
   `<iframe id="contentAreaFrame">` 안쪽에서 lazy load. Task 14 final
   shape: 학번 = `sIdno` trust (phase 1 success marker passed + portal
   session cookie issued by saint proves sIdno = the actual student),
   이름 = `.top_user` greeting의 ordered suffix-strip
   (`["님 접속을 환영합니다.", "님 환영합니다.", "님"]`), major/
   enrollmentStatus = `null`. The deep iframe endpoints are Task 16's
   problem and need their own spike + fixtures.
3. **Korean major mapping** — ssutoday hardcodes the supported
   majors (`컴퓨터학부` → `cse`, …) and rejects others. For ssuAI we
   should be more permissive — store the raw major string, do not
   reject. Different scope: ssutoday filters push topics, ssuAI is a
   general-purpose tool.
4. **Refresh token rotation** — we should rotate refresh tokens on
   each `/api/auth/refresh` call (standard practice). Implementation
   detail; not a blocker.
5. **Logout** — `POST /api/auth/logout` invalidates the refresh
   token server-side, clears the cookie. Trivial; do in PR 14b.

## 8. Implementation plan

Three PRs, sequenced.

### PR 14a — Spike + design lock-in

- 30-min manual spike: open
  `smartid…/smln.asp?apiReturnUrl=<test url>` in a browser, log in,
  observe SmartID behavior. Document outcome inline in this spec §7 #1
  with a "RESOLVED" status (positive or negative).
- If positive → proceed to PR 14b.
- If negative → §10 stop-and-flag #1 fires; we surface to user.

This PR ships **no code**, only spec annotation + dev-log entry.

### PR 14b — Backend SSO callback + JWT + user system

- `SaintSsoProperties` with `saint-sso-url`, `saint-portal-url`
- `SaintSsoService` — Phase 1 + Phase 2 logic, line-by-line modeled on
  ssutoday's `AuthServiceImpl.uSaintAuth`. RestClient for HTTP, Jsoup
  for parsing. Test against pinned HTML fixtures (developer captures
  one real response, scrubs PII except the parse anchors).
- `Student` JPA entity (H2 dev), `StudentRepository`, `StudentService`.
- `JwtProvider` + `JwtProperties` + `JwtAuthFilter`.
- `SaintSsoCallbackController`:
  - GET `/api/auth/saint/sso-callback?sToken=...&sIdno=...`
  - calls `SaintSsoService.authenticate(sToken, sIdno)` → returns
    `UsaintAuthResult { studentId, name, major }`
  - calls `StudentService.upsertOnLogin(...)` → returns `Student`
  - calls `JwtProvider.issue(student)` → access + refresh
  - sets refresh cookie, 302s to `ssuai.vercel.app/auth/return?ok=1`
- `POST /api/auth/refresh` and `GET /api/auth/me` endpoints
- Error paths:
  - SmartID validation fail → 302 to `/auth/return?error=auth_failed`
  - Saint portal HTML parse fail → 302 to `/auth/return?error=portal_unavailable`
  - Anything else → 302 to `/auth/return?error=unknown` (server-side log carries detail)
- Tests:
  - `SaintSsoServiceTests` with MockWebServer for both phases
  - `SaintSsoCallbackControllerTests` `@WebMvcTest`
  - `JwtProviderTest` issue / verify / expiry
  - `StudentRepositoryTest` upsert idempotency

### PR 14c — Frontend SSO entry + return + auth state

- `/auth/login` page with "유세인트로 로그인" button
- Click handler: `window.location.href = '${API_BASE}/api/auth/saint/sso-init'`
  (a tiny backend endpoint that 302s to SmartID — keeps the URL building
  on the backend so the frontend never hardcodes SSO config)
- `/auth/return` page reads `?ok=1` or `?error=...`, on success calls
  `/api/auth/refresh` to mint the in-memory access JWT
- `useSaintAuth` hook — exposes `{ user, isAuthenticated, login, logout }`
- Dashboard adds a "안녕하세요 OOO 학생" greeting line when authenticated
- Tests:
  - `SaintLoginButton.test.tsx` click navigation
  - `useSaintAuth.test.ts` token refresh handshake mock
  - vitest for `/auth/return` page error-path rendering
- ADR 0014 written here.

## 9. Security checklist

- [ ] `sToken` / `sIdno` never logged. Only `MaskUtil.maskStudentId(sIdno)` style fingerprints.
- [ ] JWT secret in `SSUAI_JWT_SECRET` env, never committed. Rotation runbook in `docs/security.md`.
- [ ] Refresh cookie: `HttpOnly; Secure; SameSite=Lax; Path=/api/auth`. NOT readable from JS.
- [ ] Access JWT in memory only (not localStorage, not sessionStorage).
- [ ] HTTPS-only on `api.ssumcp.duckdns.org` (already enforced).
- [ ] `apiReturnUrl` in the SSO link is built server-side from configured base; never reflect user input into it.
- [ ] gitleaks scan covers `JwtProperties` (it shouldn't match a real secret pattern, but verify).
- [ ] No password-shaped field ever appears anywhere in this code path.
- [ ] Add `docs/security.md` §X "External identity SSO handling": fingerprint-only logging, no SSO token persistence.

## 10. Stop and flag

Stop and check in with the user during Task 14 work if any of the
following happens:

- **RESOLVED POSITIVE 2026-05-16 — does not fire (see §7 #1).**
  Was: SmartID refuses arbitrary `apiReturnUrl` values → web-port
  pattern collapses. Fallback options retained for history in case
  SmartID's behavior changes server-side: (a) ssuAI Companion browser
  extension (Task 13 §12 option B revival), (b) build a tiny
  iOS/Android app just for the SSO step, (c) negotiate with SSU IT to
  whitelist our return URL.
- saint.ssu.ac.kr portal HTML structure has shifted significantly
  from ssutoday's parse anchors (no `main_box09` etc.) → need to
  re-parse and pin new fixtures.
  **PARTIALLY FIRED 2026-05-16**: ssutoday's `.main_box09 .main_box09_con`
  positional cells matched **0** on the real portal. PR #112 rewrote
  parser to a key-based `<dt>→<dd>` map (still wrong target — that
  markup lives inside the iframe, not the wrapper); PR #113 rewrote
  again to read `.top_user` greeting + sIdno trust (correct for the
  wrapper). Pinned fixtures: `portal-success.html`, `portal-missing-name.html`,
  `portal-greeting-unknown-suffix.html` — all `홍길동`/`20999999`/dummy
  IP/timestamp placeholders.
- 학번 (`sIdno`) appears in HTML in a way that suggests it can be
  spoofed without SmartID validation → trust the saint response,
  not the query string, for identity. Note this in ADR.
- The user decides Task 14 should ship realtime-data tools (성적,
  시간표) too → rescope, this becomes Task 14 + Task 15 merged.

## 11. Out-of-scope work this enables

- **Task 15 — u-SAINT realtime data tools**. `get_my_schedule`,
  `get_my_grades`. Needs the portal cookies from Phase 2 to be
  retained (or recapture via a fresh SSO each session). Decision
  deferred — both have UX costs.
- **Task 16 — LMS tools**. `get_my_assignments`, etc. LMS has its
  own auth (likely SmartID-adjacent but distinct upstream).
- **Phase 4 write tools** — `cancel_library_seat_reservation`,
  `enroll_course`, etc. Requires the action-tool guardrails listed
  in [`docs/vision.md`](../vision.md) §3 Layer 4.
- **ssuAI-internal user features** — once `Student` exists, user
  preferences, history, notifications can be keyed to it.

## 12. Relationship to Task 13

Task 13 and Task 14 are **sibling** Phase 3 tasks. They share:

- `domain/auth/` package
- Architectural principle: passwords never enter ssuAI; only minimal
  tokens cross our trust boundary
- The "phantom-token adaptation to legacy upstream" framing in
  [ADR 0013](../adr/0013-library-session-capture-pattern.md)

They do **not** share:

- Token storage (Task 13 stores ssotoken, Task 14 throws sToken away)
- Capture mechanism (Task 13 = manual paste, Task 14 = SSO callback)
- User identity (Task 13 has no users yet, Task 14 creates them)

ssuAI's first authenticated user system lands in Task 14. Task 13's
`LibrarySessionStore` will eventually re-key from `HttpSession.getId()`
to `Student.studentId` once Task 14 ships — that migration is a
follow-up, not blocking.

## 13. Prod-live retrospective (2026-05-16)

Task 14 went prod-live on 2026-05-16 over four PRs (#112, #113, #114,
#116). Two layers tripped beyond the original §10 stop-and-flag list:

### Cross-site cookie auth needs two attributes, not one

ssuAI runs frontend on `https://ssuai.vercel.app` (Vercel) and backend
on `https://ssumcp.duckdns.org` (k3s). The refresh-cookie + access-JWT
handshake is therefore **cross-site**, and Chromium-class browsers
require BOTH of the following to be in place:

1. `Set-Cookie: …; SameSite=None; Secure` on the refresh cookie
   (so the cookie is **sent** with cross-site POST `/api/auth/refresh`).
   PR #114 fixed this — `application-prod.yml` adds
   `refresh-cookie.same-site: None`. The original spec §9 said
   `SameSite=Lax`, which only works when the frontend lives on the
   same registrable domain as the backend.
2. `Access-Control-Allow-Credentials: true` on the response (so the
   browser **exposes the response body to JS**) — even when the
   response is 200 OK and `Set-Cookie` succeeds, the browser silently
   blocks `await response.json()` if this header is missing.
   PR #116 fixed this — `ApiCorsDefaults.allowCredentials(true)`.
   Requires `allowedOrigins` to be an **explicit origin** (not `*`)
   for the Spring `CorsConfiguration` validator to accept the
   combination, which we already had.

Both must be present. The intermediate state (PR #114 only) was the
worst kind of broken: 200 OK in DevTools, cookie stored, but frontend
silently catches `INVALID_ENVELOPE` and tells the user "세션 갱신
실패". Backend log shows nothing because the request did succeed.
See `TROUBLESHOOTING.md` 2026-05-16 entry for the debugging trail.

### Portal HTML is a frameset wrapper, not a single page

See §7 #2 update. The student-data cards Task 14 originally planned
to harvest (소속/학적/학년) live inside an iframe and are not
reachable through `/irj/portal` alone. Task 14 ships identity-only
(학번 + 이름) with major/enrollmentStatus as `null`. Task 16 picks up
the iframe-deep-endpoint work.

### Spec-fixture-reality drift

Task 14 §10 stop-and-flag #2 warned about this exact class of bug
("portal HTML structure has shifted from ssutoday's parse anchors")
but the original fixture (`portal-success.html`) was a synthetic
shape based on the spec, not a real captured response. The synthetic
fixture made all tests green even though the parser couldn't read the
real portal. Lesson: fixtures for external integrations must be
**captured from real responses** (with PII scrubbed) before the parser
is considered tested. Followed in PR #113's new fixture set.
