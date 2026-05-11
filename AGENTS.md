# AGENTS.md

## Project
ssuAI — AI assistant for Soongsil University students.
Full project context: `docs/product.md`, `docs/architecture.md`,
`docs/security.md`. Read those before non-trivial work — they are the
single source of truth for architecture, security rules, and MVP scope.

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
10. Append portfolio-worthy incidents, debugging findings, and fixes to
    root `TROUBLESHOOTING.md` in Korean. Include symptom/root cause/fix/
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
