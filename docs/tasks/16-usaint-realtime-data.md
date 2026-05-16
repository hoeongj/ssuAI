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

### 3.5 성적 (`get_my_grades`) — 단일 GET 으로 전체 누적 fetch

성적은 시간표와 결정적으로 다름: **multi-term iterate 가 불필요**.
페이지 자체에 모든 학기 누적 GPA history 가 한 번에 표시됨.

**엔드포인트 확정** (사용자 brower spike, 2026-05-16):

- Host: `https://ecc.ssu.ac.kr:8443` (시간표와 동일)
- Component: **`ZCMB3W0017`** (시간표의 `ZCMW2102` 와 다른 SAP 모듈 —
  성적 전용)
- 응답 shape: 시간표와 동일 WebDynpro full-update XML wrapper
  (`<updates><full-update><content-update><![CDATA[...HTML...]]>`)
- 인증·CSRF: 시간표와 동일 (MYSAPSSO2 + sap-wd-secure-id)

**페이지 영역 2개**:

| 영역 | 내용 | 컬럼 (lsdata 에서 추출) |
|---|---|---|
| 상단 "학기별 성적" | 입학년도 ~ 현재 학기 모든 학기의 GPA history (한 번에 다 보임) | 학년도 / 학기 / 신청학점 / 취득학점 / P·F학점 / 평점평균 / 평점계 / 학기별석차 |
| 하단 "학기별 세부 성적" | 선택된 한 학기의 과목별 상세 (학기 dropdown 변경 + "조회" 버튼 클릭 후 갱신) | 성적 / 상세성적 / 과목학점 / 과목명 / 교수 / 등 |

추가로 응답 안 (separate 학적 통계 row):

- **학적부 누적 통계**: 학적부신청학점 / 학적부취득학점 / 학적부평점평균
  / 학적부평점계 / 학적부P·F학점 — 졸업요건 기준
- **증명용 누적 통계**: 증명신청학점 / 증명취득학점 / 증명평점평균 /
  증명평점계 / 증명P·F학점 — 재수강 / P·F 처리 차이를 반영한 외부
  증명서 기준. 학적부와 다를 수 있음.

**학기 코드 enum** (lsdata `'4':'091'/'092'` 등에서 추출):

| 코드 | 의미 |
|------|------|
| `091` | 1학기 |
| `092` | 2학기 |
| `093` | 여름계절학기 (추정 — 실제 spike 시 확정) |
| `094` | 겨울계절학기 (추정) |

학년도는 4자리 숫자 그대로 (`2025` 등).

**Connector pseudo-code** (PR 16c — 단일 GET):

```java
public GradesResponse fetchGrades(String studentId, PortalCookies cookies) {
    String html = httpGet(ZCMB3W0017_URL, cookies);
    Document doc = Jsoup.parse(html);

    // 상단: 학기별 성적 누적 표 (모든 학기 GPA history)
    Elements termRows = doc.select("[id$=_GL] [lsdata*=학기별 성적] tbody tr[rt=1]");
    List<TermGpa> byTerm = termRows.stream()
        .map(GradesParser::parseTermRow)
        .toList();

    // 통계 row 들 — `학적부평점평균` / `증명평점평균` 등 ID 로 매칭
    GpaSummary academicRecord = parseSummary(doc, "학적부");  // WD0147~WD015C
    GpaSummary certificate = parseSummary(doc, "증명");        // WD0168~WD017D

    // 하단: 현재 학기 세부 (default 가 가장 최근 학기) — optional
    List<CourseGrade> currentDetails = parseDetailsTable(
        doc.select("[lsdata*=학기별 세부 성적] tbody tr[rt=1]"));

    return new GradesResponse(byTerm, academicRecord, certificate, currentDetails);
}
```

**시간표 (§3.4) 와의 차이 요약**:

| 항목 | 시간표 (`get_my_schedule`) | 성적 (`get_my_grades`) |
|---|---|---|
| Component | `ZCMW2102` | `ZCMB3W0017` |
| Multi-term iterate | 필요 (WDA7 prev N회) | **불필요** (단일 GET 으로 전체) |
| 누적 통계 | 없음 (학기별 표만) | 있음 (학적부 + 증명용 각각) |
| 학기 UI | PREV/NEXT 버튼 (WDA7) | listbox dropdown (학년도/학기) + 조회 버튼 |
| 응답 단위 | 한 학기 표 | 전체 학적 + 한 학기 세부 |

**LLM prompt 정책 재확인** (Task 16 §6 #6 + §8 동일):

- `get_my_grades` MCP tool 응답으로 LLM 에 보내는 것 = **요약만**:
  `{ "totalTerms": int, "cumulativeGpa": double (학적부 기준),
     "creditsEarned": int, "link": "/grades" }`
- 학기별 GPA history / 과목별 성적 / 등급 / 석차 등 **세부 데이터는
  controller path 로만** (`GET /api/saint/grades` → dashboard card +
  REST consumer). LLM 토큰 스트림에 절대 안 들어감.
- `LlmChatService.compactToolResponse` 의 `get_my_grades` 분기 단위
  테스트로 고정 — 회귀 시 CI 차단.

### 3.5.1 PR 16c spike 결과 잠금 (2026-05-16)

브라우저 spike (Network 탭 → ZCMB3W0017 응답 Copy response, raw 180KB)
결과로 §3.5 의 추정 사항들이 다음과 같이 확정/갱신됨. fixture 는
`backend/src/test/resources/saint/grades-success.html` 에 잠금 (PII 치환,
시간표 fixture 와 동일 패턴 — 핵심 anchor 보존).

**확정 — 시간표와 같은 부분**:

- Host + Component: `https://ecc.ssu.ac.kr:8443/sap/bc/webdynpro/SAP/ZCMB3W0017` ✓
- 인증: cross-subdomain MYSAPSSO2 (시간표와 동일 패턴, 별도 ecc 로그인 X)
- CSRF: `<input id="sap-wd-secure-id" value="...">` (페이지 안에 들어있음)
- 표 anchor: `tbody[id$="-contentTBody"]`, `tr[rt="2"]` 헤더 / `tr[rt="1"]`
  데이터, `td[role="gridcell"][cc="N"]` cell, `<span class*="lsTextView">`
  text content — 시간표 §3.2 selector 패턴 그대로 재사용 가능
- 빈 cell anchor: `<div class="lsSTEmptyRow">` 동일

**갱신 — 시간표와 다른 부분 (중요)**:

1. **첫 GET 응답이 WebDynpro full-update XML wrapper 형태** — 시간표
   첫 GET 은 raw HTML, WDA7 POST 응답만 wrapper 였는데 ZCMB3W0017 는
   **첫 GET 부터** wrapper. connector 가 `WebDynproResponseUnwrapper.extractHtml()`
   통과 후 parse 해야 함.

   ```xml
   <updates>
     <full-update windowid="sapwd_main_window">
       <content-update id="sapwd_main_window_root_">
         <![CDATA[ ... HTML (표 + secure-id + form) ... ]]>
       </content-update>
     </full-update>
   </updates>
   ```

2. **상단 학기별 표 컬럼 14개** (cc=0..13) — §3.5 의 8개 추정에서
   확장:

   | cc | 컬럼 | 비고 |
   |----|------|------|
   | 0 | (선택 컬럼) | 행 선택 토글, parser skip |
   | 1 | 학년도 | "2025", "2022" 같은 4자리 |
   | 2 | 학기 | **한국어 텍스트** ("1학기" / "2학기" / "여름학기" / "겨울학기") — `091/092/093/094` enum 가정 폐기 |
   | 3 | 신청학점 | "19.5" |
   | 4 | 취득학점 | |
   | 5 | P/F학점 | |
   | 6 | 평점평균 | P/F 학기는 "0.00" |
   | 7 | 평점계 | |
   | 8 | 산술평균 | 신규 (§3.5 에 없던 컬럼) |
   | 9 | 학기별석차 | "70&#x2f;140" (HTML-encoded "/") |
   | 10 | 전체석차 | 신규 |
   | 11 | 학사경고여부 | 신규 (정상 학적은 빈 cell) |
   | 12 | 상담여부 | 신규 |
   | 13 | 유급 | 신규 |

3. **학적부/증명 통계는 별도 grid** (학기별 표 아래의 두 개 group
   layout container) — `<label for="WDxxx">` ↔ `<input id="WDxxx" value="N">`
   쌍. label.lsdata 의 `3:'학적부신청학점'` 텍스트 또는 label 자식
   span text 가 anchor. 산술평균 행도 포함 (학적부산술평균 + 증명산술평균
   = 신규).

4. **학기별 세부 표 default 빈 상태** — 페이지 첫 로드 시 `<tbody id="...-contentTBody">`
   안에 헤더 row (rt="2") 만 있고 data row (rt="1") 가 0개. 학년도/학기
   listbox + 조회 button 을 click 해야 채워짐. → **PR 16c 단일 GET 만으로
   세부 성적을 못 받음.** 첫 cut scope 에서 제외:

   | PR 16c 첫 cut | follow-up PR |
   |---|---|
   | 학기별 GPA history (전체) | 학기별 세부 (과목별 성적) |
   | 학적부 누적 통계 (6 항목) | 학기 dropdown nav + "조회" button-press POST iterate (시간표 WDA7 와 동일 패턴) |
   | 증명용 누적 통계 (6 항목) | |

5. **응답 본문에 학번/이름 미포함** — spike grep 결과 0 hit. portal
   wrapper (portal-success.html) 가 학번/이름 추출 담당. ZCMB3W0017
   응답은 grade 통계만. fixture redaction 부담 작음 (수치만 placeholder).

**메뉴 nav ID** (portal contentAreaFrame 의 PrevNavTarget 에서 캡처):
`65c7bab0180b19f422443f400d4d041c` (학기별 성적조회). 시간표 메뉴 ID
들 (개인수업시간표조회 `1724938fdd5d98311a8647b31efd21fe`, 강의시간표
`56883564eb5b429e9876b8176235a960`) 과 함께 portal nav ID 목록에 추가.

**URL query params** (`sap-sessioncmd=USR_ABORT` 등) 은 portal 의 iView
transient ID — 매 nav 마다 바뀜. connector 는 base path (`/sap/bc/webdynpro/SAP/ZCMB3W0017`)
만 쓰고 query 는 ecc 가 자체 생성. 시간표 RealSaintScheduleConnector 와
동일 패턴.

### 3.5.2 학기별 세부 iterate — 2차 spike 결과 (2026-05-16)

3.5.1 의 "PR 16c 첫 cut 은 학기별 GPA history + 학적부/증명만, 세부는
follow-up" 가정은 **iterate 가능 확인 후 폐기**. PR 16c 첫 cut 에 학기별
세부도 포함. 이전학기 button-press POST 한 번 spike 결과로 path
확정됨.

**Button IDs** (raw fixture 안 `<div ct="B" id="...">` element):

| 버튼 | WD id | lsdata | 비고 |
|------|-------|--------|------|
| 이전학기 | `WD01F0` | `{0:'이전학기',2:'PREVIOUS'}` | 시간표 WDA7 와 동일 동작 — Button_Press event |
| 다음학기 | `WD01F2` | `{0:'다음학기',2:'NEXT'}` | follow-up (현재 학기 후 학기 = 미래, PR 16c 첫 cut 에선 미사용) |
| 조회(새로고침) | `WD0187` | `{0:'조회\x28새로고침\x29'}` | dropdown 변경 후 명시 갱신 — prev/next button-press 자체가 자동 갱신 트리거라 PR 16c connector 는 사용 안 함 |

모두 `lsevents={Press:[{ResponseData:'delta',ClientAction:'submit'}]}`
— 시간표 WDA7 와 같은 form submit 패턴. `WebDynproSapEventEncoder.encodeButtonPress`
재사용 가능 (button id 만 인자로).

**Prev 응답 shape** (raw 226KB, fixture `grades-prev-success.html` 5KB
minimal):

- `<updates><full-update><content-update><![CDATA[...]]>` wrapper —
  첫 GET 과 동일. `WebDynproResponseUnwrapper.extractHtml()` 통과.
- 학기별 세부 표 (`tbody[id$="-contentTBody"]`) **data row 채워짐**
  (이번 spike 결과 = 8 row, 2025-2학기 정규학기 분량).
- 학기별 GPA history 표 + 학적부/증명 통계 = 첫 GET 과 **동일 내용**
  (변경 없음). 즉 connector 가 prev 응답에서 GPA history / 통계 다시
  파싱할 필요 없음 — 첫 GET 결과 재사용.
- `<input id="sap-wd-secure-id" value="...">` rotation — 매 prev 마다
  새 CSRF (시간표 WDA7 와 동일).

**세부 row 컬럼 매핑** (7개, cc=1..7. cc=0 은 선택 컬럼 skip):

| cc | 컬럼 | 예시 (PII redacted) |
|----|------|---------------------|
| 1 | 성적 (점수, 0-100) | "95" / 또는 P/F 학기는 "P" |
| 2 | 등급 | "A+" / "A0" / "A-" / "B+" / "B0" / "P" / "F" |
| 3 | 과목명 | "[교양영역]과목A" (대괄호 prefix 가능) |
| 4 | 상세성적 (학수번호) | "21500001" 8자리 numeric |
| 5 | 과목학점 | "3.0", "0.5", "2.0" |
| 6 | 교수명 | "김교수" |
| 7 | 비고 | 대부분 빈 cell (`<div class="lsSTEmptyRow">`) |

**Connector pseudo-code** (PR 16c 첫 cut, 시간표 RealSaintScheduleConnector
의 prev iterate 패턴 그대로):

```java
GradesResponse fetchGrades(String studentId, PortalCookies cookies) {
    // 1. 첫 GET (응답은 이미 WebDynpro full-update XML wrapper)
    HttpResponse<String> first = httpGet(ZCMB3W0017_URL, cookies);
    String mergedCookies = mergeSetCookies(cookies.rawCookieHeader(),
            first.headers().allValues("Set-Cookie"));
    String html = WebDynproResponseUnwrapper.extractHtml(first.body());
    guardAuthOrThrow(html);  // 학기별 표 anchor 없으면 SaintSessionExpired

    // 2. 학기별 GPA history + 학적부/증명 누적 통계 (전체)
    List<TermGpa> history = GradesParser.parseTermHistory(html);
    GpaSummary academicRecord = GradesParser.parseAcademicSummary(html);
    GpaSummary certificate = GradesParser.parseCertificateSummary(html);
    Optional<String> secureId = WebDynproResponseUnwrapper.extractSecureId(html);

    // 3. default 학기 세부 (첫 GET 응답이 비어있을 수도 — P/F 학기 case)
    Map<String, List<CourseGrade>> details = new LinkedHashMap<>();
    List<CourseGrade> defaultDetails = GradesParser.parseDetailRows(html);
    if (!defaultDetails.isEmpty()) {
        details.put(history.get(0).termKey(), defaultDetails);
    }

    // 4. 이전학기 button-press iterate — history 의 나머지 학기들
    int hops = 0;
    for (int i = 1; i < history.size() && hops < MAX_PREV_HOPS; i++) {
        if (secureId.isEmpty()) break;
        String xml = httpPostButtonPress(mergedCookies, secureId.get(),
                                         PREV_TERM_BUTTON_ID);  // WD01F0
        html = WebDynproResponseUnwrapper.extractHtml(xml);
        secureId = WebDynproResponseUnwrapper.extractSecureId(html);
        List<CourseGrade> rows = GradesParser.parseDetailRows(html);
        if (!rows.isEmpty()) {
            details.put(history.get(i).termKey(), rows);
        }
        hops++;
    }
    return new GradesResponse(history, academicRecord, certificate, details);
}
```

**MAX_PREV_HOPS** = `history.size()` 또는 안전 상한 (~20). 학기별
GPA history 가 N 학기라면 N-1 번 prev iterate 면 전 학기 다 받음.
시간표 multi-term iterate 의 MAX_PREV_YEAR_HOPS=10 패턴과 동일한 안전
상한.

**학기 키 (termKey)** = `"${year}-${semester}"` 형태 (예 "2025-2학기",
"2022-여름학기"). history 의 row 가 학년도 + 학기 컬럼을 명시 보유하므로
parser 가 그대로 추출.

**세부 응답에 학기 라벨이 없는 이슈** — 응답 자체에 "이 row 들이 어느
학기" 라는 직접 라벨이 없음 (header 부 학기 dropdown 의 selected 값을
보면 알 수 있으나 selector 가 무겁고 fragile). connector 가 **호출
횟수 + history 인덱스** 로 학기를 외부에서 매핑하는 게 더 robust —
시간표의 "WDA7 N회 후 학년도 = currentYear - N" 와 같은 외부 추적
패턴.

**PR 16c 첫 cut scope (3.5.1 의 갱신)**:

| 항목 | 들어감 |
|------|--------|
| 학기별 GPA history (전체) | ✓ |
| 학적부 누적 통계 (6 항목) | ✓ |
| 증명용 누적 통계 (6 항목) | ✓ |
| 학기별 세부 성적 (모든 학기, prev iterate) | ✓ |
| 다음학기 (NEXT) iterate | ✗ — 미래 학기 = 미수강 학기, 의미 없음 |
| 학기 dropdown 임의 점프 (학년도/학기 listbox 변경) | ✗ — prev 만으로 전 학기 cover, 임의 점프 불필요 |

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

- [x] `SSUAI_CREDENTIAL_ENCRYPTION_KEY` env required in prod. Dev/test
      ephemeral fallback acceptable. — `SaintSessionStore.buildAesKey` 빈
      값일 때 SecureRandom AES-256 key + 경고 로그.
- [x] Portal cookies encrypted at rest (AES-GCM, per-record IV). —
      `SaintSessionStore` AES/GCM/NoPadding 256-bit, 96-bit IV per record.
- [x] Cookie store TTL ≤ 30 minutes. Expired entries pruned on read. —
      `SaintSessionProperties.ttl` default 30분, `cookies()` 가 expiry 체크
      후 expired 면 remove.
- [x] **Never log** the cookie value, the schedule rows, the grade rows.
      Log only counts/shape: `connector=saint-schedule status=ok rows=12`. —
      `RealSaintScheduleConnector` / `RealSaintGradesConnector` 모든
      log 가 `studentFp={fingerprint}` + count/reason 만, raw row/cookie X.
- [x] `LlmChatService.compactToolResponse` budgets schedule rows
      conservatively; **grades are never included in LLM prompts** —
      tool calls return content to the *controller* path only. —
      compactAndCap 의 `get_my_schedule` 분기 (dayOfWeek/period/course/room
      만) + `get_my_grades` 분기 (`{count, link}` 만) PR #130 잠금, 단위
      테스트 3개로 영구 고정.
- [ ] Tool-call audit on grades — when chat invokes `get_my_grades`,
      log only "user X requested grades", not what it returned. —
      MCP tool 등록 (chat thread-local pattern 갖춰진 뒤 별 PR) 시점에
      executeToolCall 분기에서 audit log 추가.
- [x] Test fixtures contain only `20999999` / 홍길동 / placeholder
      grades. Never a real student row. — `grades-success.html` /
      `grades-prev-success.html` / `timetable-success.html` 다 placeholder.
- [x] `gitleaks` rule already catches Anthropic keys; verify it doesn't
      false-positive on encrypted-cookie hex blobs in fixtures. —
      `.gitleaks.toml` extends default; 모든 PR 의 pre-commit hook 에서
      통과 (fixtures 에는 encrypted blob 자체가 없고, plaintext placeholder
      값만 있음).

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
