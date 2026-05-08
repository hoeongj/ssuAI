# Task 07 — GitOps rollout with ArgoCD + Helm chart

> Hand-off spec for the implementer (Codex CLI). Read
> `docs/tasks/06-prod-deploy.md`, `docs/adr/0007-prod-deploy-oracle-k3s.md`,
> and `deploy/README.md` first — Task 07 builds directly on the cluster
> stood up there. ADR 0008 (this task) records the GitOps tooling
> decision.
> Reply using the Required Output Format in `AGENTS.md`.

## Goal

Replace the manual `kubectl apply -f deploy/k8s/` workflow with **Pull a
Git repo, sync the cluster automatically**:

- Backend manifests live as a **Helm chart** under `deploy/charts/ssuai-backend/`,
  with values overridable per environment.
- **ArgoCD** runs in the cluster, watches the chart, and reconciles the
  cluster state to git on every push to `main`.
- **ArgoCD Image Updater** watches `ghcr.io/hoeongj/ssuai-backend`, picks
  up new SHA tags pushed by CI, writes the new tag back to git, and
  ArgoCD picks up the change → no human in the rollout loop.

After this task is merged, the upgrade workflow described in
`deploy/README.md §9` (manual `kubectl set image …`) is retired — pushing
to `main` is the only required step to deploy a new backend version.

## Why this slice now

- Task 06 froze a working manifest layout. The next narrative beat is
  "those manifests are reconciled by a controller, not a sysadmin's
  laptop". This is the move from "I deployed it once" to "I run it".
- ArgoCD is the dominant GitOps controller in the K8s ecosystem in 2026,
  so the portfolio gain is real — recruiters in Korean cloud-native
  shops actively look for it.
- Image Updater + Helm + auto-sync removes three manual steps (image
  tag bump, kubectl apply, rollout watch) and replaces them with a
  declarative loop the cluster runs on its own.
- ADR 0008 (this task) locks the GitOps tool decision so later tasks
  (multi-env via ApplicationSet, observability stack, secret operator)
  can plug into a known shape.

## Scope — in

1. **Helm chart for the backend** under `deploy/charts/ssuai-backend/`.
   Each plain manifest from `deploy/k8s/` (Deployment, Service, Ingress,
   ConfigMap, Secret template, Middleware CRDs) becomes a chart template;
   the host, image tag, frontend origin, JVM opts, and resource limits
   are exposed as `values.yaml` keys. The chart's default `values.yaml`
   matches today's production configuration.
2. **Install ArgoCD via the upstream Helm chart**
   (`argo/argo-cd`, currently 7.x) into the `argocd` namespace. Pin a
   chart version. Disable Dex (no SSO yet — single-operator portfolio
   project). Expose the UI on `argo.duckdns.org` (or
   `argocd.ssuai-api.duckdns.org` — pick the simpler one) behind a
   Traefik ingress with a dedicated cert-manager Certificate.
3. **One ArgoCD `Application`** named `ssuai-backend` pointed at this
   repo's `deploy/charts/ssuai-backend/` path on `main`. Sync policy:
   automated + prune + self-heal. Sync waves: cert-manager CRDs (already
   installed in Task 06) → namespace → ConfigMap/Secret → Service →
   Deployment → Ingress.
4. **Install ArgoCD Image Updater** (`argoproj-labs/argocd-image-updater`,
   chart `argo/argocd-image-updater`). Watch
   `ghcr.io/hoeongj/ssuai-backend` for new `sha-<full>` tags, pick the
   newest, write the tag back to `deploy/charts/ssuai-backend/values.yaml`
   via a git commit. Use a fine-grained PAT scoped to `contents:write` on
   this repo only — stored as a `Secret` in the `argocd` namespace, never
   in the manifest.
5. **Retire `deploy/k8s/`** raw manifests. Move what's still needed
   (`clusterissuer.yaml`) under `deploy/cluster-bootstrap/` because it
   lives outside the chart (cluster-wide, applied once during VM setup).
   `deploy/k8s/` itself is deleted once the chart provably renders to
   the same set of resources.
6. **Operator runbook update** — `deploy/README.md` gets two new
   sections: "GitOps via ArgoCD" (how the cluster picks up changes) and
   "Bootstrap order" (one-time cert-manager install → ArgoCD install →
   apply Application → done). The "Upgrade workflow" section in §9 is
   replaced with "Push to `main` and wait" + a `kubectl` cheatsheet for
   inspecting ArgoCD when something goes wrong.
7. **ADR 0008** records the decisions (ArgoCD over Flux, Helm over
   Kustomize, Image Updater over GHA-writes-back, single Application
   over App-of-apps for now).

## Scope — out (later tasks)

- **Multi-environment via ApplicationSet** — the project has one prod
  cluster. Adding `dev`/`stage` clusters is its own task; pulled out
  once a real preview environment is needed.
- **App-of-apps pattern** — overkill for one Application. Promote to
  app-of-apps only when there are 3+ workloads (e.g. once observability
  + a database + the backend coexist).
- **SSO for ArgoCD** (Dex + GitHub OAuth) — single-operator project,
  admin + strong password is enough. Enable Dex when there's a second
  human with cluster access.
- **ArgoCD Notifications to Slack/Discord** — nice-to-have, deferred
  until the project actually runs into a sync failure that the
  developer didn't notice.
- **Sealed Secrets / SOPS / external-secrets-operator** — the MVP has
  no real secrets to manage yet. Pick one when the first real secret
  arrives (DB password, JWT key, etc.). ADR 0008 mentions it in
  "Alternatives considered" but doesn't pick one.
- **PodDisruptionBudgets, NetworkPolicies, HPA** — single-replica
  workload, no policies to enforce. Comes with Task 08 (observability
  + autoscaling) once metrics-server + Prometheus are in.
- **Helm chart published to a chart repo** — the chart is consumed
  in-repo by ArgoCD, so publishing it to ChartMuseum / OCI is unneeded.
  Add only if a second consumer appears.
- **Frontend (Vercel) under ArgoCD** — Vercel is a separate platform
  with its own auto-deploy on git push. Out of scope.

## Stack & rationale

| Choice | Why |
|---|---|
| **ArgoCD** (over Flux) | Dominant GitOps controller in 2026; Korean cloud-native job postings call it out by name far more than Flux. Strong UI for "what synced what when" — important for a portfolio demo where reviewers will click around. Better Helm-chart support out of the box than Flux's `HelmRelease`. |
| **Helm** (over Kustomize, raw + ArgoCD vars) | Helm is the de-facto K8s package format. ArgoCD speaks Helm natively. Variable substitution + chart versioning are first-class. Kustomize's overlay model is fine but adds a second tool to learn for no extra power at this size. |
| **ArgoCD Image Updater** | Closes the "CI built an image, but who bumps the manifest?" loop without dragging GitHub Actions into the cluster's deploy path. Image Updater watches the registry, writes back to git via a fine-grained PAT, ArgoCD then reconciles. Industry-standard ArgoCD pattern. |
| **Auto-sync + self-heal + prune** | Drift detection is the whole point of GitOps. Manual sync is fine for early debugging; auto is the production posture. Self-heal reverts hand-edits to the cluster (a kubectl apply by mistake won't stick). Prune deletes resources removed from git, so the chart stays the source of truth. |
| **Sync waves via annotations** | Lightweight ordering for the small set of resources here. App-of-apps would be over-engineered for one Application. |
| **No Dex / SSO** | Single operator. Admin password + strong password manager entry is enough. Dex requires running an extra deployment + an OAuth app on GitHub — both are pure cost until there's a second human. |
| **Image SHA tags** (already CI default) | Immutable references; `:latest` is never the deploy target. Image Updater writes the SHA explicitly, so a `git revert` is also a deploy revert. |

## Files to create / modify

```
deploy/
├── charts/
│   └── ssuai-backend/
│       ├── Chart.yaml
│       ├── values.yaml                # default values (= today's prod config)
│       ├── values-prod.yaml           # prod overrides if any (e.g. host)
│       └── templates/
│           ├── _helpers.tpl
│           ├── namespace.yaml
│           ├── configmap.yaml
│           ├── secret.example.yaml    # template; toggled off by default
│           ├── deployment.yaml
│           ├── service.yaml
│           ├── ingress.yaml
│           ├── middleware-redirect-https.yaml
│           ├── middleware-hsts.yaml
│           └── NOTES.txt
├── argocd/
│   ├── README.md                      # bootstrap + day-2 ops for ArgoCD itself
│   ├── values.yaml                    # values for the upstream argo-cd Helm chart
│   ├── ingress.yaml                   # Traefik ingress + cert-manager Certificate for argo.<host>
│   ├── application-ssuai-backend.yaml # ArgoCD Application CRD pointing at deploy/charts/ssuai-backend
│   └── image-updater/
│       ├── values.yaml                # values for argocd-image-updater Helm chart
│       └── secret.example.yaml        # template for the PAT secret (template only)
├── cluster-bootstrap/
│   └── clusterissuer.yaml             # MOVED from deploy/k8s/clusterissuer.yaml
└── README.md                          # MODIFY: GitOps section + new bootstrap order

docs/
├── adr/
│   └── 0008-gitops-argocd-helm.md     # NEW: this task's ADR
└── tasks/
    └── 07-argocd-gitops.md            # this file (already created)

deploy/k8s/                            # DELETED (templates moved into the chart)
```

`deploy/charts/` is the convention used by other open-source K8s
projects (`charts/<name>` under the repo). Keeping it sibling to
`docker/` and `argocd/` makes the deploy directory's role self-evident.

## Configuration

### `values.yaml` shape (chart defaults)

```yaml
image:
  repository: ghcr.io/hoeongj/ssuai-backend
  tag: latest                          # ArgoCD Image Updater overwrites this
  pullPolicy: IfNotPresent

namespace: ssuai-prod

env:
  springProfilesActive: prod
  frontendOrigin: https://ssuai.vercel.app
  javaOpts: "-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

resources:
  requests:
    cpu: 100m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1Gi

ingress:
  host: ssuai-api.duckdns.org
  tlsSecretName: ssuai-api-tls
  clusterIssuer: letsencrypt-prod

probes:
  liveness:
    path: /actuator/health/liveness
    initialDelaySeconds: 60
  readiness:
    path: /actuator/health/readiness
    initialDelaySeconds: 20

# Toggle off by default — set to true once a real Secret exists.
secretRef:
  enabled: false
  name: ssuai-backend-secrets
```

### ArgoCD Application annotations for Image Updater

```yaml
metadata:
  annotations:
    # Image Updater scans this image source.
    argocd-image-updater.argoproj.io/image-list: backend=ghcr.io/hoeongj/ssuai-backend
    # Match the immutable SHA tags CI pushes (sha-<40-char-hex>).
    argocd-image-updater.argoproj.io/backend.update-strategy: newest-build
    argocd-image-updater.argoproj.io/backend.allow-tags: regexp:^sha-[0-9a-f]{40}$
    # Write the new tag back to git, not via ArgoCD parameter override.
    # This keeps `values.yaml` the source of truth so a git revert is a deploy revert.
    argocd-image-updater.argoproj.io/write-back-method: git
    argocd-image-updater.argoproj.io/git-branch: main
    argocd-image-updater.argoproj.io/backend.helm.image-tag: image.tag
```

### Secrets (in cluster only, never in repo)

| Where | Name | Purpose |
|---|---|---|
| `argocd` namespace | `argocd-image-updater-secret` | GitHub PAT for write-back. Scope: contents:write on `hoeongj/ssuAI` only. |
| `argocd` namespace | `argocd-secret` (chart default) | ArgoCD's own admin/server keys. Chart generates on install; capture and rotate. |

The `argocd` namespace's secrets are **never** committed; the
`secret.example.yaml` files document the keys' shapes only.

### Domain

Add one DNS record for the ArgoCD UI. Easiest: a duckdns CNAME
`argo-ssuai.duckdns.org` → the cluster's public IP, same updater cron as
`ssuai-api.duckdns.org`. Document in `deploy/argocd/README.md`.

## Test plan / verification

1. **Chart renders**: `helm template ssuai-backend deploy/charts/ssuai-backend/`
   produces the same set of resources (modulo cosmetic diffs) as today's
   `deploy/k8s/` raw manifests applied. Spot-check: same Service ports,
   same Ingress host, same Deployment image + probe paths.
2. **ArgoCD installs cleanly**: `kubectl get pods -n argocd` shows all
   ArgoCD components Running 1/1. UI reachable at
   `https://argo-ssuai.duckdns.org` with a real Let's Encrypt cert.
3. **Application syncs**: `argocd app sync ssuai-backend` (or auto-sync
   on first apply) reaches `Synced + Healthy`. `kubectl get pods -n
   ssuai-prod` looks identical to the Task 06 baseline.
4. **Drift detection works**: `kubectl edit deploy -n ssuai-prod
   ssuai-backend` to change replica count → ArgoCD self-heal reverts it
   within ~30s.
5. **Image Updater works end-to-end**:
   - Push a no-op commit to `main` → CI builds + pushes
     `ghcr.io/hoeongj/ssuai-backend:sha-<new>` → Image Updater detects
     within its poll interval (default 2 min) → writes the new tag to
     `deploy/charts/ssuai-backend/values.yaml` via a git commit
     authored as `argocd-image-updater[bot]` → ArgoCD reconciles → pod
     rolls. Total wall time ≤ ~5 min after CI completes.
6. **Rollback**: `git revert <image-bump-commit>` on `main` → ArgoCD
   reconciles back → pod rolls to the previous SHA. No `kubectl`
   needed.
7. **Logs / events sane**: `argocd app logs ssuai-backend`,
   `kubectl -n argocd logs deploy/argocd-image-updater` show no ERROR
   spam. Image Updater logs show "no update needed" steady-state.
8. **The chart is the source of truth**: deleting a Service from the
   chart, committing, pushing → ArgoCD prunes the Service in the
   cluster within the next reconcile.

If any step fails, fix before reporting done.

## Subtask breakdown (for separate commits)

Codex commits in this order, one logical change per commit. Push after
each one — do not stack the whole thing.

1. `feat(infra): add Helm chart deploy/charts/ssuai-backend (template only)`
   - Convert `deploy/k8s/` manifests into chart templates with values
     extracted. Do **not** delete `deploy/k8s/` yet — keep both during
     verification.
   - Verify with `helm template …` + `diff` against rendered current
     manifests. The diff should be cosmetic only (sort order, comments).
2. `feat(infra): install ArgoCD via the upstream Helm chart`
   - `deploy/argocd/values.yaml` for the `argo/argo-cd` chart, pinned
     version. `deploy/argocd/README.md` step-by-step bootstrap.
3. `feat(infra): expose ArgoCD UI on argo-ssuai.duckdns.org with TLS`
   - `deploy/argocd/ingress.yaml` (Traefik ingress + cert-manager
     Certificate). Update duckdns runbook to add the second subdomain.
4. `feat(infra): add ArgoCD Application for ssuai-backend chart`
   - `deploy/argocd/application-ssuai-backend.yaml`. Auto-sync, prune,
     self-heal. Sync waves on the chart templates.
5. `feat(infra): install ArgoCD Image Updater + git write-back config`
   - `deploy/argocd/image-updater/{values,secret.example}.yaml`.
     Document PAT creation + Secret apply (PAT lives in cluster only,
     never repo).
6. `chore(infra): retire deploy/k8s/ raw manifests`
   - Delete `deploy/k8s/` (move `clusterissuer.yaml` to
     `deploy/cluster-bootstrap/`). Update `deploy/README.md` apply
     order to "kubectl apply -f deploy/cluster-bootstrap/ once, then
     ArgoCD owns everything else".
7. `docs: add ADR 0008 GitOps with ArgoCD + Helm`
   - Captures the alternatives (Flux, Kustomize, raw + GHA write-back,
     Spinnaker, Jenkins X) and the rationale. Append a one-line
     `docs/dev-log.md` entry.
8. `docs(infra): rewrite deploy/README.md "Upgrade workflow" for GitOps`
   - The §9 manual cheatsheet is replaced with "Push to main, watch
     `argocd app get ssuai-backend`". Keep a small "If GitOps is broken"
     break-glass section: how to disable auto-sync and `kubectl apply`
     manually for a single emergency.

If any subtask is too large to land cleanly, split further — never
combine.

## Verification before reporting done

Codex must do all of these:

1. The 8 verification steps in §"Test plan / verification" above.
2. CI green on the PR.
3. `argocd app get ssuai-backend` shows `Sync Status: Synced`,
   `Health Status: Healthy`.
4. A test commit to `main` (e.g. a comment-only README change) demonstrably
   triggers the full loop: CI → ghcr.io push → Image Updater → git
   commit by the bot → ArgoCD sync → pod roll. Captured as a screenshot
   or a copy-paste of the relevant ArgoCD events in the PR.
5. `git log` on `main` after merge shows ~8 small commits.

If any step fails, fix before reporting done.

## Security notes (from `docs/security.md`)

- The Image Updater PAT is the most sensitive new artifact. It must
  use a **fine-grained** PAT scoped to `contents:write` on
  `hoeongj/ssuAI` only — not classic. Stored as a K8s Secret in the
  `argocd` namespace; never committed; documented for rotation in
  `deploy/argocd/README.md`.
- ArgoCD admin password is rotated immediately after first install
  (default password is `argocd-initial-admin-secret` in the cluster —
  capture, change via UI, delete the bootstrap secret). Rotation
  procedure documented in the runbook.
- ArgoCD UI is **HTTPS only** (cert-manager + Let's Encrypt). HTTP
  redirects to HTTPS via the same Traefik middleware as the backend.
  HSTS header reused.
- The ArgoCD `Application` does **not** enable
  `server-side-apply: cascading` or any cluster-wide RBAC grant
  beyond what's needed to manage `ssuai-prod` resources. Grant
  least-privilege via `Project` CRD if the operator wants belt-and-
  suspenders.
- Helm chart's `secret.example.yaml` template stays toggled off by
  default — same shape as in Task 06 (no `PLACEHOLDER` value pushed
  into the cluster).
- Image Updater commits are authored by the bot, not by the developer.
  Set the commit message format so they're easy to filter out of `git
  log` (e.g. `chore(image): bump backend to sha-…`).

## PR description draft

Title: `feat(infra): GitOps rollout — ArgoCD watches deploy/charts/ssuai-backend`

Body (use as-is or trim):

```markdown
## What
Replaces the manual `kubectl apply` upgrade workflow from Task 06 with a
GitOps loop:

- Backend manifests are now a Helm chart at
  `deploy/charts/ssuai-backend/`.
- **ArgoCD** runs in the cluster, watches `main`, and reconciles the
  cluster to git on every commit.
- **ArgoCD Image Updater** watches `ghcr.io/hoeongj/ssuai-backend`,
  writes new SHA tags back to `values.yaml` via a fine-grained PAT,
  and ArgoCD picks up the change.

After merge, pushing to `main` is the only required step to deploy a
new backend version. The `deploy/README.md §9` manual `kubectl set
image` cheatsheet is replaced with `argocd app get ssuai-backend`.

## Why
Task 06 froze a working manifest layout. This task is the move from
"deployed once" to "operated continuously". GitOps is the dominant
posture for K8s workloads in 2026 and a strong portfolio signal for
Korean cloud-native roles.

## Notable decisions
- **ArgoCD over Flux**: ADR 0008 has the full reasoning. TL;DR — better
  Helm support, dominant in JD postings, strong UI for the demo.
- **Helm over Kustomize**: chart packaging + values.yaml is the
  baseline K8s skill, and Helm + ArgoCD is the more common pairing.
- **Image Updater (write-back to git) over GHA-writes-back**: keeps
  the deploy path inside the cluster and makes git the single source
  of truth. A `git revert` is a deploy revert.
- **Single Application, no App-of-apps yet**: only one workload to
  reconcile. App-of-apps lands once observability + a DB join the
  cluster.

## Test plan
- [ ] `helm template` for the chart matches the Task 06 manifest set
- [ ] ArgoCD UI reachable at `argo-ssuai.duckdns.org` with a real cert
- [ ] `Application` syncs Healthy on first apply
- [ ] Drift test: `kubectl edit` reverts within 30s (self-heal)
- [ ] Image Updater test: push to `main` → bot commit → pod roll, ≤5 min
- [ ] Rollback test: `git revert` on the bot commit → pod rolls back
- [ ] `deploy/k8s/` directory removed; `cluster-bootstrap/` holds only
      `clusterissuer.yaml`

## Next
- Task 08 — observability (Prometheus + Grafana + Loki) reachable via
  ArgoCD Applications, same chart pattern.
- Sealed Secrets / external-secrets-operator when the first real secret
  arrives.
```

## Commit guidance

- Commit per subtask in §"Subtask breakdown" — small, focused, each
  one pushable on its own.
- Conventional Commits style. `feat(infra)` and `chore(infra)` are the
  prefixes for this task.
- Push frequently — do not stack 8 local commits before the first push.
- The PAT creation step is **manual** (GitHub UI) and not committed; it
  belongs in `deploy/argocd/README.md`.

## ADR & dev-log

- Add `docs/adr/0008-gitops-argocd-helm.md` capturing: ArgoCD vs Flux,
  Helm vs Kustomize, Image Updater vs GHA-writes-back, single
  Application vs App-of-apps, no SSO. Follow the format of
  `docs/adr/0007-prod-deploy-oracle-k3s.md`.
- Append one line to `docs/dev-log.md`:
  `2026-XX-XX: Task 07 GitOps — ArgoCD + Helm + Image Updater. main
  push → automatic rollout. <한 줄 인상깊은 트러블슈팅>`.

## What this task explicitly does NOT cover (FAQ)

- "Why not Flux?" — strictly viable; ArgoCD wins on Helm support and
  on JD frequency in the Korean market. ADR 0008 has the side-by-side.
- "Why not Kustomize?" — adds a second tool that overlaps with Helm's
  values.yaml without paying back the complexity at this scale.
- "Why not Spinnaker / Jenkins X?" — operational weight is too high
  for one workload + one operator. ADR 0008 mentions both as
  alternatives considered.
- "Why no Sealed Secrets?" — no real secrets to seal yet. Pick one
  (Sealed Secrets vs SOPS vs external-secrets-operator) when the first
  real secret arrives, in its own task.
- "Why expose ArgoCD UI publicly?" — to demo the project's GitOps
  posture (the UI is part of the portfolio narrative). Behind HTTPS
  + admin password. Switching to private + `kubectl port-forward` is a
  one-line ingress edit if the developer ever wants to reduce attack
  surface.
- "Why no auto-promotion to a stage env?" — there is no stage env.
  ApplicationSet + multi-cluster is its own task (Task 09+).
