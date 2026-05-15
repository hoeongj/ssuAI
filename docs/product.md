# ssuAI Product Document

> 이 문서는 ssuAI 의 단기 (Phase 1–2) 제품 정의입니다. 장기 4-layer
> vision 과 phase 3–4 의 개인 데이터 / 에이전트 계획은
> **[`docs/vision.md`](vision.md)** 가 source of truth 입니다.

## 1. One-line description

The ssuAI project ships **two distinct products** that together unify SSU
campus information:

1. **The Soongsil MCP server** — a public server that exposes academic,
   cafeteria, dorm, facility, and (later) library + u-SAINT/LMS data as
   MCP standard tools. Claude Desktop, Cursor, or any other MCP client
   can connect and use it directly.
2. **The ssuAI web/app** — a self-built client that consumes the MCP
   server above. A friendly card-based dashboard with an embedded
   natural-language chatbot and an action-capable AI agent on top, so
   students can drive their entire school life through one chat box.

The long-term shape is a 4-layer architecture — the MCP server is Layer 1,
and the ssuAI web/app + chatbot + agent are Layers 2-4. See
[`docs/vision.md`](vision.md) for the full picture. This document covers
what we build *first* and *next*; the phases beyond that live in the
vision document.

## 2. User problems this project solves

Today, a Soongsil student has to visit several disconnected systems to handle
ordinary daily questions:

- **Scattered information sources.** u-SAINT, LMS, the cafeteria site, and the
  library site each live in their own silo. Checking "what assignments are due
  this week, what's for lunch, and is there a free seat in the library?"
  requires opening three or four different services.
- **Slow, clunky UIs.** The school portals are not designed for quick lookups
  on a phone, and many flows require multiple clicks and re-logins.
- **No conversational access.** There is no natural-language way to ask
  "내일 1교시 수업 뭐였지?" or "오늘 학식 메뉴 알려줘". Students currently
  rely on screenshots in group chats or word of mouth.
- **No proactive notifications.** Assignment deadlines, grade updates, and new
  notices are easy to miss because students must check manually.
- **No unified portfolio of campus data.** Even simple things like "is this
  book in the library and where is it?" take longer than they should.

ssuAI's job is to be the single, fast, conversational front door to all of
this information.

## 3. MVP scope

The MVP is the smallest version that proves the architecture (Backend +
Connectors + MCP tools + Web) and is genuinely useful to a real student.

**In scope for MVP:**

1. **Backend skeleton (Spring Boot)**
   - Layered architecture (Controller → Service → Repository / Connector)
   - Global exception handling and standard response format
   - Health check via Spring Boot Actuator

2. **Cafeteria menu API** (no login required)
   - Mock connector first, then a real connector
   - `GET /api/meals/today` and `GET /api/meals?date=...`

3. **Library book search API** (public, no login)
   - `GET /api/library/books?query=...`
   - Returns title, author, location, availability

4. **Library seat availability API** (public, no login)
   - `GET /api/library/seats`
   - Returns rooms and free/occupied counts

5. **Read-only MCP server**
   - Tools: `get_today_meal`, `search_library_book`, `get_library_seat_status`
   - Wired to the same services used by the REST API
   - Usable from Claude Desktop / Claude Code as a proof of concept

6. **Minimal web dashboard (Next.js)**
   - Three cards: today's meal, library book search, library seat status
   - No login, no personalization
   - Mostly to demonstrate end-to-end usage

7. **Basic chatbot (text-only)**
   - Uses Spring AI + the read-only MCP tools above
   - Answers questions like "오늘 학식 뭐야?" / "이 책 도서관에 있어?"
   - No memory of personal academic data yet

The MVP intentionally avoids any login to school systems. This keeps the
privacy and security surface very small while still producing something
demo-worthy.

## 4. Future expansion features

After the MVP is stable, the project's expansion converges on a single
**flagship deliverable: a real-time library seat reservation agent**.
Everything else is either a prerequisite for that agent (auth, personal
data, library connectors) or an adjacent surface that shares the same
infrastructure (LMS read-only, notifications, mobile app).

Phase ordering (also see [`docs/vision.md`](vision.md) §4):

1. **Library public read tools** — `search_library_book`,
   `get_library_book_status`, `get_library_seat_status`. These enable
   the chatbot to *answer* "is seat 412 free?" before it can *reserve*
   it.
2. **User authentication** for ssuAI itself + encrypted credential
   storage (AES-GCM) so students can safely entrust their u-SAINT /
   LMS / library logins.
3. **Personal read tools** — `get_my_schedule`, `get_my_grades`,
   `get_my_assignments`, `get_my_library_loans`. Same connector
   pattern, gated on the authenticated user identity.
4. **Action tool infrastructure** — confirmation flow, dry-run preview,
   audit log table, distributed lock against same-user concurrent
   actions, race-condition handling.
5. **🏆 `reserve_library_seat` agent (flagship)** — chatbot recommends
   live seats, agent performs the actual reservation POST against the
   school's library site under the user's credential.
6. **Follow-up action tools** — cancel/extend reservation, LMS
   assignment-deadline reminders (ssuAI's own notifications, not LMS
   state changes), library book holds.
7. **Mobile app (Expo React Native)** — same MCP server, mobile-first
   surface so students can check / reserve while walking to the library.

Every action tool follows the same policy: explicit user confirmation,
dry-run preview, audit log row, no plaintext credential anywhere in
logs.

## 5. Features that should NOT be built first

These are tempting but should be deferred. They either carry too much
security/privacy risk for the current phase, or they are too large for one
student to ship safely as the first thing.

- u-SAINT login flow and credential handling
- LMS login flow and credential handling
- Storing student passwords or long-lived session cookies
- Graduation requirement automation
- Library seat **auto-reservation** (any action that changes school state)
- Course registration automation
- LMS assignment submission
- Mobile app
- Complex RAG pipelines, vector DBs, or fine-tuning
- A fully autonomous chatbot agent with broad tool access

The rule of thumb: **read-only, public data first; logged-in data later;
actions last, and only with explicit user confirmation.**

## 6. First-week development goals

The goal of week 1 is *not* to ship features. It is to lock in the
documentation and the backend skeleton so every later feature has a clean
place to land.

**Day 1 — Product & architecture docs**
- Finalize `docs/product.md` (this file)
- Draft `docs/architecture.md` (layers, package layout, data flow)
- Skim `docs/security.md` and `docs/mcp-tools.md` for consistency

**Day 2 — Backend skeleton**
- Spring Boot project with Java 21, Gradle
- Dependencies: Web, Validation, Actuator (Security/JPA added when needed)
- Package structure under `com.ssuai` per CLAUDE.md
- Global exception handler + standard response wrapper
- `/actuator/health` reachable

**Day 3 — Cafeteria menu API with mock connector**
- `MealController`, `MealService`, `MealConnector` interface
- `MockMealConnector` clearly labeled as mock
- DTOs for request/response
- `GET /api/meals/today` returns mock JSON

**Day 4 — Tests for the cafeteria slice**
- Unit test for `MealService`
- Controller test for `/api/meals/today`
- Connector test using a fixture
- Confirm `gradlew.bat test` is green

**Day 5 — Real cafeteria connector (first pass)**
- Implement `RealMealConnector` with Jsoup against the public menu page
- Keep it behind the same `MealConnector` interface
- Add a profile/config flag to switch mock ↔ real
- Handle the "site is down / format changed" case gracefully

**Day 6 — Library book search API (mock first)**
- Same connector pattern as meals
- `GET /api/library/books?query=...` returning mock results
- Tests at the same three layers

**Day 7 — Cleanup, README, and a small commit history**
- Update `README.md` with how to run the backend
- Verify all tests pass
- One small, well-described commit per feature slice
- Write down what was learned and what to tackle in week 2

By the end of week 1, the project should have: a clean backend skeleton, one
real public API (meals), one mocked public API (library books), a working
test setup, and enough documentation that a reviewer can understand the
project's direction in five minutes.
