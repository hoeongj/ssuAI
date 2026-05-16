# CLAUDE.md

> **Mirror with `AGENTS.md` — identical body.** Claude Code 는
> `CLAUDE.md`, Codex CLI 는 `AGENTS.md` 를 자동 로드. 프로젝트 규칙
> 변경 시 **두 파일 같은 commit 으로 동기화 필수**. (한쪽 편집 → 다른쪽
> 복사)

## Project
ssuAI 는 **두 개의 분리된 제품**을 함께 만든다.

1. **숭실대 MCP 서버** — 학식·기숙사·시설·도서관·u-SAINT/LMS 등 캠퍼스
   정보를 MCP 표준 도구로 노출하는 공개 서버. Claude Desktop · Cursor
   등 모든 MCP 클라이언트에서 동일하게 동작.
2. **ssuAI 웹/앱** — 위 MCP 서버를 소비하는 자체 클라이언트. 카드형
   대시보드 + 자연어 챗봇 + AI 에이전트.

**🏆 Flagship — 도서관 좌석 자동 예약 에이전트.** *"이 자리 예약해줘"*
한 마디로 실제 학교 시스템 상태를 바꾸는 end-to-end 에이전트.

Long-term direction: `README.md`, `docs/vision.md`. Short-term scope &
현재 MVP: `docs/product.md`. Architecture / Security playbook:
`docs/architecture.md`, `docs/security.md`. **대형 문서는 관련 섹션만
read** — 전체 read 는 task 범위가 정말 넓을 때만.

## Your Role
3-AI rotation (claude1 / claude2 / codex) — 토큰 잔량에 따라 교대.
현재 세션의 owner 는 architect + implementer + reviewer 다 담당.
다른 agent 기다리지 마라. State 는 `docs/handoff/latest.md`,
`docs/tasks/`, `docs/dev-log.md`, git history 로 인계.

비자명 feature 는 design (Goal/API/data flow/security/test) → 사용자
승인 → 구현. 작은 fix 는 그냥 구현. multi-step 작업은 `TaskCreate` 로
내부 추적.

## User Context
숭실대 컴퓨터학부 3학년. 기본 Spring CRUD 익숙 / production backend
학습 중. 포트폴리오 프로젝트. 설명은 step-by-step, 과한 추상화 X,
"학생 1명이 현실적으로 만들 수 있는 인상적 결과물" 지향. 간결한 한국어
응답 선호.

## Review Style
기존 코드 리뷰 시:
1. Architecture consistency (`docs/architecture.md`)
2. Responsibility separation (Controller / Service / Repository / Connector)
3. Security (`docs/security.md` 특히 §4 Logging)
4. Testability
5. 현재 stage 대비 과한지

최대 3개 high-priority issue. 형식:

```
Overall: Good / Needs changes / Risky

Top issues:
1. ...
2. ...
3. ...

Recommended next action:
...
```

## Design Style
비자명 feature 는 코드 전에 짧은 design: (1) Goal / Scope / Non-goals
(2) API design (3) 패키지·클래스 책임 (4) data flow (5) security
(6) test plan. 작거나 기계적 변경 skip. 사용자 승인 후 구현, 별도
hand-off 파일 X.

durable feature spec 은 `docs/tasks/<NN>-<name>.md`. 그 외는 conversation
context 에서 작업.

## Implementation Workflow
- `git -C C:/Users/akftj/ssuAI status --short --branch` 로 시작 (PowerShell cwd 가 backend/ 일 수 있으니 절대경로)
- 백엔드 테스트: PowerShell cwd = backend/ 기준 `.\gradlew.bat test` 또는 `.\gradlew.bat test --tests "..."`
- git 커밋/push: `git -C C:/Users/akftj/ssuAI <subcommand>` 또는 Bash 툴 사용
- 한 feature = 한 PR. 너무 크면 분할
- Branch: `feat/` `fix/` `refactor/` `chore/` `docs/` + kebab-case
- Commit: Conventional Commits (`feat(backend): ...`)
- Verify 후 done 선언: 백엔드 `.\gradlew.bat test`, 프론트 `pnpm --dir frontend test|lint|typecheck`

## opusplan 워크플로 (현재 모델 설정)
- **설계·아키텍처 결정** (`/plan` 진입) → Opus 4.7 작동. 비자명 feature 설계, spec 검토, security 판단 등
- **구현·테스트·커밋** (일반 모드) → Sonnet 작동. 파일 편집, 테스트 실행, git 커밋, PR 열기
- `/plan` 은 설계 판단이 필요한 순간에만 — 단순 fix·커밋·테스트 실행은 일반 모드로 처리

## Authorship & Merge
- **No Claude/AI/Anthropic attribution** — commit / PR body / docs /
  code comment 어디에도 "Claude", "Anthropic", "🤖 Generated with…",
  `Co-Authored-By: Claude` trailer 금지. 미머지 브랜치에 흔적 있으면
  amend/rebase 로 제거. 머지된 legacy 는 silent rewrite 금지.
  [[feedback-no-claude-coauthor]]
- **Auto-merge safe PRs** — `mergeable: MERGEABLE` + tests pass +
  런타임 영향 OFF by default + 신규 파일 위주이면 confirm 없이
  `gh pr merge <N> --rebase --delete-branch` → `git checkout main &&
  git pull --ff-only origin main`. force-push main / prod flag flip /
  DB migration / major dep bump / 다른 clone(server·phone) 영향은 ask
  먼저. 자세한 기준 [[feedback-auto-merge-safe-prs]].

## External work, session lifecycle, ops detail
구체 routine 은 `docs/handoff/runbook.md`. trigger 시 거기 가서 절차
실행.

- **"내가 알려줄게" / "끝나면 알려줄게"** 한 외부 작업 → 폴링/언급/옵션
  매트릭스 포함 금지. 사용자가 결과 통지. [[feedback-user-will-notify]]
- **"토큰 끝났어" / "다른 AI 로 갈게" / "claude2·codex 로 넘길게"** →
  runbook §Session-handoff (snapshot → handoff doc overwrite →
  next-AI opener block → handoff commit)
- **"대화 종료" / stop-for-now** → `git status` 먼저, 미커밋 자동
  commit/push 금지. runbook §Session-close-sync
- **TROUBLESHOOTING.md / 원격 서버 / CI 절약** → runbook 의 해당 섹션

## Current Phase
`docs/tasks/` 에 active / pending spec. 사용자가 final decision maker —
광범위한 아키텍처 변경은 propose → 사용자 승인 → 진행. silent 변경 X.
