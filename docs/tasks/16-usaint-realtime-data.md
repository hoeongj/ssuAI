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
`backend/src/test/resources/saint/`. Capture the **real
response**, not a synthetic shape — Task 14 §13 retrospective documents
why synthetic fixtures cost three rewrites at prod-verify time.

### 3.1 PR 16a 1차 spike 결과 (2026-05-16 — 개인수업시간표조회)

**시간표 endpoint 확정**: SAP WebDynpro 컴포넌트 `ZCMW2102` (대문자.
SAP custom Z-namespace component naming), host **`https://ecc.ssu.ac.kr:8443`**
(saint 아님!). portal 의 contentAreaFrame 이 cross-subdomain iframe 으로
`https://ecc.ssu.ac.kr:8443/sap/bc/webdynpro/SAP/ZCMW2102;sap-ext-sid=<transient>?sap-contextid=...`
에 진입. 메뉴 nav ID (개인수업시간표조회) =
`1724938fdd5d98311a8647b31efd21fe`. 같은 wrapper 응답에서 강의시간표
메뉴 ID = `56883564eb5b429e9876b8176235a960` 도 함께 잡힘.

**인증 모델 — cross-subdomain SAP SSO**:

- portal 의 phase 1 / phase 2 가 발급한 cookie 중 `MYSAPSSO2` 가
  domain `.ssu.ac.kr` 으로 set 되어 `ecc.ssu.ac.kr:8443` 에도 자동
  attach. base64 디코드하면 `portal:20221528` 처럼 학번이 들어있는
  SAP NetWeaver 표준 cross-subdomain SSO 토큰.
- 따라서 ssuAI backend connector 는 **별도 ecc 로그인 round-trip 불필요**.
  `SaintSessionStore` 에 portal phase cookies 다 담아두면 ecc 호출
  시 그대로 `Cookie:` 헤더로 보내면 SAP 가 SSO 통과시킴.
- 추가로 ecc 가 발급하는 session cookie 들 (`SAP_SESSIONID_SSP_100`,
  `sap-usercontext=sap-language=KO&sap-client=100`, `WAF`) 도 첫
  ZCMW2102 응답에서 받아 후속 호출까지 유지.

**CSRF 토큰 = `sap-wd-secure-id`** (예: `871202BF3389E157FBEB10D187CEB48A`).
WebDynpro 의 표준 CSRF 토큰. 매 page load 마다 다름. connector 의
sequence:

1. **GET** `.../sap/bc/webdynpro/SAP/ZCMW2102` (cookies 첨부, ecc 세션
   초기화)
2. 응답 HTML 에서 `<input name="sap-wd-secure-id" value="..."/>` 또는
   유사 hidden field parse → 캐싱
3. 응답 HTML 안에 시간표 표가 직접 들어있는지, 아니면 WebDynpro
   partial XHR delta 한두 번 더 보내야 표가 채워지는지는 **별도 확인**
   (사용자 paste 한 cURL 은 partial XHR update 였음 — 페이지 load 후
   client-side init telemetry. 진짜 시간표 표 HTML 의 출처는 첫 GET
   응답 body 를 봐야 확정).

**Custom Headers WebDynpro 가 보내는 것들** (connector 가 흉내내야
할지 별도 spike 필요):

- `SAP-PASSPORT: 2A54482A0300E60000...` — SAP telemetry. 없어도
  동작할 가능성 큼.
- `X-XHR-Logon: accept` — SAP login challenge 처리 모드 (logon ticket
  expire 시 popup 대신 401 JSON 응답 요청). connector 가 401 처리
  파이프 있으면 보내는 게 안전.
- `X-Requested-With: XMLHttpRequest` — XHR 표식. 정적 GET 만 한다면
  생략 가능.

**Request body** (POST 일 때 — partial update sequence):

- `sap-charset=utf-8`
- `sap-wd-secure-id=<CSRF token from step 2>`
- `fesrAppName=ZCMW2102`, `fesrUseBeacon=true` — frontend telemetry,
  생략 가능 추정
- `SAPEVENTQUEUE=...` — WebDynpro client→server event delta. 초기
  page load 만 필요하면 단순 GET 이라 SAPEVENTQUEUE 안 보낸다.

### 3.2 표 parsing structure (fixture 로 잠금)

표 자체의 parsing structure (`backend/src/test/resources/saint/timetable-success.html`):

| Selector | Role | Notes |
|---|---|---|
| `tbody[id$="-contentTBody"]` | 표 컨테이너 | WD-prefix id 는 dynamic 이지만 suffix 는 안정 |
| `tr[rt="2"]` | 헤더 row | 1개. `<th>` 의 `lsdata="{7:'월요일'}"` 가 요일명 |
| `tr[rt="1"]` | 데이터 row | 10개 (1-10교시). 각 row 는 (시간 column + 6요일 column) 7개 cell |
| `td[role="gridcell"][cc="N"]` | cell | `cc=0` 시간 column, `cc=1..6` 월~토 (일요일은 아예 column 없음) |
| `div.lsSTEmptyRow` | 빈 cell | 이 div 가 있으면 해당 요일·교시는 강의 없음 |
| `span.lsTextView--wrap` | 강의 cell | text content 가 `과목명<br>교수<br>시간<br>강의실` 4줄 |

빈 cell vs 강의 cell 의 구분이 명확 (서로 배타적). cc attribute 가
column 매핑을 줘서 헤더-data row 의 column 순서 drift 도 robust.

### 3.3 PR 16b 시작 전 마지막 spike — 결과 (시나리오 a 확정)

ZCMW2102 페이지의 **첫 GET response body** (96KB; gzip transferred
8.3KB) 안에 표 markup 이 **직접 포함**돼있음을 확인:

- `<tbody id="...-contentTBody">` ✓ (시간표 본체)
- `<form action="/sap/bc/webdynpro/SAP/ZCMW2102;sap-ext-sid=...?sap-contextid=...">` ✓
- `<input name="sap-wd-secure-id" value="...">` ✓ (hidden CSRF — read 전용 GET 만 할 거면 안 써도 됨)
- "학년도" / "학기" 텍스트 dropdown UI 도 같이 들어있음 (학기 선택
  parser 확장은 PR 16b 첫 cut 의 scope 밖)

즉 **시나리오 (a) — 단순 GET 한 번으로 표 받음**. WebDynpro partial
XHR delta / SAPEVENTQUEUE / secure-id round-trip 모두 불필요 (read-only
fetch 한정).

**Connector sequence 확정** (PR 16b — 4-5줄):

```java
HttpRequest req = HttpRequest.newBuilder()
    .uri("https://ecc.ssu.ac.kr:8443/sap/bc/webdynpro/SAP/ZCMW2102")
    .header("Cookie", buildCookieHeader(portalCookies))   // SaintSessionStore lookup
    .header("Accept", "text/html")
    .GET()
    .build();
HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
Document doc = Jsoup.parse(res.body());
Elements rows = doc.select("tbody[id$=-contentTBody] tr[rt=1]");
// ... parse each row's td[role=gridcell] cells → ScheduleEntry DTOs
```

### 3.4 학기 이동 (multi-term iterate) — 2차 spike 결과

ssuAI 의 `get_my_schedule` 의 **본질은 입학년도부터 현재 학기까지 누적
시간표** (학번에서 입학 학년 추출 → 현재까지 모든 학기 iterate). 첫
cut 의 단일 학기 GET 만으로는 부족.

**UI 패턴**: 시간표 페이지에 `<select>` dropdown 이 아니라
**PREVIOUS / NEXT 버튼** 으로 학기 nav. 사용자 spike 결과 `WDA7` 가
"이전 학년도" 버튼 (한 번 클릭하면 2026 → 2025 로 학년도 단위 점프.
2026-1 → 2025-1). 즉:

- `WDA7` = 이전 학년도 (학년도 −1, 학기 그대로 유지 추정)
- 계절학기 (여름·겨울) 와 2학기 nav 는 별도 버튼 (`WDA8`/`WDA9` 등)
  일 가능성. PR 16b 통합 테스트 시 응답 HTML 에서 button ID grep 으로
  확정.

**SAPEVENTQUEUE event shape** (사용자 cURL paste 디코드):

```
POST https://ecc.ssu.ac.kr:8443/sap/bc/webdynpro/SAP/ZCMW2102;sap-ext-sid=...
Content-Type: application/x-www-form-urlencoded
Cookie: <portal phase cookies (MYSAPSSO2 등)>

sap-charset=utf-8
sap-wd-secure-id=<current secureId>
fesrAppName=ZCMW2102
fesrUseBeacon=true
SAPEVENTQUEUE=
  MessageArea_Expand~E002Id~E004WD0316~E003...
  ClientInspector_Notify~E002Id~E004WD01~E005Data~E004...
  Button_Press~E002Id~E004WDA7~E003~E002ResponseData~E004delta~E005ClientAction~E004submit~E003~E002~E003
  Form_Request~E002Id~E004sap.client.SsrClient.form~E005Async~E004false~E005FocusInfo~E004~0040~007B~0022sFocussedId~0022~003A~0022WDA7~0022~007D~E005Hash~E004~E005DomChanged~E004false~E005IsDirty~E004false~E003~E002ResponseData~E004delta~E003~E002~E003
```

핵심 event 만 있으면 동작 (다른 ClientInspector_Notify 등은 telemetry,
빠져도 무방 추정 — PR 16b 통합 테스트로 검증). control char 인코딩:

| Token | Decoded | 의미 |
|---|---|---|
| `~E001` | 0x01 | event 구분자 |
| `~E002` | 0x02 | event meta start |
| `~E003` | 0x03 | event meta end |
| `~E004` | 0x04 | key=value 구분자 |
| `~E005` | 0x05 | param 구분자 |
| `~003A` | `:` | URL-encoded |
| `~007B` `~007D` | `{` `}` | URL-encoded JSON wrapper |
| `~0040` | `@` | JSON literal prefix |

**응답 shape — WebDynpro full-update XML wrapper**:

```xml
<updates>
  <full-update windowid="sapwd_main_window">
    <content-update id="sapwd_main_window_root_">
      <![CDATA[ ... 전체 페이지 HTML (시간표 + 새 secure-id + form) ... ]]>
    </content-update>
  </full-update>
</updates>
```

`<full-update>` (= 전체 page re-render) 라 partial DOM op apply 불필요.
CDATA 안 HTML 추출 → Jsoup parse → 첫 GET 과 동일한 selector 로 표 row
+ 새 `sap-wd-secure-id` 추출.

**Multi-term connector pseudo-code** (PR 16b):

```java
List<TermSchedule> fetchAllTerms(String studentId, PortalCookies cookies) {
    int enrollYear = Integer.parseInt(studentId.substring(0, 4));
    LocalDate now = clock.now();
    int currentYear = now.getYear();
    int currentTerm = (now.getMonthValue() <= 8) ? 1 : 2;  // 8월말 이후 2학기

    // 1. 첫 GET — 현재 학기
    String html = httpGet(ZCMW2102_URL, cookies);
    String secureId = parseSecureId(html);
    List<ScheduleEntry> currentRows = parseRows(html);
    List<TermSchedule> all = new ArrayList<>();
    all.add(new TermSchedule(currentYear, currentTerm, currentRows));

    // 2. 학년도 prev (WDA7) 반복 — 입학년도 도달까지
    int year = currentYear;
    while (year > enrollYear) {
        String xml = httpPostSapEvent(ZCMW2102_URL, cookies, secureId,
                                       "Button_Press", "WDA7");
        html = extractCdata(xml);
        secureId = parseSecureId(html);
        currentRows = parseRows(html);
        year--;
        // 학기는 일단 currentTerm (1학기) 가정 — WDA7 가 학년도만 점프
        all.add(new TermSchedule(year, currentTerm, currentRows));
    }
    // 3. 2학기 iterate 는 follow-up — 별도 button (WDA8?) spike 필요
    return all;
}
```

**보안 / 운영 considerations**:

- iterate 가 학번 입학년도까지 ~ 4-5 학년도 → 최대 ~10 POST 요청.
  SaintSessionStore 의 cookie TTL 30분 내에 다 끝나야 함. 한 시간표
  fetch 가 ~10 ecc round-trip = 보통 1-2초. 문제 없음.
- 각 POST 응답에서 새 secure-id 추출해 다음 POST 에 사용 (CSRF
  rotation). 한 step 실패하면 ProgressException → 부분 결과만
  반환 + UI 에 경고.
- `ScheduleService` 가 fetch 한 누적 시간표를 **WeeklyMealCache 처럼
  in-memory cache** (TTL ~6시간) 해서 사용자가 같은 chat session 에서
  여러 번 "내 시간표" 물어도 재 fetch 안 함.
- 학번 → 입학 학년 매핑은 단순 substring (앞 4자리). 휴학·복학으로
  학적이 끊긴 학생도 입학년도는 학번에 박혀있어 변하지 않음.

**PR 16b 첫 cut scope 결정**:

- **단일 학기** (현재 학기) — 위 1번 GET 만, 4-5줄 connector. 가장
  단순.
- **전체 학년도 1학기 iterate** (입학년 1학기 ~ 현재 1학기) — 1번 +
  2번. WDA7 prev 만 사용. 2학기·계절학기는 미포함.
- **전체 학기 누적** (모든 1·2학기, 선택적 계절학기) — WDA8/WDA9 등
  추가 button spike 필요. 별도 PR.

권장: PR 16b 첫 cut = "**전체 학년도 1학기 iterate**". 사용자 의도의
70% cover, code 복잡도 적당. 2학기 nav 는 follow-up spike + PR 로.

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
