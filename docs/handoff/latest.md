# Session handoff — 2026-05-17 새벽 (PR 16b WIP, 토큰 소진 중단)

> Single rolling handoff file (CLAUDE.md / AGENTS.md "Session handoff
> to a different AI" 정책). 이전 핸드오프를 overwrite 한다.

## TL;DR

- **PR 16b (시간표 connector) 중단** — encoder / unwrapper / parser /
  connector + 단위 테스트까지 작성하고 **gradlew test 실행 전에 토큰
  소진**. 다음 세션 첫 액션 = `gradlew.bat test` 돌려서 빨강 잡고
  나머지 (service / controller / MCP class / yml wiring / PR open)
  마무리.
- **브랜치**: `feat/task-16b-schedule-connector` (commit 1개 push 완료).
- **머지된 PR 없음** — 이번 세션은 한 줄도 main 안 건드림.
- **외부 의존 그대로**: TTL spike (PR #81, 사용자 직접) + GitHub
  Actions auto-deploy (선택).

## 이번 세션 머지/푸시

| 항목 | 상태 |
|------|------|
| 머지된 PR | 없음 |
| 푸시된 브랜치 | `feat/task-16b-schedule-connector` (WIP — PR open 안 됨) |

## 열린 PR

| PR | 브랜치 | 내용 | 상태 |
|----|--------|------|------|
| #81 | `chore/spike-ssotoken-ttl-script` | Task 13 ssotoken TTL spike script | 사용자가 마지막에 하기로 ([[feedback-user-defers-ttl-spike]]) |

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | TTL spike (PR #81) | ⏳ 사용자 직접 |
| 2 | GitHub Actions auto-deploy (KUBE_CONFIG) | ⏳ 사용자 의지 (선택) |

## PR 16b WIP — 현재 상태 (정확)

### ✅ 작성 완료 (브랜치에 commit + push 됨)

| 파일 | 비고 |
|------|------|
| `backend/src/main/java/com/ssuai/domain/saint/service/WebDynproSapEventEncoder.java` | `~E001..~E005` + URL escape. `encodeButtonPress(WDA7)` |
| `backend/src/main/java/com/ssuai/domain/saint/service/WebDynproResponseUnwrapper.java` | CDATA 추출 + secure-id parse |
| `backend/src/main/java/com/ssuai/domain/saint/service/SaintScheduleParser.java` | `tbody[id$=-contentTBody] tr[rt=1]` selector, cc→ISO DayOfWeek |
| `backend/src/main/java/com/ssuai/domain/saint/dto/ScheduleEntry.java` | record |
| `backend/src/main/java/com/ssuai/domain/saint/dto/TermSchedule.java` | record |
| `backend/src/main/java/com/ssuai/domain/saint/dto/ScheduleResponse.java` | record |
| `backend/src/main/java/com/ssuai/domain/saint/config/SaintScheduleProperties.java` | `ssuai.saint.schedule.timetable-url` + `timeout` |
| `backend/src/main/java/com/ssuai/domain/saint/connector/SaintScheduleConnector.java` | interface |
| `backend/src/main/java/com/ssuai/domain/saint/connector/SaintScheduleHelpers.java` | `parseEnrollmentYear`, `termFor` |
| `backend/src/main/java/com/ssuai/domain/saint/connector/MockSaintScheduleConnector.java` | default (`matchIfMissing=true`). 학번→enrollmentYear→iterate, hardcoded entries |
| `backend/src/main/java/com/ssuai/domain/saint/connector/RealSaintScheduleConnector.java` | `@ConditionalOnProperty(..., havingValue="real")`. java.net.http.HttpClient, GET + WDA7 POST iterate, mergeSetCookies, guardAuthOrThrow → `SaintSessionExpiredException` |
| `backend/src/test/java/com/ssuai/domain/saint/service/WebDynproSapEventEncoderTests.java` | captured cURL 와 round-trip |
| `backend/src/test/java/com/ssuai/domain/saint/service/WebDynproResponseUnwrapperTests.java` | CDATA / secure-id |
| `backend/src/test/java/com/ssuai/domain/saint/service/SaintScheduleParserTests.java` | fixture `timetable-success.html` 기반, 7 entries |
| `backend/src/test/java/com/ssuai/domain/saint/connector/MockSaintScheduleConnectorTests.java` | iterate / fresh enroll |
| `backend/src/test/java/com/ssuai/domain/saint/connector/RealSaintScheduleConnectorTests.java` | MockWebServer, 3 scenarios + mergeSetCookies |

### ❌ 아직 안 함

1. **`gradlew.bat test` 한 번도 안 돌림** — 컴파일 에러 / Jsoup `wholeText()`
   가 `<br>` 를 `\n` 으로 변환하는지 (parser test fallback path 의도) /
   MockWebServer setBody UTF-8 byte length / `SaintScheduleProperties`
   가 `@ConfigurationProperties` 로 등록되려면 `@EnableConfigurationProperties`
   필요 여부 등 다 미검증. **첫 액션 = test 돌려서 빨강 다 잡기.**
2. `SaintScheduleService` (cookies lookup → connector → `SaintSessionExpiredException`
   처리, 옵션 cache) — 아직 없음
3. `SaintScheduleController` (`GET /api/saint/schedule`, JwtAuthFilter
   `AuthAttributes.STUDENT_ID` 읽기, `UnauthorizedException` if missing) — 아직 없음
4. `SaintScheduleMcpTool` — 작성 안 함. **중요 결정**: spec §9 의 "chat
   thread-local pattern 미구현" 때문에 PR 16b 첫 cut 에는 **MCP tool 등록
   하지 않음** (class 만 작성하고 `McpServerConfig` 에 추가 안 함). chat
   thread-local pattern 갖춰지면 별도 PR 에서 등록. PR body 에 명시할 것.
5. `application.yml` 에 `ssuai.connector.saint-schedule: mock` default
   추가 — 아직 안 함 (Mock connector 는 `matchIfMissing=true` 라 동작은 함)
6. `dev-log` 한 줄 추가 안 함
7. PR open 안 함
8. tests for service / controller — 아직 없음

### 결정 사항 (이번 세션에 정한 것)

- **MCP tool 등록은 follow-up PR**. 이유: 현 LlmChatService.executeToolCall
  switch case 가 명시적 라우팅이고 chat path 가 studentId 를 MCP self-call
  에 propagate 하는 mechanism 없음. 외부 MCP client (Claude Desktop) 에
  학번 인자 노출은 위조 가능 → 보안 위험. REST `/api/saint/schedule` 가
  PR 16b 의 첫 user-facing path. chat 의 "내 시간표 알려줘" 는 일단
  `LlmChatService.looksLikePrivateAcademicRequest()` 가 catch.
- **첫 cut scope = "전체 학년도 1학기 iterate"** (WDA7 prev only).
  2학기 / 계절학기 nav 는 follow-up. spec §3.4 그대로.
- **Mock 이 default** (`matchIfMissing=true` + 추후 yml `mock`). prod 전환
  은 PR 16b/16c 안정 + 사용자 검증 후 별도 `application-prod.yml` patch.

## 다음 세션 액션 (우선순위 순서)

### 1. `gradlew.bat test` 돌려서 컴파일/테스트 빨강 다 잡기 (필수 첫 단계)

예상 함정 / hotspot:
- `SaintScheduleParser.splitLines` 의 `wholeText()` vs `<br>` 분할 —
  Jsoup 1.22 가 `<br>` 를 newline 으로 normalize 하는지 fixture 로 검증.
  안 되면 fallback path 만 남기고 wholeText 빼기.
- `SaintScheduleProperties` 가 `@Component + @ConfigurationProperties`
  로 등록돼있어 별도 `@EnableConfigurationProperties` 불필요해야 하나,
  Spring Boot 4 에서 동작 확인.
- `SaintSessionStore.fingerprint(studentId)` static call — store 이미
  있음 ✅.
- `RealSaintScheduleConnector.mergeSetCookies` 가 package-private static
  이라 test 에서 직접 호출 가능 (확인 완료).
- `MockWebServer` 가 OkHttp 5.x 라 `enqueue(MockResponse)` API 동일.

### 2. `SaintScheduleService` + service test

```java
@Service
public class SaintScheduleService {
    private final SaintScheduleConnector connector;
    private final SaintSessionStore sessionStore;
    
    public ScheduleResponse fetchSchedule(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new UnauthorizedException();
        }
        PortalCookies cookies = sessionStore.cookies(studentId)
                .orElseThrow(SaintSessionExpiredException::new);
        return connector.fetchSchedule(studentId, cookies);
    }
}
```

cache 는 첫 cut 에 생략 가능 (sessionStore TTL 30분 안에 fetch 가 무거우면
follow-up). spec §6 #5 의 ~1h TTL cache 는 nice-to-have.

### 3. `SaintScheduleController` + MockMvc test

```java
@RestController
@RequestMapping("/api/saint")
@Tag(name = "Saint", description = "u-SAINT realtime data API")
public class SaintScheduleController {
    private final SaintScheduleService service;
    
    @GetMapping("/schedule")
    public ApiResponse<ScheduleResponse> getMySchedule(HttpServletRequest request) {
        String studentId = (String) request.getAttribute(AuthAttributes.STUDENT_ID);
        if (studentId == null || studentId.isBlank()) {
            throw new UnauthorizedException();
        }
        return ApiResponse.success(service.fetchSchedule(studentId));
    }
}
```

Tests: 200 envelope / 401 missing attr / 401 SAINT_SESSION_EXPIRED.

### 4. `application.yml` 에 `ssuai.connector.saint-schedule: mock` default 추가

```yaml
ssuai:
  connector:
    saint-schedule: mock # mock | real
```

이미 `matchIfMissing=true` 라 yml 없어도 mock 동작은 하지만 명시성을 위해.

### 5. `dev-log` 한 줄 + commit + PR open

PR body 에 명시할 점:
- 첫 cut scope = 전체 학년도 1학기 iterate (WDA7 only)
- MCP tool 등록은 follow-up (chat thread-local pattern 구비 후)
- prod 전환은 사용자 검증 후 별도 PR (`application-prod.yml` patch)
- Mock 이 default — CI/prod 가 saint 안 침

자동 머지 정책 적용 — mergeable + tests pass + mock default 면
`gh pr merge --auto --rebase --delete-branch`.

### 6. 그 뒤 — PR 16c (성적 connector) — spec §3.5 의 단일 GET. 같은 패턴.

성적은 fixture 도 아직 없음. PR 16c 시작 시 사용자 brower spike 필요.

### 7. TTL spike (PR #81), GitHub Actions auto-deploy — 사용자 직접.

## 사용자 컨텍스트 — 잊지 말 것

- **숭실대 컴퓨터학부 3학년**, 포트폴리오 프로젝트
- 데드라인 (5/15) 지나서 SmartID 라이브 데모는 이미 완성된 상태
- 3-AI rotation (claude1/claude2/codex) — CLAUDE.md = AGENTS.md mirror
  ([[role-3-ai-rotation]])
- **commit/PR body 에 Claude/AI 흔적 절대 금지** ([[feedback-no-claude-coauthor]])
- 안전한 PR 자동 머지 — mergeable + tests pass + mock default
  ([[feedback-auto-merge-safe-prs]])
- 외부 작업 사용자 통지 대기 — "내가 알려줄게" 한 작업은 폴링/언급 금지
  ([[feedback-user-will-notify]])
- 본인 JWT/secret/cookie/학번 chat 노출 시 rotate/redact 권고 X
  ([[feedback-own-auth-values-not-sensitive]],
  [[feedback-user-student-id-not-sensitive]])
- TTL spike 는 마지막에 ([[feedback-user-defers-ttl-spike]])
- 간결한 한국어 응답 선호
- in-flight context 는 in-repo (handoff / task spec / dev-log) 에 쓰고
  auto-memory 에는 안 씀 ([[feedback-save-progress-to-project]])
- portal HTML 캡처 부탁 시 Ctrl+U vs F12 Elements 구분 명시 필수
  ([[learning-browser-view-source-vs-devtools]])
- 두 제품 framing — MCP 서버 + ssuAI 웹/앱 ([[project-final-goal]])

## 보안 주의

- prod Secret 매뉴얼 잡힘 (이전 세션). `ssuai-backend-secrets` (plural).
- 사용자 본인 학번/이름은 chat 면제. fixture / commit / log 에는 절대
  placeholder (`홍길동` / `20999999` / `0.0.0.0`).
- **성적은 LLM 프롬프트 절대 금지** — PR 16c 시 `LlmChatService.compactToolResponse`
  단위 테스트로 본문 누출 고정.
- MYSAPSSO2 SAP SSO 토큰 base64 디코드 시 학번 노출. SaintSessionStore
  AES-256-GCM 으로 이미 처리 ✅.

## 자동 메모리

`C:\Users\akftj\.claude-personal\projects\C--Users-akftj-ssuAI\memory\`

이번 세션 신규 메모리 없음 (PR 16b 작업 자체는 in-repo 에 다 잠김).

---

## Next-AI opener block

다음 AI 세션을 시작할 때 사용자가 통째로 paste 할 첫 turn:

```text
ssuAI 프로젝트 이어받음. 다음 순서대로 시작:

1. AGENTS.md (또는 CLAUDE.md — 동일 mirror) 의 Project + Session-handoff
   섹션 읽기
2. docs/handoff/latest.md 읽기 — 이번 인계 컨텍스트 (PR 16b WIP)
3. MEMORY.md (auto-memory) 한 번 훑기
4. git checkout feat/task-16b-schedule-connector (브랜치 이미 push 됨)
5. git status --short --branch — clean 인지 확인 (frontend/next-env.d.ts
   M 은 Next.js auto-gen 무시 OK)

현재 상태:
- 브랜치 `feat/task-16b-schedule-connector` push 됨 (PR 안 열림)
- PR 16b WIP: encoder/unwrapper/parser/connector + 단위 테스트까지
  작성. gradlew.bat test 한 번도 안 돌렸음 — 빨강 가능성 높음
- 머지된 PR 없음, 외부 의존 그대로 (TTL spike PR #81 + 선택 auto-deploy)

다음 세션 첫 액션 — 핸드오프 doc "다음 세션 액션 #1":
**`gradlew.bat test` 돌려서 컴파일/테스트 빨강 다 잡기**. 그 다음
순서대로 #2 service → #3 controller → #4 yml → #5 dev-log + PR open.

MCP tool 등록은 follow-up PR (이번 PR 에 포함 X — spec §9 chat
thread-local pattern 미구현 + 외부 MCP client 학번 노출 보안).
```
