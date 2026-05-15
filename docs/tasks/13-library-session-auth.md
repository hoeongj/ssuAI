# Task 13 — Library session auth (Phase 3 first deliverable)

> Created 2026-05-15 after Task 12 §8 stop-and-flag fired during PR 1b
> research. The library seat upstream is fully auth-gated; this task
> unlocks the real-data path and becomes Phase 3's first concrete
> milestone.

## 1. Goal / Scope / Non-goals

### Goal

Capture an authenticated session against the SSU library (`oasis.ssu.ac.kr`)
without ever putting the user's password through ssuAI. Use the captured
session to power `RealLibrarySeatConnector` (Task 12 PR 1b) and any
future library-side personal tool. The pattern must be **directly reusable**
for u-SAINT (`saint.ssu.ac.kr` via `smartid.ssu.ac.kr`) in subsequent
Phase 3 work.

### In scope

- A frontend WebView/popup login flow that loads `oasis.ssu.ac.kr/login`
  in an iframe-isolated context, lets the user log in **at the official
  page**, and reads only the resulting `ssotoken` cookie after success
- A backend `LibrarySessionStore` keyed by ssuAI user (in-memory for MVP;
  H2/SQLite-backed when Phase 3 grows users)
- `RealLibrarySeatConnector` that injects the stored cookie into
  `RestClient` calls to `/pyxis-api/api/smuf/reading-rooms`
- `LibrarySeatService` becoming auth-aware: throws
  `LibraryAuthRequiredException` (new) → 401 when no session, prompting
  the frontend to launch the login flow
- ADR 0013 documenting the WebView-capture pattern (mirrors ssutoday's
  u-SAINT SSO approach, adapted because oasis uses its own login)
- `docs/security.md` §X — external session token handling policy
  (fingerprint-only logging, never log raw cookie, AES-GCM at rest if
  persisted, TTL-bounded)

### Non-goals

- ❌ u-SAINT auth in this task (deferred to Task 14, will reuse this
  pattern). Library and u-SAINT are separate auth domains.
- ❌ Server-side credential storage. Passwords never enter ssuAI's
  domain. The only token in flight is `ssotoken` post-login.
- ❌ Sharing one session across users (the "ssuai-bot account" pattern).
  Privacy + ToS risk; rejected on portfolio scope.
- ❌ Phase 4 seat reservation (`reserve_library_seat`). Still write-tool,
  still a separate task spec.

## 2. Why this hit us now

Task 12's spec assumed the library seat status page was anonymous. PR 1a
(`feat/library-seat-mock-slice`, PR #77) shipped the mock end-to-end
slice, then Task 12 PR 1b's research phase discovered:

- `https://oasis.ssu.ac.kr/library-services/smuf/reading-rooms` serves an
  empty Angular SPA shell to anonymous users
- The SPA fetches data from `https://oasis.ssu.ac.kr/pyxis-api/api/...`
- **Every** pyxis-api endpoint probed returns
  `{"success":false,"code":"error.authentication.needLogin"}` — gateway-
  level auth, no anonymous fallback found
- Library uses its own login (`/pyxis-api/api/login` JSON POST), NOT
  the SmartID SSO that u-SAINT uses

Task 12 §8 explicitly named this case as a Phase-3 trigger:
*"Library seat page requires authentication or returns a CAPTCHA …
that pushes this task into Phase 3 territory."*

## 3. Reference prior art — `jonghokim27/ssutoday`

The user pointed us at https://github.com/jonghokim27/ssutoday for
SSU SSO experience. Their pattern for u-SAINT:

1. React Native WebView loads
   `https://smartid.ssu.ac.kr/Symtra_sso/smln.asp?apiReturnUrl=https%3A%2F%2Fsaint.ssu.ac.kr%2FwebSSO%2Fsso.jsp`
2. User logs in at the **official SSU SSO page** inside the WebView —
   ssutoday never sees the password
3. After auth success, SmartID redirects to
   `https://saint.ssu.ac.kr/webSSO/sso.jsp?sToken=...&sIdno=...`
4. WebView's `onLoadStartHandler` intercepts the redirect URL, extracts
   `sToken` and `sIdno` from the query string
5. App sends those two short tokens to its Spring backend
6. Spring backend uses `sToken`+`sIdno` to authenticate against the
   u-SAINT portal, extracts set-cookie headers, scrapes student info

Key security property: **the password never crosses ssutoday's trust
boundary.** Only short-lived SSO tokens do.

ssuAI's library auth needs the same property. Two adaptations:

- Library doesn't go through SmartID — it's a direct
  `/pyxis-api/api/login` username/password POST. So we cannot redirect
  through SmartID to obtain a token; we have to let the user log in on
  the library's own page.
- The "redirect URL token" handoff is replaced by a "post-login cookie
  read." After successful login, `oasis.ssu.ac.kr` sets an `ssotoken`
  cookie on its own origin. The frontend WebView/popup reads
  `document.cookie` for `ssotoken` and sends only that to the backend.

## 4. Confirmed endpoints (from today's reverse engineering)

| Path | Status | Notes |
|---|---|---|
| `https://oasis.ssu.ac.kr/library-services/smuf/reading-rooms` | 200 (SPA shell) | Public page returns empty Angular shell; data loads via JS |
| `https://oasis.ssu.ac.kr/pyxis-api/api/login` | POST | Login endpoint. Empty body → `error.badRequest`. Expected payload likely `{loginId, password}` (verify before implementing). |
| `https://oasis.ssu.ac.kr/pyxis-api/api/smuf/reading-rooms` | GET | Returns seat data. Currently `needLogin`. |
| `https://oasis.ssu.ac.kr/pyxis-api/api/smuf/rooms` | GET | Adjacent endpoint. Same auth wall. |
| `https://oasis.ssu.ac.kr/pyxis-api/api/smuf/dashboards` | GET | Adjacent endpoint. Same auth wall. |

Cookie names (from oasis main.js, key
`commonService.getConfig("AUTH.COOKIE_NAME")`):

- `ssotoken` — primary session token. This is what the frontend captures.
- Side cookies (`uuid*`, `ssotoken*`) — managed by Pyxis, may or may not
  be needed. Verify experimentally.

## 5. Architecture

```text
Browser                                Backend                  oasis.ssu.ac.kr
───────                                ───────                  ───────────────
[User clicks "도서관 로그인"]
  │
  │ window.open("https://oasis.ssu.ac.kr/login")
  ▼
[Popup: oasis login form]
[User enters library credentials]
  │
  │ oasis sets `ssotoken` cookie on its origin
  ▼
[Popup: oasis dashboard page loads]
  │
  │ parent window polls popup.document.cookie  (same-origin? — see §7)
  │ OR popup.postMessage(token, parent.origin)
  ▼
Parent: extracts ssotoken
  │
  │ POST /api/library/session  { token: "<ssotoken>" }
  ▼                                  ─────────────────────►
                                     [LibrarySessionStore]
                                     stores {userId, ssotoken,
                                             capturedAt, ttl}
                                     ◄─────────────────────
Parent: invalidate React Query for ["library", "seats"]
  │
  ▼
[Dashboard card re-renders]
  │
  │ GET /api/library/seats?floor=4
  ▼                                  ─────────────────────►
                                     [LibrarySeatService]
                                       reads session for current user
                                       │
                                       ▼
                                     [RealLibrarySeatConnector]
                                       GET /pyxis-api/api/smuf/reading-rooms
                                       Cookie: ssotoken=<...>      ────────►
                                                                  [seat JSON]
                                                                  ◄────────
                                     parse → LibrarySeatStatusResponse
                                  ◄──────────────────────
[Card shows real data]
```

The same diagram with `smartid.ssu.ac.kr` instead of `oasis.ssu.ac.kr`
and `sToken+sIdno` extracted from the redirect URL instead of `ssotoken`
read from the cookie powers Task 14 (u-SAINT). The frontend component
and `SessionStore` shape are shared.

## 6. Package additions

```
backend/src/main/java/com/ssuai/
├── domain/library/
│   ├── connector/
│   │   └── RealLibrarySeatConnector.java     # NEW — uses RestClient + Cookie header
│   └── service/
│       ├── LibrarySessionStore.java          # NEW — keyed by ssuAI userId
│       └── LibrarySeatService.java           # MODIFIED — auth-aware
├── domain/auth/                              # NEW package
│   ├── controller/
│   │   └── LibrarySessionController.java     # POST /api/library/session
│   ├── dto/
│   │   └── LibrarySessionCaptureRequest.java
│   └── service/
│       └── LibrarySessionService.java
└── global/exception/
    └── LibraryAuthRequiredException.java     # NEW — maps to 401

frontend/
├── components/library/
│   ├── LibraryLoginButton.tsx                # NEW — opens popup
│   └── LibrarySeatCard.tsx                   # MODIFIED — 401 → login CTA
├── hooks/
│   └── useLibrarySession.ts                  # NEW — popup orchestration
└── lib/api/
    └── library.ts                            # MODIFIED — session capture POST
```

`domain/auth/` is created here because this is the first auth-adjacent
feature. u-SAINT will join later as a sibling sub-package or a separate
domain depending on coupling.

## 7. Open questions (resolve during implementation, not now)

1. **Cookie SameSite + same-origin reads.** Whether the parent window
   can read `document.cookie` on the popup depends on browser policy. If
   blocked, fall back to `window.postMessage` from a small bootstrap
   script we control via `oasis.ssu.ac.kr/library-services/...` — but
   we don't control oasis. The realistic fallback is: ask the user to
   paste the cookie after login (ugly), OR proxy the login through our
   own backend by recreating the POST. The latter brings the password
   back to ssuAI — rejected. **First experiment**: verify whether the
   popup's cookie is readable by the opener via `popup.document.cookie`
   on `oasis.ssu.ac.kr`'s own page after login. Browsers may block this
   when origin-policy treats it as third-party.
2. **`ssotoken` lifetime.** Pyxis sessions are typically rolling. We
   need to know: TTL? Refresh mechanism? What happens on 401 from
   downstream API — auto-refresh by re-popping the login? Decide once we
   have a working token in hand.
3. **Multi-user vs single-user MVP.** ssuAI does not yet have its own
   user system. Until then, `LibrarySessionStore` can be keyed by
   conversation-id or browser-session cookie. When Phase 3 adds proper
   ssuAI auth (own login), migrate the store key to ssuAI userId.
4. **Pyxis ToS.** Is automated API access by an authenticated student
   user explicitly forbidden? Need to read SSU library terms before
   shipping. Currently treat as "user-initiated request on user's own
   credentials = same as them clicking" — same legal frame the official
   SPA operates under.

## 8. Implementation plan

Three PRs, sequenced.

### PR 13a — Backend session store + auth-aware service + 401 mapping

- `LibrarySessionStore` (in-memory, `ConcurrentHashMap<String, Session>`)
- `LibraryAuthRequiredException` + `GlobalExceptionHandler` mapping → 401
  + new ErrorCode `LIBRARY_SESSION_REQUIRED`
- `LibrarySeatService.getSeatStatus()` consults the store; no session →
  throw `LibraryAuthRequiredException`
- `POST /api/library/session` accepts captured token; stores under the
  current ssuAI session key (cookie-based for MVP)
- Tests: store hit/miss/TTL, service throws 401, controller round-trip
- **No real upstream call yet** — `RealLibrarySeatConnector` stays
  behind `ssuai.connector.library-seat=real`, which still defaults to
  `mock`.

### PR 13b — `RealLibrarySeatConnector` + parse fixtures

- `RealLibrarySeatConnector` using `RestClient` with `Cookie: ssotoken=...`
  header. Use the new `LibrarySessionStore` to look up the cookie.
- Pin JSON fixtures (from a real authenticated dev session of the user)
  to `backend/src/test/resources/library/` — **NEVER commit the
  ssotoken**; only the response JSON with student-identifying fields
  scrubbed.
- Parse tests against fixtures.
- Property `ssuai.connector.library-seat=real` switches the bean.
- Manual smoke: developer logs in via frontend popup → curl returns real
  seat data.

### PR 13c — Frontend popup login + auto re-fetch

- `LibraryLoginButton` opens a `400×600` popup at `oasis.ssu.ac.kr/login`
- `useLibrarySession` orchestrates: open popup → poll URL/cookie until
  login success → read `ssotoken` → POST to `/api/library/session` →
  invalidate the `["library", "seats"]` query key
- `LibrarySeatCard` renders "도서관 로그인" CTA when query errors with
  `LIBRARY_SESSION_REQUIRED`
- Tests: button click flow with mocked popup, error→CTA transition
- ADR 0013 written here (the architecture is now fully observable in
  code).

## 9. Security checklist

- [ ] `docs/security.md` adds §X "External session token handling": never
      log raw `ssotoken`, only `sha256(token)[:8]` fingerprint
- [ ] Store enforces a TTL (start with 2h, observe behavior); expired
      entries are silently dropped on read
- [ ] No password-shaped field ever appears in DTOs, logs, or requests
      to/from our backend
- [ ] `LibrarySessionCaptureRequest` body length capped; reject anything
      that doesn't pattern-match the expected `ssotoken` shape
- [ ] gitleaks rules updated if needed (probably not; `ssotoken=...` is
      not a known pattern)
- [ ] CORS + CSRF: the `POST /api/library/session` endpoint must not be
      callable cross-origin. Reuse existing same-origin policy.

## 10. Stop and flag

Stop and check in with the user during PR 13 work if any of the
following happens:

- Browser blocks cross-origin `document.cookie` read on the popup AND
  `postMessage` is not viable → ask user how to proceed (likely: ship
  a manual paste UI, ugly but works)
- Pyxis library terms of service forbids API access → re-scope the
  entire library data plane
- `ssotoken` rotates so aggressively that captured sessions die in
  seconds → may need a real refresh flow, possibly out of MVP scope
- The user decides Phase 3 work should start with u-SAINT instead
  (different connector, different SSO URL) — pause this and pick up
  Task 14 first

## 11. Out-of-scope work this enables

- **Task 12 PR 1b** (`RealLibrarySeatConnector`) is essentially folded
  into PR 13b. Task 12's spec can mark PR 1b as "subsumed by Task 13."
- **Phase 4 `reserve_library_seat`** — a future write tool — will need
  this same session plumbing, plus the action-tool guardrails listed in
  [`docs/vision.md`](../vision.md) §3 Layer 4. Don't add reservation
  logic here.
- **u-SAINT integration (Task 14)** — same WebView-capture pattern,
  different URL (`smartid.ssu.ac.kr` → `sToken`+`sIdno`), same backend
  store shape. ADR 0013's pattern documentation should be written so
  Task 14 mostly copies the diagram and swaps URLs.
