# Session handoff — 2026-05-17 오전 (Task 17 LMS 구현 진행 중, 테스트 1개 남음)

> Single rolling handoff (CLAUDE.md / AGENTS.md 정책). 이전 handoff overwrite.

## TL;DR

- **Task 17 LMS auth + connector/service/tool layer 구현 완료**, 미커밋 상태.
- **브랜치 `feat/task-17-lms-auth`** — 아직 PR 미개설, 테스트 367개 중 **1개 실패 남음**.
- 마지막 실패: `McpSelfDogfoodTests.clientCanListEveryToolExposedByServer` — `get_my_assignments` 추가 후 fixture에 맞게 수정 완료했으나 `.\gradlew.bat test` 마지막 실행이 거절(토큰 소진)로 미확인.
- 다음 세션: 테스트 최종 확인 → commit → PR → merge.
- TTL spike 결과 사용자 통지 대기 (폴링 금지).

## 이번 세션 머지/푸시

없음 (모두 미커밋).

## 열린 PR

없음.

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | TTL spike — ssumcp 서버 nohup polling | 결과 사용자 통지 대기 ([[feedback-user-will-notify]]) |

## Task 17 현재 구현 상태 (미커밋, 브랜치 `feat/task-17-lms-auth`)

### 완료된 파일 (모두 untracked — commit 필요)

**LMS auth layer** (이전 세션에서 작성):
- `domain/auth/lms/LmsCookies.java` — record (rawCookieHeader 검증)
- `domain/auth/lms/LmsSessionEntry.java` — package-private record (iv, ciphertext, 만료)
- `domain/auth/lms/LmsSessionProperties.java` — @ConfigurationProperties ttl=2h, maxSessions=1000
- `domain/auth/lms/LmsSessionStore.java` — AES-GCM + LRU + TTL (SaintSessionStore 동일 패턴)
- `domain/auth/lms/LmsSsoProperties.java` — @ConfigurationProperties gwCallbackUrl, canvasBaseUrl, timeout
- `domain/auth/lms/LmsSsoService.java` — Phase1(gw-cb.php) + Phase2(canvas dashboard), followRedirects(NEVER)
- `domain/auth/lms/LmsSsoCallbackController.java` — GET /api/auth/lms/sso-init, /sso-callback
- `global/exception/LmsAuthFailedException.java`
- `global/exception/LmsSessionExpiredException.java`
- `test/resources/fixtures/lms/courses.json` — 3과목 scrubbed fixture
- `test/resources/fixtures/lms/todos.json` — 3항목 scrubbed fixture

**LMS connector/service/tool layer** (이번 세션에서 작성):
- `domain/lms/dto/AssignmentItem.java` — record (courseName, title, type, dueDate)
- `domain/lms/dto/AssignmentsResponse.java` — record (termId, List<AssignmentItem>)
- `domain/lms/connector/LmsAssignmentsConnector.java` — interface
- `domain/lms/connector/MockLmsAssignmentsConnector.java` — classpath fixture 읽기, new ObjectMapper() 직접 생성 (주입 X)
- `domain/lms/connector/RealLmsAssignmentsConnector.java` — canvas 3-step API (terms→courses→todos), 401=LmsSessionExpiredException
- `domain/lms/mcp/LmsToolContext.java` — ThreadLocal (SaintToolContext 동일 패턴)
- `domain/lms/service/LmsAssignmentsService.java` — LmsSessionStore 쿠키 조회 → connector
- `domain/lms/controller/LmsAssignmentsController.java` — GET /api/lms/assignments
- `domain/mcp/tool/LmsAssignmentsMcpTool.java` — @Tool get_my_assignments

**수정된 파일** (tracked, 미커밋):
- `domain/mcp/config/McpServerConfig.java` — LmsAssignmentsMcpTool 추가
- `domain/chat/service/LlmChatService.java` — LmsAssignmentsService 주입, get_my_assignments case, dispatchPrivateLmsTool(), compactAssignmentsNode(), LmsToolContext scope, out-of-scope 필터에서 lms/과제 제거
- `global/exception/ErrorCode.java` — LMS_AUTH_FAILED, LMS_SESSION_EXPIRED 추가
- `resources/application.yml` — ssuai.lms.* 설정 블록 + lms-assignments: mock 커넥터
- **테스트 수정**:
  - `LlmChatServiceTests.java` — lmsAssignmentsService mock 추가, 생성자 업데이트, out-of-scope 테스트 "lms 과제" → "수강신청"
  - `McpServerConfigTests.java` — get_my_assignments 추가
  - `McpSelfDogfoodTests.java` — get_my_assignments 추가

### 마지막 테스트 결과 (직전 실행)

```
367 tests completed, 1 failed
McpSelfDogfoodTests > clientCanListEveryToolExposedByServer() FAILED
```

이 test는 실제 SSE over HTTP로 MCP server에 연결해서 tool list를 검증. `McpSelfDogfoodTests.java`의 `containsExactlyInAnyOrder`에 `"get_my_assignments"` 이미 추가 완료. 단 마지막 `.\gradlew.bat test` 실행이 사용자에 의해 거절됨 — **다음 세션 첫 액션은 `.\gradlew.bat test` 실행으로 통과 확인**.

### 알려진 기술 결정 사항

1. `MockLmsAssignmentsConnector`가 `ObjectMapper`를 Spring 주입 대신 `new ObjectMapper()`로 직접 생성 — 이유: `@SpringBootTest` 맥락에서 ObjectMapper bean이 일부 테스트 슬라이스에서 없어서 context fail이 발생했기 때문. fixture-only 목적이므로 충분.
2. canvas API 3-step: `GET /learningx/api/v1/users/{studentId}/terms` → 첫 번째 term id 사용 → courses + todos 페치.
3. LMS 쿠키 캡처는 `followRedirects(NEVER)` HttpClient로 gw-cb.php 302 응답의 Set-Cookie 수집.

## 다음 세션 액션 (순서대로)

1. **테스트 통과 확인**
   ```powershell
   cd C:\Users\akftj\ssuAI\backend
   .\gradlew.bat test -x javadoc
   ```
   367 tests, 0 failed 이면 다음으로.

2. **전체 파일 commit** (브랜치: `feat/task-17-lms-auth`)
   ```
   git -C C:/Users/akftj/ssuAI add backend/src/main/java/com/ssuai/domain/auth/lms/ \
     backend/src/main/java/com/ssuai/domain/lms/ \
     backend/src/main/java/com/ssuai/domain/mcp/tool/LmsAssignmentsMcpTool.java \
     backend/src/main/java/com/ssuai/global/exception/Lms*.java \
     backend/src/test/resources/fixtures/lms/ \
     backend/src/main/java/com/ssuai/domain/mcp/config/McpServerConfig.java \
     backend/src/main/java/com/ssuai/domain/chat/service/LlmChatService.java \
     backend/src/main/java/com/ssuai/global/exception/ErrorCode.java \
     backend/src/main/resources/application.yml \
     backend/src/test/java/com/ssuai/domain/chat/service/LlmChatServiceTests.java \
     backend/src/test/java/com/ssuai/domain/mcp/McpSelfDogfoodTests.java \
     backend/src/test/java/com/ssuai/domain/mcp/config/McpServerConfigTests.java
   ```
   커밋 메시지: `feat(task-17): LMS auth + assignments connector/service/MCP tool`

3. **PR 오픈** — base: main, auto-merge eligible (mock default, tests pass, 신규 파일 위주)

4. **다음 Task 확인** — `docs/tasks/` 리스트. Task 18 또는 사용자 지시 대기.

## 사용자 컨텍스트 — 잊지 말 것

- 숭실대 컴퓨터학부 3학년. 본인 학번/이름 chat paste OK. fixture/commit/log는 placeholder.
- **commit/PR body에 Claude/AI 흔적 절대 금지** ([[feedback-no-claude-coauthor]]).
- 안전한 PR 자동 머지 가능 ([[feedback-auto-merge-safe-prs]]).
- 외부 작업 결과는 사용자가 통지 ([[feedback-user-will-notify]]) — TTL spike 폴링 금지.
- 간결한 한국어 응답 선호. 계획 길게 늘어놓지 말고 바로 진행.
- opusplan 모드: 설계 결정은 `/plan` (Opus), 구현/테스트/커밋은 일반 Sonnet.
- git 명령은 `git -C C:/Users/akftj/ssuAI <subcommand>` (cwd가 backend/일 수 있으므로 절대경로).

## 보안 주의

- LMS 쿠키 (`xn_api_token` JWT 포함)는 AES-256-GCM으로 암호화 저장. raw header는 log/echo 금지.
- MCP tool은 LmsToolContext ThreadLocal만 신뢰 — get_my_assignments에 학번 인자 없음.
- fixture는 scrubbed (id 10001-10003, 홍길동). 실 학번/과목명 절대 fixture에 넣지 말 것.
- LMS session cookie TTL = 2h (xn_api_token JWT 기준). 만료 시 LmsSessionExpiredException → HTTP 401.

---

## Next-AI opener block

```text
ssuAI 프로젝트 이어받음. 다음 순서대로:

1. CLAUDE.md (또는 AGENTS.md) Project + Implementation Workflow 섹션 읽기
2. docs/handoff/latest.md 읽기 — Task 17 LMS 구현 진행 중 (테스트 1개 확인 필요)
3. git -C C:/Users/akftj/ssuAI status --short --branch — feat/task-17-lms-auth

현재 상태:
- 브랜치: feat/task-17-lms-auth
- 미커밋 작업 전부: LMS auth + connector/service/tool 완성
- 마지막 실행 `.\gradlew.bat test` 에서 1 failed (McpSelfDogfoodTests) — 수정 완료됐으나 재실행 미확인
- 다음 액션: .\gradlew.bat test → 0 failed 확인 → git add/commit → PR → merge

사용자 짧은 답 선호. 바로 테스트 실행부터 시작.
```
