# ssuAI MCP Tools

## 1. 개요
ssuAI MCP server 는 숭실대학교 학생을 위한 캠퍼스 정보 조회 기능을 MCP(Model Context Protocol) tool 로 노출한다. Claude Desktop, Cursor, MCP inspector 같은 MCP client 는 이 서버에 붙어서 학식, 기숙사 식단, 캠퍼스 시설 정보를 대화 중에 조회할 수 있다.

MCP server 는 별도 프로세스가 아니라 기존 ssuAI Spring Boot backend 안에서 REST API 와 함께 실행된다. backend 를 띄우면 REST endpoint 와 MCP endpoint 가 같은 JVM 안에서 같이 살아난다.

현재 transport 는 SSE(Server-Sent Events) / Streamable HTTP 계열이며 기본 endpoint 는 `http://localhost:8080/sse` 이다. 이 문서는 local 개발 환경에서 MCP server 를 띄우고 MCP inspector, Claude Desktop, Cursor 에 연결하는 절차만 다룬다.

## 2. 노출 tool 4개
현재 MCP server 는 read-only tool 4개를 제공한다. 응답 DTO 의 자세한 JSON shape 는 Java record 가 source of truth 이므로 도메인 타입명만 적는다.

| tool name | 설명 | 인자 | 응답 DTO |
| --- | --- | --- | --- |
| `get_today_meal` | 오늘 숭실대학교 캠퍼스 식당 메뉴를 조회한다. `restaurant` 를 비우면 전체 식당, 지정하면 해당 식당만 반환한다. | `restaurant` (선택): 학생식당 / 숭실도담식당 / 스낵코너 / 푸드코트 / THE KITCHEN / FACULTY LOUNGE | `MealResponse` |
| `get_meal_by_date` | 지정한 날짜의 숭실대학교 캠퍼스 식당 메뉴를 조회한다. `restaurant` 동작은 `get_today_meal` 과 동일. | `date`: `yyyy-MM-dd` (예: `2026-05-07`), `restaurant` (선택): 위와 동일 | `MealResponse` |
| `get_dorm_weekly_meal` | 레지던스홀 기숙사 식당의 이번 주 주간 메뉴를 조회한다. | 없음 | `WeeklyMealResponse` |
| `search_campus_facilities` | 식당, 카페, 편의점, 서점, 복사/출력 시설 등을 검색한다. `query` 가 비어 있으면 전체 시설을 반환한다. | `query`: 선택 문자열 | `CampusFacilityListResponse` |

학식 데이터는 `WeeklyMealCache` 가 애플리케이션 시작 시점과 매주 월요일 06:00 KST 에 일괄 적재하므로, `get_today_meal` / `get_meal_by_date` 호출은 일반적으로 캐시 히트로 응답한다. 캐시 miss 가 발생하면 그 자리에서 connector 를 호출해 채워 넣는다.

Tool 구현은 `com.ssuai.domain.mcp.tool` 아래의 `MealMcpTools`, `DormMcpTools`, `CampusMcpTools` 에 있다. 각 tool 은 Connector 를 직접 호출하지 않고 도메인 Service 에만 위임한다. REST 와 MCP 가 같은 business logic 을 공유하게 하기 위한 규칙이다.

`/api/chat` 의 LLM mode 도 같은 tool bean 을 재사용한다. 다만 chat 내부에서는
LLM prompt 비용을 줄이기 위해 tool 응답을 그대로 넣지 않고 compact JSON 으로
줄여서 전달한다.

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

```bash
cd backend
./gradlew bootRun
```

Windows:

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
5. tool 4개가 보이는지 확인한다.

보여야 하는 tool 이름은 `get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`, `search_campus_facilities` 이다.

`get_meal_by_date` 호출 예시:

```json
{"date":"2026-05-07"}
```

`get_today_meal` 을 특정 식당으로 좁혀 호출하는 예시:

```json
{"restaurant":"학생식당"}
```

`search_campus_facilities` 호출 예시:

```json
{"query":"학식"}
```

전체 시설 목록은 `query` 를 빈 문자열로 보낸다.

```json
{"query":""}
```

dev profile 에서는 mock 학식이 반환되는 것이 정상이다.

## 5. Claude Desktop 등록
Claude Desktop 연결은 버전에 따라 두 방식이 가능하다. 먼저 SSE 직접 연결을 시도하고, config validation 이 실패하거나 tool 이 보이지 않으면 `mcp-proxy` 어댑터 방식으로 fall back 한다.

설정 파일 위치:

| OS | 설정 파일 |
| --- | --- |
| Windows | `%APPDATA%\Claude\claude_desktop_config.json` |
| macOS | `~/Library/Application Support/Claude/claude_desktop_config.json` |

설정 파일을 수정한 뒤에는 Claude Desktop 을 완전히 종료했다가 다시 시작한다.

### 5.1 SSE 직접 지원 옵션
Claude Desktop 이 URL 기반 remote MCP server 를 지원하는 환경에서는 다음 형태를 먼저 시도한다.

```json
{
  "mcpServers": {
    "ssuai": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

이미 다른 MCP server 가 있으면 `mcpServers` 객체 안에 `"ssuai"` 항목만 추가한다. JSON 은 trailing comma 를 허용하지 않는다.

정상 등록되면 `get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`, `search_campus_facilities` 가 보여야 한다. `"url"` 항목이 유효하지 않은 config 로 처리되거나 tool 이 보이지 않으면 `mcp-proxy` 방식을 사용한다.

### 5.2 `mcp-proxy` 어댑터 옵션
일부 Claude Desktop 버전은 STDIO MCP server 만 안정적으로 처리한다. 이 경우 `mcp-proxy` 가 STDIO 와 SSE 사이를 연결한다.

```text
Claude Desktop -> STDIO -> mcp-proxy -> SSE -> http://localhost:8080/sse
```

참고 도구: `https://github.com/sparfenyuk/mcp-proxy`

설치 예시:

```bash
uv tool install mcp-proxy
```

대안:

```bash
pipx install mcp-proxy
```

Claude Desktop config 예시:

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

실행 파일을 찾지 못하면 Windows 에서는 `where.exe mcp-proxy`, macOS / Linux 에서는 `which mcp-proxy` 로 전체 경로를 확인한다.

전체 경로 예시:

```json
{
  "mcpServers": {
    "ssuai": {
      "command": "C:\\Users\\YOUR_NAME\\.local\\bin\\mcp-proxy.exe",
      "args": ["http://localhost:8080/sse"]
    }
  }
}
```

## 6. Cursor 등록
Cursor 는 project-local MCP 설정을 `.cursor/mcp.json` 에 둘 수 있다.

```text
.cursor/mcp.json
```

예시:

```json
{
  "mcpServers": {
    "ssuai": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

등록 후 Cursor 를 재시작하거나 MCP 설정 화면에서 server refresh 를 실행한다. `ssuai` 가 연결되고 tool 4개가 보이면 된다.

Cursor 에서도 backend 가 `dev` profile 로 떠 있으면 mock connector 응답이 반환된다. 실제 외부 사이트 데이터를 보고 싶다면 connector 설정을 `real` 로 바꾸고 backend 를 다시 띄운다.

## 7. 트러블슈팅
### 포트 8080 이 이미 점유됨

임시로 포트를 바꿔 실행한다.

```bash
./gradlew bootRun --args="--server.port=8081"
```

Windows:

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

이 경우 MCP URL 도 `http://localhost:8081/sse` 로 바꾼다.

### `Connection refused`

backend 가 떠 있지 않은 상태일 가능성이 높다. `curl http://localhost:8080/actuator/health` 에서 `UP` 이 나오지 않으면 `backend/` 에서 `bootRun` 을 다시 실행한다.

### Tool 이 안 보임

MCP inspector 의 `Tools` 탭에서 `List Tools` 를 다시 클릭한다. Claude Desktop 이나 Cursor 는 설정 저장 후 client 재시작이 필요할 수 있다. 서버 로그에 `Registered tools: 4` 가 보이면 backend 쪽 tool 등록은 된 것이다.

### `get_meal_by_date` 가 mock 응답을 반환함

정상이다. local 기본값은 mock connector 이다.

```yaml
ssuai:
  connector:
    meal: mock
```

real 데이터를 보고 싶으면 `ssuai.connector.meal` 을 `real` 로 바꿔야 한다. 단, real connector 는 외부 사이트를 hit 하므로 반복 호출을 피한다.

### Claude Desktop 에서 안 보임

설정 파일 저장 후 Claude Desktop 을 완전히 종료하고 재시작한다. 그래도 안 되면 SSE 직접 설정 대신 `mcp-proxy` 방식을 사용한다.

### `mcp-proxy` 실행 파일을 찾지 못함

`ENOENT` 비슷한 오류가 나오면 `command` 의 실행 파일 경로를 못 찾는 것이다. `where.exe mcp-proxy` 또는 `which mcp-proxy` 로 전체 경로를 확인해서 config 에 넣는다.

## 8. 위험·write tool 정책 (향후)
현재 노출된 4개 MCP tool 은 모두 read-only 이다.

```text
get_today_meal
get_meal_by_date
get_dorm_weekly_meal
search_campus_facilities
```

이 tool 들은 공개 캠퍼스 정보 조회만 수행하며, 학교 시스템 상태를 변경하지 않는다. u-SAINT, LMS, 도서관 좌석 예약 같은 write 성격 기능도 현재 MCP tool 로 노출하지 않는다.

향후 예약, 제출, 설정 변경처럼 실제 학교 시스템 상태를 바꾸는 tool 을 추가할 때는 다음 원칙을 적용한다.

- 사용자 명시 confirmation 을 반드시 받는다.
- 실행 전 dry-run 결과를 먼저 보여준다.
- 실행 요청과 결과를 audit log row 로 남긴다.
- 실패, 부분 성공, 취소를 사용자에게 명확히 반환한다.
- `docs/security.md` 의 logging 정책을 지킨다.
- 비밀번호, session cookie, token, 학생 개인정보를 log 에 남기지 않는다.
- `docs/architecture.md` 의 MCP 안전 정책과 Service layer 위임 규칙을 유지한다.

구체적인 write tool UX, audit table shape, confirmation payload 는 별도 task 에서 정의한다. 그 전까지 ssuAI MCP server 는 read-only 도구만 제공한다.
