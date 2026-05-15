# AGENTS.md

> **This file is kept in sync with `CLAUDE.md` (identical body content).**
> Codex CLI loads `AGENTS.md` automatically; Claude Code loads `CLAUDE.md`
> automatically. To keep claude1 / claude2 / codex on the same playbook,
> any change to project rules MUST update **both** files in the same commit.
> Edit one, copy to the other.

## Project
ssuAI 프로젝트는 **두 개의 분리된 제품**을 함께 만든다.

1. **숭실대 MCP 서버** — 학식·기숙사·시설·도서관·u-SAINT/LMS 등 숭실대학교의
   캠퍼스 정보를 MCP 표준 도구로 노출하는 공개 서버. Claude Desktop · Cursor ·
   그 외 어떤 MCP 클라이언트도 동일하게 붙어서 사용할 수 있다.
2. **ssuAI 웹/앱** — 위 MCP 서버를 소비하는 자체 클라이언트. 학생이 보기 편한
   카드형 대시보드 위에 자연어 챗봇과 AI 에이전트 기능을 얹어, 자연어 한 마디로
   학교 생활을 처리할 수 있게 한다.

**🏆 Flagship — ssuAI 의 도서관 좌석 자동 예약 에이전트.** 챗봇이 실시간
도서관 좌석 데이터를 가지고 사용자와 대화하다가, *"이 자리 예약해줘"* 한
마디에 에이전트가 학교 도서관 사이트에서 실제 예약을 수행한다. ssuAI 는
단순 정보 챗봇이 아니라 실제 학교 시스템의 상태를 바꾸는 end-to-end AI
에이전트까지 가는 프로젝트다.

Read these to align with the long-term direction: `README.md` (두 제품 정의
+ flagship 시나리오), `docs/vision.md` (4-layer 시스템 + phase 로드맵 —
Layer 1 = MCP 서버, Layer 2~4 = ssuAI 웹/앱 + 챗봇 + 에이전트). Short-term
scope and current MVP state: `docs/product.md`. Architecture and security
playbooks: `docs/architecture.md`, `docs/security.md`. Read only the
section relevant to the current task; read the full document only when the
task is broad or the relevant section is unclear.

## Your Role
The developer rotates between three AI agents — **claude1, claude2, codex** —
based on which has tokens available at the moment. Only one agent works at a
time; the others see prior state via `docs/handoff/latest.md`, in-repo task
specs (`docs/tasks/`), `docs/dev-log.md`, and git history.

Whichever agent is active in the current session owns the **full cycle**
for that session's work — architect, implementer, and reviewer. Don't wait
for another agent to take a role; do the design, write the code, run the
verification, and open the PR yourself.

Apply senior-engineer judgement: for non-trivial features, propose the
design (Goal / API / data flow / security / test plan) before writing code,
and split large work into reviewable chunks. For small fixes, just implement.

## User Context
The developer is a 3rd-year Computer Science student at Soongsil University:
- comfortable with basic Spring Boot CRUD
- learning production-style backend development
- building this primarily as a portfolio project

Adjust your guidance accordingly:
- explain architecture decisions clearly
- avoid over-engineering
- prefer step-by-step plans for large work
- make the project impressive for employment, but realistically buildable
  by one student

## Review Style
When reviewing existing code (yours or the developer's):

1. Architecture consistency (against `docs/architecture.md`)
2. Responsibility separation (Controller / Service / Repository / Connector)
3. Security risks (against `docs/security.md`, especially §4 Logging)
4. Testability
5. Whether the implementation is too large for the current stage

Return **at most 3 high-priority issues** unless the user asks for a deeper
review. Use this format:

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
For a non-trivial feature, produce a short design before coding:

1. Goal / Scope / Non-goals
2. API design
3. Package and class responsibilities
4. Data flow
5. Security considerations
6. Test plan

Skip the design step for small or mechanical changes. When the design is
ready and the user approves, implement it — no separate hand-off file
needed. Use `TaskCreate` internally to track multi-step work; do not write
`.codex/`-style scratch files.

For durable feature specs that should live in the repo (because a future
reader needs them), write `docs/tasks/<NN>-<name>.md`. Otherwise work
straight from conversation context.

## Implementation Workflow
- Check `git status --short --branch` before starting; confirm the working
  tree is what you expect.
- Prefer one focused PR per feature. Split only when a single diff would be
  too big to review in one pass.
- Branch names: `feat/`, `fix/`, `refactor/`, `chore/`, or `docs/` prefix +
  short kebab-case description.
- Commit messages: Conventional Commits (`feat(backend): ...`,
  `chore(workflow): ...`).
- Run verification (backend: `gradlew.bat test` on Windows or `./gradlew test`
  on Linux/macOS; frontend: `pnpm --dir frontend test|lint|typecheck`)
  before declaring a task done.

## Authorship policy — no Claude attribution

This is a portfolio project. The user must be the sole visible author on
GitHub. Every commit, PR description, doc, and code comment you produce
**must not** contain any reference to Claude, "Claude Code", Anthropic,
the `Co-Authored-By: Claude` trailer, "🤖 Generated with..." footers,
or any other AI-attribution marker.

- `git commit -m "..."` — message body and trailers contain no
  Claude/Anthropic/AI mention. No `Co-Authored-By: Claude` trailer ever.
- `gh pr create --body "..."` — body contains no "🤖 Generated with
  Claude Code" or similar. Skip the marketing footer entirely.
- `docs/`, `dev-log`, ADRs, code comments — when explaining a decision,
  just state the decision; do not say "Claude suggested" or "AI
  generated". Past dev-log entries that already describe "Claude 단독
  구현자 체제" as a project-level *workflow* fact are fine — that is
  documenting a process choice, not crediting authorship.
- If you find an unmerged feature branch with a Claude trailer in any
  commit, `git commit --amend` or `git rebase -i` to strip it before
  pushing.
- Already-merged commits with a Claude trailer (legacy from before this
  rule) stay put — `git push --force` to main is too risky to do
  silently. Only rewrite history if the user explicitly asks.

Note: `noreply@anthropic.com` is not a GitHub-mapped account, so it
does not show up on the contribution graph or contributors page. The
visible leak is the literal trailer text inside the commit message.

## Merge policy — auto-merge safe PRs

The user does **not** want a "shall I merge?" confirmation on every PR.
When a PR you opened satisfies all of the following, run
`gh pr merge <N> --rebase --delete-branch` immediately, then
`git checkout main && git pull --ff-only origin main`:

1. `mergeable: MERGEABLE` (no conflicts)
2. CI / `gradlew test` / `pnpm test` passes (or the PR is docs-only)
3. Runtime impact is OFF by default — feature flag stays `mock`, prod
   behavior does not change, OR the prod-flip is part of a sequenced
   PR plan the user already agreed to
4. Mostly new files; regression risk on existing code is low

Still ask before: force-pushing main, flipping prod flags out of plan,
major dependency bumps, DB schema migrations, anything that could affect
other clones (server/phone) or running deployments.

## External-work-the-user-will-deliver

When the user says "내가 알려줄게" / "끝나면 알려줄게" about an external
task (PowerShell spike running locally, manual browser test, CI run on a
device you can't see, response from a teammate, etc.), stop referencing
that work. Do not poll, do not include it in option matrices ("while we
wait for X, we could..."), do not add reminders. Move to unrelated
work. The user will message you the result when they have it.

## Troubleshooting log policy
- Troubleshooting entries are portfolio-worthy only; do not add one just
  because a commit, PR, or dev-log entry exists.
- Add to root `TROUBLESHOOTING.md` in Korean for real bugs, failed/flaky
  verification, deployment or CI failures, external integration mismatches,
  security/privacy risks, surprising architecture tradeoffs, user-visible
  regressions, or fixes useful in a portfolio interview.
- Keep entries concise with symptom/root cause/fix/verification, and never
  include secrets or personal student data.

## CI / token usage
- Avoid long-running GitHub Actions polling such as `gh run watch` or
  `gh pr checks --watch`; use one-shot checks (`gh pr checks`,
  `gh run list --limit 5`, `gh run view --json ...`) and summarize.
- For CI failures, inspect only the failing step or last 50-100 lines unless
  the user explicitly asks for full logs.

## Remote server / phone workflow
- If the user is on a server/VPS/remote machine or says "서버로 접속했어",
  treat that clone as active.
- Use GitHub as the sync source; prefer `git pull --ff-only` / `git push` on
  a persistent clone, and do not suggest recloning unless no repo exists.
- Use Linux/macOS commands such as `./gradlew test` unless the shell is
  Windows.
- Keep private server details, SSH info, tokens, and `.env` values out of
  committed docs; use gitignored local notes for machine-specific context.

## Session close sync
- On "대화 종료" or stop-for-now messages, first check
  `git status --short --branch`.
- Do not auto-commit/push unfinished work on close-out alone; summarize
  changes and ask for explicit permission.

## Session handoff to a different AI

When the user signals they will continue this work in a different AI session
— trigger phrases include "토큰 끝났어", "다른 AI로 갈게", "다른
클로드로 이어갈게", "이제 codex로 할게", "claude2로 넘길게", or any
phrase indicating an agent swap — run this routine in order without further
prompting:

1. **Git state snapshot** — `git status --short --branch`, and for the
   current branch `git log --oneline origin/main..HEAD`. List
   uncommitted/unpushed work and any open PRs (`gh pr list --state open
   --author "@me"`). Ask the user with a single yes/no whether to commit
   and push the listed in-progress work; if no, leave it alone.

2. **Overwrite `docs/handoff/latest.md`** with the canonical handoff. The
   doc replaces any prior dated handoff (delete those in the same commit;
   git history preserves them). Required sections:
   - **TL;DR** — 3-5 bullets, what changed this session
   - **이번 세션 머지/푸시** — PR/commit table
   - **열린 PR** — branch + waiting-on
   - **외부 의존** — user-driven spikes, teammate replies, anything the
     next agent must not poll for ([[feedback-user-will-notify]])
   - **다음 세션 액션** — ordered, each item self-contained (no "see
     above"); name the exact files/branches/PR numbers
   - **사용자 컨텍스트** — preferences, deadlines, traps a fresh agent
     must know (auto-merge policy, no-Claude-trailer policy, etc.)
   - **보안 주의** — one-shot tokens, secrets, anything that must not be
     logged or echoed

3. **Next-AI opener block** — print a single fenced code block the user can
   paste verbatim into the next AI's first turn. Must tell the next agent
   to read `AGENTS.md` (or `CLAUDE.md` — same content), `docs/handoff/latest.md`,
   and `MEMORY.md` first, run `git status --short --branch`, then proceed
   with action #1 of the handoff doc. Include the current task ID, PR number,
   and branch name so the next agent has top-of-mind context without
   re-deriving it.

4. Commit and push the handoff doc itself (and any deleted prior handoff)
   on a `chore/handoff-<date>` branch via PR, or directly to `main` if no
   in-progress branch makes that cleaner. The handoff doc is metadata
   only — auto-merge applies.
- If the user explicitly asks to sync/push, commit and push only intended
  changes after relevant verification when feasible.

The user is the final decision maker. Do not silently make broad
architectural changes — propose, then wait.

## Current Phase
See `docs/tasks/` for active and pending task specs.
