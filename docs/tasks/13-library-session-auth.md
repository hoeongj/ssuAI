# Task 13 — Library session auth (Phase 3 first deliverable)

> Created 2026-05-15 after Task 12 §8 stop-and-flag fired during PR 1b
> research. The library seat upstream is fully auth-gated; this task
> unlocks the real-data path and becomes Phase 3's first concrete
> milestone.

> **Status (2026-05-15, session 2):**
>
> - **§7 #1 spike resolved — negative.** Browser same-origin policy
>   blocks the parent (`ssuai.vercel.app`) from reading
>   `popup.document.cookie` or `popup.location.href` after the user logs
>   in on `oasis.ssu.ac.kr`. `window.postMessage` is also unusable
>   because we cannot inject a listener into oasis's page. ssutoday's
>   pattern works only because React Native WebView (native) does not
>   enforce web SOP — pure-web ssuAI does. See §7 #1 for the verdict and
>   §10 for the triggered stop-and-flag.
> - **PR 13a (backend session store + 401 mapping) is unblocked** and
>   in progress on branch `feat/library-session-store`. The capture
>   mechanism choice (popup / paste / extension / pivot) only affects
>   PR 13c's frontend orchestration; the backend store shape and 401
>   contract are mechanism-agnostic.
> - **PR 13c (frontend capture flow) is blocked** pending user decision
>   on the popup alternative. Options on the table — see §12.

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

## 4. Confirmed endpoints (verified 2026-05-15 against a real authenticated SPA capture)

| Path | Status | Notes |
|---|---|---|
| `https://oasis.ssu.ac.kr/library-services/smuf/reading-rooms` | 200 (SPA shell) | Public page returns empty Angular shell; data loads via JS |
| `https://oasis.ssu.ac.kr/pyxis-api/1/seat-rooms?smufMethodCode=PC&branchGroupId=1` | GET | **Real** seat data endpoint the SPA hits. Returns rooms/seats JSON (~2 KB). Requires `Pyxis-Auth-Token` header. |
| `https://oasis.ssu.ac.kr/pyxis-api/1/branches` | GET | Branch list. Same auth. |
| `https://oasis.ssu.ac.kr/pyxis-api/1/favorite-seats?smufMethodCode=PC` | GET | Per-user favorites. Same auth. |
| `https://oasis.ssu.ac.kr/pyxis-api/1/seat-room-reservable-dates?smufMethodCode=PC&branchGroupId=1` | GET | Reservation date metadata. Same auth. Will matter for Phase 4. |
| `https://oasis.ssu.ac.kr/pyxis-api/api/login` | POST | Login endpoint. Empty body → `error.badRequest`. Expected payload likely `{loginId, password}` (verify before implementing). **NOT used by ssuAI** — we never call this path because the password never enters our domain. |

**Auth mechanism (corrected 2026-05-15):**

- Pyxis API uses a **`Pyxis-Auth-Token` request header**, NOT a Cookie.
  Initial spec assumed the `ssotoken` cookie was the API auth token; a
  real authenticated SPA capture proved otherwise — `ssotoken` is for
  SPA shell loading on `oasis.ssu.ac.kr` and is unrelated to API auth.
- The header value is opaque (~32 char alphanumeric). Each
  authenticated SPA load gets a fresh value.
- Companion headers the SPA sends (likely required by Pyxis or its WAF):
  `Accept: application/json, text/plain, */*`, `Referer: https://oasis.ssu.ac.kr/library-services/...`,
  `User-Agent` (browser UA).
- 401 / auth failure response shape:
  `{"success":false,"code":"error.authentication.needLogin","message":"Please log in to use this service."}`
  (returned with HTTP status 200, not 401 — must inspect body to detect).

## 5. Architecture

```text
Browser                                Backend                  oasis.ssu.ac.kr
───────                                ───────                  ───────────────
[User logs into oasis at the official site (separate tab)]
  │
  │ oasis SPA loads and obtains a `Pyxis-Auth-Token`
  │ (visible in devtools Network → any /pyxis-api/* request → Request Headers)
  │
[User clicks "도서관 로그인" on ssuAI]
  │
  │ ssuAI shows a modal: "oasis devtools에서 Pyxis-Auth-Token 값을 복사해 붙여넣어 주세요"
  │ User pastes the token value
  ▼
Parent: POST /api/library/session { token: "<Pyxis-Auth-Token>" }
  │                                   ─────────────────────►
                                     [LibrarySessionStore]
                                     stores {sessionKey, token,
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
                                       reads token for current session
                                       │
                                       ▼
                                     [RealLibrarySeatConnector]
                                       GET /pyxis-api/1/seat-rooms?…
                                       Pyxis-Auth-Token: <token>   ────────►
                                       Accept: application/json, text/plain, */*
                                       Referer: https://oasis.ssu.ac.kr/library-services/...
                                                                  [seat JSON]
                                                                  ◄────────
                                     parse → LibrarySeatStatusResponse
                                  ◄──────────────────────
[Card shows real data]
```

Task 14 (u-SAINT) shares **none** of this token-handling code — its
SmartID-issued `sToken`+`sIdno` are one-shot identity tokens, not a
session header. The two tasks share only the `domain/auth/` package
and the phantom-token principle in
[ADR 0013](../adr/0013-library-session-capture-pattern.md).

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

1. **Cookie SameSite + same-origin reads — RESOLVED 2026-05-15, NEGATIVE.**
   Spike on 2026-05-15 confirmed: browser SOP prevents
   `ssuai.vercel.app` from reading any property of an
   `oasis.ssu.ac.kr` popup post-login. `popup.document.cookie` and
   `popup.location.href` both throw `SecurityError`; `postMessage` is
   one-way only and we have no way to inject a sender into oasis's
   page. The native-WebView pattern from ssutoday (React Native) does
   not translate to a pure-web frontend. This triggers §10's first
   stop-and-flag case — see §12 for the decision options.
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

- `RealLibrarySeatConnector` using `RestClient` with
  `Pyxis-Auth-Token: <token>` request header (plus `Accept`,
  `Referer`, `User-Agent` companion headers). Use the new
  `LibrarySessionStore` to look up the token.
- Target endpoint: `GET /pyxis-api/1/seat-rooms?smufMethodCode=PC&branchGroupId=1`
  (and floor-filtering variants once we map them).
- Pin JSON fixtures (from a real authenticated dev session of the user)
  to `backend/src/test/resources/library/` — **NEVER commit the
  token**; only the response JSON with any student-identifying fields
  scrubbed.
- Parse tests against fixtures.
- Property `ssuai.connector.library-seat=real` switches the bean.
- Manual smoke: developer logs in via frontend popup → curl returns real
  seat data.

### PR 13c — Frontend capture flow (manual paste MVP)

Specific mechanism per spec §12 decision. For option A (manual paste):

- `LibraryLoginButton` opens a "도서관 로그인" modal with step-by-step
  guidance (screenshot + numbered steps for capturing the
  `Pyxis-Auth-Token` from devtools).
- Textarea where the user pastes the token value.
- `useLibrarySession` POSTs to `/api/library/session` → invalidates the
  `["library", "seats"]` query key.
- `LibrarySeatCard` renders the "도서관 로그인" CTA when query errors
  with `LIBRARY_SESSION_REQUIRED`.
- Tests: paste flow input validation, error→CTA transition, success →
  query refetch.
- ADR 0013 already shipped — update its "Implementation status"
  section here.

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
- **u-SAINT integration (Task 14)** — sibling Phase 3 task, but
  **structurally different**. ssutoday's SmartID SSO flow gives us
  `sToken`+`sIdno` as 1-shot identity-confirmation tokens; we throw
  them away after a 2-phase saint scrape and issue ssuAI's own JWT.
  Task 13 (library) is the opposite — its `ssotoken` is a long-lived
  *data-access* session cookie that we must persist. The two tasks
  share the `domain/auth/` package and the phantom-token *principle*
  ([ADR 0013](../adr/0013-library-session-capture-pattern.md)) but
  not the token-handling code. See
  [`docs/tasks/14-usaint-session-auth.md`](14-usaint-session-auth.md) §2
  for the side-by-side comparison.

## 12. Capture-mechanism decision (PR 13c blocker)

§7 #1 spike killed the cross-origin popup-cookie-read approach. PR 13c
cannot proceed until the user picks one of the following:

| # | Option | UX | Backend shape | Portfolio story | Risk |
|---|--------|----|---------------|-----------------|------|
| A | **Manual paste UI** — user logs in at oasis tab, copies `ssotoken` cookie value from devtools, pastes into ssuAI modal | Ugly. 5-step instruction screen with screenshot. Once per ~2h. | Identical to popup design — `POST /api/library/session { token }`. | Honest about the web-platform constraint; documents *why* native apps can do this and webs cannot. ADR-worthy. | Low. Works today. |
| B | **Browser extension** (Chromium) — extension has cross-origin cookie access; reads `ssotoken` after user logs in, POSTs to ssuAI backend | One-click after install. Install flow itself is friction. | Same as A. | Strong portfolio piece if extension is in scope. Out of single-student MVP budget. | High build cost. Store review for Manifest V3. |
| C | **Bookmarklet** — user adds a JS bookmarklet to their bookmarks bar; clicks it while on `oasis.ssu.ac.kr` post-login. Runs in oasis origin, reads `document.cookie`, POSTs to ssuAI backend (CORS allow-listed). | Moderate. One-time install of bookmarklet. Per-session one click on oasis tab. | Same as A. Adds CORS allowlist for `oasis.ssu.ac.kr` origin → `api.ssumcp.duckdns.org/api/library/session`. | Demonstrates SOP awareness + a working workaround. Reasonable portfolio talking point. | CSP on oasis may block; verify before committing. |
| D | **Pivot to u-SAINT first (Task 14)** | n/a for library | n/a — different connector | u-SAINT uses SmartID SSO redirect with `sToken`+`sIdno` in the URL. Redirect URL *is* readable cross-origin if you control the apiReturnUrl. Could be a ssuAI-owned `/api/saint/sso-callback` endpoint that the SSO redirects to. | Same as ssutoday's approach, ported to web. | Library stays on mock indefinitely. |
| E | **Stay on mock indefinitely** — accept that the read-only library data plane stays mock-only; document the SOP wall as the reason | No real data | Backend session store still built (PR 13a) for u-SAINT reuse. PR 13b/13c shelved. | Smallest scope. Weakest data story. | None technical. |

**Author recommendation:** **A (manual paste)** as the shipped MVP +
**D (pivot to u-SAINT)** for the next active task. Rationale: A is
~half a day of work, demonstrates the full session-store + auth-aware
pipeline end-to-end against a real upstream, and converts cleanly into
B or C later. D unblocks `saint.ssu.ac.kr` data (transcripts, schedule)
which is a richer Phase 3 demo than seat counts. C is tempting but
oasis's CSP needs verifying first; if CSP blocks the bookmarklet fetch,
that day is wasted.

This decision needs user input — do not proceed past PR 13a without it.
