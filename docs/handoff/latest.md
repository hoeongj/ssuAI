# Session handoff — 2026-05-17 새벽 (Task 16 데이터 layer + MCP + multi-term nav 완료)

> Single rolling handoff (CLAUDE.md / AGENTS.md 정책). 이전 핸드오프 overwrite.

## TL;DR

- **Task 16 u-SAINT 전부 완료**. 학적 portal + 시간표 + 성적 3 endpoints
  end-to-end live, MCP tool 2개 등록 (`get_my_schedule`, `get_my_grades`),
  LLM 본문 누출 가드 잠금, audit log + security checklist 8/8 ✅.
- **이번 세션 7 PR 머지** (PR #128~#134) — 16c-B fix, handoff roll,
  LLM compact guard, checklist tick, MCP tool 등록 + chat 인증 plumb,
  audit log, 시간표 multi-term nav 확장 (WDA7 의미 fix + 4-term cycle).
- **main clean, 열린 PR 없음, 작업 브랜치 다 삭제**.
- **외부 의존 그대로**: TTL spike (ssumcp 서버 nohup polling) +
  GitHub Actions auto-deploy (선택).
- **다음 세션 옵션**: Task 17 LMS (auth shape spike 사용자 필요).

## 이번 세션 머지/푸시

| 항목 | PR | Branch (deleted) |
|------|----|------------------|
| Task 16c-B 빨강 3 → green | #128 | `feat/task-16c-grades-impl` |
| Handoff doc roll | #129 | `chore/handoff-post-16c-b` |
| LLM compact 누출 가드 | #130 | `feat/chat-grades-schedule-leak-guard` |
| spec §8 checklist 7/8 + handoff | #131 | `chore/docs-task16-checklist-progress` |
| MCP tool 등록 + chat 인증 plumb | #132 | `feat/mcp-tools-grades-schedule` |
| audit log + checklist 8/8 ✅ | #133 | `chore/grades-audit-log` |
| 시간표 multi-term nav (WDA7 fix + 4-term cycle) | #134 | `feat/schedule-multi-term-nav` |

## 열린 PR

없음.

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | TTL spike — ssumcp 서버 nohup polling | 🟢 PID 385859, log `~/ssuAI/scripts/ssotoken-ttl.log`. 결과 사용자 통지 대기 ([[feedback-user-will-notify]]) |
| 2 | GitHub Actions auto-deploy (KUBE_CONFIG) | ⏳ 사용자 선택 |

## Task 16 현재 상태 — 완료

- **dto/parser/connector/service/controller** — 3 endpoints (학적/시간표/성적)
- **MCP tools** — `get_my_schedule`, `get_my_grades` (chat thread-local
  pattern, 외부 client 학번 위조 차단)
- **chat 인증 plumb** — `ChatController` → `chatService.reply(.., studentId)` →
  `SaintToolContext.withStudentId(...)` scope → executeToolCall direct
  service call (MCP loopback 우회, ThreadLocal 안전망)
- **LLM 누출 가드** — `compactAndCap`: grades = `{count, link}` 만,
  schedule = compact row (dayOfWeek/period/course/room 만)
- **audit log** — `dispatchPrivateSaintTool` 의 requested/completed/expired
  세 log line 다 `tool={name} studentFp={SHA-256 prefix}` 만, payload X.
  logback ListAppender 기반 테스트로 영구 고정.
- **시간표 multi-term nav** — WDA7 = 이전학기 (4-term cycle), parser 가
  응답 dropdown 에서 (year, term) 직접 읽음. 4년 학생 = 17 entries
  (현재 + 16 PREV hops).
- **security checklist 8/8 ✅**

## 다음 세션 옵션

### Task 17 — LMS integration (Phase 3 4번째)

`docs/tasks/17-lms-integration.md` §1/§2 부터. encrypted session store
패턴 (Task 16 SaintSessionStore) 재사용. **선결 spike (사용자)**:
LMS host, auth mechanism, assignments page URL, parse target. 캡쳐
방법은 Task 16 PR #126 / #134 과 동일 (DevTools Network 또는 view-source).

### 기타 follow-up

- 시간표 cache (spec §6 #5) — Service-layer in-memory TTL ~1h
- 다음학기 (WDA9) 도 지원 (현재는 PREV iterate 만). 휴학·복학으로
  현재 학기보다 미래에 등록된 강의가 있는 케이스가 거의 없어 우선순위
  낮음.

## 사용자 컨텍스트 — 잊지 말 것

- 숭실대 컴퓨터학부 3학년 (학번 20221528, 홍성주). 본인 학번/이름은
  채팅 paste OK ([[feedback-user-student-id-not-sensitive]]). 단
  **fixture/commit/server log 에는 절대 placeholder** (20999999/홍길동).
- 3-AI rotation (claude1/claude2/codex). CLAUDE.md = AGENTS.md mirror
  ([[role-3-ai-rotation]]).
- **commit/PR body 에 Claude/AI 흔적 절대 금지** ([[feedback-no-claude-coauthor]]).
- 안전한 PR 자동 머지 — mergeable + tests pass + mock default
  ([[feedback-auto-merge-safe-prs]]). 본 세션 7 PR 다 auto-merge.
- 외부 작업 사용자 통지 대기 ([[feedback-user-will-notify]]) — TTL +
  Actions auto-deploy 그대로.
- 본인 JWT/secret/cookie 채팅 노출 시 rotate/redact 권고 X
  ([[feedback-own-auth-values-not-sensitive]]).
- 간결한 한국어 응답 선호. 진단/계획 길게 늘어놓지 말고 바로 진행.
- 두 제품 framing — MCP 서버 + ssuAI 웹/앱 ([[project-final-goal]]).
- portal HTML 캡쳐 부탁 시 Ctrl+U view-source 명시 필수
  ([[learning-browser-view-source-vs-devtools]]).

## 보안 주의

- prod Secret `ssuai-backend-secrets` (plural).
- 사용자 본인 학번/이름은 chat 면제. fixture/commit/log 는 placeholder.
- **성적/시간표 raw → LLM 프롬프트 절대 금지** — `compactAndCap` PR #130
  + 단위 테스트로 영구 잠금.
- MYSAPSSO2 SAP SSO 토큰 base64 디코드 시 학번 노출. SaintSessionStore
  AES-256-GCM 으로 처리 ✅.
- MCP tool 의 학번 인자 X — `SaintToolContext` ThreadLocal 만 신뢰.
  외부 MCP client 호출 시 미바인딩 → `IllegalStateException`.

## 자동 메모리

`C:\Users\akftj\.claude-personal\projects\C--Users-akftj-ssuAI\memory\`

이번 세션 신규 메모리 없음 (Task 16 작업은 in-repo 에 다 잠김).

---

## Next-AI opener block

```text
ssuAI 프로젝트 이어받음. 다음 순서대로:

1. AGENTS.md (또는 CLAUDE.md — 동일 mirror) 의 Project + Session-handoff 섹션 읽기
2. docs/handoff/latest.md 읽기 — 이번 인계 (Task 16 데이터 layer + MCP + multi-term nav 전부 완료)
3. MEMORY.md (auto-memory) 한 번 훑기
4. git status --short --branch — main clean 확인

현재 상태:
- main clean, 열린 PR 없음, 작업 브랜치 다 삭제
- Task 16 u-SAINT 완료 — 학적/시간표/성적 3 endpoints end-to-end live, MCP tool 2개 등록, LLM 누출 가드 + audit log 영구 잠금, multi-term nav 4-term cycle 확정
- 외부 의존 (TTL spike 서버 polling + Actions auto-deploy 선택) — 폴링/언급 금지, 사용자가 결과 통지

다음 세션은 사용자 의지 확인 후 진행:
(a) Task 17 (LMS integration) — docs/tasks/17-lms-integration.md §1/§2, auth shape spike 사용자 필요
(b) 기타 follow-up (시간표 cache, WDA9 NEXT 지원 등 — 우선순위 낮음)

사용자 짧은 답 선호. 진단/계획만 길게 늘어놓지 말고 바로 진행.
```
