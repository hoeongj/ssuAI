# ssuAI MCP Tools

## 1. 개요
ssuAI MCP server 는 숭실대학교 학생을 위한 캠퍼스 정보 조회 기능을 MCP(Model Context Protocol) tool 로 노출한다. Claude Desktop, Cursor, MCP inspector 같은 MCP client 는 이 서버에 붙어서 학식, 기숙사 식단, 캠퍼스 시설 정보를 대화 중에 조회할 수 있다.

MCP server 는 별도 프로세스가 아니라 기존 ssuAI Spring Boot backend 안에서 REST API 와 함께 실행된다. backend 를 띄우면 REST endpoint 와 MCP endpoint 가 같은 JVM 안에서 같이 살아난다.

현재 transport 는 SSE(Server-Sent Events) / Streamable HTTP 계열이며 기본 endpoint 는 `http://localhost:8080/sse` 이다. 이 문서는 local 개발 환경에서 MCP server 를 띄우고 MCP inspector, Claude Desktop, Cursor 에 연결하는 절차만 다룬다.

## 2. 현재 노출 tool 10개

MCP server 는 read-only tool 10개를 제공한다. 응답 DTO 의 자세한 JSON shape 는 Java record 가 source of truth 이므로 도메인 타입명만 적는다.

### 공개 tool (인증 불필요)

| tool name | 설명 | 인자 | 응답 DTO |
| --- | --- | --- | --- |
| `get_today_meal` | 오늘 숭실대학교 캠퍼스 식당 메뉴를 조회한다. `restaurant` 를 비우면 전체 식당, 지정하면 해당 식당만 반환한다. | `restaurant` (선택): 학생식당 / 숭실도담식당 / 스낵코너 / 푸드코트 / THE KITCHEN / FACULTY LOUNGE | `MealResponse` |
| `get_meal_by_date` | 지정한 날짜의 숭실대학교 캠퍼스 식당 메뉴를 조회한다. `restaurant` 동작은 `get_today_meal` 과 동일. | `date`: `yyyy-MM-dd` (예: `2026-05-07`), `restaurant` (선택): 위와 동일 | `MealResponse` |
| `get_dorm_weekly_meal` | 레지던스홀 기숙사 식당의 이번 주 주간 메뉴를 조회한다. | 없음 | `WeeklyMealResponse` |
| `search_campus_facilities` | 식당, 카페, 편의점, 서점, 복사/출력 시설 등을 검색한다. `query` 가 비어 있으면 전체 시설을 반환한다. | `query`: 선택 문자열 | `CampusFacilityListResponse` |
| `get_library_seat_status` | 중앙도서관의 층별 좌석 현황 (전체/이용 가능/예약/사용 불가) 과 구역별 분포를 반환한다. 읽기 전용; 예약은 Phase 4 의 별도 도구. | `floor`: 정수 (`-1` B1, `1`~`6` 1~6층) | `LibrarySeatStatusResponse` |
| `search_library_book` | 중앙도서관 소장 도서를 키워드로 검색한다 (Pyxis JSON API, 익명 GET). 제목·저자·청구기호·소장 위치·대출 가능 여부를 반환한다. | `query`: 검색어 (1~64자), `page` (선택, 0부터), `size` (선택, 최대 20) | `LibraryBookSearchResponse` |

### 인증 필요 tool (u-SAINT 로그인)

chat 세션에서만 동작. 외부 MCP client 에서는 context 미바인딩으로 `IllegalStateException` 반환.

| tool name | 설명 | 인자 | 응답 DTO |
| --- | --- | --- | --- |
| `get_my_schedule` | 로그인된 학생의 전 학기 시간표 (과목·요일·교시·강의실) 를 반환한다. | 없음 | `SaintScheduleResponse` |
| `get_my_grades` | 로그인된 학생의 누적 GPA + 학기별 과목 수를 반환한다. **보안**: LLM 에는 과목 수와 `/grades` 링크만 전달 (점수·등급·교수명 차단). | 없음 | `GradesResponse` |

### 인증 필요 tool (LMS 로그인)

| tool name | 설명 | 인자 | 응답 DTO |
| --- | --- | --- | --- |
| `get_my_assignments` | 로그인된 학생의 현재 학기 미제출 과제·퀴즈 목록을 반환한다. 과목명·제목·유형·마감일 포함. | 없음 | `AssignmentsResponse` |

### 인증 필요 tool (도서관 세션 연동)

| tool name | 설명 | 인자 | 응답 DTO |
| --- | --- | --- | --- |
| `get_my_library_loans` | 연동된 사용자의 중앙도서관 대출 현황을 반환한다. 도서 제목·반납 기한·연장 가능 여부 포함. | 없음 | `LibraryLoansResponse` |

학식 데이터는 `WeeklyMealCache` 가 애플리케이션 시작 시점과 매주 월요일 06:00 KST 에 일괄 적재하므로, `get_today_meal` / `get_meal_by_date` 호출은 일반적으로 캐시 히트로 응답한다.

### 예정된 도구 (Phase 4)

<!-- markdownlint-disable MD013 MD060 -->

| tool name | 종류 | 설명 |
| --- | --- | --- |
| **`reserve_library_seat`** | **write (flagship)** | **도서관 사이트에 좌석 예약 POST 자동 수행** |
| `cancel_library_seat_reservation` | write | 본인이 잡은 좌석 취소 |
| `extend_library_seat` | write | 사용 시간 연장 |

<!-- markdownlint-enable MD013 MD060 -->

`reserve_library_seat` 는 ssuAI 의 flagship deliverable 이다. write tool 의 confirmation / dry-run / audit log / 분산 lock 정책은 §8 참고.

Tool 구현은 `com.ssuai.domain.mcp.tool` 아래에 있다. 각 tool 은 Connector 를 직접 호출하지 않고 도메인 Service 에만 위임한다. REST 와 MCP 가 같은 business logic 을 공유하게 하기 위한 규칙이다.

`/api/chat` 의 LLM mode 도 같은 tool bean 을 재사용한다. 다만 chat 내부에서는 LLM prompt 비용을 줄이기 위해 tool 응답을 그대로 넣지 않고 compact JSON 으로 줄여서 전달한다.

## 3. 서버 띄우기
Windows:

```powershell
cd backend
.\gradlew.bat bootRun
```

macOS / Linux / WSL:

```bash
cd backend
./gradlew bootRun
```

기본 profile 은 `dev` 이고, `dev` 기본 connector 는 `mock` 이다.

```yaml
ssuai:
  connector:
    meal: mock
    dorm-meal: mock
    library-seat: mock
    library-book: mock
    library-loans: mock
    saint-schedule: mock
    saint-grades: mock
    lms-assignments: mock
```

따라서 기본 실행에서는 외부 사이트를 hit 하지 않는다. local 에서 MCP 연결, tool 목록, DTO shape 만 확인하려면 mock connector 로 충분하다.

서버가 정상 기동하면 다음 URL 이 살아 있어야 한다.

```text
REST health: http://localhost:8080/actuator/health
MCP SSE:     http://localhost:8080/sse
```

SSE endpoint 는 연결이 열린 채 대기하는 것이 정상이다.

```bash
curl -N -D - http://localhost:8080/sse
```

정상 응답에는 `Content-Type: text/event-stream`, `event:endpoint`, `data:/mcp/message?sessionId=...` 가 보인다. 확인이 끝나면 `Ctrl+C` 로 종료한다.

## 4. MCP inspector 로 검증
먼저 backend 를 켠다.

```powershell
cd backend
.\gradlew.bat bootRun
```

다른 터미널에서 inspector 를 실행한다.

```bash
npx @modelcontextprotocol/inspector
```

브라우저 UI 에서 다음 순서로 연결한다.

1. Transport 로 `SSE` 를 선택한다.
2. URL 에 `http://localhost:8080/sse` 를 입력한다.
3. Connect 를 누른다.
4. `Tools` 탭에서 `List Tools` 를 누른다.
5. tool 10개가 보이는지 확인한다.

보여야 하는 tool 이름:
```
get_today_meal, get_meal_by_date, get_dorm_weekly_meal, search_campus_facilities,
get_library_seat_status, search_library_book,
get_my_schedule, get_my_grades, get_my_assignments, get_my_library_loans
```

`get_meal_by_date` 호출 예시:

```json
{"date":"2026-05-07"}
```

`get_library_seat_status` 호출 예시:

```json
{"floor":4}
```

dev profile 에서는 mock 데이터가 반환되는 것이 정상이다.

## 5. Claude Desktop 등록
설정 파일 위치:

| OS | 설정 파일 |
| --- | --- |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |

설정 파일을 수정한 뒤에는 Claude Desktop 을 완전히 종료했다가 다시 시작한다.

### 5.1 SSE 직접 지원 옵션

```json
{
  "mcpServers": {
    "ssuai": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

### 5.2 `mcp-proxy` 어댑터 옵션

일부 Claude Desktop 버전은 STDIO MCP server 만 안정적으로 처리한다.

```json
{
  "mcpServers": {
    "ssuai": {
      "command": "mcp-proxy",
      "args": ["http://localhost:8080/sse"]
    }
  }
}
```

## 6. Cursor 등록

```json
{
  "mcpServers": {
    "ssuai": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

등록 후 Cursor 를 재시작하거나 MCP 설정 화면에서 server refresh 를 실행한다. `ssuai` 가 연결되고 tool 10개가 보이면 된다.

## 7. 트러블슈팅
### 포트 8080 이 이미 점유됨

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

이 경우 MCP URL 도 `http://localhost:8081/sse` 로 바꾼다.

### `Connection refused`

backend 가 떠 있지 않은 상태일 가능성이 높다. `curl http://localhost:8080/actuator/health` 에서 `UP` 이 나오지 않으면 `backend/` 에서 `bootRun` 을 다시 실행한다.

### Tool 이 안 보임

MCP inspector 의 `Tools` 탭에서 `List Tools` 를 다시 클릭한다. Claude Desktop 이나 Cursor 는 설정 저장 후 client 재시작이 필요할 수 있다.

### 인증 필요 tool 이 에러를 반환함

`get_my_schedule`, `get_my_grades`, `get_my_assignments`, `get_my_library_loans` 는 외부 MCP client (Claude Desktop, Cursor) 에서 직접 호출 시 chat context 가 없어 항상 에러를 반환한다. 이 tool 들은 ssuAI chat (`/api/chat`) 을 통해서만 동작하도록 설계됐다.

## 8. 위험·write tool 정책 (향후)
현재 노출된 10개 MCP tool 은 모두 read-only 이다. 학교 시스템 상태를 변경하지 않는다.

### Phase 4 flagship — 도서관 좌석 자동 예약

ssuAI 의 가장 중요한 write tool 은 **`reserve_library_seat`** 이다. 숭실대 도서관 사이트의 층별 좌석 예약 인터랙션을 자동화한다. 자세한 사용자 시나리오는 [`docs/vision.md`](vision.md) §3.4 참고.

### Write tool 공통 정책

향후 예약, 제출, 설정 변경처럼 실제 학교 시스템 상태를 바꾸는 tool 을 추가할 때는 다음 원칙을 적용한다. 메커니즘은 [ADR 0015](adr/0015-action-tool-infrastructure.md) 가 source of truth.

- 모든 write tool 은 **`prepare_X` + 공용 `confirm_action(pending_action_id)`** 두 개의 MCP tool 로 노출한다.
- `prepare_X` 가 정확한 dry-run 문구를 반환하고 `action_audit` 에 `PREPARED` row 를 기록한다.
- `confirm_action` 은 lookup → in-process lock → 실제 upstream 호출 → audit 상태 전이만 담당한다.
- `pending_action_id` TTL 은 5분.
- 비밀번호, session cookie, token, 학생 개인정보, upstream HTML 은 audit row 와 log 어디에도 안 들어간다 (`docs/security.md` §4 / §5).
