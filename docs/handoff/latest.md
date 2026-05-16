# Session handoff — 2026-05-16 밤 (Task 16 u-SAINT 데이터 layer 완료)

> Single rolling handoff (CLAUDE.md / AGENTS.md 정책). 이전 핸드오프 overwrite.

## TL;DR

- **Task 16 u-SAINT realtime data — 데이터 layer 완료**. 학적 portal + 시간표
  + 성적 3 endpoints 모두 end-to-end live (mock-default + real connector
  + REST controller + 단위 테스트 그린).
- **이번 세션 — PR 16c-B 빨강 3 → green 잡고 머지** (PR #128). prev fixture
  tbody id suffix fix + `termHistory` row 0 `passFailCredits` 기대값 6.0
  (spec-locked fixture 의 P/F-only 6학점 truth). `gradlew test` 전체 green.
- **main clean, 열린 PR 없음, 작업 브랜치 다 삭제**. 외부 의존 (TTL spike
  서버 polling + GitHub Actions auto-deploy) 만 남음.
- **이번 세션 자동 작업 추가**: PR #130 — `LlmChatService.compactAndCap` 에
  `get_my_grades` ({count,link} 만) / `get_my_schedule` (compact row) 분기
  잠금 + 단위 테스트 3개로 본문 누출 영구 고정 (spec §6 #6 / §8). Task
  16 security checklist 8개 중 7개 ✅, 1개 (grades audit log) MCP tool 등록
  시점으로 deferred.
- **다음 세션 옵션**: (a) Task 16 follow-up — `get_my_schedule`/`get_my_grades`
  MCP tool 등록 — chat thread-local pattern 결정 필요 (아키텍처) /
  (b) Task 17 (LMS integration) 신규 시작 — `get_my_assignments`, 세션
  store 패턴 재사용, auth shape spike 필요 (사용자).

## 이번 세션 머지/푸시

| 항목 | 상태 | PR / Branch |
|------|------|-------------|
| PR #128 성적 service+controller+tests | ✅ MERGED | `feat/task-16c-grades-impl` (deleted) |
| PR #130 chat LLM 누출 가드 (grades/schedule compactAndCap + tests) | ✅ MERGED | `feat/chat-grades-schedule-leak-guard` (deleted) |

이전 세션 정리 잔여: 메타 (이 핸드오프 doc 자체) 만 남음. 본 doc 머지로 정리 완료.

## 열린 PR

없음.

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | TTL spike — ssumcp 서버 nohup polling | 🟢 백그라운드. PID 385859, log `~/ssuAI/scripts/ssotoken-ttl.log`, 1h interval, expired 감지 시 자동 종료. 결과 사용자 통지 대기 ([[feedback-user-will-notify]]) |
| 2 | GitHub Actions auto-deploy (KUBE_CONFIG) | ⏳ 사용자 의지 (선택) |

## Task 16 현재 상태 — 정확

### ✅ 완료 (main 머지됨)

| 영역 | 산출물 |
|------|--------|
| 학적 portal | PR #110~ : `PortalSnapshotConnector` + `SaintLoginService` (smart-id auth → cookie store) |
| 시간표 (PR #126) | dto/parser/encoder/unwrapper/connector(mock+real)/service/controller + `application.yml` `ssuai.connector.saint-schedule: mock` |
| 성적 fixture+spec lock (PR #127) | `grades-success.html` (180KB redact), spec §3.5 잠금 (WebDynpro wrapper, cc=0..13, 한국어 학기 라벨, prev-button iterate) |
| 성적 구현 (PR #128) | dto (TermGpa/GpaSummary/CourseGrade/GradesResponse), `GradesParser`, `MockSaintGradesConnector` (default), `RealSaintGradesConnector` (WD01F0 prev iterate), `SaintGradesService`, `SaintGradesController` (`GET /api/saint/grades`), 5 test class 그린 |

### ❌ Task 16 follow-up (다음 PR 후보)

1. **`get_my_schedule` / `get_my_grades` MCP tool 등록** — 시간표/성적 둘 다
   first cut 에서 빠짐. 이유: spec §9 의 "chat thread-local pattern" 미구비
   (chat path 가 studentId 를 MCP self-call 에 propagate 하는 mechanism 없음)
   + 외부 MCP client (Claude Desktop) 가 학번 인자 위조 가능. **선결 조건**:
   chat 본인 호출 vs 외부 MCP client 호출 구분 + studentId thread-local
   주입. 그 다음 별도 PR 에서 두 tool 등록. ← **아키텍처 결정 필요**.
   compactAndCap 정책은 이미 잠겨있어 (PR #130) wiring 시 자동으로 안전한
   형태로만 LLM 에 전달.
2. ~~`LlmChatService.compactToolResponse` 본문 누출 가드 단위 테스트~~
   ✅ DONE PR #130. grades = `{count,link}` 만, schedule = compact row,
   단위 테스트 3개로 영구 고정.
3. **multi-term nav 확장** — 시간표 현재는 "전체 학년도 1학기 iterate" 만
   (WDA7 prev). 2학기/계절학기 nav follow-up (spec §3.4). 새 fixture
   capture 필요 (사용자 브라우저 spike).

## Task 17 — LMS integration (다음 큰 마디 후보)

`docs/tasks/17-lms-integration.md` 작성 완료 (이전 세션). Phase 3 4번째
deliverable. `get_my_assignments` 1개. Task 16 의 encrypted session store
패턴 재사용 — LMS host 가 별 host + 별 auth 라 첫 단계가 **auth shape spike**.
Task 17 §1 / §2 부터 읽어서 시작.

## 사용자 컨텍스트 — 잊지 말 것

- 숭실대 컴퓨터학부 3학년 (학번 20221528, 홍성주). 본인 학번/이름은
  채팅 paste OK ([[feedback-user-student-id-not-sensitive]]). 단
  **fixture/commit/server log 에는 절대 placeholder** (20999999/홍길동).
- 3-AI rotation (claude1/claude2/codex). CLAUDE.md = AGENTS.md mirror
  ([[role-3-ai-rotation]]).
- **commit/PR body 에 Claude/AI 흔적 절대 금지** ([[feedback-no-claude-coauthor]]).
- 안전한 PR 자동 머지 — mergeable + tests pass + mock default
  ([[feedback-auto-merge-safe-prs]]). 본 세션도 PR #128 auto-merge.
- 외부 작업 사용자 통지 대기 ([[feedback-user-will-notify]]) — TTL spike +
  GitHub Actions auto-deploy 둘 다 그대로.
- 본인 JWT/secret/cookie 채팅 노출 시 rotate/redact 권고 X
  ([[feedback-own-auth-values-not-sensitive]]).
- 간결한 한국어 응답 선호. "너무 애매하게 설명하지 말고 그냥 내가
  해줘야하는것만 알려줘" — 다음 AI 도 짧게 짧게.
- 두 제품 framing — MCP 서버 + ssuAI 웹/앱 ([[project-final-goal]]).

## 보안 주의

- prod Secret `ssuai-backend-secrets` (plural).
- 사용자 본인 학번/이름은 chat 면제. fixture/commit/log 는 placeholder.
- **성적 / 시간표는 LLM 프롬프트 raw 금지** — Task 16 follow-up #2
  (`compactToolResponse` 누출 가드 단위 테스트) 가 영구 고정 책임.
- MYSAPSSO2 SAP SSO 토큰 base64 디코드 시 학번 노출. SaintSessionStore
  AES-256-GCM 으로 이미 처리 ✅.
- 사용자 본인 성적 raw 데이터 다운로드 파일 (`C:\Users\akftj\Downloads\grades-raw.txt`
  등) PR 작업 중 cleanup 완료. 잔여 없음.

## 자동 메모리

`C:\Users\akftj\.claude-personal\projects\C--Users-akftj-ssuAI\memory\`

이번 세션 신규 메모리 없음 (PR 16c-B 작업은 in-repo 에 다 잠김).

---

## Next-AI opener block

```text
ssuAI 프로젝트 이어받음. 다음 순서대로:

1. AGENTS.md (또는 CLAUDE.md — 동일 mirror) 의 Project + Session-handoff 섹션 읽기
2. docs/handoff/latest.md 읽기 — 이번 인계 (Task 16 u-SAINT 데이터 layer 완료, main clean)
3. MEMORY.md (auto-memory) 한 번 훑기
4. git status --short --branch — main clean 확인

현재 상태:
- main clean, 열린 PR 없음, 작업 브랜치 다 삭제됨
- u-SAINT 3 endpoints (학적/시간표/성적) end-to-end live with mock default
- compactAndCap 의 LLM 누출 가드 잠금 (PR #130) — grades = {count,link} 만, schedule = compact row
- Task 16 security checklist 7/8 ✅ (남은 1개는 MCP tool 등록 시점)
- 외부 의존 그대로 (TTL spike 서버 polling + GitHub Actions auto-deploy 선택) — 폴링/언급 금지, 사용자가 결과 통지

다음 세션 옵션 — 사용자 의지 확인 후 진행:
(a) Task 16 follow-up — get_my_schedule/get_my_grades MCP tool 등록 (chat thread-local pattern 결정 필요)
(b) Task 17 (LMS integration) 신규 시작 — docs/tasks/17-lms-integration.md §1/§2 읽기

사용자 짧은 답 선호. 진단/계획만 길게 늘어놓지 말고 바로 진행.
```
