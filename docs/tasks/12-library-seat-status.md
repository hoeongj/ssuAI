# Task 12 — Library seat status (read-only MCP tool)

> First task of Phase 2 from [`docs/vision.md`](../vision.md). This task
> intentionally stops at **read-only** seat status. The actual
> `reserve_library_seat` action tool is Phase 4 and lives in a separate
> task spec.

## 1. Goal / Scope / Non-goals

### Goal

Expose a read-only `get_library_seat_status` MCP tool that returns the
current number of available seats per floor (and, where the source page
supports it, per zone or seat id) for the Soongsil University library.

This unblocks two things that depend on seat data existing first:

- chatbot conversational queries like *"지금 도서관 4층에 자리 있어?"*
- Phase 4 flagship — the agent's seat picker needs a known list of seat
  identifiers to reserve

### In scope

- New `LibrarySeatConnector` interface + real Jsoup-based implementation +
  deterministic mock implementation
- REST endpoint `GET /api/library/seats?floor={n}` returning
  `ApiResponse<LibrarySeatStatusResponse>` (the dashboard and chatbot
  consume the same shape)
- MCP tool `get_library_seat_status(floor)` wired through `LibrarySeatMcpTool`
- Short-TTL caching — seat occupancy is volatile, so the cache must avoid
  serving stale data to a student about to walk over there
- Tests for the connector (HTTP + parse + timeout), the service, the
  controller, the MCP tool, and the cache behavior

### Non-goals

- ❌ Seat reservation. Not in this task. Phase 4 only.
- ❌ User authentication, sessions, or any private credential handling.
  The seat-status endpoint must work for anonymous public users without
  ever touching `u-saint` or library login.
- ❌ Real-time push / SSE updates. Polling on the client is fine.
- ❌ Historical occupancy graphs.
- ❌ A library "where is this book?" search — separate task.

## 2. API design

### REST

```http
GET /api/library/seats?floor=4
```

Response (success):

```json
{
  "success": true,
  "data": {
    "floor": 4,
    "totalSeats": 36,
    "availableSeats": 12,
    "reservedSeats": 18,
    "outOfServiceSeats": 6,
    "fetchedAt": "2026-05-15T07:30:14Z",
    "zones": [
      { "label": "창가", "available": 3, "total": 8, "seatIds": ["412", "415", "418"] },
      { "label": "중앙", "available": 9, "total": 28, "seatIds": [] }
    ]
  }
}
```

- `floor` is required. Validation: integer, range `[B1, 1, 2, 3, 4, 5, 6]`
  (use a `LibraryFloor` enum if floor labels are not pure integers).
- `zones[].seatIds` may be empty when the source page only gives us
  aggregate counts. Where individual seat ids are available they MUST be
  preserved so Phase 4 can reference them.
- `fetchedAt` is the connector's scrape time, not the controller's
  response time. This is what the cache key invalidation uses.

Error contract — reuses existing `ConnectorException` family:

- `LIBRARY_SEAT_UPSTREAM_TIMEOUT` → 504
- `LIBRARY_SEAT_UPSTREAM_UNAVAILABLE` → 502
- `LIBRARY_SEAT_PARSE_FAILED` → 502 (logged with the failing HTML
  fingerprint, never the full page body)
- `LIBRARY_SEAT_INVALID_FLOOR` → 400

### MCP

Tool name: `get_library_seat_status`.

Input schema:

```json
{
  "type": "object",
  "properties": {
    "floor": {
      "type": "integer",
      "description": "Library floor number. Allowed values: -1 (B1), 1, 2, 3, 4, 5, 6."
    }
  },
  "required": ["floor"],
  "additionalProperties": false
}
```

Output: same JSON body as REST, returned as `TextContent` (matches how
existing `MealMcpTools` returns its payload).

## 3. Package and class responsibilities

```
backend/src/main/java/com/ssuai/domain/library/
├── connector/
│   ├── LibrarySeatConnector.java          # interface
│   ├── MockLibrarySeatConnector.java      # deterministic fixture
│   └── RealLibrarySeatConnector.java      # Jsoup, retries, timeouts
├── controller/
│   └── LibrarySeatController.java         # GET /api/library/seats
├── service/
│   ├── LibrarySeatService.java            # cache + connector orchestration
│   └── LibrarySeatCache.java              # short-TTL in-memory cache
└── dto/
    ├── LibraryFloor.java                  # enum + validator
    ├── LibrarySeatStatusResponse.java
    └── LibrarySeatZone.java
```

MCP wiring goes alongside existing tool beans:

```
backend/src/main/java/com/ssuai/domain/mcp/tool/
└── LibrarySeatMcpTool.java                # @Tool annotation + delegates to service
```

Responsibility split:

- **Connector** — pure HTTP + parse. No caching. No business decisions.
  Throws `ConnectorTimeoutException`, `ConnectorUnavailableException`,
  `ConnectorParseException`. Mirrors `RealMealConnector` patterns.
- **Service** — calls cache first, falls back to connector, normalizes
  errors, hands typed DTOs upward.
- **Cache** — keyed by floor, TTL **≤ 30 seconds** (see §4).
- **Controller** — request validation, ApiResponse envelope.
- **MCP tool bean** — converts MCP input schema to service call.

## 4. Data flow + cache policy

```
client (chatbot or dashboard)
  ↓ GET /api/library/seats?floor=4   (or MCP tool call)
LibrarySeatController / LibrarySeatMcpTool
  ↓
LibrarySeatService
  ↓ cache miss / expired?
LibrarySeatCache  ←──── (hit) return cached snapshot
  ↓ miss
RealLibrarySeatConnector
  ↓ Jsoup GET (library seat page for floor)
parse HTML → LibrarySeatStatusResponse
  ↓ store in cache with fetchedAt timestamp
return to client
```

### Cache TTL — explicit decision needed in implementation

Seat occupancy is volatile. Two opposing constraints:

- **Freshness** — a student about to walk over wants now-accurate data.
- **Politeness** — we cannot hammer the library page at chat-message
  frequency.

Default: **TTL = 30 seconds, per floor**. Tunable via
`ssuai.library.seat.cache-ttl`. Document in `application.yml`. If during
implementation the upstream proves unstable or rate-limits, drop to 15s
and add jittered retry.

Cache implementation: in-memory `ConcurrentHashMap<LibraryFloor, Entry>`
similar to `WeeklyMealCache`. Do **not** use a generic cache framework
(Spring Cache + Caffeine) — the TTL semantics are tight enough that the
visible explicit cache is the right portfolio surface.

## 5. Security considerations

Cross-reference [`docs/security.md`](../security.md).

- **No credentials touch this code path.** The seat status page is
  served by the library to anonymous users. If during research it turns
  out the page requires login, **stop and flag** — that pushes this task
  into Phase 3 territory.
- **Logging policy** (`docs/security.md` §4):
  - Log: floor, request count, latency, success/failure, parse-failure
    HTML fingerprint (≤ 32 chars of a body hash).
  - Never log: full HTML response, user IP, session cookies, anything
    user-identifying. The endpoint is anonymous; the only request data
    is the `floor` query param.
- **Rate-limit posture toward the upstream**:
  - Single-flight per floor: if a request is in flight for floor N, a
    second concurrent miss for floor N must wait for the first instead
    of starting a parallel scrape.
  - Per-floor TTL ≥ 15s means at most 4 upstream calls per floor per
    minute regardless of how many students hit the API.
- **Floor enum validation** at the controller — never pass an arbitrary
  string to the upstream URL. SSRF surface check.
- **No outbound URL parameters built from user input** beyond the
  validated floor enum.

## 6. Test plan

Tests must cover:

1. **`MockLibrarySeatConnectorTests`** — deterministic fixture returns
   stable response for each floor; matches the documented JSON shape.
2. **`RealLibrarySeatConnectorHttpTests`** — `@SpringBootTest` with
   MockWebServer:
   - happy path → parsed DTO
   - 5xx → `ConnectorUnavailableException`
   - timeout (slow response) → `ConnectorTimeoutException`
   - malformed HTML → `ConnectorParseException`
3. **`RealLibrarySeatConnectorParseTests`** — pin known good HTML
   fixtures under `src/test/resources/library/` and assert parsed seat
   counts and zone breakdown.
4. **`LibrarySeatCacheTests`** — TTL hit, TTL miss, per-floor scoping,
   single-flight under concurrent misses.
5. **`LibrarySeatServiceTests`** — orchestrates cache + connector;
   verify cache HIT does not call connector; verify exceptions bubble up
   typed.
6. **`LibrarySeatControllerTests`** — `@WebMvcTest`:
   - 200 on valid floor
   - 400 on missing/invalid floor
   - 502/504 mapping for connector errors via `GlobalExceptionHandler`
7. **`LibrarySeatMcpToolTests`** — input schema validation; output is a
   single `TextContent` matching REST JSON.
8. **`McpSelfDogfoodTests`** — extend the existing self-dogfood test to
   include `get_library_seat_status` in the list of expected tools and
   to round-trip a `floor=4` call.

Manual verification after deploy:

- `curl -s "https://ssumcp.duckdns.org/api/library/seats?floor=4" | jq`
- In `ssuai.vercel.app/chat`: ask *"4층 자리 있어?"* and confirm the
  bot returns a count without hallucinating seat ids.

## 7. Implementation plan

Two PRs, sequenced. Skip the multi-step hand-off framing — implement
directly. Use internal `TaskCreate` to track sub-steps inside each PR.

> **Status (2026-05-15)** — PR 1 is split into two slices because the
> upstream library URL was not yet available when implementation
> started:
>
> - **PR 1a (in flight, `feat/library-seat-mock-slice`)** — everything
>   that doesn't need the URL: enum, DTOs, connector interface, **mock
>   connector**, cache, service, controller, MCP tool, ADR 0012, full
>   test suite end-to-end against the mock. `ssuai.connector.library-seat`
>   defaults to `mock`; flipping to `real` will fail-fast until 1b
>   lands.
> - **PR 1b (blocked on URL)** — `RealLibrarySeatConnector` (Jsoup,
>   timeouts, retry), pinned HTML fixtures under
>   `src/test/resources/library/`, `RealLibrarySeatConnectorHttpTests`
>   and `RealLibrarySeatConnectorParseTests`. Stop-and-flag conditions
>   in §8 still apply during 1b research.

### PR 1 — Backend slice (DTOs → connector → service → controller → MCP)

One reviewable PR that takes the feature end-to-end on the backend:

- `LibraryFloor` enum + Spring `Converter<String, LibraryFloor>`
- `LibrarySeatZone`, `LibrarySeatStatusResponse` records
- `LibrarySeatConnector` interface + `MockLibrarySeatConnector`
- `RealLibrarySeatConnector` with Jsoup, timeouts (3s connect, 5s read),
  one retry on 5xx with jittered backoff. Pin one HTML fixture per floor
  under `src/test/resources/library/`. Research the library seat page
  URL/shape first; if it requires login or returns JSON instead of HTML,
  stop and re-scope per §8.
- Property `ssuai.connector.library-seat: mock|real`, default `mock`
- `LibrarySeatCache` (`ConcurrentHashMap<LibraryFloor, Entry>`,
  single-flight CAS) + `LibrarySeatService` (cache → connector)
- Property `ssuai.library.seat.cache-ttl` defaulting to `30s`
- `LibrarySeatController` exposes `GET /api/library/seats?floor=N`
- `GlobalExceptionHandler` mappings for the new error codes
- `LibrarySeatMcpTool` with `@Tool` annotation + input schema
- Extend `McpSelfDogfoodTests` and `chatTools()` discovery to include
  the new tool
- Tests for all of: mock connector, real connector HTTP/parse, cache,
  service, controller, MCP tool, self-dogfood
- Swagger / OpenAPI annotations matching existing endpoints
- ADR `docs/adr/0012-library-seat-tool.md` documenting the cache TTL
  choice and the read-only-only boundary; dev-log + README entry

If during implementation the PR diff grows past a comfortable review size,
split off the controller+MCP wiring as PR 1b; otherwise keep it together.

### PR 2 — Frontend dashboard card (optional, same wave)

- Dashboard card "도서관 좌석" showing per-floor availability with
  client-side polling (every 30s or on focus)
- Defer to a separate `13-frontend-library-seats.md` spec only if the
  backend PR slipped or the frontend slice grows.

## 8. Stop and flag

Stop and check in with the user before pushing if any of the following
happens during PR 1:

- Library seat page requires authentication or returns a CAPTCHA.
- HTML structure varies wildly between floors and a single parser cannot
  cover them — needs a per-floor strategy decision.
- Upstream returns rate-limit errors at TTL=30s — the politeness budget
  needs revisiting.
- Discovery that the library exposes a JSON/AJAX endpoint instead of an
  HTML page — switch from Jsoup to `RestClient` and update §4 + §5.
