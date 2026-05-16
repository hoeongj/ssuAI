# Session handoff — 2026-05-16 야간 (SmartID prod live + Task 16 spec 완전 잠금)

> Single rolling handoff file (CLAUDE.md / AGENTS.md "Session handoff
> to a different AI" 정책). 이전 핸드오프를 overwrite 한다.

## TL;DR

- **SmartID 로그인 prod end-to-end 완전 동작 + portfolio 데모 가용**.
  대시보드 "안녕하세요, 홍성주 학생" 까지 확인. ([[feedback-own-auth-values-not-sensitive]]
  로 본인 이름 chat 인용 OK)
- **Task 16 PR 16a (spike + spec) 100% 완료** — 시간표 (`get_my_schedule`)
  + 성적 (`get_my_grades`) 모두 endpoint / host / 인증 / CSRF / 응답 shape /
  fetch sequence 까지 spec 에 명문화. 다음 세션 첫 액션 = PR 16b 의 실
  코드 (connector + service + controller + MCP tool).
- **이번 세션 9 PR 머지**: SmartID prod fix (#116) + handoff/spec roll
  (#117/#118/#119) + Task 16 PR 16a 5건 (#120-#124).
- **외부 작업 두 가지 완료**: (a) prod Secret 매뉴얼 (`ssuai-backend-secrets`,
  plural — manifest 와 매칭) 으로 `SSUAI_JWT_SECRET` + `SSUAI_CREDENTIAL_ENCRYPTION_KEY`
  설정 → WARN 두 줄 사라짐. (b) Secret 회전 1회 (echo 로 노출된 값
  교체). 매 pod 재시작마다 세션 invalidate 되던 문제 sealed.
- **새 feedback memory**: 사용자 본인 인증 값 (JWT secret / encryption
  key / 본인 token / cookie) chat paste 시 rotate/redact 권고 반복 X.
  [[feedback-own-auth-values-not-sensitive]] 신규.

## 이번 세션 머지/푸시 (2026-05-16, 9 PR)

| PR | 내용 |
|----|------|
| #116 | fix(cors): allow credentials so cross-site cookie auth works in prod — `ApiCorsDefaults.allowCredentials(true)`. SmartID prod 의 마지막 layer. PR #114 SameSite=None + 이 PR 의 ACAC 로 cross-site cookie auth 완성 |
| #117 | chore(handoff): roll handoff to 2026-05-16 evening (SmartID prod end-to-end live) — TROUBLESHOOTING CORS entry 추가 + handoff overwrite |
| #118 | docs: update Task 14/16 specs + draft auto-deploy workflow — frameset wrapper 회고 / iframe deep endpoint 가정 / `.github/workflows/deploy.yml` no-op draft (KUBE_CONFIG secret 없으면 skip) |
| #119 | docs: lock cross-site cookie auth design + correct Secret name drift — ADR 0014 Addendum + handoff 의 `ssuai-backend-secret` → `ssuai-backend-secrets` (plural) 정정 + TROUBLESHOOTING |
| #120 | feat(task-16a): pin timetable fixture + WebDynpro endpoint spike result — `backend/src/test/resources/saint/timetable-success.html` (3 row, PII placeholder) + 표 parsing structure 잠금 |
| #121 | fix(task-16a): correct timetable component to ZCMW2102 + host to ecc.ssu.ac.kr — `zcmw9001n` 추측 무효, 실제 `ZCMW2102` @ `ecc.ssu.ac.kr:8443` (cross-subdomain iframe). MYSAPSSO2 cookie cross-subdomain SSO 메커니즘 명시 |
| #122 | feat(task-16a): resolve fetch shape — single GET returns table HTML directly — 96KB initial response 안에 표 markup 포함 (시나리오 a 확정). connector 4-5줄 |
| #123 | feat(task-16a): design multi-term iterate fetch for get_my_schedule — WDA7 button (이전 학년도) SAPEVENTQUEUE event 디코드 + WebDynpro full-update XML response shape. 학번 substring(0,4) → 입학년도 → iterate |
| #124 | feat(task-16a): design get_my_grades fetch — single GET returns full cumulative GPA — `ZCMB3W0017` component (시간표 ZCMW2102 와 별개). 단일 GET 으로 전체 학기 GPA history + 학적부/증명용 통계 + 현재 학기 세부 다 옴 — multi-term iterate 불필요 |

backend 전체 test BUILD SUCCESSFUL. 자동 머지 정책으로 직접 머지.

**현장 매뉴얼 patch (git 외부)** — 이번 세션:
- `sudo kubectl create secret generic ssuai-backend-secrets --from-literal=SSUAI_JWT_SECRET="..." --from-literal=SSUAI_CREDENTIAL_ENCRYPTION_KEY="..." -n ssuai-prod --dry-run=client -o yaml | sudo kubectl apply -f -`
  — 이름이 **plural `ssuai-backend-secrets`** (manifest 의 `envFrom.secretRef.name`
  과 매칭). singular `ssuai-backend-secret` 은 무관 — 다음 세션이 헷갈리지
  않게 정정.
- Secret 회전 1회 (echo 로 chat 에 노출된 첫 값 폐기 후 새 random). 현재
  prod 의 JWT signing key + saint AES key 는 **2026-05-16 18:53 이후
  생성된 값** 그대로.

## 열린 PR

| PR | 브랜치 | 내용 | 상태 |
|----|--------|------|------|
| #81 | `chore/spike-ssotoken-ttl-script` | Task 13 ssotoken TTL spike script | 사용자 PC 에서 실행 중. 사용자가 **마지막에 하겠다고 선언** ([[feedback-user-defers-ttl-spike]]) — 측정 오래 걸려서. 다른 active 작업 다 끝난 후. **폴링 금지** ([[feedback-user-will-notify]]) |

## 외부 의존 — 사용자 직접 (폴링 금지)

| # | 항목 | 상태 |
|---|------|------|
| 1 | ~~SmartID `apiReturnUrl` whitelist spike~~ | ✅ RESOLVED (5/15 세션) |
| 2 | TTL spike (PR #81) | ⏳ 대기. 사용자가 마지막에 하겠다고 명시 |
| 3 | ~~u-SAINT 시간표/성적 fixture capture~~ | ✅ RESOLVED (이번 세션 — Task 16 PR 16a 완전 spike) |
| 4 | ~~PR #114 new pod + 브라우저 검증~~ | ✅ RESOLVED |
| 5 | ~~prod Secret 매뉴얼 + 회전~~ | ✅ RESOLVED (이번 세션) |
| 6 | (선택) GitHub Actions auto-deploy 활성화 | ⏳ 사용자 의지. `KUBE_CONFIG` repo secret 등록만 하면 다음 PR 머지부터 자동. `deploy/README.md` §7.2 가이드 |
| 7 | 시간표 2학기 / 계절학기 nav button id (`WDA8`/`WDA9`?) | ⏳ PR 16b 통합 테스트 시 응답 HTML grep — 사용자 추가 spike 불필요 (응답에서 button id 다 보임) |
| 8 | 성적 계절학기 코드 (`093`/`094`?) | ⏳ PR 16c 통합 테스트 시 응답에서 dropdown option lsdata grep |

## 다음 세션 액션 (우선순위 순서)

### 1. PR 16b — 시간표 connector 본격 코드 (큰 PR, 1-2시간)

spec 다 잠겼음 (`docs/tasks/16-usaint-realtime-data.md` §3.1-§3.4).
구현해야 할 것:

**Shared infra (PR 16b 가 먼저 만들면 PR 16c 재사용)**:

- `backend/src/main/java/com/ssuai/domain/auth/saint/`:
  - `SaintSessionStore.java` — `ConcurrentMap<String, SaintSessionEntry>`,
    AES-GCM 암호화, per-record IV, TTL 30분, LRU cap. `SSUAI_CREDENTIAL_ENCRYPTION_KEY`
    env (이미 prod 설정됨)
  - `SaintSessionEntry.java` (record: encryptedCookies, expiresAt)
  - `PortalCookies.java` (record: rawCookieHeaderValue)
  - `SaintSsoService.authenticate` 수정 — phase 2 cookies 를 store 에
    `put(studentId, ...)` 으로 저장 (현재는 discard)
- `backend/src/main/java/com/ssuai/domain/saint/`:
  - `service/WebDynproSapEventEncoder.java` — `~E001`..`~E005` +
    URL-encoded `{}@:;` 인코딩 헬퍼. PR 16b/16c 공통
  - `service/WebDynproResponseUnwrapper.java` — `<updates><full-update>
    <content-update><![CDATA[...]]></content-update>...</full-update>`
    의 CDATA 추출 + 새 secure-id parse helper

**Schedule-specific (PR 16b)**:

- `connector/SaintScheduleConnector.java` (interface)
- `connector/RealSaintScheduleConnector.java` — multi-term iterate
  (§3.4 pseudo-code). HttpClient + `HttpClient.Redirect.NORMAL`.
  학번 substring(0,4) → 입학년도 → WDA7 prev N회. **첫 cut scope = "전체
  학년도 1학기 iterate"** (2학기는 follow-up). cookies = SaintSessionStore
  lookup
- `connector/MockSaintScheduleConnector.java` — `mock` profile / `ssuai.connector.saint-schedule:
  mock` 으로 fixture 응답 (PR 16b 첫 cut 은 default mock)
- `dto/`: `ScheduleResponse`, `TermSchedule`, `ScheduleEntry`
- `service/SaintScheduleService.java` — store 에서 cookies 조회 →
  connector 호출 → `SaintSessionExpiredException` 처리
- `controller/SaintScheduleController.java` — `GET /api/saint/schedule`,
  `JwtAuthFilter` 가 박은 studentId 사용
- `mcp/tool/SaintScheduleMcpTool.java` — `get_my_schedule` (no args)
- `global/exception/SaintSessionExpiredException.java` — 401 +
  `SAINT_SESSION_EXPIRED` error code

**Tests**:

- `SaintSessionStoreTests` — AES round-trip, TTL 만료, LRU
- `WebDynproSapEventEncoderTests` — 인코딩 round-trip
- `WebDynproResponseUnwrapperTests` — CDATA / secure-id 추출
- `SaintScheduleParserTests` — `timetable-success.html` fixture →
  ScheduleEntry list
- `SaintScheduleServiceTests` — happy / expired / missing cookies
- `SaintScheduleControllerTests` — 200 envelope / 401 (no attr) / 401
  (SESSION_EXPIRED)
- `RealSaintScheduleConnectorTests` — MockWebServer 로 (GET → HTML) +
  (POST WDA7 → XML wrapper) 시나리오 두 번. 통합 테스트 시 실제 brower
  spike 한 응답 body 를 fixture 로 한 번 더 추가

**Default config** — `ssuai.connector.saint-schedule: mock` (prod /
CI 가 실 saint 안 침). prod 전환은 PR 16b 안정 + 사용자 검증 후 별도
`application-prod.yml` patch.

### 2. PR 16c — 성적 connector (PR 16b 직후, 같은 패턴)

- `connector/SaintGradesConnector.java` + Real/Mock
- `dto/`: `GradesResponse`, `TermGpa`, `GpaSummary` (학적부 / 증명용 각각),
  `CourseGrade`
- `service/SaintGradesService.java` — 단일 GET (multi-term iterate 불필요,
  §3.5)
- `controller/SaintGradesController.java` — `GET /api/saint/grades`
- `mcp/tool/SaintGradesMcpTool.java` — `get_my_grades` (응답으로 LLM 에
  보내는 건 `{totalTerms, cumulativeGpa, creditsEarned, link}` 요약만.
  **본문은 controller path 로만** — Task 16 §6 #6 + §8)
- `LlmChatService.compactToolResponse` 의 `get_my_grades` 분기 단위
  테스트로 본문 누출 차단 고정

성적은 fixture (`grades-success.html`) 가 아직 없음 — PR 16c 시작 시
사용자 brower spike (Task 16 §3.5 의 응답 패턴) 을 PII placeholder
로 scrub 해서 추가. **placeholder 권장**: 강의명 `자료구조` / `알고리즘`,
교수 `김교수` / `이교수`, 학기별 GPA `3.50` / `4.00` 같은 generic
숫자. 학번 = `20999999`, 이름 = `홍길동`, 석차 = `99/200`.

### 3. PR 16b/16c 통합 후 prod 전환

`application-prod.yml`:
```yaml
ssuai:
  connector:
    saint-schedule: real
    saint-grades: real
```

머지 후 SmartID 로그인 → 챗봇에 "내 시간표 알려줘" / "내 성적 알려줘" →
실 데이터 조회 확인. **데모 시점에는 secret rotate 직후 SmartID 재로그인
필수** (이번 세션 끝에 회전했으므로 기존 access JWT 다 무효).

### 4. (선택) GitHub Actions auto-deploy 활성화

`deploy/README.md` §7.2:
1. SSH 에서 `/etc/rancher/k3s/k3s.yaml` base64 (server 주소 ssumcp.duckdns.org:6443 으로 치환)
2. GitHub repo Settings → Secrets → `KUBE_CONFIG` 추가
3. 다음 push to main 으로 smoke test

cluster-admin 권한 kubeconfig 라 미래에는 scoped ServiceAccount 로
좁힐 follow-up. portfolio 환경이라 일단 OK.

### 5. TTL spike (PR #81) — 사용자가 마지막에 하겠다고 명시 ([[feedback-user-defers-ttl-spike]])

### 6. Phase 4 ActionInfrastructure (낮은 우선순위)

ADR 0015 첫 항목 — `reserve_library_seat` 시작 전 `action_audit`
테이블 + `ActionAuditService` + `ActionLock` interface (in-process →
Redis SETNX) + `PreparedActionExpiryRunner`. PR 16b/16c 안정 후.

## 사용자 컨텍스트 — 잊지 말 것

- **숭실대 컴퓨터학부 3학년**, 포트폴리오 프로젝트
- **데드라인 2026-05-15 (3일 전)** — 현장실습. SmartID 라이브 데모
  ssuai.vercel.app 에서 동작 확인 완료. portfolio 핵심 deliverable
  가용
- **3-AI rotation** (claude1/claude2/codex). 정책 변경 시 CLAUDE.md
  + AGENTS.md 양쪽 같은 commit. [[role-3-ai-rotation]]
- **commit/PR body 에 Claude/AI 흔적 절대 금지** ([[feedback-no-claude-coauthor]])
- **안전한 PR 자동 머지** — mergeable + tests pass + mock default면
  묻지 말고 `gh pr merge <N> --auto --rebase --delete-branch`.
  [[feedback-auto-merge-safe-prs]]
- **외부 작업 사용자 통지 대기** — "내가 알려줄게" / "끝나면
  알려줄게" 한 작업은 폴링/언급 금지. [[feedback-user-will-notify]]
- **본인 인증 값 chat 노출 → redact/rotate 권고 X**. JWT secret /
  encryption key / 본인 access·refresh JWT / portal cookie 모두 포함.
  로그아웃 + 새 SSO 로 sealed. server-side fixture / log 정책은 그대로
  유지 (chat hygiene 만 relax). [[feedback-own-auth-values-not-sensitive]]
  **NEW 이번 세션**
- **본인 학번/이름/IP chat 인용 면제** (사용자 = 홍성주 / 20221528 /
  218.50.132.144). 단, fixture / commit / log 는 placeholder
  (`홍길동` / `20999999` / `0.0.0.0`) 그대로. [[feedback-user-student-id-not-sensitive]]
- **TTL spike 는 마지막에** ([[feedback-user-defers-ttl-spike]])
- **간결한 한국어 응답 선호**. 답답할 땐 명령 1-2개로 좁힘
- **in-flight context 는 in-repo (handoff / task spec / dev-log)** 에
  쓰고 auto-memory 에는 안 씀 ([[feedback-save-progress-to-project]])
- **portal HTML 캡처 부탁 시 Ctrl+U vs F12 Elements 구분 명시 필수**
  ([[learning-browser-view-source-vs-devtools]])
- **두 제품 framing** — MCP 서버 + ssuAI 웹/앱 ([[project-final-goal]])

## 보안 주의

- **prod Secret 매뉴얼 잡힘**: `ssuai-backend-secrets` namespace
  `ssuai-prod` 에 `SSUAI_JWT_SECRET` + `SSUAI_CREDENTIAL_ENCRYPTION_KEY`
  둘 다 설정. WARN 두 줄 사라짐. Secret 1회 회전 완료 (chat 에 echo
  된 첫 random 값 폐기).
- 사용자 본인 학번/이름은 chat 면제이지만 **fixture / commit / log
  에는 절대 placeholder 사용**.
- **성적은 LLM 프롬프트 절대 금지** — Task 16 §6 #6, §8. PR 16c 통합
  시 `LlmChatService.compactToolResponse` 단위 테스트로 본문 누출 고정.
- ssutoday `sToken`/`sIdno` 는 method-scoped — 로그/DB/세션 어디에도
  안 남김.
- **MYSAPSSO2 SAP SSO 토큰 디코드**: base64 디코드 시 `portal:20221528`
  처럼 학번 노출. portal phase cookies 자체가 학번을 포함한다는 사실.
  SaintSessionStore 에 저장 시 AES-256-GCM encryption (`SSUAI_CREDENTIAL_ENCRYPTION_KEY`
  활용) 필수.

## 자동 메모리

`C:\Users\akftj\.claude-personal\projects\C--Users-akftj-ssuAI\memory\`

핵심 파일 (인덱스 `MEMORY.md` 자동 로드). 이번 세션 신규 1개:

- `feedback-own-auth-values-not-sensitive.md` — 본인 JWT/secret/cookie
  chat paste 시 rotate/redact 권고 반복 X. server-side 정책은 그대로.

---

## Next-AI opener block

다음 AI 세션을 시작할 때 사용자가 통째로 paste 할 첫 turn:

```text
ssuAI 프로젝트 이어받음. 다음 순서대로 시작:

1. AGENTS.md (또는 CLAUDE.md — 동일 mirror) 의 Project + Session-handoff
   섹션 읽기
2. docs/handoff/latest.md 읽기 — 이번 인계 컨텍스트
3. MEMORY.md (auto-memory) 한 번 훑기 — feedback-own-auth-values-not-sensitive
   포함
4. git status --short --branch (현재 main, frontend/next-env.d.ts 미커밋
   — Next.js auto-gen, 무시 OK)
5. 핸드오프 doc 의 "다음 세션 액션" 우선순위대로 진행

현재 상태:
- main HEAD: 959b5b9 (feat(task-16a): design get_my_grades fetch)
- 이번 세션 머지 9 PR: #116 (CORS allowCredentials), #117 (handoff
  roll), #118 (Task 14/16 spec + auto-deploy draft), #119 (ADR 0014
  Addendum + Secret 이름 정정), #120 (timetable fixture), #121
  (ZCMW2102/ecc 정정), #122 (single GET shape), #123 (multi-term
  WDA7 iterate), #124 (grades fetch design)
- 열린 PR: #81 (TTL spike, 사용자 직접) 하나뿐
- **SmartID prod end-to-end live**. 대시보드 "안녕하세요, 홍성주 학생"
  까지 동작. prod Secret 매뉴얼 완료 (`ssuai-backend-secrets`).

다음 세션 첫 액션 — 핸드오프 doc "다음 세션 액션 #1":
**PR 16b — 시간표 connector 본격 코드**. spec 다 잠겼음
(docs/tasks/16-usaint-realtime-data.md §3.1-§3.5). 구현 범위:
SaintSessionStore + WebDynproSapEventEncoder + WebDynproResponseUnwrapper
(공통 helper) + SaintScheduleConnector (Real/Mock) + dto + service
+ controller + MCP tool + fixture-based unit tests. 첫 cut scope =
전체 학년도 1학기 iterate (WDA7 prev N회). 2학기/계절학기 nav 는
follow-up.

외부 의존 — 사용자 직접 (폴링 금지):
- TTL spike (PR #81): 사용자 마지막에 한다고 명시
- (선택) GitHub Actions auto-deploy: KUBE_CONFIG GitHub repo secret
  등록만 하면 다음 PR 머지부터 자동
```
