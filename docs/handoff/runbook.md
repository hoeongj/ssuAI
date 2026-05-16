# Handoff & operations runbook

CLAUDE.md / AGENTS.md 의 짧은 포인터에서 들어오는 상세 절차. 매 턴
로드되지 않으므로 필요할 때만 펼쳐서 사용.

## Session handoff to a different AI

Trigger 문구: "토큰 끝났어", "다른 AI로 갈게", "다른 클로드로 이어갈게",
"이제 codex 로 할게", "claude2 로 넘길게", 기타 agent swap 신호. 다음
루틴을 순서대로 실행:

1. **Git state snapshot** — `git status --short --branch`, 현재 branch
   에 대해 `git log --oneline origin/main..HEAD`. 미커밋/미푸시 work +
   열린 PR (`gh pr list --state open --author "@me"`). 인-프로그레스
   work commit/push 여부를 yes/no 로 한 번만 물음. no 면 그대로 둠.

2. **Overwrite `docs/handoff/latest.md`** — single rolling handoff.
   이전 dated handoff 는 같은 commit 에서 삭제 (git history 가 보존).
   섹션:
   - **TL;DR** — 3-5 bullets
   - **이번 세션 머지/푸시** — PR/commit table
   - **열린 PR** — branch + waiting-on
   - **외부 의존** — 사용자가 직접 처리할 spike / 동료 응답 등.
     [[feedback-user-will-notify]] 표시
   - **다음 세션 액션** — ordered, 각 항목 self-contained (no "see
     above"), 파일/브랜치/PR 명시
   - **사용자 컨텍스트** — preferences, deadlines, fresh agent 가 모를
     함정 (auto-merge / no-Claude-trailer 등)
   - **보안 주의** — one-shot 토큰, secrets, 로그/echo 금지 항목

3. **Next-AI opener block** — 사용자가 다음 AI 첫 턴에 그대로 paste
   할 단일 fenced code block 출력. **첫 줄은 반드시 `/model opusplan`**.
   다음 agent 가 `AGENTS.md` 또는 `CLAUDE.md`, `docs/handoff/latest.md`,
   `MEMORY.md` 를 먼저 읽고 `git status --short --branch` 후 액션 #1
   부터 진행하도록 지시. 현재 task ID / PR # / branch 명 포함.

4. Handoff 문서 자체를 commit/push — `chore/handoff-<date>` 브랜치 PR
   또는 main 직 push. 메타데이터만이므로 auto-merge 정책 적용.

사용자가 explicit sync/push 요청 시: 의도된 변경만 commit/push, feasible
하면 verify 후.

## Session close sync

"대화 종료" / stop-for-now 등 close-out 신호 시:
1. `git status --short --branch` 먼저 확인
2. 미커밋/미푸시 있어도 자동 commit/push X — 변경 사항 요약 + explicit
   permission 받고 진행

## Troubleshooting log policy

`TROUBLESHOOTING.md` 추가 기준 — portfolio interview 에서 꺼낼 만한
case 만:
- 실제 bug, failed/flaky verification
- 배포/CI 실패
- 외부 integration mismatch
- security/privacy risk
- 의외의 architecture trade-off
- user-visible regression
- portfolio 에 쓸 fix

엔트리 포맷 (한국어): symptom / root cause / fix / verification. 시크릿
이나 개인 학생 데이터 절대 X. 단순 commit 했다고 자동 추가 금지.

## CI / token usage

- 장시간 polling 금지: `gh run watch` / `gh pr checks --watch` X
- One-shot 확인: `gh pr checks`, `gh run list --limit 5`,
  `gh run view --json ...` 후 summary
- CI 실패 분석은 실패 step 또는 마지막 50-100 lines 만 (사용자가
  full log 요청하지 않는 한)

## Remote server / phone workflow

- 사용자가 "서버로 접속했어" / VPS / 원격 머신 신호 → 그 clone 을 active
  로 취급
- GitHub 를 sync source 로. 영구 clone 에서 `git pull --ff-only` /
  `git push`. 재clone 권유는 repo 가 없을 때만
- 셸이 Windows 가 아니면 Linux/macOS 커맨드 (`./gradlew test`)
- 서버 세부정보 / SSH / 토큰 / `.env` 값은 committed docs 에 절대 X.
  머신별 컨텍스트는 gitignored local note 사용
