# Task 09 — Secret scanning (gitleaks CI + lefthook pre-commit)

> Hand-off spec for Codex CLI. Reply using the **Required Output Format**
> in `AGENTS.md`. Single small PR.

## Goal

Catch accidentally committed secrets **before** they reach `main`, and
ideally before they reach the local commit. Two layers:

1. **CI** — a `gitleaks` GitHub Actions job runs on every PR and push.
   Fails the build if any high-confidence secret pattern is matched in
   the diff or full repo.
2. **Local (optional but recommended)** — `lefthook` pre-commit hook
   runs the same gitleaks scan against the staged diff. Cross-platform
   (Windows + macOS + Linux) without Python.

## Why this slice

- `docs/security.md` §1 lists "accidental commit of secrets" as the
  most likely real incident on a student project. The countermeasure
  named in §3 is "a pre-commit hook (`gitleaks`, `detect-secrets`) or a
  GitHub Actions job". Neither exists yet.
- A future `SSUAI_ANTHROPIC_API_KEY` (Task 07) is exactly the kind of
  string a scanner catches — adding the scanner before the key arrives
  closes the window.
- Lefthook is the modern cross-platform alternative to the Python-based
  `pre-commit` framework. Single Go binary, project-local config,
  works on Windows without a Python install.

## Scope — in

1. `.gitleaks.toml` — start from the gitleaks default config (no allowlist
   yet), with one project-specific allowlist for known false positives:
   - `docs/security.md` itself (mentions of "JWT", "API key" as terms),
   - `deploy/k8s/secret.example.yaml` (placeholder strings only).
2. `.github/workflows/security.yml` — new workflow named "Security",
   one job:
   - `gitleaks/gitleaks-action@v2` (or pin to a specific SHA — pin is
     safer for CI integrity).
   - Triggers: `pull_request` and `push` to `main`.
   - Permissions: `contents: read`, no write.
   - On detection: workflow fails, summary printed in the PR.
3. `lefthook.yml` (project root) — pre-commit hook that runs gitleaks
   against the staged diff:
   ```yaml
   pre-commit:
     parallel: true
     commands:
       gitleaks:
         run: gitleaks protect --staged --redact --config .gitleaks.toml
   ```
4. `README.md` — short "Local pre-commit hook (optional)" section
   pointing to lefthook install + `lefthook install`.
5. `docs/security.md` §3 — remove the "Pre-commit guardrails" TODO
   wording; replace with "We use `gitleaks` in CI (mandatory) and
   optionally `lefthook` locally."

## Scope — out

- Custom rule packs beyond gitleaks defaults — premature; tune the
  allowlist when first false positive lands.
- `detect-secrets` baseline file workflow — gitleaks's regex-based
  approach is enough at this scale.
- TruffleHog / SAST tools — Dependabot is Task 11, SAST is post-MVP.
- Forcing pre-commit on contributors — the project is one developer;
  CI is the hard gate, lefthook is convenience.

## Files to create / modify

```
.gitleaks.toml                              # NEW
.github/workflows/security.yml              # NEW
lefthook.yml                                # NEW
README.md                                   # MODIFY: pre-commit hook section
docs/security.md                            # MODIFY: §3 pre-commit guardrails wording
docs/dev-log.md                             # MODIFY: one line
```

## Implementation notes

### `.gitleaks.toml`

Minimal — extend the defaults, add only project-specific allowlists:

```toml
[extend]
# inherit gitleaks default rule pack
useDefault = true

[[allowlists]]
description = "ssuAI: documentation that legitimately mentions secret types"
paths = [
    '''docs/security\.md''',
    '''docs/architecture\.md''',
]

[[allowlists]]
description = "ssuAI: deploy templates with placeholder values"
paths = [
    '''deploy/k8s/secret\.example\.yaml''',
]
```

Verify against the current gitleaks 8.x config schema (the `[extend]`
block + `useDefault` is the canonical way to inherit defaults).

### CI workflow

`.github/workflows/security.yml`:

```yaml
name: Security

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  gitleaks:
    name: gitleaks scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # full history for accurate scan
      - uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITLEAKS_ENABLE_UPLOAD_ARTIFACT: "false"
```

(Pin the action to a specific SHA in a follow-up if the security
posture warrants it. For now `v2` is fine — that's the published
upgrade-track tag.)

### Lefthook config

`lefthook.yml`:

```yaml
pre-commit:
  parallel: true
  commands:
    gitleaks:
      run: gitleaks protect --staged --redact --config .gitleaks.toml
```

Install instructions in `README.md` (cross-platform):

```bash
# macOS
brew install lefthook gitleaks
# Windows
scoop install lefthook gitleaks
# Linux
go install github.com/evilmartians/lefthook@latest
go install github.com/gitleaks/gitleaks/v8@latest

# in the repo
lefthook install
```

Lefthook config is committed; the per-developer `lefthook install`
step writes the actual `.git/hooks/pre-commit` shim. If a developer
hasn't installed lefthook, **CI is still the gate** — they can't
accidentally bypass it on `main`.

### `docs/security.md` §3 update

Replace the existing "Pre-commit guardrails" paragraph with:

```
### Pre-commit guardrails

gitleaks runs in CI (`.github/workflows/security.yml`) on every PR and
push to `main`. The build fails on any high-confidence secret match.
Configuration lives in `.gitleaks.toml` (extends the gitleaks default
rule pack with one allowlist for documentation files).

For local-time defense before CI, install lefthook (`lefthook
install`); the pre-commit hook runs `gitleaks protect --staged` so a
secret never reaches a commit. CI remains the hard gate; the local
hook is convenience.
```

## Verification before reporting done

1. Open a throwaway local commit that includes a fake key (e.g.,
   `sk-ant-api03-AAAA...`) — gitleaks **must** reject it locally
   (lefthook installed) and the CI job **must** fail on a PR
   containing the same. Roll back the test commit before opening any
   real PR.
2. The CI workflow runs on the PR for this task itself and is green
   (no secrets in the change).
3. `./gradlew test` and `pnpm --dir frontend test` unaffected.
4. `lefthook install && git commit -m "x" --allow-empty --dry-run` (or
   any clean commit) succeeds.

## Security notes

- The gitleaks action is third-party — pin the major tag (`v2`) or a
  specific SHA. Major-tag pinning is the project default elsewhere
  (`actions/checkout@v4`), so `v2` is consistent.
- Don't add the project's own past commits to the allowlist as a
  shortcut. If gitleaks flags a real (now-rotated) secret, rotate
  again to be safe and confirm the secret is dead before allowlisting.
- Lefthook config is committed but the actual hook install is per
  developer — no one is forced to install it; CI is the gate.

## PR description draft

Title: `chore(security): add gitleaks CI + optional lefthook pre-commit`

```markdown
## What
- `.github/workflows/security.yml` — gitleaks scan on every PR and
  push to main. Hard fail on any high-confidence match.
- `lefthook.yml` — optional local pre-commit hook running the same
  scan against the staged diff. Cross-platform (no Python).
- `.gitleaks.toml` — extends gitleaks defaults; one allowlist for
  `docs/security.md`, `docs/architecture.md`, `deploy/k8s/secret.example.yaml`.

## Why
`docs/security.md` §1 lists accidental secret commits as the most
likely real incident on this project. The forthcoming
`SSUAI_ANTHROPIC_API_KEY` (Task 07) makes the scanner timely. CI is
mandatory; lefthook is convenience.

## Test plan
- [ ] PR for this task itself passes the new CI job.
- [ ] Local fake-secret commit attempt blocked by lefthook (confirmed
      manually, not committed).
- [ ] Existing CI jobs unaffected.
```
