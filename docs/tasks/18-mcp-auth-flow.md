# Task 18 — MCP 인증 세션 흐름 재설계

## Status

- Draft / design review needed.
- 구현 전 사용자 승인 필요.

## Problem

현재 MCP 서버는 공개 도구와 private 도구가 같은 서버에 등록되어 있지만,
private 도구 인증 모델은 "ssuAI 웹 챗봇 내부 호출"에 맞춰져 있다.

- `get_my_schedule`, `get_my_grades`, `get_my_assignments` 는
  `SaintToolContext` / `LmsToolContext` ThreadLocal 에 학생 식별자가
  들어있을 때만 동작한다.
- `get_my_library_loans` 는 `LibraryToolContext` ThreadLocal 에 웹
  세션 키가 들어있을 때만 동작한다.
- 이 컨텍스트는 `ChatController` / `LlmChatService` 가 웹 요청 안에서
  임시로 바인딩한다.
- Claude Desktop, Cursor 같은 외부 MCP 클라이언트는 ssuAI JWT,
  Servlet session, ThreadLocal 을 자연스럽게 갖지 못한다.

결과적으로 현재 구현은 "웹/챗봇이 MCP 서버를 보강해서 쓰는 형태"에
가깝고, 목표인 "다른 MCP 클라이언트도 쉽게 쓰는 공개 MCP 서버"와
맞지 않는다.

## Goal

MCP 서버가 인증 필요한 학교 정보를 자체적으로 요청, 안내, 보관, 조회할
수 있게 만든다.

최종 UX:

1. 사용자가 외부 MCP 클라이언트에서 `내 성적 알려줘` 같은 요청을 한다.
2. MCP private tool 이 인증 상태를 확인한다.
3. 인증이 없으면 `AUTH_REQUIRED` 응답과 로그인 URL 을 반환한다.
4. 사용자가 브라우저에서 SmartID / LMS / 도서관 로그인을 완료한다.
5. MCP 서버가 provider session 을 암호화 저장한다.
6. 같은 MCP 사용자/세션의 다음 tool call 은 저장된 upstream session 으로
   정보를 가져와 정리된 응답을 반환한다.

## Non-goals

- 학생 ID 를 MCP tool argument 로 받지 않는다. spoofing 위험이 있다.
- u-SAINT/LMS/도서관 비밀번호를 MCP 클라이언트 채팅창에서 직접 받지 않는다.
- 자동 좌석 예약 같은 상태 변경 action tool 은 이 task 범위가 아니다.
- 영구 DB 저장은 MVP 범위가 아니다. 우선 encrypted in-memory store 를
  사용하고, 운영 확장 시 Redis/JDBC 로 교체 가능하게 경계만 잡는다.

## Current Constraints

- MCP 서버는 Spring Boot 백엔드 내부 Spring AI MCP server 로 동작한다.
- REST controller 와 MCP tool 은 Service layer 를 공유해야 한다.
- 보안 문서 기준:
  - u-SAINT/LMS 쿠키, SmartID token, 도서관 token 은 secret 이다.
  - 시간표, 성적, LMS 과제는 sensitive 이다.
  - raw cookie/JWT/session id/upstream HTML 은 로그에 남기지 않는다.
- 기존 stores:
  - `SaintSessionStore`: `studentId -> encrypted PortalCookies`
  - `LmsSessionStore`: `studentId -> encrypted LmsCookies`
  - `LibrarySessionStore`: `web sessionKey -> encrypted Pyxis token`

## Proposed Shape

### 1. MCP Auth Session

새 패키지:

`com.ssuai.domain.auth.mcp`

주요 책임:

- `McpAuthSessionId`
  - 외부에 노출되는 random opaque id.
  - 최소 128-bit entropy.
  - 로그에는 fingerprint 만 사용.
- `McpAuthSessionStore`
  - `mcpSessionId -> principal/provider sessions` 매핑.
  - TTL, LRU cap, explicit logout 지원.
  - 저장 값은 최소화한다.
- `McpAuthStateStore`
  - login URL 에 들어가는 one-time `state` 저장.
  - `state -> {mcpSessionId, provider, returnUrl?, expiresAt}`.
  - callback 에서 한 번만 소비한다.
- `McpAuthService`
  - auth status 조회.
  - provider login URL 생성.
  - callback 완료 후 provider session 연결.

MVP 에서 `mcpSessionId` 전달 방식은 tool argument 로 명시한다.

이유:

- Spring AI MCP transport 에서 안정적인 per-client session id 를 꺼낼 수
  있는지 아직 불명확하다.
- 외부 MCP 클라이언트 호환성을 우선하면 명시 argument 가 가장 예측 가능하다.
- 단, studentId 같은 권한 식별자는 argument 로 받지 않는다. argument 는
  random opaque session handle 일 뿐이다.

추후 spike 로 MCP transport session principal 을 안정적으로 얻을 수 있으면
argument 를 optional 로 낮추고 서버 바인딩으로 전환한다.

### 2. Tool Contract

새 public MCP tools:

- `get_auth_status(mcp_session_id?)`
  - 연결된 provider 상태를 반환한다.
  - provider: `SAINT`, `LMS`, `LIBRARY`
- `start_auth(provider, mcp_session_id?)`
  - 없으면 새 `mcp_session_id` 를 발급한다.
  - 로그인 URL, 만료 시각, 사용해야 할 `mcp_session_id` 를 반환한다.
- `logout_provider(provider, mcp_session_id)`
  - 해당 provider session 을 삭제한다.
- `logout_all(mcp_session_id)`
  - 해당 MCP auth session 전체 삭제.

private MCP tools:

- `get_my_schedule(mcp_session_id)`
- `get_my_grades(mcp_session_id)`
- `get_my_assignments(mcp_session_id)`
- `get_my_library_loans(mcp_session_id)`

인증이 없거나 만료된 경우 exception 대신 구조화 응답을 반환한다.

```json
{
  "status": "AUTH_REQUIRED",
  "provider": "SAINT",
  "mcpSessionId": "opaque-session-id",
  "loginUrl": "https://api.example.com/api/mcp/auth/saint/start?...",
  "expiresAt": "2026-05-18T12:00:00Z",
  "message": "u-SAINT 로그인이 필요합니다. 브라우저에서 로그인 후 다시 요청해주세요."
}
```

성공 시에는 기존 DTO 를 그대로 반환하되 `status: "OK"` 래퍼를 둘지,
기존 DTO 유지할지는 구현 직전 한 번 더 결정한다. 외부 MCP 클라이언트의
tool schema 안정성을 생각하면 래퍼 DTO 로 통일하는 쪽이 더 안전하다.

### 3. Provider Login Flow

#### SAINT

1. `start_auth(SAINT)` 또는 private tool 이 `McpAuthService` 를 통해
   `state` 와 login URL 을 만든다.
2. 로그인 URL 은 `/api/mcp/auth/saint/start?state=...` 로 진입한다.
3. 서버가 SmartID 로 redirect 한다.
4. SmartID callback 은 `/api/mcp/auth/saint/callback` 으로 돌아온다.
5. 서버는 기존 `SaintSsoService.authenticate(sToken, sIdno)` 를 사용한다.
6. `studentId` 와 `PortalCookies` 를 MCP auth session 에 연결한다.
7. 브라우저에는 "로그인 완료, MCP 클라이언트로 돌아가 다시 요청" 화면을
   보여준다.

#### LMS

SAINT 와 같은 shape 를 사용한다.

- `/api/mcp/auth/lms/start`
- `/api/mcp/auth/lms/callback`
- 기존 `LmsSsoService.authenticate(sToken, sIdno)` 재사용.

SAINT 로그인 중 best-effort LMS 연결은 유지할 수 있지만, MCP auth model 에서는
SAINT 와 LMS 를 독립 provider 로 취급한다. 실패한 provider 때문에 다른
provider 로그인 성공을 막지 않는다.

#### LIBRARY

도서관은 SmartID SSO 가 아니라 도서관 계정 로그인이므로 별도 웹 폼이 필요하다.

- `/api/mcp/auth/library/start?state=...`
- 서버가 간단한 로그인 페이지 또는 프론트엔드 MCP auth page 로 redirect.
- 사용자가 학번/비밀번호를 입력한다.
- 기존 `LibraryCredentialLoginService.login(...)` 로 Pyxis token 을 얻는다.
- token 을 MCP auth session 에 연결한다.

비밀번호는 서버 메모리에 오래 보관하지 않고, 요청 처리 중 upstream 로그인에만
사용한다.

### 4. Service/Data Flow

기존 service layer 는 유지한다.

SAINT private tool:

```text
MCP client
  -> get_my_grades(mcp_session_id)
  -> McpAuthService.requireProvider(SAINT, mcp_session_id)
  -> principal.studentId 확인
  -> SaintSessionStore 또는 MCP provider session 에서 PortalCookies 확인
  -> SaintGradesService.fetchGrades(studentId)
  -> connector
```

주의:

- `SaintGradesService.fetchGrades(studentId)` 는 현재 `SaintSessionStore` 에서
  studentId 기준 cookie 를 찾는 구조다.
- 구현 단계에서 선택지가 있다.
  1. MCP callback 성공 시 기존 `SaintSessionStore.put(studentId, cookies)` 도
     같이 호출한다.
  2. service 가 `PortalCookies` 를 직접 받을 수 있도록 overload 를 추가한다.
- MVP 에서는 1번이 작다. 다만 외부 MCP session 과 웹 login session 의 수명이
  섞일 수 있으므로, 장기적으로는 provider session repository 를 서비스에
  명시 주입하는 구조가 더 낫다.

LMS 도 같은 원칙을 따른다.

LIBRARY private tool:

```text
MCP client
  -> get_my_library_loans(mcp_session_id)
  -> McpAuthService.requireProvider(LIBRARY, mcp_session_id)
  -> provider token 확인
  -> LibraryLoansService.getLoansForSession(sessionKey)
```

`LibraryLoansService` 는 현재 web `sessionKey` 기반이다. 구현 단계에서
`LibraryLoansService.getLoansForToken(...)` 또는 provider-session abstraction 이
필요하다. 웹 session key 를 MCP session id 로 재사용하면 동작은 쉽지만 책임이
섞이므로 피한다.

### 5. Web/App Alignment

웹은 MCP 서버를 우회해서 보강하지 않는다.

정리 방향:

- 웹 카드와 챗봇은 같은 provider auth 상태를 본다.
- 웹 로그인 완료 후에도 provider session 은 `McpAuthService` 또는 동일한
  provider-session service 에 저장된다.
- 웹 REST endpoint 는 MCP tool 과 같은 Service/Auth 경계를 사용한다.
- 챗봇은 더 이상 `SaintToolContext` / `LmsToolContext` 로 private tool 을
  억지로 열지 않는다.

단계적으로는 기존 웹 흐름을 한 번에 깨지 않고 다음 순서로 옮긴다.

1. MCP auth session 모델 추가.
2. 외부 MCP private tools 가 새 모델로 동작하게 만든다.
3. 웹 카드 REST endpoint 를 새 provider-session service 로 맞춘다.
4. 챗봇 private tool dispatch 에서 ThreadLocal context 를 제거한다.
5. 문서에서 "외부 MCP 클라이언트는 private tool 호출 불가" 문구를 제거한다.

### 6. Chat Memory

이미 `ChatConversationStore` 가 conversation 단위 memory 를 제공한다.

현재 상태:

- 같은 `conversationId` 안에서 user/assistant turn 을 저장한다.
- TTL 과 최대 turn 수가 있다.
- 따라서 "해당 대화 세션에서는 기억" 요구는 기본 구조가 있다.

추가로 필요한 정리:

- 프론트가 새로고침/탭 이동 후에도 같은 `conversationId` 를 유지하는지 확인.
- 사용자 로그아웃 시 해당 conversation memory 를 지울지 정책 결정.
- private data tool 결과는 지금처럼 compact/scrub 해서 LLM memory 에
  민감 본문을 넣지 않는다.

### 7. Security Rules

- `mcp_session_id`, `state`, provider token, cookie 는 secret 취급.
- 로그에는 fingerprint 만 남긴다.
- `state` 는 one-time, 짧은 TTL, provider 고정.
- callback 에 provider mismatch 가 있으면 실패 처리한다.
- login success page 에 cookie/token/studentId 를 노출하지 않는다.
- private tool 은 caller-supplied student id/name 을 받지 않는다.
- MCP auth session logout 을 provider 별로 제공한다.
- upstream auth 실패/만료는 `AUTH_REQUIRED` 로 degrade 한다.
- LLM provider 로 성적/과제 원문을 보내지 않는 기존 compact policy 는 유지한다.

### 8. Test Plan

Backend unit tests:

- `McpAuthStateStore`
  - state 생성/소비 성공.
  - 만료 state 거부.
  - 이미 소비한 state 재사용 거부.
  - provider mismatch 거부.
- `McpAuthSessionStore`
  - provider 연결/조회/삭제.
  - TTL 만료.
  - LRU cap.
  - fingerprint logging helper.
- MCP auth tools
  - 인증 없음: `AUTH_REQUIRED` + loginUrl 반환.
  - 인증 있음: service 호출.
  - 만료 session: `AUTH_REQUIRED`.
- callback controllers
  - SAINT/LMS success: provider session 연결.
  - auth failure: 완료 페이지가 실패 메시지를 보여주고 secret 미노출.
- library MCP login
  - credential login success: token 저장.
  - credential failure: session 미저장.

Integration tests:

- 실제 upstream 은 mock connector / mock auth service 로 대체.
- private tool 호출 → auth required → callback simulation → private tool 성공.

Regression tests:

- 기존 웹 로그인/card API 는 깨지지 않아야 한다.
- 기존 public MCP tools 는 인증 없이 계속 동작해야 한다.

### 9. Implementation Slices

#### Slice A — Auth session primitives

- `domain.auth.mcp` package 추가.
- provider enum, state/session stores, response DTO 추가.
- 아직 기존 tool behavior 는 바꾸지 않는다.

#### Slice B — MCP auth tools/endpoints

- `get_auth_status`, `start_auth`, `logout_provider`, `logout_all` 추가.
- `/api/mcp/auth/{provider}/start|callback` 추가.
- SAINT/LMS callback 은 기존 SSO service 재사용.

#### Slice C — Private MCP tools migrate

- `get_my_schedule`, `get_my_grades`, `get_my_assignments`,
  `get_my_library_loans` 를 ThreadLocal-only 에서 MCP auth session 기반으로
  변경한다.
- 인증 없음은 exception 대신 `AUTH_REQUIRED` 응답.

#### Slice D — Web align

- 웹 카드와 챗봇이 같은 provider auth status 를 사용하도록 정리.
- SmartID/LMS/도서관 logout 버튼은 provider 별 session 을 지운다.
- 프론트의 conversation id 유지 정책 확인.

#### Slice E — Docs

- `docs/mcp-tools.md` 갱신.
- `docs/architecture.md` MCP auth subsection 추가.
- `docs/security.md` 에 MCP session/state logging rule 보강.

## Open Questions

1. `mcp_session_id` 를 tool argument 로 둘지, Spring AI MCP transport 에서
   per-client session id 를 꺼낼 수 있는지 spike 후 숨길지.
2. 성공 응답을 기존 DTO 그대로 둘지, `status: "OK"` 래퍼로 통일할지.
3. MCP auth session 을 in-memory 로 충분히 둘지, 운영 배포에서 Redis/JDBC
   도입을 이 task 에 포함할지.
4. 도서관 MCP 로그인 페이지를 백엔드 HTML 로 둘지, 프론트엔드 라우트로 둘지.

## Recommended First Implementation

MVP 는 다음 선택을 권장한다.

- `mcp_session_id` 명시 argument 사용.
- 모든 private tool 은 `{status, data?, auth?}` 래퍼 응답 사용.
- stores 는 encrypted in-memory 로 시작.
- SAINT/LMS 는 기존 SSO service 재사용.
- Library 는 백엔드 간단 HTML form 보다 프론트엔드 `/mcp/auth/library`
  페이지로 시작한다. 기존 디자인/상태 관리와 맞추기 쉽다.

이 순서가 현재 코드 변경량을 작게 유지하면서도 목표 방향, 즉 "웹이 MCP 를
보강하는 구조"에서 "웹이 MCP auth/service model 을 소비하는 구조"로 넘어가는
가장 현실적인 경로다.
