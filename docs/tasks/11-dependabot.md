# Task 11 — Dependabot configuration

> Hand-off spec for Codex CLI. Reply using the **Required Output Format**
> in `AGENTS.md`. Tiny single-PR task.

## Goal

Enable Dependabot for automated dependency-update PRs across the three
ecosystems this project uses: Gradle (backend), npm (frontend),
GitHub Actions (workflows). Group patch + minor updates so the PR
volume stays human-reviewable.

## Why this slice

- `docs/security.md` §10 explicitly says "Enable Dependabot (or
  Renovate) for both `backend/` and `frontend/` once CI exists." CI
  exists (since Task 06).
- A single Spring AI minor bump or a transitive Jsoup CVE will be
  caught on the day GitHub publishes the advisory, not three months
  later.
- Renovate is more powerful but heavier to configure. Dependabot is
  GitHub-native, zero-install, and adequate for a one-developer
  project.

## Scope — in

1. `.github/dependabot.yml` covering:
   - Gradle in `/backend`
   - npm in `/frontend`
   - GitHub Actions in `/` (the workflow files themselves)
2. Schedule: weekly (Monday morning Asia/Seoul) — daily is too noisy
   for one reviewer.
3. **Group** patch + minor updates per ecosystem so the developer
   gets one PR per week per ecosystem, not 12.
4. **Allow security updates immediately** (out of the weekly cadence)
   — that's Dependabot's default for security PRs but make the intent
   explicit in a comment.
5. PR limits: 5 open PRs per ecosystem (Dependabot's default is 5;
   pin it).
6. README — one-line note that Dependabot is on; Codex doesn't need
   to do anything else here.

## Scope — out

- Auto-merge rules — explicit human review for every dep bump until
  the test suite is wider. Revisit after Task 10 lands real component
  tests + Task 09 lands gitleaks.
- Renovate migration — overkill at this scale.
- `npm audit` / `gradle dependencyCheck` cron — separate task if a
  reviewer asks for SCA depth.

## Files to create / modify

```
.github/dependabot.yml          # NEW
README.md                       # MODIFY: 1-line "Dependency updates" note
docs/dev-log.md                 # MODIFY: 1 line
```

## `.github/dependabot.yml` shape

```yaml
version: 2

updates:
  - package-ecosystem: gradle
    directory: /backend
    schedule:
      interval: weekly
      day: monday
      time: "09:00"
      timezone: Asia/Seoul
    open-pull-requests-limit: 5
    groups:
      backend-minor-and-patch:
        update-types: [minor, patch]
    labels: [dependencies, backend]
    commit-message:
      prefix: chore(deps)
      include: scope

  - package-ecosystem: npm
    directory: /frontend
    schedule:
      interval: weekly
      day: monday
      time: "09:00"
      timezone: Asia/Seoul
    open-pull-requests-limit: 5
    groups:
      frontend-minor-and-patch:
        update-types: [minor, patch]
    labels: [dependencies, frontend]
    commit-message:
      prefix: chore(deps)
      include: scope

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
      day: monday
      time: "09:00"
      timezone: Asia/Seoul
    open-pull-requests-limit: 5
    groups:
      actions-minor-and-patch:
        update-types: [minor, patch]
    labels: [dependencies, ci]
    commit-message:
      prefix: chore(deps)
      include: scope

# Major-version bumps continue to file individual PRs (one per dep)
# so they get individual scrutiny — that's Dependabot's behaviour
# whenever a `groups` block doesn't include `[major]`.
#
# Security advisories file out-of-band PRs immediately, regardless of
# the weekly schedule. That's the default and is preserved.
```

## Verification before reporting done

1. After merge, GitHub Insights → Dependency graph → Dependabot must
   show three configurations: gradle, npm, github-actions.
2. Within 24 hours, a "Dependabot opened a pull request" notification
   for at least one ecosystem (or a "no updates available this week"
   silence — both are valid).
3. Existing CI runs unchanged.

## Security notes

- Dependabot's GitHub-Actions ecosystem is what flags pinned-action
  versions falling out of date — important for the gitleaks action
  pinned in Task 09.
- No new permissions needed; Dependabot uses its own GitHub-issued
  identity.

## PR description draft

Title: `chore(deps): enable Dependabot for gradle, npm, github-actions`

```markdown
## What
`.github/dependabot.yml` covering backend (gradle), frontend (npm),
and workflow (github-actions). Weekly on Monday Asia/Seoul. Patch +
minor grouped per ecosystem; major versions file separate PRs.

## Why
`docs/security.md` §10 explicitly asks for this once CI exists. CI
exists (Task 06). Spring AI minors and Jsoup CVEs will land as PRs
on the day they ship.

## Test plan
- [ ] PR CI green.
- [ ] After merge: Dependency graph shows three configs.
- [ ] Within 24h: at least one Dependabot PR opens (or quiet week —
      either is fine).
```
