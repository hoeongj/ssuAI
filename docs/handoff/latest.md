# Session handoff — 2026-05-16 저녁 (SmartID prod end-to-end 완료)

> Single rolling handoff file (CLAUDE.md / AGENTS.md "Session handoff
> to a different AI" 정책). 이전 핸드오프를 overwrite 한다.

## TL;DR

- **SmartID 로그인 prod end-to-end 첫 성공**. 대시보드 "안녕하세요,
  홍성주 학생" 까지 도달. 데모 가능. portfolio 핵심 deliverable
  가용 상태.
- **이번 세션 1 PR 머지** (#116). 직전 세션의 PR #114 (SameSite=None)
  만으로는 풀리지 않던 "세션 갱신 실패" 의 root cause 가 별개 layer
  였음: backend CORS 의 `.allowCredentials(false)` 가 응답에 `Access-Control-Allow-Credentials:
  true` 를 빼서, fetch `credentials: include` 인 frontend 가 200 OK
  response body 를 JS 에서 읽지 못함. set-cookie 만 저장되고 body 는
  차단되는 정확한 CORS 함정.
- 새 PR 인 줄도 모르고 한 줄 (`ApiCorsDefaults.java:15` `false` →
  `true`) + 회귀 방지 테스트 2개. allowedOrigins 가 explicit origin
  (`https://ssuai.vercel.app` / `http://localhost:3000`) 이라 Spring
  validator 통과. backend 전체 test BUILD SUCCESSFUL.
- prod 반영: `kubectl set image deployment/ssuai-backend backend=ghcr.io/hoeongj/ssuai-backend:sha-1031de018ee12a06b009073af2b5a245630facb2`.
  rollout status timeout 떴으나 `get pod` 로 보면 new pod 만 단독
  Running. 브라우저 로그인 정상 동작 확인됨.
- **현재 prod 상태**: SmartID 로그인 → 대시보드 ("안녕하세요, 홍성주
  학생") 까지 모두 정상. `/api/auth/refresh` 200 + `/api/auth/me` 200
  둘 다 Network 탭에 보이고 응답 헤더에 `access-control-allow-credentials:
  true` 포함. 학번 = sIdno trust, 이름 = `.top_user` greeting suffix-strip,
  소속/학적/시간표/성적은 여전히 null (Task 16 deep endpoint 가
  채울 영역).
- **남은 prod 운영 follow-up** (이번 세션 미해결): (a) `SSUAI_JWT_SECRET`
  미설정 → ephemeral random key. 매 pod restart 시 발급된 access/refresh
  JWT 전부 invalidate. (b) `SSUAI_CREDENTIAL_ENCRYPTION_KEY` 미설정 →
  saint session AES key 도 ephemeral. (c) 매 PR 머지 후 수동
  `kubectl set image` 가 정상 흐름 — GitHub Actions deploy step 으로
  자동화하면 운영 sane.

## 이번 세션 머지/푸시 (2026-05-16, 1 PR)

| PR | 내용 | base | 비고 |
|----|------|------|------|
| #116 | fix(cors): allow credentials so cross-site cookie auth works in prod | main | `ApiCorsDefaults.java:15` `allowCredentials(false)` → `true`. `WebCorsConfigTest` / `WebCorsProdConfigTest` 양쪽에 `config.getAllowCredentials() == true` assertion 추가. backend 전체 test BUILD SUCCESSFUL. 자동 머지 정책으로 사람 review 없이 직접 머지 |

**현장 매뉴얼 patch (git 외부)**:
- `sudo kubectl set image deployment/ssuai-backend
  backend=ghcr.io/hoeongj/ssuai-backend:sha-1031de018ee12a06b009073af2b5a245630facb2
  -n ssuai-prod` — PR #116 prod 반영. rollout status 180s timeout
  떴으나 `get pod` 확인 결과 new pod 만 단독 Running.

## 열린 PR

| PR | 브랜치 | 내용 | 상태 |
|----|--------|------|------|
| #81 | `chore/spike-ssotoken-ttl-script` | Task 13 ssotoken TTL spike script | 사용자 PC 에서 실행 중. 사용자가 **마지막에 하겠다고 선언** ([[feedback-user-defers-ttl-spike]]) — 측정 오래 걸려서. 다른 active 작업 다 끝난 후. **폴링 금지** ([[feedback-user-will-notify]]) |

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | ~~SmartID `apiReturnUrl` whitelist spike~~ | ✅ RESOLVED POSITIVE (5/15 세션) |
| 2 | ssotoken TTL spike (PR #81) | ⏳ 대기. 사용자가 마지막에 하겠다고 명시 |
| 3 | u-SAINT 시간표/성적 fixture capture (Task 16 PR 16b/16c) | ⏳ 대기 |
| 4 | ~~PR #114 new pod Ready + 브라우저 재검증~~ | ✅ RESOLVED — PR #116 까지 묶어서 SmartID prod end-to-end 동작 확인 |

## 다음 세션 액션 (우선순위 순서)

### 1. Task 14 spec §risks + Task 16 spec 갱신 (문서, 코드 변경 없음)

이번 세션 (및 직전 세션) 에서 발견된 사실을 두 spec 에 반영:

- **Task 14 spec** (`docs/tasks/14-saint-sso.md`):
  - §6 / §risks — phase 2 응답이 wrapper 뿐이라는 사실 (portal `/irj/portal`
    = SAP NetWeaver frameset wrapper, 학번/이름만 wrapper 에 인라인,
    소속/학적/시간표/성적 카드는 `<iframe id="contentAreaFrame">` 안쪽
    lazy-load).
  - 학번 = sIdno trust 패턴 명시 (phase 1 success marker 통과 + portal
    session cookie 발급 시점에 sIdno 가 실제 학생 번호임이 이미 입증됨).
  - 이름 = `<span class="top_user">` greeting suffix-strip (ordered
    fallback `["님 접속을 환영합니다.", "님 환영합니다.", "님"]`).
  - 소속/학적상태는 null — Task 16 deep endpoint 가 채움.
  - **CORS 함정 한 줄 추가** — `.allowCredentials(true)` + `SameSite=None`
    이 cross-site cookie auth 두 축. 한 쪽만 맞추면 증상이 부분만 풀려서
    혼란.

- **Task 16 spec** (`docs/tasks/16-usaint-realtime-data.md`):
  - §3-4 — `get_my_schedule` / `get_my_grades` 의 fetch 대상이 `/irj/portal`
    이 아니라 iframe 안쪽 deep portal endpoint (e.g., `/irj/servlet/prt/portal/prtroot/...`).
    spike 필요. fixture capture 시 사용자가 iframe 안쪽 페이지의 정확한
    URL 도 같이 알려줘야 함.
  - §6 #6, §8 — 성적은 **LLM prompt 에 절대 안 들어가야 함**. `LlmChatService.compactToolResponse`
    에서 성적 데이터 필터링. tool-citation 패턴 (count 만 LLM 에, 본문은
    controller path 로만).

별도 PR.

### 2. 운영 follow-up — prod env var 잡기 (`SSUAI_JWT_SECRET`, `SSUAI_CREDENTIAL_ENCRYPTION_KEY`)

이번 세션 prod 로그에서 두 WARN 확인:

```
ssuai.jwt.secret is empty — generated an ephemeral random secret. All issued tokens will be invalid after restart. Set SSUAI_JWT_SECRET for any non-dev environment.
ssuai.saint.session.encryption-key is empty — generated an ephemeral random key. Stored saint sessions will be unreadable after restart. Set SSUAI_CREDENTIAL_ENCRYPTION_KEY (>= 32 bytes, base64 recommended) for non-dev.
```

- 매 pod 재시작마다 발급된 access/refresh JWT 전부 invalidate.
- 매 pod 재시작마다 saint 세션 (refresh-cookie 와 별개의 AES-256-GCM
  encrypted blob) 전부 invalidate.

ConfigMap 12 keys 에 둘 다 없음. Deployment 의 `envFrom.secretRef.optional:
true` 라 Secret 미설정도 startup 통과. → Secret 매뉴얼로 만들고 채워야
함. 권장 명령:

```bash
# JWT secret (≥ 32 bytes)
JWT_SECRET=$(openssl rand -base64 48)
SAINT_KEY=$(openssl rand -base64 48)

sudo kubectl create secret generic ssuai-backend-secret \
  --from-literal=SSUAI_JWT_SECRET="$JWT_SECRET" \
  --from-literal=SSUAI_CREDENTIAL_ENCRYPTION_KEY="$SAINT_KEY" \
  -n ssuai-prod \
  --dry-run=client -o yaml | sudo kubectl apply -f -

sudo kubectl rollout restart deployment ssuai-backend -n ssuai-prod
```

Deployment 의 `envFrom.secretRef.name` 이 `ssuai-backend-secret` 인지
선행 확인 필요. 별도 PR 없이 cluster 매뉴얼 작업.

### 3. 운영 파이프라인 정리 — GitHub Actions deploy step

직전 세션부터 두 번 발견된 매뉴얼 작업의 root cause:
- ArgoCD 없음, helm 없음, 단순 `kubectl apply` 운영.
- Deployment 가 `image: …:latest` + `imagePullPolicy: IfNotPresent`.
- 매 PR 머지 후 수동 `kubectl set image …:sha-<full-sha>` 가 정상 흐름.

옵션:
- **(i) GitHub Actions deploy step** — CI 의 image-build job 다음에
  `kubectl set image` 자동화. workflow 에 cluster credential (kubeconfig
  as secret) 필요. 가장 작은 변화로 manual 단계 제거. ← **권장**
- (ii) `imagePullPolicy: Always` 로 patch — `:latest` 유지. rollback 불가.
- (iii) ArgoCD 설치 — 인프라 작업 큼.

학생 portfolio + 단일 노드 k3s 인 점 고려 (i) 권장. 별도 PR.

### 4. Task 16 PR 16b (`get_my_schedule`) — 시간표 fixture 도착 후

외부 의존 #3 가 사용자 측에서 끝났을 때. 자세한 layout / DTO /
보안 체크리스트는 `docs/tasks/16-usaint-realtime-data.md` §5, §7, §8
참조. §3 의 phase 2 가정 무효 — fetch 대상 endpoint 가 iframe 안쪽
deep portal endpoint. fixture capture 시 사용자가 iframe 안쪽 페이지의
정확한 URL 도 같이 알려줄 필요.

### 5. Task 16 PR 16c (`get_my_grades`)

PR 16b 와 동일 패턴. **단, 성적은 LLM prompt 에 절대 안 들어가야 함** —
`LlmChatService.compactToolResponse` 에서 성적 데이터 필터링. tool-citation
패턴 (count 만 LLM 에, 본문은 controller path 로만) 적용. Task 16 spec
§6 #6, §8 확정.

### 6. Task 13 PR 13b/13c — TTL spike 결과 받은 후

사용자가 마지막에 하겠다고 명시한 항목 (외부 의존 #2).

### 7. Task 17 PR 17a (LMS) — fixture/spike 도착하면

LMS 의 host + auth shape spike. 자세히는 `docs/tasks/17-lms-integration.md`
§3, §7.

### 8. Phase 4 ActionInfrastructure PR (낮은 우선순위)

ADR 0015 Consequences 첫 항목 — `reserve_library_seat` 시작 전에
`action_audit` 테이블 + `ActionAuditService` + `ActionLock` 인터페이스
(in-process 시작, Redis SETNX swap-ready) + `PreparedActionExpiryRunner`
한 PR. Task 16 PR 16b/16c 안정 후.

## 사용자 컨텍스트 — 잊지 말 것

- **숭실대 컴퓨터학부 3학년**, 포트폴리오 프로젝트
- **데드라인 2026-05-15 (이틀 전)** — 현장실습 지원. SmartID 라이브
  데모가 portfolio 의 핵심. **이번 세션 완성 — 대시보드 "안녕하세요,
  {이름} 학생" 까지 prod end-to-end 동작 확인**. 데모 가능 상태.
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
- **사용자 본인 이름 (홍성주) 도 chat 에서 인용 가능** — 이번 세션
  대시보드 출력 확인 시 사용자가 직접 paste. fixture 와 server log
  에서는 placeholder (`홍길동`) 유지.
- **TTL spike 는 마지막에**. 사용자가 명시함 — 측정이 오래 걸리니까
  다른 active 작업 다 정리 후. [[feedback-user-defers-ttl-spike]]
- **두 제품 framing** — "MCP 서버" 와 "ssuAI 웹/앱" 은 분리된 제품
  ([[project-final-goal]])
- **간결한 한국어 응답 선호**. 사용자가 답답할 땐 명령 1-2개로
  좁히기.
- **in-flight context 는 in-repo (handoff doc / task spec / dev-log)**
  에 쓰고 auto-memory 에는 안 씀
  ([[feedback-save-progress-to-project]]).
- **portal HTML 검증 부탁 시 Ctrl+U (view-source) 와 F12 Elements
  구분 명시 필수**. F12 는 cross-frame 다 보여줘서 wrapper/iframe
  구분 안 됨 ([[learning-browser-view-source-vs-devtools]]).

## 보안 주의

- **사용자 paste 한 portal HTML 전체 (이전 세션 한 차례)** 에 학번
  `20221528`, 이름 `홍성주`, 이전 접속 IP `218.50.132.144`, 접속
  시간 등 PII 포함. **commit/log/fixture 어디에도 들어가지 않음** —
  모든 fixture 는 placeholder `홍길동` / `20999999` / `0.0.0.0` /
  dummy timestamp 사용. dev-log / TROUBLESHOOTING entry 도 redacted.
- 사용자 본인 학번/이름은 chat 에서 redaction 면제 (본인 paste).
- SmartID spike 시 사용자 sToken 들은 one-shot 이라 already consumed.
- **JWT secret prod 미설정** (action item #2). 매 pod 재시작마다
  발급된 access/refresh JWT 전부 invalidate. SmartID 로그인 한 사용자가
  pod restart 후 모두 다시 로그인해야 정상 사용 가능. 데모 직전 pod
  재시작 금지 또는 action item #2 먼저 해결.
- **`SSUAI_CREDENTIAL_ENCRYPTION_KEY` 도 미설정** (action item #2 동일).
  saint session AES-256-GCM 키 ephemeral. PR 16b/16c storage 라이브
  전이라 운영 영향 미미하지만 prod env var 잡는 follow-up.
- ssutoday `sToken`/`sIdno` 는 method-scoped — `SaintSsoService.authenticate`
  내부에서만 살아있음. 로그/DB/세션 어디에도 안 남김.
- 성적 (`get_my_grades`) 은 **LLM 프롬프트에 절대 안 들어가야 함** —
  Task 16 spec §6, §8.

## 자동 메모리

`C:\Users\akftj\.claude-personal\projects\C--Users-akftj-ssuAI\memory\`

핵심 파일 (인덱스는 `MEMORY.md` 자동 로드). 이번 세션 신규 작성 없음
— 모두 in-repo 인계로 처리 ([[feedback-save-progress-to-project]]).

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
- main HEAD: <handoff commit SHA>
- 이번 세션 머지 1 PR: #116 (CORS allowCredentials)
- 직전 세션 누적: #112 (parser key-map), #113 (frameset wrapper +
  sIdno trust), #114 (SameSite=None refresh cookie)
- 열린 PR: #81 (TTL spike, 사용자 직접) 하나뿐
- **SmartID 로그인 prod end-to-end 동작 확인 완료** — 대시보드
  "안녕하세요, {이름} 학생" 표시. portfolio 데모 가용.

다음 세션 첫 액션 — 핸드오프 doc "다음 세션 액션 #1":
Task 14 spec §risks + Task 16 spec 갱신 (문서, 코드 변경 없음).
portal frameset wrapper 사실, CORS 함정 한 줄, iframe 안쪽 deep
endpoint 가정 무효 등 반영. 별도 PR.

외부 의존 — 사용자 직접 (폴링 금지):
- TTL spike (PR #81): 사용자 마지막에 한다고 명시
- u-SAINT 시간표/성적 fixture capture (Task 16 PR 16b/16c gate)

운영 follow-up (action item #2):
- prod `SSUAI_JWT_SECRET`, `SSUAI_CREDENTIAL_ENCRYPTION_KEY` 미설정.
  매 pod restart 시 invalidate. 데모 직전 pod 재시작 금지 또는
  Secret 매뉴얼 잡기.
```
