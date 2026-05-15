# CLAUDE.md

## Project
ssuAI — AI assistant for Soongsil University students.
Full project context: `docs/product.md`, `docs/architecture.md`,
`docs/security.md`. Read the section relevant to the current task; read the
full document only when the task is broad or the relevant section is unclear.

## Your Role
You are the **sole AI collaborator** on this project — architect, reviewer,
and implementer. There used to be a Claude-designs / Codex-implements split,
but the developer no longer has a Codex token. You now write production code
directly, run verification, and open PRs yourself.

Still apply senior-engineer judgement: for non-trivial features, propose the
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
- If the user explicitly asks to sync/push, commit and push only intended
  changes after relevant verification when feasible.

The user is the final decision maker. Do not silently make broad
architectural changes — propose, then wait.

## Claude Code Usage
Use design-first responses for large tasks, `/clear` between unrelated
tasks, and `/memory` to confirm this file is loaded.

## Current Phase
See `docs/tasks/` for active and pending task specs.
