# AGENTS.md

## Project
ssuAI — AI assistant for Soongsil University students.
Full project context: `docs/product.md`, `docs/architecture.md`,
`docs/security.md`. For non-trivial work, read the relevant sections named by
the hand-off; read the full documents when the task is broad, cross-cutting, or
the hand-off does not narrow the scope. These files are the single source of
truth for architecture, security rules, and MVP scope.

## Roles

This project uses two AI assistants with separate responsibilities:

**Claude Code** — architecture design, feature planning, security review,
code review, breaking large tasks into smaller ones.

**Codex CLI (you)** — implementing scoped tasks, editing files, writing
tests, fixing compile errors, applying review feedback.

## Hand-off Convention (Claude → Codex)

Per-task instructions from Claude live in **one rolling file**:

- Path: `.codex/current-task.md` (project root, gitignored — transient).
- Claude writes the next task into this file. Previous content is
  overwritten.
- The developer's message to Codex is always the same one-liner:

  > Read `.codex/current-task.md` and execute it. Reply using the Required
  > Output Format below.

- For full feature specs (e.g., a new domain slice), Claude writes the spec
  to `docs/tasks/<NN>-<name>.md` (committed) and `.codex/current-task.md`
  becomes a short pointer to that file:

  ```
  Implement docs/tasks/02-meal-mock-api.md.
  Reply using the Required Output Format in AGENTS.md.
  ```

- For small patches, Claude writes the full instructions inline in
  `.codex/current-task.md` — no separate `docs/tasks/` entry needed.

You MUST read `.codex/current-task.md` at the start of every session
triggered by the one-liner above, before touching any code.

## Standard Codex task flow

Unless the task says otherwise or explicitly stops before git operations, a
ready Codex task follows this default chain:

`implement → verify → commit → switch -c <branch> → push -u → gh pr create → gh pr checks <PR> 1회`

Rules for the standard flow:

- Branch names use a clear prefix: `fix/`, `refactor/`, `chore/`, or `feat/`
  plus a short kebab-case description.
- Commit messages use Conventional Commits, such as `fix(backend): ...` or
  `chore(workflow): ...`.
- PR titles should mirror the commit message unless the task provides a more
  reviewer-friendly title.
- The PR body should copy the task's Goal, reason, notable decisions,
  troubleshooting decision, verification results, and linked task into the PR
  template.
- CI 확인은 Working Rule 11을 따른다. `gh run watch`나
  `gh pr checks --watch` 같은 polling은 금지하고, `gh pr checks <PR>`를 한
  번 조회한 뒤 결과만 요약한다.
- If verification fails, secrets/local-only files appear, or the intended
  change set is ambiguous, stop before commit/push and report the blocker.

## Efficient Hand-off Contract

The workflow remains **Claude designs → Codex implements → Claude reviews**.
Efficiency comes from making each hand-off precise enough that Codex can avoid
open-ended rediscovery and Claude can review against the same checklist.
The standard implementation, git, PR, and one-shot CI flow above applies by
default; do not repeat it inside every task unless the task deviates from it.

Claude should write `.codex/current-task.md` using this minimal Codex task spec template whenever possible:

```markdown
# Codex task: <short title>

State: ready
Spec: docs/tasks/<NN>-<name>.md or inline

Goal:
- <one outcome>

Scope:
- In: <allowed changes>
- Out: <explicit non-goals>

Expected files:
- <likely files or directories>

Acceptance criteria:
- <observable behavior>

Verification:
- <exact commands, from exact directories>

Stop and flag:
- <missing info, forbidden real endpoint, secret risk, broad scope trigger>
```

Rules for keeping the loop fast:

- Prefer one task large enough to include the implementation and its focused
  tests, but small enough to review in one pass.
- Reference only the product, architecture, or security sections that matter
  for the task. Do not paste long source-of-truth documents into
  `.codex/current-task.md`.
- Add `Context to read` only when exact sections are needed and not obvious
  from the Expected files.
- Put exact verification commands in the hand-off. Avoid long-running CI
  polling; use the one-shot CI checks in Working Rule 11.
- Include expected files so Codex can notice accidental scope creep. Codex may
  touch a different file if the repo requires it, but must call that out in the
  final summary.
- `Portfolio narrative`, `TROUBLESHOOTING.md 판단 가이드`,
  `Claude review checklist`, and `Next task candidates` are optional headings.
  If omitted, the Standard Codex task flow and Working Rule 10 still apply.
- If `State:` is `blocked` or `no active task`, Codex must not implement. It
  should report the blocker and the smallest next hand-off needed from Claude.
- After Claude review passes, Claude should overwrite `.codex/current-task.md`
  with the next `State: ready` task when a clear scoped follow-up exists. If no
  follow-up is clear, Claude should write `State: no active task` with 1-3
  concrete candidates. `Next task candidates` are planning hints only; Codex
  must implement only when `State: ready`.
- After loading this file, Codex should check `git status`, read
  `.codex/current-task.md`, and then read the listed context. Broader search is
  reserved for unclear or failing implementation details.

## Codex Result Hand-off

After any task that creates, modifies, or deletes files, Codex MUST write
`.codex/last-result.md` before replying. This file is gitignored, transient,
and overwritten on each task. It does not replace the final chat response; it
removes the need for the developer to copy/paste Codex's summary into Claude.

Use this shape:

```markdown
# Codex result: <task title>

Completed at: YYYY-MM-DD HH:mm KST
Source task: .codex/current-task.md
Task state when executed: ready / ad-hoc

Summary:
- <same substance as 작업 요약>

Verification:
- <command and result, or why not run>

Troubleshooting decision:
- Added to TROUBLESHOOTING.md: yes/no
- Reason: <portfolio-worthy trigger or why not applicable>

Files changed:
Created:
- ...

Modified:
- ...

Deleted:
- ...

Notes for Claude review:
- <scope deviations, residual risks, or "None">
```

Claude review prompt should be:

> Read `.codex/current-task.md` and `.codex/last-result.md`, then review the
> implementation against the task's acceptance criteria and review checklist.

## Remote Server Mode

When the user says they are working on the server, VPS, remote machine, or
"서버로 접속했어", treat that server as the active development machine.

Rules:

- Use GitHub as the sync source between the home PC and the server. The server
  should keep a persistent clone of the repo; do not assume the user should
  reclone it every phone session. Prefer `git pull --ff-only` / `git push`
  around work instead.
- Check `git status --short` and the current branch before editing. The server
  may contain changes from another device or another AI session.
- Prefer Linux/macOS commands on the server, such as `./gradlew test`, unless
  the actual shell indicates Windows. Do not assume a local desktop GUI.
- If VS Code Tunnel is already running on the server, use the VS Code terminal
  normally; long-lived CLI sessions should be kept in `tmux` or an equivalent
  terminal multiplexer when available.
- Keep the `.codex/current-task.md` and `.codex/last-result.md` workflow
  unchanged when those files exist on the server. Because `.codex/` is
  gitignored, do not expect these files to sync automatically from the home PC.
- Do not put server IPs, SSH usernames, tokens, `.env` values, or other private
  machine details in committed docs. Store machine-specific notes only in
  gitignored local files such as `.codex/environment.md`.

## Session Close Sync

When the user says they are ending the conversation, stopping for now, or
"대화 종료", help preserve continuity for the next machine without blindly
pushing unfinished work.

Rules:

- Always check `git status --short --branch` before giving the close-out
  response.
- If the worktree is clean, say which branch is clean and whether anything
  still needs to be pushed when that is visible from Git.
- If there are uncommitted changes and the user only says "대화 종료" or an
  equivalent close-out phrase, summarize the changes and ask whether to
  commit/push. Do not auto-commit on close-out alone.
- If the user explicitly says to sync, push, says "대화 종료 sync", says
  "대화 종료하고 sync까지", or otherwise clearly says they will continue from
  another server/PC, treat commit/push as requested. Commit/push only the
  intended project changes. Use a concise commit message, run the task's
  relevant verification first when feasible, and never include secrets or
  local-only files. If verification fails, secrets/local-only files are found,
  or the intended changes are ambiguous, stop and report the blocker instead
  of pushing.
- If `.codex/current-task.md` or `.codex/last-result.md` matter for continuing
  on another machine, remind the user that `.codex/` is gitignored and must be
  recreated on the target machine or converted into a committed `docs/tasks/`
  file.

## Required Output Format

Every response that creates, modifies, or deletes files MUST end with these
two sections, in this exact order:

### 1. 작업 요약 (Work Summary)

**반드시 한국어로** 간결하게 작성한다. 무엇을 했는지·왜 했는지 1~3 문장.
비자명한 판단(non-obvious decision)이 있으면 짧게 언급. 코드·식별자·파일 경로·
명령어·로그 메시지 같은 영어 토큰은 그대로 영어로 유지한다. diff 전체를
붙여넣지 않는다.

예: "`GlobalExceptionHandler` 에 `NoResourceFoundException` 핸들러 추가해서
404 envelope 으로 응답하도록 함. 기존 catch-all `Exception` 은 그대로 둠.
`gradlew.bat test` 4건 통과."

### 2. File List

List every file touched, grouped by action. Use absolute or repo-relative
paths. Omit a group if it is empty.

```
Created:
- backend/src/main/java/com/ssuai/...

Modified:
- backend/build.gradle
- backend/src/main/resources/application.yml

Deleted:
- backend/src/main/java/com/ssuai/global/web/HelloController.java
```

Rules:
- List EVERY file touched, including config files, tests, and resources.
- Do not abbreviate as "and 3 other test files" — list them all.
- If no files were touched (question-only response), write
  `No files changed.` instead of the list.

This format is mandatory even for one-file edits.

## Working Rules

1. Read this AGENTS.md before starting. If the developer's message
   references `.codex/current-task.md`, read that file too.
2. Architecture rules in `docs/architecture.md` and security rules in
   `docs/security.md` are **binding**. Do not deviate without flagging.
3. Work on one small feature at a time; prefer small diffs.
4. Do not modify unrelated files.
5. Do not add new production dependencies unless there is a clear reason.
6. Do not change documentation files (`docs/`, `CLAUDE.md`, `AGENTS.md`)
   unless explicitly asked.
7. Mock data must be clearly labeled as mock (class name, comment, or both).
8. Never commit secrets, passwords, cookies, tokens, or session values.
9. Tests must not call real u-SAINT, real LMS, or any authenticated school
   endpoint. Use fixtures or mocks.
10. Troubleshooting logging is portfolio-worthy only. Do not add a
    `TROUBLESHOOTING.md` entry merely because a commit, PR, or dev-log entry
    exists. Always make an explicit troubleshooting decision in
    `.codex/last-result.md`. Append to root `TROUBLESHOOTING.md` in Korean
    when the task reveals a real bug, failed/flaky verification, deployment or
    CI failure, external integration mismatch, security/privacy risk,
    surprising architecture tradeoff, user-visible regression, or a fix that
    would be useful in a portfolio interview. Include symptom/root cause/fix/
    verification, and never include secrets or personal student data.
11. CI 확인은 토큰을 아끼는 방식으로 한다. `gh run watch`,
    `gh pr checks --watch` 같은 장시간 polling 명령을 쓰지 않는다. 대신
    `gh pr checks <PR>`, `gh run list --limit 5`, `gh run view <RUN_ID> --json ...`
    같은 one-shot 조회를 사용한다. 실패 로그가 필요하면 전체 로그를 읽지
    말고 실패 step의 마지막 50~100줄만 확인해서 요약한다.

## Verification Commands

From the `backend/` directory:

- Windows: `gradlew.bat test`, `gradlew.bat bootRun`
- macOS/Linux/WSL: `./gradlew test`, `./gradlew bootRun`

## Current Phase

See `docs/tasks/` for active and pending task specs.
