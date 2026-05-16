# Session handoff — 2026-05-17 새벽 (MCP 서버 완성, Phase 4 대기)

> Single rolling handoff (CLAUDE.md / AGENTS.md 정책). 이전 handoff overwrite.

## TL;DR

- **MCP 서버 tool 10개 전부 구현 완료** (PR #139 머지됨).
- **main 브랜치 최신, 미커밋 작업 없음.**
- 다음 단계: **Phase 4 — 도서관 좌석 자동 예약 에이전트** (`reserve_library_seat`).
- 코드 전체 최적화 완료 (system prompt 정정, docs 업데이트, 불필요 코드 정리).

## 완성된 MCP tool 목록 (10개)

| tool | 종류 | 인증 |
|------|------|------|
| `get_today_meal` | read | 공개 |
| `get_meal_by_date` | read | 공개 |
| `get_dorm_weekly_meal` | read | 공개 |
| `search_campus_facilities` | read | 공개 |
| `get_library_seat_status` | read | 공개 (실 데이터: Pyxis-Auth-Token) |
| `search_library_book` | read | 공개 |
| `get_my_schedule` | read | u-SAINT SSO |
| `get_my_grades` | read | u-SAINT SSO |
| `get_my_assignments` | read | LMS SSO |
| `get_my_library_loans` | read | 도서관 세션 연동 |

## 열린 PR

없음 (모두 머지됨).

## 외부 의존

없음.

## 다음 Task — Phase 4 도서관 좌석 자동 예약

### 배경

`docs/mcp-tools.md` §8, `docs/adr/0015-action-tool-infrastructure.md`, `docs/vision.md` §3.4 참고.

### 구현 필요 사항

1. **`prepare_reserve_library_seat`** MCP tool  
   - 입력: `floor` (층), `seat_id` (좌석 번호)  
   - Pyxis 예약 POST 전 dry-run 문구 생성, `action_audit` DB row 삽입 (PREPARED)  
   - 응답: `pendingActionId` (UUID), 예약 내용 요약 문구

2. **`confirm_action`** MCP tool (공용)  
   - 입력: `pending_action_id`  
   - lookup → ActionLock → Pyxis 예약 POST → audit 상태 전이  
   - outcome: SUCCESS / FAILURE_RACE / FAILURE_AUTH / FAILURE_UPSTREAM / EXPIRED

3. **DB migration**: `action_audit` 테이블 (H2/Postgres 호환)

4. **`RealLibrarySeatReservationConnector`**: Pyxis 예약 POST 엔드포인트 스파이크 필요  
   - 실제 예약 POST shape 가 아직 미확인 → `/plan` 진입 후 spike 먼저

### 설계 선결 조건 (다음 세션 `/plan` 트리거)

- 실제 Pyxis 예약 POST 엔드포인트 + 요청/응답 body shape (DevTools 스파이크 필요)
- `action_audit` 테이블 스키마 확정
- ActionLock MVP (in-process ConcurrentHashMap) 구현 범위

## 사용자 컨텍스트

- 숭실대 컴퓨터학부 3학년. 포트폴리오 프로젝트.
- commit/PR body 에 Claude/AI 흔적 절대 금지 ([[feedback-no-claude-coauthor]]).
- 안전한 PR 자동 머지 가능 ([[feedback-auto-merge-safe-prs]]).
- 외부 작업 결과는 사용자가 통지 ([[feedback-user-will-notify]]).
- 간결한 한국어 응답 선호.

## Next-AI opener block

```text
/model opusplan

ssuAI 프로젝트 이어받음. 다음 순서대로:

1. CLAUDE.md (또는 AGENTS.md) Project + Implementation Workflow 섹션 읽기
2. docs/handoff/latest.md 읽기
3. git -C C:/Users/akftj/ssuAI status --short --branch — main 최신 상태 확인

현재 상태:
- MCP 서버 10개 tool 완성, main 브랜치 최신
- 다음: Phase 4 도서관 좌석 자동 예약 에이전트
- Pyxis 예약 POST shape 가 미확인 → `/plan` 모드로 스파이크 설계부터

사용자 짧은 답 선호. Phase 4 설계 방향 제안부터 시작.
```
