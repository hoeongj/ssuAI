# Session handoff — 2026-05-17 밤 (버그 수정 + 인프라 정리, Phase 4 대기)

> Single rolling handoff (CLAUDE.md / AGENTS.md 정책). 이전 handoff overwrite.

## TL;DR

- **이 세션 작업**: 대시보드 카드 5개 추가, Helm 커넥터 플래그, dev .env 로딩 수정, k8s 운영 스크립트 2개, 대출 카드 인증 UI 수정.
- **main 브랜치 최신, 미커밋 없음.**
- 다음: **Phase 4 — 도서관 좌석 자동 예약 에이전트** (`reserve_library_seat`).
- 서버 수동 작업 2가지 남아 있음 (아래 §Operations 참고).

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

## 이번 세션 커밋 목록

| SHA | 내용 |
|-----|------|
| `af7c615` | feat(frontend): 대시보드에 5개 카드 추가 (ScheduleCard, GradesCard, AssignmentsCard, LibraryLoansCard, LibraryBookSearchCard) |
| `9ae3cac` | chore(deploy): Helm 차트에 누락 커넥터 env 추가 + dev .env 로딩 수정 |
| `5dffacd` | chore(deploy): k8s secrets/image 스크립트 추가 + 도서관 좌석 기본 층 4→2 수정 |
| `f2b70aa` | fix(frontend): LibraryLoansCard LIBRARY_SESSION_REQUIRED 전용 인증 UI |
| `1164bb8` | chore(backend): application-prod.yml에 real 커넥터 기본값 명시 |

## Operations — 서버에서 수동으로 해야 할 것

### 1. API 키를 k8s Secret에 적용 (챗봇 CHAT_UNAVAILABLE 해결)

서버 SSH에서 (repo root 기준):
```bash
bash deploy/scripts/apply-k8s-secrets.sh
kubectl rollout restart deployment/ssuai-backend -n ssuai-prod
```
`backend/.env`를 읽어서 Secret을 idempotent하게 적용하고 재시작.

### 2. 파드 이미지 업데이트 (MCP 4개 툴 + 새 커넥터 활성화)

CI가 최신 커밋(`1164bb8`)으로 이미지를 빌드하면:
```bash
bash deploy/scripts/update-k8s-image.sh
```
`KUBE_CONFIG` GitHub Secret이 없으면 위 스크립트를 서버에서 직접 실행.

### 3. Vercel 환경변수 확인

Vercel 프로젝트 → Environment Variables에 아래가 있는지 확인:
```
NEXT_PUBLIC_SSUAI_API_BASE=https://ssumcp.duckdns.org
```

### 4. 도서관 좌석 데이터 불일치

실제 도서관 앱과 층/공간 종류 및 좌석 수가 다름. 사용자가 실제 데이터 제공 후 수정 예정.

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
- MCP 서버 10개 tool 완성, 대시보드 카드 10개 모두 있음
- 서버 Operations 2가지 수동 남아 있음 (§Operations 참고)
- 다음: Phase 4 도서관 좌석 자동 예약 에이전트 (Pyxis 예약 POST spike 필요)

Phase 4 설계 방향 제안부터 시작. Pyxis DevTools 스파이크 선행 필요.
```
