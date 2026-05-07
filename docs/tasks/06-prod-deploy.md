# Task 06 — Production deploy (k3s on Oracle Cloud + Vercel frontend)

> Hand-off spec for the implementer (Codex CLI). Read `docs/architecture.md`
> §8, `docs/security.md` §3/§4/§8, and `docs/tasks/05-frontend-mvp.md` first
> (this task is the production counterpart of 05's local-only deploy).
> Reply using the Required Output Format in `AGENTS.md`.

## Goal

Get a **live demo URL** of ssuAI online — backend on a Kubernetes cluster
(k3s) on Oracle Cloud Free Tier (Seoul region), frontend on Vercel — both
behind Let's Encrypt TLS, with production CORS explicitly allowlisting the
Vercel origin.

After this task is merged the README should link a working
`https://ssuai-api.duckdns.org/api/meals/today` and
`https://ssuai.vercel.app` (or equivalent).

## Why this slice now

- Task 05 proved the contract end-to-end **locally**. The next narrative
  beat is "it runs on real infrastructure". Without this, the project is
  not credible as a portfolio piece.
- ADR 0007 (this task) locks the deployment topology so later tasks
  (ArgoCD GitOps, observability) can plug into a known shape.
- Oracle Cloud Free Tier ARM Ampere A1 is **permanent free** (4 cores /
  24 GB RAM in `ap-seoul-1`) — same region as Soongsil's sites, so the
  connector latency budget stays small.
- Choosing k3s over a managed serverless runtime is deliberate: the
  portfolio narrative for this project is "I can operate cloud-native
  infrastructure", not "I can click deploy in a PaaS dashboard". ADR 0007
  captures the reasoning.

## Scope — in

1. Provision a single Oracle Cloud ARM Ampere A1 instance in `ap-seoul-1`
   (Ubuntu 22.04 LTS, 4 OCPU / 24 GB / 200 GB block storage from the free
   allowance). Document the manual console steps — **not** Terraform yet
   (that's a stretch for Task 07+).
2. Install **k3s** on the VM (single-node cluster, includes Traefik
   ingress + ServiceLB by default).
3. Container image build pipeline: a GitHub Actions workflow that on
   push to `main` builds the backend Docker image and pushes it to
   **GitHub Container Registry** (`ghcr.io/<user>/ssuai-backend:<sha>`
   and `:latest`). ghcr.io is free for public repos and avoids paying
   for Docker Hub Pro.
4. Backend Dockerfile (multi-stage: Eclipse Temurin 21 JDK build → JRE
   runtime) producing an ARM64-compatible image (Ampere A1 is ARM64 —
   build with `--platform linux/arm64` or use buildx).
5. Kubernetes manifests under `deploy/k8s/` — `Deployment`, `Service`,
   `Ingress`, `ConfigMap`, `Secret` (template only), `Namespace`. Plain
   `kubectl apply` for now; Helm chart refactor is Task 07.
6. **cert-manager** installed in the cluster; one `ClusterIssuer` for
   Let's Encrypt production via HTTP-01 challenge. Backend ingress gets
   a real cert.
7. **Free dynamic DNS via duckdns.org** — `ssuai-api.duckdns.org` →
   Oracle VM's public IP. The duckdns updater can run as a cron job on
   the VM (single line) or as a CronJob in k3s. Pick the simpler one
   (cron on the VM) and document.
8. Frontend deploy to **Vercel** (Next.js — first-party support, fastest
   path). One project linked to the GitHub repo's `main` branch, env
   var `NEXT_PUBLIC_SSUAI_API_BASE=https://ssuai-api.duckdns.org` set in
   the Vercel project settings.
9. Production CORS in the backend — a `WebCorsConfig` variant gated on
   `@Profile("prod")` allowing the deployed Vercel origin **only** (no
   `*`, no preview URLs — those need a separate decision).
10. `application-prod.yml` already exists and flips connectors to
    `real`. Add the JVM defaults appropriate for a 24 GB ARM box (heap
    sizing, GC). Document any `${ENV_VAR}` references that the
    deployment injects at runtime.
11. README badges + a "Live demo" section linking the two URLs and
    showing example curl. Top-of-README screenshot/GIF is a nice-to-have.

## Scope — out (later tasks)

- **ArgoCD / GitOps** — Task 07. This task does manual `kubectl apply`
  from a laptop or via a GitHub Actions deploy job. ArgoCD comes once
  the manifests are stable.
- **Helm chart** — Task 07. Plain manifests are fine until they repeat.
- **Prometheus + Grafana + Loki observability stack** — Task 08.
- **Postgres / Redis in the cluster** — there are no DB-backed
  features yet; spinning these up before they're needed is YAGNI. Add
  in the task that introduces auth or cache.
- **Auto-scaling, HPA, PDBs, network policies** — single-replica
  Deployment is enough for current traffic. Add when needed.
- **Vercel preview deploy CORS** — non-trivial (rotating subdomains).
  Out of scope; document as a known limitation in the PR.
- **Custom domain (`ssuai.app` etc.)** — duckdns is fine for the demo.
  Custom domain is a Task 06 follow-up if the developer wants to spend
  the few dollars on a `.xyz` domain.
- **Terraform / Pulumi / OpenTofu IaC** — manual console + documented
  steps for now. IaC is its own task.

## Stack & rationale

| Choice | Why |
|---|---|
| Oracle Cloud Free Tier ARM Ampere A1, `ap-seoul-1` | Permanent free, 4 cores / 24 GB / Seoul region (low latency to Soongsil sites). The strongest free option in 2026. |
| k3s (single-node) | Lightweight K8s (~100 MB RAM overhead), bundled Traefik + ServiceLB, perfect for one cheap VM. Real K8s API surface for portfolio narrative. |
| Traefik (k3s default) | Comes with k3s, zero install, fine for one ingress. Swap to nginx-ingress in Task 07 if the narrative warrants it. |
| cert-manager + Let's Encrypt HTTP-01 | Industry-standard cert automation. HTTP-01 is simpler than DNS-01 and works fine for a single ingress with a public IP. |
| GitHub Container Registry (ghcr.io) | Free for public repos, no rate limits like Docker Hub free tier, integrates with GitHub Actions out of the box. |
| duckdns.org | Free dynamic DNS, no signup friction, supports Let's Encrypt fine. |
| Vercel for frontend | First-party Next.js support, free tier covers this comfortably, deploys on every push. Splitting frontend off the cluster keeps the K8s narrative focused on backend ops. |
| ghcr.io image tagged `<sha>` + `latest` | SHA tag is the immutable deploy artifact; `latest` is convenience. Rolling deploy uses the SHA tag for traceability. |

ADR 0007 will document the alternatives considered (Cloud Run, GKE
Autopilot, Fly.io, Railway) and why Oracle + k3s won on
**permanent-free + region + portfolio narrative**.

## Files to create / modify

```
deploy/
├── k8s/
│   ├── namespace.yaml                 # ssuai-prod namespace
│   ├── configmap.yaml                 # SPRING_PROFILES_ACTIVE=prod, JVM opts
│   ├── secret.example.yaml            # template for runtime secrets (none yet, but reserve)
│   ├── deployment.yaml                # backend Deployment, image: ghcr.io/<user>/ssuai-backend:<sha>
│   ├── service.yaml                   # ClusterIP service on port 8080
│   ├── ingress.yaml                   # Traefik ingress, host: ssuai-api.duckdns.org, TLS via cert-manager
│   └── clusterissuer.yaml             # Let's Encrypt prod ClusterIssuer
├── docker/
│   └── Dockerfile                     # multi-stage backend image (build on JDK 21, run on JRE 21, ARM64)
└── README.md                          # one-page operator guide: VM setup → k3s → cert-manager → first deploy → upgrade workflow

backend/src/main/java/com/ssuai/global/config/
└── WebCorsConfig.java                 # MODIFY: add @Profile("prod") variant alongside existing dev one,
                                       # OR split into WebCorsDevConfig + WebCorsProdConfig — pick whichever
                                       # makes the profile gating most readable.

backend/src/main/resources/
├── application-prod.yml               # MODIFY: add JVM-friendly tuning notes, ${SSUAI_FRONTEND_ORIGIN} ref
└── application.yml                    # MODIFY (if needed): document new prod-only env vars

.github/workflows/
├── ci.yml                             # MODIFY: add backend-image job that builds + pushes to ghcr.io on push to main
└── (no separate deploy.yml yet — manual kubectl apply for this task; deploy.yml comes Task 07)

docs/
├── adr/
│   └── 0007-prod-deploy-oracle-k3s.md # ADR for this deployment topology decision
└── tasks/
    └── 06-prod-deploy.md              # this file (already created)

frontend/
└── README.md                          # MODIFY: add "Production" section noting Vercel deploy + env var

README.md                              # MODIFY: add "Live demo" section, badges, screenshot
```

`deploy/` is a new top-level dir — keep it sibling to `backend/` and
`frontend/` so it's clearly infra (not application code).

## Configuration

### Environment variables (production, set via Vercel / Kubernetes
ConfigMap+Secret)

**Backend** (k3s ConfigMap unless secret):

| Var | Source | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ConfigMap | `prod` |
| `SSUAI_FRONTEND_ORIGIN` | ConfigMap | e.g. `https://ssuai.vercel.app` — read by `WebCorsConfig` prod variant |
| `JAVA_OPTS` | ConfigMap | `-Xmx1g -XX:+UseG1GC` (24 GB box, but single backend pod for now) |
| (future) `SSUAI_DB_URL` etc. | Secret | placeholder, no DB yet |

**Frontend** (Vercel project settings):

| Var | Value |
|---|---|
| `NEXT_PUBLIC_SSUAI_API_BASE` | `https://ssuai-api.duckdns.org` |

The same name as in dev (`.env.example`) — the only difference is the
value.

### Domain

`ssuai-api.duckdns.org` → Oracle VM public IP.

duckdns updater on the VM:

```bash
# /etc/cron.d/duckdns
*/5 * * * * root curl -sk -o /dev/null "https://www.duckdns.org/update?domains=ssuai-api&token=<TOKEN>"
```

Token comes from duckdns.org login. Treat it as a secret — store the
file readable only by root.

### TLS

`ClusterIssuer` for Let's Encrypt prod uses HTTP-01 via the Traefik
ingress. cert-manager handles renewal automatically. No staging issuer
needed for one host (rate limits are generous enough).

## Test plan / verification

1. **VM reachable**: `curl https://ssuai-api.duckdns.org/actuator/health`
   returns `{"status":"UP"}`. Cert is valid Let's Encrypt (not
   self-signed, browser shows lock icon).
2. **Backend live data**: `curl https://ssuai-api.duckdns.org/api/meals/today`
   returns the standard `ApiResponse<T>` envelope with real data
   (connectors are `real` in `application-prod.yml`).
3. **Frontend live**: `https://ssuai.vercel.app` (or assigned
   `<project>.vercel.app`) renders all 4 cards with real data from the
   k3s backend. No CORS errors in browser console.
4. **CORS allowlist enforced**: a curl with
   `-H "Origin: https://attacker.example"` to the prod API gets **no**
   `Access-Control-Allow-Origin` header (or gets explicitly null/empty).
5. **TLS auto-renewal works**: `kubectl get certificate -A` shows
   `READY=True` and `cert-manager` log shows the cert was issued by
   Let's Encrypt prod.
6. **Container image builds + pushes on push to main**: GitHub Actions
   "Build backend image" job is green; the image with the latest
   commit SHA appears at `ghcr.io/<user>/ssuai-backend`.
7. **Rolling deploy**: bump `deployment.yaml`'s image tag to a new SHA
   → `kubectl apply` → `kubectl rollout status` — pod replaced with
   zero downtime (single replica, but Traefik holds requests during
   the swap because of `readinessProbe`).
8. **Logs**: `kubectl logs -n ssuai-prod deploy/ssuai-backend` shows
   the same JSON-structured logs as in dev (per `docs/security.md §4`,
   no PII or raw HTML in logs — already enforced).

If any step fails, fix before reporting done.

## Subtask breakdown (for separate commits)

Codex commits in this order, one logical change per commit. Push after
each one — do not stack the whole thing.

1. `chore(infra): add backend Dockerfile (multi-stage, ARM64)`
   - `deploy/docker/Dockerfile` only. Test locally:
     `docker build --platform linux/arm64 -t ssuai-backend:dev backend/`
     succeeds and `docker run -p 8080:8080 ssuai-backend:dev` boots.
2. `feat(ci): build and push backend image to ghcr.io on push to main`
   - Adds an `image-build` job to `.github/workflows/ci.yml`. Uses
     `docker/setup-buildx-action`, `docker/login-action`, and
     `docker/build-push-action`. Tags `:<sha>` and `:latest`. Job runs
     only on `push` to `main` (not on PRs — saves ghcr.io storage).
3. `feat(infra): add k8s manifests for backend deployment`
   - `deploy/k8s/{namespace,configmap,deployment,service,ingress,clusterissuer}.yaml`.
     `secret.example.yaml` as a template. No real secrets committed.
4. `feat(backend): add @Profile("prod") CORS config for deployed origin`
   - Adds the prod `WebCorsConfig` variant reading
     `SSUAI_FRONTEND_ORIGIN`. Splits dev/prod cleanly. Test: a unit
     test asserting that the prod config bean is NOT loaded under the
     `dev` profile (and vice versa).
5. `chore(infra): document Oracle Cloud + k3s + cert-manager setup`
   - `deploy/README.md`. Step-by-step:
     1. Create Oracle Cloud account, launch ARM Ampere A1 in
        `ap-seoul-1`, open ports 22/80/443.
     2. SSH in, `curl -sfL https://get.k3s.io | sh -`, copy
        `/etc/rancher/k3s/k3s.yaml` to laptop, edit `server:` to public IP.
     3. Install cert-manager:
        `kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml`.
     4. Apply `clusterissuer.yaml`, then `namespace`, `configmap`,
        `deployment`, `service`, `ingress` in order.
     5. Set up duckdns subdomain + cron updater on the VM.
     6. First deploy: bump image tag to a real SHA, `kubectl apply -f
        deploy/k8s/`.
     7. Upgrade workflow: edit image tag → `kubectl apply` → watch rollout.
   - This README is the **operator runbook** — readable enough that
     the developer can re-do the setup from scratch in 1 hour.
6. `feat(frontend): document Vercel production deploy`
   - `frontend/README.md` add a "Production" section. The actual
     Vercel project linkage is a manual click-through (cannot be
     committed). Document: link repo → set
     `NEXT_PUBLIC_SSUAI_API_BASE` → deploy from `main` branch.
7. `docs: add ADR 0007 prod deploy topology + dev-log entry`
   - ADR captures: Oracle + k3s + ghcr + duckdns + Vercel decision,
     alternatives considered (Cloud Run, GKE, Fly.io, Railway), and
     trade-offs. Dev-log: one line summarizing the live demo URL.
8. `docs(readme): add Live demo section + badges`
   - Top-of-README links + 1 screenshot. Sells the project at-a-glance.

If any subtask is too large to land cleanly, split further — never
combine.

## Verification before reporting done

Codex must do all of these:

1. The 8 verification steps in §"Test plan / verification" above.
2. CI green on the PR.
3. `kubectl get pods -n ssuai-prod` shows `Running 1/1`.
4. Vercel deploy shows green for the latest commit on `main` (after
   merge — preview deploys are fine for the PR).
5. A `git log` on `main` after merge shows 8 (or so) small commits, not
   one mega-commit.

If any step fails, fix before reporting done.

## Security notes (from `docs/security.md`)

- `application-prod.yml` is **committed** but must never hold secrets —
  only `${ENV_VAR:default}` references (§3). This task adds a few env
  refs; nothing secret yet (no DB / auth in MVP).
- duckdns token is a **secret** — keep it on the VM only, in a root-owned
  cron file, not in the repo. If/when the updater moves into k3s as a
  CronJob, it goes in a `Secret`, never a `ConfigMap`.
- ghcr.io image is **public** (free tier requirement). The image must
  not contain secrets — Dockerfile must not `COPY .env*`, and the
  build context should `.dockerignore` env files. Verify with
  `docker history --no-trunc <image>` that no env files are baked in.
- HTTPS only on the public ingress. cert-manager + Traefik handle this.
  HTTP redirects to HTTPS (Traefik default with the right
  `redirectScheme` middleware — document in the ingress YAML).
- HSTS header on prod responses (§8). Add via Traefik
  `Headers` middleware in the ingress annotations, or via a Spring
  filter — Traefik is simpler and stays consistent across services.
- CORS allowlist is **explicit** (the deployed Vercel origin), never
  `*` (§8). Already in §"Scope — in" #9.
- Backend logs must remain JSON-structured and free of PII / school
  HTML / connector tokens (§4). Verify after deploy by tailing
  `kubectl logs` for one full request cycle.

## PR description draft

Title: `feat(infra): production deploy on k3s (Oracle Cloud) + Vercel`

Body (use as-is or trim):

```markdown
## What
First production deployment of ssuAI:

- **Backend** on a single-node k3s cluster on Oracle Cloud Free Tier
  ARM Ampere A1 (`ap-seoul-1`), behind Traefik ingress with Let's
  Encrypt TLS via cert-manager.
- **Frontend** on Vercel, pointed at the prod backend.
- **Live demo**: https://ssuai.vercel.app — backed by
  https://ssuai-api.duckdns.org

Both deploy automatically: backend image is built and pushed to
`ghcr.io/<user>/ssuai-backend:<sha>` on every push to `main`; Vercel
deploys frontend on every push to `main`. Backend rollout itself is
still manual `kubectl apply` for this PR — ArgoCD GitOps is Task 07.

## Why
Task 05 proved the contract end-to-end locally. This task proves it
runs on real infrastructure in the same region as the school sites
(Seoul). Permanent-free hosting + a real K8s control plane gives the
project a credible production shape without ongoing cost.

## Notable decisions
- **Oracle Cloud + k3s over Cloud Run / GKE / Fly.io / Railway**: ADR
  0007 has the full reasoning. TL;DR — only Oracle's free tier is
  permanent + Seoul region, and self-managed k3s gives a richer
  portfolio narrative than a managed runtime.
- **ghcr.io over Docker Hub**: free for public repos, no pull rate
  limit on the cluster.
- **duckdns over a custom domain**: keeps the project free; trivial
  swap to a real domain later (one ingress edit + cert-manager
  re-issue).
- **Manual `kubectl apply` for this task**: getting the manifests
  stable before adding ArgoCD avoids cargo-culting GitOps. ArgoCD is
  Task 07.

## Test plan
- [ ] `curl https://ssuai-api.duckdns.org/actuator/health` — `UP`
- [ ] `curl https://ssuai-api.duckdns.org/api/meals/today` — real envelope
- [ ] `https://ssuai.vercel.app` — all 4 cards render with real data
- [ ] CORS rejects requests with a non-allowlisted Origin
- [ ] `kubectl get certificate -A` — Let's Encrypt cert READY
- [ ] CI: image build + push job green
- [ ] Rolling deploy: bump SHA → `kubectl apply` → zero-downtime swap

## Next
- Task 07 — ArgoCD GitOps + Helm chart refactor (auto-deploy backend
  on `main` push, no more manual `kubectl apply`).
- Task 08 — Observability stack (Prometheus + Grafana + Loki).
```

## Commit guidance

- Commit per subtask in §"Subtask breakdown" — small, focused, each
  one pushable on its own.
- Conventional Commits style (matches existing history). `feat(infra)`
  and `chore(infra)` are the new prefixes for this task.
- Push frequently — do not stack 8 local commits before the first push.
- The Vercel + Oracle Cloud account creation steps are **manual** and
  not commits — they belong in the PR description and in
  `deploy/README.md`.

## ADR & dev-log

- Add `docs/adr/0007-prod-deploy-oracle-k3s.md` capturing: Oracle
  Cloud + k3s vs Cloud Run vs GKE Autopilot vs Fly.io vs Railway,
  ghcr.io vs Docker Hub, duckdns vs custom domain, Vercel for
  frontend. Follow the format of `docs/adr/0006-frontend-stack.md`.
- Append one line to `docs/dev-log.md`:
  `2026-XX-XX: Task 06 production deploy — ssuAI live at <URL>. k3s
  on Oracle Cloud Free Tier (Seoul) + Vercel + cert-manager.
  <한 줄 인상깊은 트러블슈팅>`.

## What this task explicitly does NOT cover (FAQ)

- "Why no Postgres?" — no DB-backed feature exists yet. Adding it now
  is YAGNI. The first task that needs persistence brings it in.
- "Why no auth?" — same reason. Read-only public data, no user
  accounts. Auth lands when chat / personalization does.
- "Why single replica?" — current traffic is "the developer + 2
  reviewers", and the readiness probe handles the rolling deploy
  window. Multi-replica + HPA is Task 08+ once Prometheus is up.
- "Why not Terraform?" — runbook in `deploy/README.md` is enough for
  one VM. IaC pays off when there are 3+ environments or multiple
  developers, neither of which is true yet.
