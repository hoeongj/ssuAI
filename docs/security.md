# ssuAI Security

## Goals of this document

Define the rules that protect ssuAI's users — Soongsil students — and the
sensitive academic data the system will eventually touch. This is the
**single source of truth** for:

- What data is sensitive and how to handle it.
- What may and may not appear in logs.
- How school-system credentials are stored and used.
- How "action" features (anything that changes school state) are gated.

`docs/architecture.md` references this document for its logging and
credential rules. If those two documents ever conflict, this one wins.

## Non-goals

- Penetration testing methodology.
- Compliance certification (KISA / ISMS / etc.) — out of scope for a
  student portfolio project.
- Production incident-response runbooks — only the lightweight version
  needed for this project's scale.

---

## 1. Threat model (lightweight)

The realistic threats this project must take seriously:

| Threat                                              | Why it matters                                                                 |
|-----------------------------------------------------|--------------------------------------------------------------------------------|
| Accidental commit of secrets / cookies / fixtures   | Most likely real incident on a student project. One leaked `.env` ends it.     |
| Credential leak via logs                            | A single `log.info("login attempt: " + password)` is catastrophic.             |
| Session/credential theft via XSS or token leak      | Becomes real once user accounts and JWTs/sessions exist.                       |
| Unauthorized "action" execution (e.g., wrong seat reserved) | Action features can do real-world harm. Must be gated by explicit confirmation. |
| Dependency vulnerability (e.g., Log4Shell-class)    | Java/Node ecosystems, regular CVEs.                                            |
| Abuse of crawling against the school's site         | Excess load could get us blocked or worse.                                     |

Threats that are **not** in scope for the MVP:

- Sophisticated targeted attacks against the running service (no real users
  yet, and the MVP exposes only public data).
- Insider threats inside Soongsil's IT team.
- Supply-chain attacks on the JDK/Node runtime itself.

---

## 2. Data classification

Every piece of data the project touches falls into one of four classes.
The class determines storage, transport, logging, and retention rules.

| Class       | Examples                                                                                  | Storage              | Logging                      | Notes                                       |
|-------------|-------------------------------------------------------------------------------------------|----------------------|------------------------------|---------------------------------------------|
| **Public**  | Today's cafeteria menu, library book catalog, library seat counts                         | Cache / DB freely    | OK to log content            | MVP is mostly this class.                   |
| **Personal**| ssuAI account email, display name, ssuAI user ID                                          | DB (plain)           | Log only the user ID, never email/name | Standard PII handling.                      |
| **Sensitive**| Course schedule, grades, graduation progress, LMS assignments, LMS submissions          | DB, owner-only access | **Never log content**, only "fetched X items" counts | Treat with the same care as health records. |
| **Secret**  | u-SAINT / LMS passwords, long-lived session cookies, API keys, signing keys, encryption keys | Env vars, KMS, or encrypted DB column | **Never log, ever**          | A leak means immediate rotation.            |

Rule of thumb: when in doubt, treat data as one class higher than you
think it is.

---

## 3. Credential & secret management

### Things that must NEVER live in the repository

- `.env` files with real values.
- Cookies, session IDs, JWTs, API keys.
- HTML / JSON fixtures captured from a logged-in school session that still
  contain personal data — strip them first.
- Database dumps.
- Screenshots that include a student ID, email, or grade.

`.gitignore` must explicitly include: `.env`, `.env.*`, `*.pem`, `*.key`,
`secrets/`, `**/application-prod.yml` (if it ever holds anything other than
`${ENV_VAR}` references), `*.dump`, `*.sql.gz`.

### Where secrets DO live

- **Local dev:** an untracked `.env` file plus `application-dev.yml` that
  references env vars. Use a `.env.example` (committed) listing the
  variable names with empty values, so a new clone knows what to set.
- **CI:** repository secrets in GitHub Actions. Never echoed to logs.
- **Production:** real environment variables on the host / container
  orchestrator. Eventually a proper secret manager (AWS Secrets Manager,
  GCP Secret Manager, or HashiCorp Vault) — pick one when production is
  real, not before.

### Required env vars (MVP and forward)

| Env var                                | Class    | When introduced                |
|----------------------------------------|----------|--------------------------------|
| `SSUAI_DB_URL` / `SSUAI_DB_USER` / `SSUAI_DB_PASSWORD` | Secret   | Day 1 (Postgres connection)    |
| `SSUAI_REDIS_URL`                      | Secret   | Day 1 (cache)                  |
| `SSUAI_OPENAI_API_KEY` (or equivalent) | Secret   | When chatbot lands             |
| `SSUAI_JWT_SIGNING_KEY`                | Secret   | When ssuAI auth lands          |
| `SSUAI_CREDENTIAL_ENCRYPTION_KEY`      | Secret   | When LMS / u-SAINT login lands |

### Pre-commit guardrails

gitleaks runs in CI (`.github/workflows/security.yml`) on every PR and
push to `main`. The build fails on any high-confidence secret match.
Configuration lives in `.gitleaks.toml` (extends the gitleaks default
rule pack with one allowlist for documentation files).

For local-time defense before CI, install lefthook (`lefthook install`);
the pre-commit hook runs `gitleaks protect --staged` so a secret never
reaches a commit. CI remains the hard gate; the local hook is convenience.

---

## 4. Logging rules (source of truth)

`docs/architecture.md` §9 defers to this section. These rules apply to
every layer (Controller, Service, Connector, MCP tool).

### Always log

- HTTP method, route, status, latency.
- `traceId` (for cross-system correlation).
- Connector name + outcome: `cache hit | cache miss | connector ok | connector failed`.
- Internal user ID (the ssuAI account's surrogate ID) — **never** the
  student number / email / name.
- Counts and shapes: "fetched 12 assignments" is fine; the assignment
  contents are not.
- Exception type and stack trace — but scrubbed of any of the categories
  below.

### Never log, ever

- Passwords (any system, any context, including failed-login attempts).
- u-SAINT / LMS / library cookies, session IDs, CSRF tokens.
- API keys, JWTs, signing keys, encryption keys.
- Student number, real name, phone number, resident registration number.
- Course names paired with grades, GPA, graduation status, transcript content.
- LMS assignment text (questions, student answers, submitted files).
- Full request bodies for endpoints that handle credentials or sensitive
  data — use a per-endpoint allowlist of safe fields instead.
- Full HTML responses from authenticated school pages (they contain all of
  the above).

### Practical mechanisms

- Use **parameterized logging** (`log.info("user {} fetched {} items", userId, count)`),
  not string concatenation, so a stray object is harder to slip in.
- Mark sensitive DTO fields with a `@SensitiveField` annotation (custom)
  and wire a Logback / Jackson masker that turns them into `***` in any
  serialized output.
- For authenticated school responses, log only a checksum + size, not the
  body.
- In test fixtures, **redact before committing**. The fixture file
  `library-search-success.html` should not contain a real student's name
  if it was captured from a real session.

---

## 5. School credentials (LMS / u-SAINT)

These rules apply when the LMS / u-SAINT integration is built — they are
not MVP work, but the rules need to be settled before any code is written.

### Storage

- **Never** store a school password in plain text. If a password must be
  stored at all, encrypt it with AES-GCM using
  `SSUAI_CREDENTIAL_ENCRYPTION_KEY`. The key lives in env vars (or a
  secret manager), never in the database.
- Prefer to store **session cookies** over passwords whenever the school
  system allows long-lived sessions — a cookie that can be invalidated
  server-side is a smaller blast radius than a reusable password.
- Even cookies are encrypted at rest with the same key.
- Stored credentials are scoped to the owning ssuAI user. The encryption
  key may be the same per environment, but the **per-record IV** must be
  unique.

### Retrieval

- Decrypt only inside the Connector that needs the credential, and only
  for the duration of the outbound call.
- Never decrypt and log the result, even at DEBUG.
- Provide a "delete my school credentials" endpoint from day one of the
  feature. Users must be able to revoke at any time.

### Rotation & loss

- Document a procedure: rotate `SSUAI_CREDENTIAL_ENCRYPTION_KEY` →
  re-encrypt all stored credentials → invalidate any cached sessions.
- Treat any suspected key leak as an incident: rotate immediately,
  invalidate all stored credentials, and require users to re-link their
  school accounts.

### Don't

- Don't proxy a user's school login through ssuAI for "convenience" —
  every login means handling a password. Use the school's official auth
  flow (OAuth-like, SSO, IP-binding, etc.) when one exists, even if it's
  more work.
- Don't share a single school account across ssuAI users for "demo
  purposes." Demos should run against the mock connector.

---

## 6. User consent & action confirmation

The MVP exposes **read-only** endpoints and tools, so this section is
forward-looking — but the rule is set now so it isn't relitigated later.

### Consent for read access

- The first time ssuAI accesses a school system on a user's behalf
  (LMS sync, u-SAINT sync), the user must see an explicit consent screen
  that names: *what* data will be fetched, *why*, and *how to revoke*.
- Consent is recorded (timestamp, scope) and is revocable from a settings
  page.

### Confirmation for actions

Any tool that changes school state (`reserve_library_seat`, anything LMS
write, anything u-SAINT write) must:

1. Show a **dry-run** first: "I will reserve seat 217 in 도서관 3F for
   14:00–17:00. Confirm?"
2. Require an explicit user "confirm" input — not auto-confirm, not
   default-yes.
3. Write an **audit log** row before executing (request payload, user ID,
   timestamp) and another after (outcome, error if any).
4. Surface the final result clearly: success, partial success, or failure.
   Do not silently swallow errors.

### Audit log

When action features land, add an `action_audit` table:

```
id (uuid)
user_id (fk)
tool_name (text)
input_payload (jsonb, sensitive fields masked)
dry_run (bool)
outcome (text: SUCCESS | FAILURE | TIMEOUT | CANCELLED)
error_message (text, nullable)
requested_at (timestamptz)
completed_at (timestamptz)
```

Audit rows are append-only. Users can read their own rows; nobody can
delete them through the API.

---

## 7. Authentication & sessions (forward-looking)

Out of MVP scope, but the design constraints:

- **Don't roll custom crypto.** Use Spring Security's password encoder
  (`BCryptPasswordEncoder` or `Argon2PasswordEncoder`) for the ssuAI
  account password — never store anything but the hash.
- **Sessions vs JWTs:** for a single backend serving web + (later)
  mobile, prefer **opaque session tokens stored in Redis** with a short
  TTL plus refresh, not self-contained JWTs. Easier to revoke.
- Tokens go in `HttpOnly`, `Secure`, `SameSite=Lax` cookies for the web
  client. Mobile uses an `Authorization: Bearer` header against the same
  endpoints.
- Login rate-limiting from day one (Redis counter keyed by IP + username).
- Password reset uses a single-use, short-lived token sent by email; the
  token is hashed in the DB.

---

## 8. Transport & network

- HTTPS only, in every environment that's reachable from the internet.
  Local dev may run plain HTTP.
- HSTS header on production responses.
- CORS allowlist is explicit (the deployed frontend origin), never `*`.
- Outbound connections to school systems use HTTPS where the school site
  supports it. If a school endpoint is HTTP-only, document it and treat
  the response as untrusted (parse defensively).

---

## 9. Input validation & output handling

- All request DTOs use Jakarta Bean Validation (`@NotBlank`, `@Size`,
  `@Pattern`, etc.). The Controller layer enforces this; downstream
  layers may assume validated inputs.
- Database access goes through JPA / parameterized queries only — never
  string-concatenated SQL.
- Frontend renders user-supplied content with React's default escaping;
  any `dangerouslySetInnerHTML` use needs an explicit security review
  comment in the PR.
- Outbound HTML parsing (Jsoup, Playwright) treats school HTML as
  untrusted: sanitize before storing, never re-render raw HTML to the
  user.

---

## 10. Dependencies & supply chain

- Pin Gradle and npm/pnpm dependency versions; commit the lockfile
  (`gradle.lockfile`, `pnpm-lock.yaml`).
- Enable Dependabot (or Renovate) for both `backend/` and `frontend/`
  once CI exists.
- Run `./gradlew dependencyCheckAnalyze` (OWASP Dependency-Check) or an
  equivalent SCA tool monthly, and on any release branch.
- Don't add a dependency for one helper function — the supply-chain risk
  isn't worth it.

---

## 11. Crawling etiquette

ssuAI crawls Soongsil's own systems. Treat them politely:

- Identify the client honestly: a `User-Agent` like
  `ssuAI/0.1 (+contact email)` is fine.
- Respect any `robots.txt` for public pages.
- Cache aggressively (see `architecture.md` §7) — every cache hit is one
  fewer request to the school.
- Rate-limit outbound calls per Connector (e.g., max 1 req/sec to the
  cafeteria site). Implement as a simple token-bucket in the Connector.
- On `429` or `503`, back off exponentially. Do not hammer.
- Do not bypass anti-automation measures designed to protect users
  (CAPTCHAs, IP-binding). If a protection exists, it's a signal to stop
  and rethink, not to evade.

---

## 12. Per-feature security checklist

Before any new feature is merged, the author (and reviewer) confirm:

- [ ] Does this feature touch data above class **Public**? If yes, list
      what.
- [ ] Are any new secrets introduced? If yes, are they env-var-driven and
      in `.env.example`?
- [ ] Is anything new being logged? Cross-checked against §4 "never log"?
- [ ] If this is a school-data feature: is there explicit user consent?
- [ ] If this is an action feature: does it have dry-run + confirmation +
      audit log?
- [ ] Are inputs validated at the Controller boundary?
- [ ] Are tests free of real personal data (no real student numbers /
      grades / cookies in fixtures)?
- [ ] Was a dependency added? Is it pinned? Is it from a reputable
      maintainer?

This checklist lives here so it can be copy-pasted into the PR template
when the project gets one.

---

## 13. Incident response (lightweight)

If a real or suspected leak occurs:

1. **Stop the bleeding.** Rotate the leaked secret immediately
   (`SSUAI_CREDENTIAL_ENCRYPTION_KEY`, DB password, API key, etc.).
2. **Invalidate sessions.** If user sessions or school cookies may be
   affected, force re-login for all impacted users.
3. **Purge from history.** A committed secret is permanently in git
   history — rotate it; don't try to rewrite history alone.
4. **Notify users** if their personal or school data may have been
   exposed. Honesty over PR.
5. **Write a short post-mortem** in `docs/incidents/YYYY-MM-DD.md`:
   what happened, how detected, what was changed, what to do
   differently. The post-mortem is for learning, not blame.

---

## 14. MVP-specific notes

The MVP is unusually safe because it:

- Has no user accounts → no passwords, no sessions, no PII at rest.
- Touches only **Public** data → no encryption-at-rest worries beyond
  standard DB hygiene.
- Has no action features → no audit log requirements yet.

This makes it a great window to **build the habits early**: the logging
rules, the connector boundary, the response envelope, and the
`.env`-based config should all already exist before the first sensitive
feature lands. The cost of adding them now is low; the cost of
retrofitting them around a working LMS integration is high.

---

## 15. Open questions

To resolve before the relevant feature starts:

- Which secret scanner do we adopt — `gitleaks`, `detect-secrets`, or
  GitHub's built-in secret scanning?
- Sessions vs JWTs — locked above as "sessions in Redis," but worth one
  more review when auth design starts.
- Does Soongsil offer any official OAuth / SSO surface for student apps?
  If yes, it changes most of §5.
- For action audit logs, is `jsonb` masking enough, or do we need a
  separate "encrypted payload" column?
