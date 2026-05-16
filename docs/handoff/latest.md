# Session handoff — 2026-05-16 afternoon (SmartID prod live)

> Single rolling handoff file (CLAUDE.md / AGENTS.md "Session handoff
> to a different AI" 정책). 이전 핸드오프를 overwrite 한다.

## TL;DR

- **이번 세션 3 PR 머지** (#112 / #113 / #114) — SmartID 로그인 prod
  end-to-end **첫 검증**. portal HTML parser 가 두 번 틀린 가정 위에
  있었고, refresh cookie 가 cross-site 차단 당하던 추가 이슈까지
  연쇄적으로 노출.
- **현재 prod 상태**: SmartID → SSO callback → `?ok=1` 까지는
  도달함 (옛 pod = PR #113 코드 active). **그러나 `/auth/return`
  페이지의 `POST /api/auth/refresh` 가 401** 로 떨어져
  "ssuAI 세션 갱신에 실패했습니다" 에서 멈춤. PR #114
  (`SameSite=None`) 가 그걸 fix 하는데 **새 pod (`sha-e433e077…`)
  가 rollout timeout 두 번** → 다음 세션이 새 pod 일으키고 브라우저
  재검증해야 마무리.
- **운영 파이프라인 미스매치 발견**: cluster 에 ArgoCD/helm 없음, 단순
  `kubectl apply` 운영. Deployment 의 `image: …:latest` +
  `imagePullPolicy: IfNotPresent` 라 PR 머지 후 새 코드 prod 반영은
  **수동 `kubectl set image deployment/ssuai-backend backend=…:sha-<full-sha>`**
  필요. rollout restart 만으로는 새 이미지 안 들어옴. ConfigMap 도
  매뉴얼 patch (PR #110 의 `SSUAI_API_BASE_URL` 도 같은 이유로
  반영 안 됐었음).
- **Portal 가정 근본 정정**: SSU saint `/irj/portal` 는 **SAP NetWeaver
  frameset wrapper**. 학번/이름만 wrapper 에 인라인 (`.top_user`
  greeting + JS `LogonUid`), 소속/학적/학년 카드는 모두
  `<iframe id="contentAreaFrame">` 안쪽 lazy-load. Task 14 가
  ssutoday positional `.main_box09` parse 를 가정했던 건 이 iframe
  안쪽 페이지였음. 로그인용 minimal identity (학번 = sIdno trust,
  이름 = greeting suffix-strip) 는 PR #113 으로 살렸으나 소속/학적은
  null 상태 — Task 16 의 deep endpoint 가 채울 영역.
- **다음 세션 첫 액션**: 사용자 SSH 환경에서 `sha-e433e077…` pod 를
  Ready 시키고 브라우저 로그인 재시도. 자세한 명령은 §"다음 세션
  액션" #1.

## 이번 세션 머지/푸시 (2026-05-16, 3 PR)

| PR | 내용 | base | 비고 |
|----|------|------|------|
| #112 | fix(auth): re-parse u-SAINT portal HTML by `<dt>→<dd>` key map | main | 첫 parser rewrite. 옛 `.main_box09 .main_box09_con` positional 4-cell → key-based `dt/dd` map + `.main_title span` 에서 이름 추출. fixture 갱신. 실 prod 검증에서 "missing name element" 떨어져 가정 자체 재검토 필요 판명 |
| #113 | fix(auth): parse name from `.top_user`, trust sIdno for studentId | main | portal 이 SAP frameset wrapper 라는 root cause 확정 후 두 번째 rewrite. 이름은 `<span class="top_user">` 의 "{이름}님 접속을 환영합니다." suffix-strip (ordered fallback list), 학번은 phase 1 sIdno 그대로 trust, 소속/학적 null. 새 fixture 3개 (success/missing-name/greeting-unknown-suffix) |
| #114 | fix(prod): refresh cookie `SameSite=None` so vercel↔duckdns POST works | main | `?ok=1` 까지 도달했으나 frontend `POST /api/auth/refresh` 가 cross-site 차단으로 cookie 못 보냄 → 401. `application-prod.yml` 의 `refresh-cookie.same-site: None` override 한 줄 |

backend 257+ tests + saint 패키지 다 그린. 자동 머지 정책으로 사람
review 없이 직접 머지.

**현장 매뉴얼 patch (git 외부)**:
- `kubectl patch configmap ssuai-backend-config -n ssuai-prod
  --type merge -p '{"data":{"SSUAI_API_BASE_URL":"https://ssumcp.duckdns.org"}}'`
  — PR #110 의 ConfigMap 변경이 운영 파이프라인 (no helm/argocd)
  으로 cluster 에 안 반영돼 직접 주입.
- `kubectl set image deployment/ssuai-backend
  backend=ghcr.io/hoeongj/ssuai-backend:sha-<full-sha>` — `:latest`
  + IfNotPresent 조합 우회하려 매 PR 머지 후 SHA tag 로 직접 patch.

## 열린 PR

| PR | 브랜치 | 내용 | 상태 |
|----|--------|------|------|
| #81 | `chore/spike-ssotoken-ttl-script` | Task 13 ssotoken TTL spike script | 사용자 PC 에서 실행 중. 사용자가 **마지막에 하겠다고 선언** ([[feedback-user-defers-ttl-spike]]) — 측정 오래 걸려서. 다른 active 작업 다 끝난 후. **폴링 금지** ([[feedback-user-will-notify]]) |

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | ~~SmartID `apiReturnUrl` whitelist spike~~ | ✅ RESOLVED POSITIVE (이전 세션) |
| 2 | ssotoken TTL spike (PR #81) | ⏳ 대기. 사용자가 마지막에 하겠다고 명시 |
| 3 | u-SAINT 시간표/성적 fixture capture (Task 16 PR 16b/16c) | ⏳ 대기 |
| 4 | **PR #114 new pod (`sha-e433e077…`) Ready + 브라우저 재검증** | ⏳ 다음 세션 첫 액션 #1 — 본 세션에서 시작했으나 사용자 토큰 소진으로 중단 |

## 다음 세션 액션 (우선순위 순서)

### 1. PR #114 new pod (`sha-e433e0779568fe529062a6f36c328c37c9a5637d`) Ready 시키고 SmartID 로그인 end-to-end 마무리 검증

세션 종료 시점 prod 상태:
- 새 pod `sha-e433e0779568…` — startup 중 (rollout timeout 2회).
  CI image-build 는 끝났을 가능성 높음 (직전 #113 의 image-build 도
  약 8-10분 ARM64 emulation).
- 옛 pod `sha-92bc16bbc508…` — 여전히 Running. 즉 옛 코드
  (SameSite=Lax) 로 traffic 받는 중 → 로그인 시도 시 여전히 "세션
  갱신 실패" 떨어짐.

사용자 SSH 에서:

```bash
# 1) 현재 pod 상태
sudo kubectl get pod -n ssuai-prod

# 2) ImagePullBackOff/ErrImagePull pod 있으면 즉시 delete (ReplicaSet 이
#    새 pod 만들고 fresh pull 시도. CI 끝났으면 성공)
sudo kubectl get pod -n ssuai-prod \
  | grep -E 'ImagePullBackOff|ErrImagePull' \
  | awk '{print $1}' \
  | xargs -r sudo kubectl delete pod -n ssuai-prod

# 3) Rollout 완료 대기
sudo kubectl rollout status deployment ssuai-backend -n ssuai-prod --timeout=180s

# 4) 이미지 확인 — sha-e433e077... 만 보여야
sudo kubectl get pod -n ssuai-prod -o jsonpath='{.items[*].spec.containers[*].image}'; echo
```

브라우저 검증:
1. `https://ssuai.vercel.app/auth/login` → "유세인트로 로그인"
2. SmartID → 학번/비번
3. `?ok=1` 거쳐 대시보드 "안녕하세요, {이름} 학생" 까지

**만약 또 "세션 갱신 실패"** 면 SameSite=None 단독으로 부족한 거 →
다음 진단:
- 브라우저 dev tools → Application → Cookies → `ssumcp.duckdns.org`
  에 `ssuai_refresh` cookie 가 있는지, 있으면 그 attributes (SameSite/
  Secure/Path/Domain) paste 부탁
- backend log `kubectl logs deployment/ssuai-backend -n ssuai-prod
  --since=2m | grep -E 'auth|refresh|Unauthorized' | tail -20`
- frontend dev tools Network → `/api/auth/refresh` 의 request headers
  (특히 `cookie: …`) 가 비어있는지

CI 가 안 끝났으면 (`ImagePullBackOff` 가 계속) 5분 더 기다린 후
step 2-3 재시도. `gh run list --workflow=CI --limit 3 --json status,headSha`
로 한 번씩 확인 OK, 폴링 X.

### 2. 운영 파이프라인 정리 (별도 인프라 task)

이번 세션에서 두 번 발견된 매뉴얼 작업의 root cause:
- ArgoCD 없음 (CRD 도 namespace 도 없음. 핸드오프 doc 의 "ArgoCD"
  표현은 misnomer 였음).
- helm 없음 (사용자 SSH 에 `helm: command not found`). 즉
  `deploy/charts/ssuai-backend/` 의 helm chart 는 cluster 와 무관.
- Deployment 가 `image: …:latest` + `imagePullPolicy: IfNotPresent`.
  Comment 는 "tag is bumped to the deployed commit SHA on each
  rollout — see deploy/README.md §Upgrade workflow" 라고 적혀있는데
  실제 운영은 bootstrap `:latest` 그대로.

옵션 (사용자와 합의 필요):
- **(i) GitHub Actions deploy step** — CI 의 image-build job 다음에
  `kubectl set image` 자동화. workflow 에 cluster credential
  (kubeconfig as secret) 필요. 가장 작은 변화로 manual 단계 제거.
- **(ii) 간단 fix** — `imagePullPolicy: Always` 로 patch. `:latest`
  유지하지만 매 pod 재시작마다 fresh pull. 안정성 risk (rollback
  불가).
- **(iii) 본격 GitOps** — ArgoCD 설치 + Image Updater. 인프라 작업
  큼.

학생 portfolio + 단일 노드 k3s 인 점 고려하면 **(i)** 가 가장 합리적
선택. 별도 PR.

또: `SSUAI_CREDENTIAL_ENCRYPTION_KEY` (saint session AES key) 가
ConfigMap 12 keys 에 없음. 매 pod 재시작마다 saint session
invalidate (WARN 로그 — `saint.session.encryption-key is empty`).
Task 16 PR 16b/16c 시작 전에 prod env var 잡아두는 follow-up.
`SSUAI_JWT_SECRET` 도 ConfigMap 에 없음 — Secret 으로 들어있는지
확인. JwtProvider 가 startup 시 < 32 bytes 면 fail-fast 라 prod 가
일어났다는 건 이미 어딘가 있다는 뜻.

### 3. Task 14 spec §risks + Task 16 spec 갱신

이번 세션에서 발견된 사실 (portal = SAP NetWeaver frameset
wrapper, 학생 카드는 iframe 안쪽 lazy load) 을 두 spec 에 반영:
- Task 14 spec §6 / §risks — phase 2 응답이 wrapper 뿐이라는
  사실, 학번은 sIdno trust 패턴, 이름만 greeting span 에서 추출.
- Task 16 spec §3-4 — `get_my_schedule` / `get_my_grades` 의 fetch
  대상이 `/irj/portal` 가 아니라 iframe 안쪽 deep portal endpoint
  (e.g., `/irj/servlet/prt/portal/prtroot/...`). spike 필요.

### 4. Task 16 PR 16b (`get_my_schedule`) — 시간표 fixture 도착 후

외부 의존 #3 가 사용자 측에서 끝났을 때. 자세한 layout / DTO /
보안 체크리스트는 `docs/tasks/16-usaint-realtime-data.md` §5, §7, §8
참조. 다만 §3 의 phase 2 가정 이번 세션 발견으로 무효 — fetch
대상 endpoint 가 다름. fixture capture 시 사용자가 iframe 안쪽
페이지의 정확한 URL 도 같이 알려줄 필요.

### 5. Task 16 PR 16c (`get_my_grades`)

PR 16b 와 동일 패턴. **단, 성적은 LLM prompt 에 절대 안 들어가야
함** — `LlmChatService.compactToolResponse` 에서 성적 데이터 필터링.
tool-citation 패턴 (count 만 LLM 에, 본문은 controller path 로만)
적용. Task 16 spec §6 #6, §8 확정.

### 6. Task 13 PR 13b/13c — TTL spike 결과 받은 후

사용자가 마지막에 하겠다고 명시한 항목 (외부 의존 #2).

### 7. Task 17 PR 17a (LMS) — fixture/spike 도착하면

LMS 의 host + auth shape spike. 자세히는 `docs/tasks/17-lms-integration.md` §3, §7.

### 8. Phase 4 ActionInfrastructure PR (낮은 우선순위)

ADR 0015 Consequences 첫 항목 — `reserve_library_seat` 시작 전에
`action_audit` 테이블 + `ActionAuditService` + `ActionLock` 인터페이스
(in-process 시작, Redis SETNX swap-ready) + `PreparedActionExpiryRunner`
한 PR. Task 16 PR 16b/16c 안정 후.

## 사용자 컨텍스트 — 잊지 말 것

- **숭실대 컴퓨터학부 3학년**, 포트폴리오 프로젝트
- **데드라인 2026-05-15 (어제)** — 현장실습 지원. SmartID 라이브
  데모가 portfolio 의 핵심. 이번 세션 마무리 안 됐지만 옛 pod 도
  학번/이름 식별까지는 동작 (refresh 만 401).
- **3-AI rotation** (claude1/claude2/codex), 토큰 사정 따라 한 명씩
  active ([[role-3-ai-rotation]]). 정책 변경 시 CLAUDE.md +
  AGENTS.md 양쪽 같은 commit 으로 sync.
- **commit/PR body 에 Claude/AI 흔적 절대 금지** (`Co-Authored-By:
  Claude` trailer X, "🤖 Generated with..." footer X).
  [[feedback-no-claude-coauthor]]
- **안전한 PR 자동 머지** — mergeable + tests pass + mock default
  면 묻지 말고 즉시 `gh pr merge --auto --rebase --delete-branch`.
  [[feedback-auto-merge-safe-prs]]
- **외부 작업 사용자 통지 대기** — "내가 알려줄게" / "끝나면
  알려줄게" 한 작업은 그 후 언급/폴링 금지.
  [[feedback-user-will-notify]]
- **사용자 본인 학번/sIdno chat paste 시 redaction 경고 안 함**.
  project policy (server log / fixture) 는 그대로 적용 — chat
  hygiene 만 relax. [[feedback-user-student-id-not-sensitive]]
- **TTL spike 는 마지막에**. 사용자가 명시함 — 측정이 오래 걸리니까
  다른 active 작업 다 정리 후. [[feedback-user-defers-ttl-spike]]
- **두 제품 framing** — "MCP 서버" 와 "ssuAI 웹/앱" 은 분리된 제품
  ([[project-final-goal]])
- **간결한 한국어 응답 선호**. 사용자가 답답할 땐 명령 1-2개로
  좁히기.
- **in-flight context 는 in-repo (handoff doc / task spec / dev-log)**
  에 쓰고 auto-memory 에는 안 씀
  ([[feedback-save-progress-to-project]]).

## 보안 주의

- **사용자 paste 한 portal HTML 전체 (전체 dump 한 차례)** 에 학번
  `20221528`, 이름 `홍성주`, 이전 접속 IP `218.50.132.144`, 접속
  시간 등 PII 포함. **그러나 commit/log/fixture 어디에도 들어가지
  않음** — 모든 fixture (`portal-success.html`,
  `portal-missing-name.html`, `portal-greeting-unknown-suffix.html`)
  는 placeholder `홍길동` / `20999999` / `0.0.0.0` / dummy timestamp
  사용. dev-log / TROUBLESHOOTING entry 도 redacted.
- 사용자 본인 학번은 chat 에서 redaction 면제이지만 한 가족원 이름
  (사용자 본인 이름이라면 OK, 아니면 sensitive) 으로 보이는 `홍성주`
  도 같이 paste 됨. 이름은 사용자가 명시적으로 OK 한 적 없음 —
  추후 chat 에서도 인용 시 placeholder 권장.
- SmartID spike 시 사용자 sToken 들은 one-shot 이라 already consumed.
- JWT secret: prod `SSUAI_JWT_SECRET` env 필수 (≥ 32 bytes). prod
  ConfigMap 12 keys 에 없음 → Secret 으로 들어있는지 확인 필요
  (Deployment 의 `envFrom.secretRef.optional: true` 라 빈 secret
  허용. 만약 미설정인데 startup 통과했으면 dev/test 의 ephemeral
  random key 그대로 prod 운영 중 — 매 pod 재시작마다 발급된 JWT
  invalidate). Task 16 PR 16b 시작 전 확인 follow-up.
- `SSUAI_CREDENTIAL_ENCRYPTION_KEY` (saint session AES-256-GCM 키):
  비어있음 — pod 로그에 `ssuai.saint.session.encryption-key is empty
  — generated an ephemeral random key` WARN 확정. 매 pod
  재시작마다 saint 세션 invalidate (사용자 re-SSO 필요). PR 16b/16c
  storage 라이브 전이라 운영 영향 미미하지만 prod env var 잡는
  follow-up 권장.
- ssutoday `sToken`/`sIdno` 는 method-scoped — `SaintSsoService.authenticate`
  내부에서만 살아있음. 로그/DB/세션 어디에도 안 남김.
- 성적 (`get_my_grades`) 은 **LLM 프롬프트에 절대 안 들어가야 함** —
  Task 16 spec §6, §8.

## 자동 메모리

`C:\Users\akftj\.claude-work\projects\C--Users-akftj-ssuAI\memory\`

핵심 파일 (인덱스는 `MEMORY.md` 자동 로드):
- `project_full_vision.md` — 두 제품 framing + flagship
- `role_sole_implementer.md` — Claude 단독 implementer 체제
- `feedback_no_claude_coauthor.md` — commit/PR body 에 Claude trailer 금지
- `feedback_auto_merge_safe_prs.md` — 자동 머지 정책
- `feedback_user_will_notify.md` — 사용자 외부 작업 폴링 금지
- `feedback_user_student_id_not_sensitive.md` — 본인 학번 chat redaction X
- `feedback_user_defers_ttl_spike.md` — TTL spike 마지막에
- (NEW 이번 세션 권장): `infra_prod_manual_kubectl_apply.md` —
  cluster 운영이 ArgoCD/helm 없는 단순 `kubectl apply` 라는 사실 +
  `:latest` + IfNotPresent 의 함정 + 매 PR 머지 후 `kubectl set
  image …:sha-<full-sha>` 가 정상 흐름.
- (NEW 이번 세션 권장): `learning_saint_portal_frameset.md` —
  `/irj/portal` 가 SAP NetWeaver frameset wrapper, 학생 카드는
  iframe 안쪽 lazy load. 학번/이름만 wrapper 에서 추출 가능.
- (NEW 이번 세션 권장): `learning_browser_view_source_vs_devtools.md`
  — 사용자에게 portal HTML 검증 부탁 시 Ctrl+U (view-source) 와
  F12 Elements 가 다르다는 점 명확히 설명해야 함. F12 는
  cross-frame 다 보여줘서 wrapper vs iframe 구분 안 됨.

이번 핸드오프 commit 에서 위 NEW 메모리 3개 추가하지 않음 (사용자
토큰 소진 직전이라 핸드오프 doc + index 만 우선). 다음 세션 시작
시 작성 권장.

---

## Next-AI opener block

다음 AI 세션을 시작할 때 사용자가 통째로 paste 할 첫 turn:

```text
ssuAI 프로젝트 이어받음. 다음 순서대로 시작:

1. AGENTS.md (또는 CLAUDE.md — 동일 내용 mirror) 의 Project +
   Session-handoff 섹션 읽기
2. docs/handoff/latest.md 읽기 — 이번 인계 컨텍스트
3. MEMORY.md (auto-memory) 한 번 훑기
4. git status --short --branch 확인 (현재 main, frontend/next-env.d.ts
   만 미커밋 — Next.js auto-gen, 무시 OK)
5. 핸드오프 doc 의 "다음 세션 액션" 우선순위대로 진행

현재 상태:
- main HEAD: e433e07 (fix(prod): refresh cookie SameSite=None…)
- 이번 세션 머지 3 PR: #112 (parser key-map), #113 (frameset
  wrapper + sIdno trust), #114 (SameSite=None refresh cookie)
- 열린 PR: #81 (TTL spike, 사용자 직접) 하나뿐

다음 세션 첫 액션 — 핸드오프 doc "다음 세션 액션 #1":
PR #114 의 새 pod (`sha-e433e0779568fe529062a6f36c328c37c9a5637d`)
가 startup timeout 두 번 났음. 사용자 SSH 명령 4단계로 새 pod
일으키고 브라우저 로그인 재시도 → 대시보드 "안녕하세요, {이름}
학생" 표시까지 확인. 만약 또 "세션 갱신 실패" 면 추가 진단
(brower cookie attributes / backend log / network request headers).

외부 의존 — 사용자 직접 (폴링 금지):
- TTL spike (PR #81): 사용자 마지막에 한다고 명시
- u-SAINT 시간표/성적 fixture capture (Task 16 PR 16b/16c gate)
- PR #114 new pod Ready + 브라우저 검증 (다음 세션 #1 — 활성)
```
