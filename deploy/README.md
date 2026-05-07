# `deploy/` — production deployment runbook

This is the operator's guide for ssuAI's production environment:
Spring Boot backend on a single-node **k3s** cluster on **Oracle Cloud
Free Tier ARM Ampere A1** (`ap-seoul-1`), Next.js frontend on **Vercel**,
TLS via **cert-manager + Let's Encrypt**, image registry at **ghcr.io**.

Architecture rationale lives in [`docs/adr/0007-prod-deploy-oracle-k3s.md`](../docs/adr/0007-prod-deploy-oracle-k3s.md).

The directory layout:

```
deploy/
├── docker/
│   └── Dockerfile         # multi-stage Spring Boot image (Temurin 21, ARM64-capable)
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.example.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   └── clusterissuer.yaml
└── README.md              # this file
```

---

## 0. Prerequisites you'll need before starting

| What | Where | Notes |
|---|---|---|
| Oracle Cloud account | [cloud.oracle.com](https://cloud.oracle.com) | Free tier signup needs a valid credit card (no charge). Choose `ap-seoul-1` (Korea Central) as home region — cannot be changed later. |
| duckdns subdomain + token | [duckdns.org](https://duckdns.org) | Sign in with GitHub/Google, claim `ssuai-api` (or any free name), copy the token from the dashboard. |
| Vercel account | [vercel.com](https://vercel.com) | Sign in with GitHub. Free tier covers this project. |
| Local tools | your laptop | `kubectl` (any 1.30+ works), `ssh`, optionally `helm` for Task 07. |

You also need a real email address you don't mind giving Let's Encrypt
(it sends expiration notices). It ends up in the cert's ACME
registration metadata.

---

## 1. Provision the Oracle Cloud VM

In the Oracle Cloud console:

1. **Compute → Instances → Create instance**.
2. Image: **Ubuntu 22.04 LTS aarch64**.
3. Shape: **VM.Standard.A1.Flex**, 4 OCPUs / 24 GB memory (the full
   Always Free allocation; you can split this across two VMs later if
   you want HA, but Task 06 uses one).
4. Networking:
   - Default VCN is fine.
   - Public IPv4 address: yes.
   - Add a public SSH key (your laptop's `~/.ssh/id_ed25519.pub`).
5. Boot volume: 100 GB (well under the 200 GB free pool).
6. Create.

Once the VM is up, find its public IP in the console.

Open the inbound ports `22`, `80`, `443` in the VCN's default security
list (or the subnet's NSG):

| Source | Protocol | Destination port |
|---|---|---|
| `0.0.0.0/0` | TCP | 22 |
| `0.0.0.0/0` | TCP | 80 |
| `0.0.0.0/0` | TCP | 443 |

Ubuntu's UFW also blocks by default. SSH in and:

```bash
ssh ubuntu@<VM_PUBLIC_IP>
sudo ufw allow OpenSSH
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable
```

---

## 2. Point duckdns at the VM

On the duckdns dashboard, set `ssuai-api.duckdns.org` (or whatever name
you claimed) to the VM's public IP. Verify:

```bash
dig +short ssuai-api.duckdns.org
# should print the VM IP
```

Install the duckdns updater on the VM so the IP stays current if the
VM is ever rebuilt with a new IP:

```bash
sudo tee /etc/cron.d/duckdns >/dev/null <<'EOF'
*/5 * * * * root curl -sk -o /dev/null \
  "https://www.duckdns.org/update?domains=ssuai-api&token=<YOUR_TOKEN>"
EOF
sudo chmod 600 /etc/cron.d/duckdns
```

Replace `<YOUR_TOKEN>` and `ssuai-api` with your values. The token is a
secret — keep this file root-owned, never commit it.

---

## 3. Install k3s on the VM

```bash
ssh ubuntu@<VM_PUBLIC_IP>
curl -sfL https://get.k3s.io | sh -
sudo k3s kubectl get nodes      # node should show Ready within ~30s
```

k3s ships with Traefik (ingress) and a built-in service load balancer
("klipper-lb"), which is what binds ports 80/443 to the host. No extra
ingress controller needed.

Copy the kubeconfig to your laptop so you can run `kubectl` locally:

```bash
# on the VM
sudo cat /etc/rancher/k3s/k3s.yaml

# paste into ~/.kube/config on your laptop, then edit:
#   server: https://127.0.0.1:6443
# →
#   server: https://<VM_PUBLIC_IP>:6443
```

Sanity-check from the laptop:

```bash
kubectl get nodes
kubectl get pods -A
```

---

## 4. Install cert-manager

cert-manager runs as a few pods in `cert-manager` namespace and watches
`Ingress` resources for the `cert-manager.io/cluster-issuer` annotation.

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml
kubectl wait --for=condition=available --timeout=120s \
  -n cert-manager deploy/cert-manager deploy/cert-manager-webhook deploy/cert-manager-cainjector
```

---

## 5. Make the ghcr.io image pullable

The CI image-build job (`.github/workflows/ci.yml`, "Backend image
(ghcr.io, ARM64)") pushes to
`ghcr.io/hoeongj/ssuai-backend:<sha>` on every push to `main`. New
ghcr.io packages default to **private**, so the cluster cannot pull
without auth. After the first successful CI run:

1. <https://github.com/hoeongj?tab=packages> → click `ssuai-backend`.
2. **Package settings** → **Change visibility** → **Public**.

(Alternative: keep it private and create a dockerconfigjson-type
Secret + reference it via `imagePullSecrets` in `deployment.yaml`. Not
needed for an MVP — public image is fine.)

---

## 6. Apply the manifests

Edit `deploy/k8s/clusterissuer.yaml` to set the operator email
(`REPLACE_WITH_OPERATOR_EMAIL@example.com` → your real email).

If the duckdns subdomain you claimed is **not** `ssuai-api.duckdns.org`,
sed-replace the host in `deploy/k8s/ingress.yaml`:

```bash
sed -i 's/ssuai-api.duckdns.org/<your-host>/g' deploy/k8s/ingress.yaml
```

Then apply, in order:

```bash
kubectl apply -f deploy/k8s/clusterissuer.yaml
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml          # edit SSUAI_FRONTEND_ORIGIN first
kubectl apply -f deploy/k8s/secret.example.yaml     # placeholder secret; fine for MVP
kubectl apply -f deploy/k8s/service.yaml
kubectl apply -f deploy/k8s/deployment.yaml
kubectl apply -f deploy/k8s/ingress.yaml
```

(Order matters because `Ingress` references the `Service`, the
`Service` references the `Deployment` selectors, and the `Certificate`
created by the Ingress's annotation references the `ClusterIssuer`.)

Watch the rollout:

```bash
kubectl -n ssuai-prod rollout status deploy/ssuai-backend
kubectl -n ssuai-prod get pods,svc,ingress
kubectl get certificate -A
```

The `Certificate` resource takes 30–90s to flip to `READY=True` while
cert-manager solves the ACME HTTP-01 challenge.

---

## 7. Verify

```bash
curl -i https://ssuai-api.duckdns.org/actuator/health
# HTTP/1.1 200 OK
# {"status":"UP"}

curl https://ssuai-api.duckdns.org/api/meals/today | jq .

# CORS allowlist enforced — this should NOT echo Access-Control-Allow-Origin:
curl -I -H "Origin: https://attacker.example" \
  https://ssuai-api.duckdns.org/api/meals/today
```

Browser sanity-check: open `https://ssuai-api.duckdns.org/actuator/health`,
confirm the green padlock and that the cert issuer is "Let's Encrypt".

---

## 8. Connect the Vercel frontend

1. Vercel → **New Project** → import the GitHub repo.
2. Framework preset: Next.js (auto-detected).
3. Root directory: `frontend`.
4. Environment variable: `NEXT_PUBLIC_SSUAI_API_BASE = https://ssuai-api.duckdns.org`.
5. Deploy.
6. Once the deploy is green, copy the production URL
   (e.g. `https://ssuai.vercel.app`) and set it as `SSUAI_FRONTEND_ORIGIN`
   in `deploy/k8s/configmap.yaml`. Re-apply:
   ```bash
   kubectl apply -f deploy/k8s/configmap.yaml
   kubectl -n ssuai-prod rollout restart deploy/ssuai-backend
   ```
   (Rollout restart is needed because `WebCorsProdConfig` reads the
   value at startup.)

---

## 9. Upgrade workflow

Each push to `main` produces a new image at
`ghcr.io/hoeongj/ssuai-backend:<full-sha>`. To deploy a specific commit:

```bash
SHA=<the 40-char commit sha>
kubectl -n ssuai-prod set image deploy/ssuai-backend \
  backend=ghcr.io/hoeongj/ssuai-backend:sha-$SHA
kubectl -n ssuai-prod rollout status deploy/ssuai-backend
```

(GitHub's `docker/metadata-action` writes the SHA tag as `sha-<full>` —
verify the exact tag string with `gh api /users/hoeongj/packages/container/ssuai-backend/versions`
or by browsing the package's "Tags" tab.)

If something is wrong:

```bash
kubectl -n ssuai-prod rollout undo deploy/ssuai-backend
```

ArgoCD GitOps replaces this manual step in **Task 07** — the SHA in
`deployment.yaml` becomes the source of truth and a push to `main`
triggers the rollout automatically.

---

## 10. Common troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Pod stays `ImagePullBackOff` | ghcr.io package is still private | See §5 — flip to public, or add `imagePullSecrets`. |
| `Certificate` stuck `READY=False` | duckdns DNS hasn't propagated, or port 80 blocked | `dig` from a public DNS resolver; check VCN security list + UFW for port 80. |
| Pod boots but health probe fails | `application-prod.yml` couldn't bind `SSUAI_FRONTEND_ORIGIN` | `kubectl logs` will show the `IllegalStateException` from `WebCorsProdConfig`. Set the value on the ConfigMap and `rollout restart`. |
| 502 from the ingress | Pod port name mismatch (`http` vs `8080`) | `kubectl describe svc/ssuai-backend` — `targetPort` should be `http` and the Deployment's `containerPort` should be named `http`. |
| ACME rate-limit hit | Re-issued cert too many times in a week | Wait an hour, or use the staging issuer first when iterating. |

---

## 11. Cost-watching reminders

- Oracle Free Tier: the Always Free ARM allowance is generous but
  technically reclaimable if the account is idle for very long periods.
  Logging into the console once every couple of months is the easiest
  insurance policy.
- Bandwidth: 10 TB/month outbound free — this project will use far
  less, but watch it if the dashboard goes viral.
- Vercel: Hobby tier has soft caps; the 4-card dashboard is well
  under them.

---

## 12. What's next

- **Task 07** — replace the manual `kubectl apply` workflow with
  ArgoCD watching `deploy/k8s/` (or a Helm chart). Push to `main` →
  rollout, no SSH needed.
- **Task 08** — Prometheus + Grafana + Loki in the cluster. Tail logs
  and metrics from a dashboard instead of `kubectl logs`.
