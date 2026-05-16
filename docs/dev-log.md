# Dev Log

ssuAI 작업 진행 회고. 매 task 끝마다 한 줄씩 누적.
큰 결정은 별도로 `docs/adr/` 에 ADR 로 적는다.

## 2026-05-16

- 2026-05-16: **Task 16 PR 16c-B 빨강 3개 fix → green**. (1) `grades-prev-success.html`
  placeholder tbody id `WD65-contentTBody-placeholder` → `WD65-contentTBody`
  (parser selector `tbody[id$=-contentTBody]` 매칭 필요, suffix 가 핵심).
  fix 로 prev fixture 가 nthTbody(0)=dummy history / nthTbody(1)=실제 detail
  로 정상 색인 → detail/cascade 2건 통과. (2) `GradesParserTests.termHistory…`
  의 row 0 `passFailCredits` 기대값 `3.0` → `6.0`. 사유: spec-locked fixture
  (PR #127) 의 2025 겨울학기 row 가 P/F-only 6학점 (cc=5=6.0), 테스트 작성 시
  착오. 전체 gradle test green.
- 2026-05-16: **Task 16 PR 16c-B — 학기별 세부 iterate path 확정**. 추가
  spike (이전학기 button-press 후 응답 캡처) 결과 spec §3.5.1 의 "학기별
  세부는 단일 GET 으로 못 받음, follow-up" 가정 폐기. button IDs 확정
  (`WD01F0` 이전학기 / `WD01F2` 다음학기 / `WD0187` 조회), 이전학기
  POST 응답이 시간표 WDA7 와 동일 full-update XML wrapper + 세부 8 row
  채워짐 + secure-id rotation. connector 가 첫 GET → 학기별 GPA history
  (전체) + 학적부/증명 + default 학기 세부, 그 후 이전학기 button-press
  POST × (history.size()-1) 회 iterate 로 전 학기 세부 받는 path 잠금
  (§3.5.2). 학기 매핑은 connector 가 호출 횟수와 history 인덱스로 외부
  추적 — 시간표 multi-year iterate 의 "WDA7 N회 후 학년도 = currentYear-N"
  동일 패턴. minimal prev fixture `grades-prev-success.html` (5KB, 두
  row — 정규학기 letter-grade + P/F 학기) 추가. PR 16c 첫 cut scope =
  학기별 GPA history + 학적부/증명 누적 + 모든 학기 세부.
- 2026-05-16: **Task 16 PR 16c-A — 성적 fixture spike + spec §3.5 잠금**.
  사용자 brower spike (Network 탭 ZCMB3W0017 응답 Copy response, raw
  180KB) 결과 spec §3.5 추정의 큰 변경 5개 잠금: (1) 첫 GET 응답이
  이미 WebDynpro full-update XML wrapper (시간표는 raw HTML 였음 —
  connector 가 unwrap 필수), (2) 상단 학기별 표 컬럼 14개 (cc=0..13)
  — 산술평균/전체석차/학사경고여부/상담여부/유급 추가, (3) 학기 라벨이
  한국어 텍스트 ("1학기"/"2학기"/"여름학기"/"겨울학기") — `091/092` enum
  가정 폐기, (4) 학기별 세부 표 default 빈 상태 — PR 16c 단일 GET 으로
  못 받음, 학기 nav + "조회" button-press POST iterate 가 follow-up 으로
  분리, (5) 응답 본문에 학번/이름 0 hit (PII 부담 작음). fixture
  `backend/src/test/resources/saint/grades-success.html` 잠금 (PII 수치
  redacted, raw 패턴 보존). PR 16c 첫 cut scope 단순해짐 — 학기별
  GPA history + 학적부/증명 누적 통계만.
- 2026-05-16: **Task 16 PR 16b — 시간표 connector + service + controller**.
  `RealSaintScheduleConnector` (ZCMW2102 GET → WDA7 prev iterate, CSRF
  rotation, Set-Cookie merge), `MockSaintScheduleConnector` (default
  via `matchIfMissing=true`), `SaintScheduleService` (SaintSessionStore
  lookup → `SaintSessionExpiredException` 변환), `SaintScheduleController`
  (`GET /api/saint/schedule`, `AuthAttributes.STUDENT_ID` 누락 시
  `UnauthorizedException`). Multi-term iterate 는 입학년도까지 학년도
  단위 prev (학기 단위·계절학기 nav 는 follow-up). 단위 테스트:
  encoder/unwrapper/parser/connector + service + controller. application.yml
  에 `ssuai.connector.saint-schedule: mock` 명시. MCP tool 등록은 spec §9
  thread-local pattern 미구비로 follow-up PR 로 분리.
- 2026-05-16: **Task 16 PR 16a — 성적 fetch shape (시간표보다 단순)**.
  사용자 brower spike: 이수구분별 성적표 / 학기별 성적 조회 류 페이지
  진입 → 응답 캡처. 핵심 발견 — 페이지 상단에 "학기별 성적" 표가
  **입학년도부터 현재까지 모든 학기 GPA 를 한 번에 표시**, 하단은
  "학기별 세부 성적" (선택된 학기의 과목별 상세). 즉 시간표의 multi-term
  iterate (WDA7 prev 반복) 와 달리 **단일 GET 한 번으로 전체 학적
  누적 fetch** 가능. 추가로 응답 안에 학적부 누적 통계 (졸업요건 기준
  GPA) + 증명용 누적 통계 (재수강·P/F 처리 반영) 가 분리되어 들어있음.
  Component = `ZCMB3W0017` (시간표 ZCMW2102 와 다른 SAP 모듈). 응답
  shape 는 시간표와 동일 WebDynpro full-update XML. 학기 코드 enum
  추출: `091` = 1학기, `092` = 2학기 (계절학기는 PR 16c 통합 테스트 시
  확정). 학년도 dropdown 옵션 `2010~2025` 다 들어있음. spec §3.5 신설
  — 성적 endpoint / 응답 영역 2개 / 학기 코드 / connector pseudo-code
  / 시간표와의 차이 요약 / LLM prompt 정책 재확인 (요약만 LLM, 세부는
  controller path 로만). PR 16c 첫 cut scope: 누적 GPA + 학적부/증명용
  통계 + 현재 학기 세부 만 — 학기별 세부 변경 (다른 학기 dropdown
  선택 후 조회) 은 follow-up.
- 2026-05-16: **Task 16 PR 16a — multi-term iterate spike**. 사용자
  요구를 다시 정리: `get_my_schedule` 의 본질은 입학년도부터 현재까지
  누적 시간표 (학번에서 입학 학년 추출 → 모든 학기 iterate). 시간표
  페이지의 학기 nav 가 dropdown 이 아니라 **PREVIOUS/NEXT 버튼**임을
  사용자 brower spike 로 확인. `WDA7` 버튼이 prev — SAPEVENTQUEUE 의
  `Button_Press~E002Id~E004WDA7` event 형태. 한 번 클릭 = 학년도 −1
  점프 (2026-1 → 2025-1). 즉 학년도 단위 prev — 학기 단위 prev / 계절
  학기 nav 는 별도 버튼 (`WDA8`/`WDA9` 등) 가능성, PR 16b 통합 테스트
  시 응답 HTML grep 으로 확정. 응답은 WebDynpro full-update XML wrapper
  (`<updates><full-update><content-update><![CDATA[...HTML...]]>`) — 즉
  partial DOM op 가 아니라 전체 페이지 re-render. CDATA 추출 → Jsoup
  parse → 첫 GET 과 동일한 selector 재사용. spec §3.4 신설로 SAPEVENTQUEUE
  control char 인코딩 표, multi-term connector pseudo-code, PR 16b 첫
  cut scope ("전체 학년도 1학기 iterate" 권장 — 사용자 의도 70% cover,
  2학기·계절학기는 follow-up spike) 잠금.
- 2026-05-16: **Task 16 PR 16a — 시나리오 (a) RESOLVED**. ZCMW2102 첫
  GET response body (gzip 8.3KB, 압축 풀면 96KB) 안에 시간표 표
  markup 이 직접 포함됨을 확인 (`contentTBody` / `<tbody>` / `<form
  action=...;sap-ext-sid=...>` / `sap-wd-secure-id` 모두 동시 검출).
  즉 WebDynpro partial XHR delta / SAPEVENTQUEUE round-trip 불필요 —
  connector 는 단순 GET 한 번 (cookies 첨부) → Jsoup parse → DTO.
  spec §3.3 가 그 sequence 4-5줄 의사코드로 잠금. PR 16b 의 첫 cut
  scope 가 확정: `SaintScheduleConnector` + `MockSaintScheduleConnector`
  + `RealSaintScheduleConnector` + `SaintScheduleService` +
  `SaintScheduleController` + `SaintScheduleMcpTool` + fixture-기반
  parser unit tests. 학기 변경 (과거 학기 시간표 조회) 은 시나리오
  (b) 경로라 follow-up.
- 2026-05-16: **Task 16 PR 16a — endpoint/host 정정**. 1차 spike 의
  `zcmw9001n` 추측 틀림. 사용자가 브라우저 Network 탭 → ZCMW2102 row
  의 "Copy as cURL" 결과를 paste 한 진단에서 4가지 사실 확정:
  (1) host = `https://ecc.ssu.ac.kr:8443` (saint 가 아닌 ecc 서브도메인 —
  portal contentAreaFrame 이 cross-subdomain iframe), (2) component =
  `ZCMW2102` (대문자, SAP custom Z-namespace), (3) 인증 = `MYSAPSSO2`
  cookie cross-subdomain 흐름 — `.ssu.ac.kr` 도메인이라 portal phase 가
  발급한 SSO 토큰이 ecc 까지 자동 attach, 별도 ecc 로그인 round-trip
  불필요. base64 디코드하면 `portal:20221528` 처럼 학번 들어있는
  SAP NetWeaver 표준 cross-domain SSO 토큰. (4) CSRF =
  `sap-wd-secure-id` (매 page load 마다 다름. WebDynpro 표준 — connector
  가 첫 GET 응답에서 parse → 후속 POST 마다 동봉). 추가로 SAP-PASSPORT
  / X-XHR-Logon / X-Requested-With custom header 도 잡힘 (생략 가능
  추정). spec §3.1 ~ §3.3 으로 재구성: §3.1 endpoint/auth/CSRF 사실,
  §3.2 표 parsing structure (fixture 그대로), §3.3 PR 16b 시작 전 남은
  마지막 spike (ZCMW2102 첫 GET response body 에 표가 직접 들어있는지
  vs partial XHR delta 가 필요한지 — connector 복잡도를 결정).
- 2026-05-16: **Task 16 PR 16a 1차 spike** — 개인수업시간표조회 endpoint
  확정. 사용자 본인 brower 에서 시간표 메뉴 진입 → wrapper 의
  contentAreaFrame iframe 안쪽 source 캡처. wrapper 가 portal nav event
  (`POST .../prteventname/Navigate/...?windowId=WID...&PrevNavTarget=
  navurl://...` body `NavigationTarget=navurl://1724938fdd5d98311a8647b31efd21fe`)
  를 거쳐 최종적으로 SAP WebDynpro 컴포넌트 `zcmw9001n` 의 HTML 응답을
  로드함을 확인. 표 parsing structure 잠금: `tbody[id$="-contentTBody"]`
  안에 헤더 row (`tr[rt="2"]`) + 1-10교시 data row (`tr[rt="1"]`), 각
  cell `td[role="gridcell"][cc="N"]` (cc=0 시간 column, cc=1..6 월~토),
  빈 cell `div.lsSTEmptyRow` / 강의 cell `span.lsTextView--wrap` 의
  text content 가 `과목명<br>교수<br>시간<br>강의실` 4줄. fixture
  `backend/src/test/resources/saint/timetable-success.html` 로 pinning
  (PII placeholder: 자료구조/알고리즘/운영체제, 김교수/이교수/박교수,
  정보과학관 30100 등 generic). Task 16 spec §3.1 신설로 spike 결과
  명문화. **아직 미정** (PR 16b 시작 직전 별도 spike): WebDynpro form
  action URL + hidden inputs + 정확한 nav request sequence. 표 자체의
  parsing 은 지금 잡힌 fixture 로 PR 16b 진행 가능.
- 2026-05-16: **PR #116 — SmartID prod 완전 동작**. PR #114 SameSite=None
  으로도 풀리지 않던 "세션 갱신 실패" root cause 가 별개 layer 였음:
  backend CORS 의 `.allowCredentials(false)` 때문에 응답에 `Access-Control-Allow-Credentials:
  true` 가 안 붙어 브라우저가 200 OK response body 를 JS 한테 차단. frontend
  `fetchJson` 이 `INVALID_ENVELOPE` throw → `useSaintAuth.refresh()` false
  → 세션 실패 메시지. set-cookie 는 정상 저장되고 backend 로그도 200 으로
  정상 처리된 것처럼 보여 디버깅 단서가 frontend Console 의 CORS 경고
  하나뿐이었음. `ApiCorsDefaults.java:15` `false` → `true` 한 줄 + 회귀
  방지 테스트 2개. allowedOrigins 가 explicit origin 이라 Spring validator
  통과. PR #116 머지 + kubectl set image `sha-1031de0…` → 대시보드 "안녕하세요,
  홍성주 학생" 까지 prod end-to-end 첫 성공. SmartID 라이브 데모 portfolio
  핵심 deliverable 가용. 자세한 회고는 TROUBLESHOOTING.md.
- 2026-05-16: **PR #112 follow-up** — 첫 parser 재작성 (`.main_box09
  .box_top .main_title span` + `<dt>→<dd>` key map) 도 여전히
  `missing name element` 떴음. 사용자 paste 한 portal main page 전체
  HTML 분석 결과 더 큰 가정 오류 발견: portal `/irj/portal` 응답은
  **SAP NetWeaver frameset wrapper** 이고 학번/소속 카드는 모두
  `<iframe id="contentAreaFrame">` 안쪽에 lazy load 됨. wrapper 자체엔
  학번/소속/학적상태 그 무엇도 없고, 단지 `<span class="top_user">{이름}님
  접속을 환영합니다.</span>` greeting + JS config 의 `LogonUid` 만
  있음. 사용자가 보여준 `.main_box09_con` markup 은 iframe 안쪽 페이지
  의 것이었음 (F12 Elements 가 cross-frame 까지 보여줘 wrapper 와 안쪽
  구분이 안 됐던 것). 두 번째 parser 재작성: 이름은 `.top_user` 의
  greeting 에서 `["님 접속을 환영합니다.", "님 환영합니다.", "님"]`
  순으로 suffix 스트립. 학번은 phase 1 의 sIdno 그대로 trust (phase 1
  success marker 통과 + portal session cookie 발급된 시점에 sIdno 가
  실제 학생 번호임이 입증됨). 소속/학적상태는 **null**, Task 16
  `get_my_schedule` / `get_my_grades` 가 별도 deep portal endpoint 에서
  채울 영역. fixture 3개 (`portal-success.html`, `portal-missing-name.html`,
  `portal-greeting-unknown-suffix.html`) 도 frameset wrapper 의 minimal
  shape 으로 재작성. Task 14 spec §risks 가 ssutoday parse anchors 변화
  를 경고했었으나 "wrapper vs iframe" 구분까지는 적시 안 됐음 — spec
  업데이트는 follow-up. 별도 PR.
- 2026-05-16: SmartID prod end-to-end 첫 검증 → portal HTML parser
  실패로 두 갈래 prod incident 해소. (1) PR #110 의 fail-fast 가
  prod 에서 발동 — `ssuai-backend-config` ConfigMap 에 `SSUAI_API_BASE_URL`
  이 없어 새 pod CrashLoopBackOff. `kubectl patch configmap` 한 줄로
  값 주입 후 rollout restart, prod 살아남. (2) SmartID/phase1 통과
  후 phase 2 portal HTML 의 selector `.main_box09 .main_box09_con`
  가 0 cell 매치 → `portal_unavailable` 로 로그인 실패. 사용자
  본인 브라우저로 portal 검사한 결과, ssutoday 옛 fixture (학번/이름/
  소속/학적상태 4개 `<div>` 셀) 와 달리 실제 portal 은 `.main_box09 .box_top
  .main_title span` 에 "{이름}님 환영합니다." greeting + `<ul class="main_box09_con">
  <li><dl><dt>키</dt><dd>값</dd></dl></li>` 4행 (학번/소속/과정·학기/학년·학기)
  키-값 카드 구조였음. Task 14 §risks 가 이미 "portal HTML structure
  has shifted significantly from ssutoday's parse anchors → need to
  re-parse and pin new fixtures" 로 경고했지만 실 검증 전이라 미반영.
  `SaintSsoService.parseIdentity` 를 positional `cells.get(i)` 에서
  key-기반 `<dt>→<dd>` map 으로 재작성 (미래 row 추가/순서 변경에도
  robust), 이름은 별도 `.main_title span` selector + "님 환영합니다."
  suffix 스트립으로 추출. `portal-success.html` fixture 를 실제 markup
  으로 교체 (학번/이름/IP/시간은 placeholder). `portal-missing-cells.html`
  는 ul-누락 케이스로 의미 재정의, `portal-missing-name.html` 신규
  추가. backend 258+ tests 그린. PR 별도. **운영 메모**: cluster 에
  ArgoCD 도 helm 도 없는 단순 `kubectl apply` 운영이고 이미지 tag 가
  `:latest` 라 PR 머지 → 다음 `kubectl rollout restart` 시점에 자동
  prod 반영. 매뉴얼 ConfigMap patch 는 git 과 동기화 안 됨 (추후
  GitOps 정리 필요, 별도 trouble entry).
- 2026-05-16: Task 14 §7 #1 + §10 #1 spike **RESOLVED POSITIVE**.
  사용자가 실제 SmartID 로그인 → `apiReturnUrl=https://example.com`
  으로 302 되는 거 확인. `SaintSsoCallbackController` 가 의존하는
  "SmartID 가 임의 apiReturnUrl 받는다" 는 가정이 retroactive 검증됨.
  이미 머지된 Task 14 PR 시리즈 (#94/#99/#100/#101/#102) 위의 web-port
  패턴 그대로 유효. handoff 의 외부 의존 #1 해소, 나머지 3건 (TTL
  spike / 모바일 CSS / u-SAINT fixture) 만 남음. 사용자에게 채팅
  paste 시 본인 sToken/sIdno redact 가이드 다시 안내.
- 2026-05-16: Task 16 PR 16a 의 storage 절반 머지 — `SaintSessionStore`
  (AES-256-GCM, 12-byte IV, 128-bit tag, 30분 TTL, 1000-entry LRU) +
  `PortalCookies` / `SaintSessionEntry` records + `SaintSessionProperties`
  + `SaintSessionExpiredException` + `ErrorCode.SAINT_SESSION_EXPIRED` +
  `SaintSsoService.authenticate` hook. encryption key 는
  `SSUAI_CREDENTIAL_ENCRYPTION_KEY` env, 빈 값이면 ephemeral random +
  WARN (JwtProvider 와 동일 패턴). `SaintSessionStoreTests` 14 케이스
  (3KB cookie round-trip, TTL eviction, LRU cap, ephemeral vs configured
  key bootstrap, short-key fail-fast, fingerprint). u-SAINT 시간표/성적
  fixture capture 는 PR 16b/16c connector parsing 에만 필요해 storage
  는 spike-독립적으로 먼저 머지. PR #107.
- 2026-05-16: Phase 4 prep — ADR 0015 (action-tool 공용 인프라).
  write tool 은 `prepare_X` + 공용 `confirm_action(pending_action_id)`
  두 MCP tool 로 분리, confirmation 은 채팅 turn 약속이 아니라 서버측
  single-use 토큰. `action_audit` append-only 상태기계 (PREPARED →
  EXECUTING → SUCCESS/FAILURE_RACE/FAILURE_AUTH/FAILURE_UPSTREAM/
  TIMEOUT/EXPIRED/CANCELLED), pending TTL 5분, in-process `ActionLock`
  seam (Redis SETNX 로 추후 스왑), `FAILURE_RACE` 는 별도 outcome 이라
  agent loop 이 다음 좌석 자동 제안 가능. ADR Consequences 첫 항목이
  `reserve_library_seat` 시작 전 ActionInfrastructure 한 PR 깔라는
  지시. PR #106.
- 2026-05-16: Task 17 spec — LMS 통합. `get_my_assignments` 메인
  deliverable. LMS 의 auth shape 가 핵심 unknown — SmartID-fronted
  (Task 14 콜백 패턴 재사용 가능) vs 학교계정 form-login (security.md
  §5 "no password proxy" 원칙 하에 비밀번호 메서드 로컬 변수 1회 사용
  후 폐기) 두 분기 모두 spec 에 명시. `LmsSessionStore` 는
  `SaintSessionStore` 와 패턴 동형. Jsoup 부터 시도, Playwright 는
  구체적 blocker 시에만 escalate. 과제 body 는 `LlmChatService.compactToolResponse`
  에서 절대 LLM prompt 에 안 들어가게 (tool-citation pattern). PR
  #106 (ADR 0015 와 같이).
- 2026-05-16: Session handoff doc — `docs/handoff/2026-05-16-evening.md`.
  사용자가 토큰 거의 다 써서 다른 Claude 계정으로 세션 이어받기 위한
  정리. Task 15 트리 (#85/#86/#88) 머지 완료, Task 14 backend 절반 (#89
  Student entity + #90 JWT infra) 머지, 남은 PR 14b-3 (SaintSsoService) +
  PR 14b-4 (Controller) + PR 14c (frontend) 계획. SmartID apiReturnUrl
  whitelist spike 는 사용자 직접 진행, TTL spike 도 사용자 알릴 때까지
  대기. 사용자 피드백 — Claude 흔적 (commit trailer / PR body footer)
  완전 제거. 이번 세션 PR 6개 body footer 정리 완료, 이미 머지된 commit
  trailer 는 force-push 위험으로 보류.
- 2026-05-16: Task 14 PR 14b-2 — JWT infra. `io.jsonwebtoken:jjwt-*:0.13.0`
  의존성 추가. `global/auth/` 패키지 신설: `JwtProperties` (secret/issuer/
  access-ttl 15m/refresh-ttl 14d, secret 은 SSUAI_JWT_SECRET env var
  override), `JwtTokenType` (ACCESS/REFRESH), `JwtClaims` record,
  `JwtProvider.issueAccess/issueRefresh/parse`, `InvalidJwtException`.
  Secret < 32 bytes 면 시작 실패. type mismatch / expired / tampered /
  foreign-secret 다 거부. 테스트 7 케이스. `JwtAuthFilter` 와 인증
  context 는 PR 14b-4 controller 와 함께.
- 2026-05-16: Task 14 PR 14b-1 시작 — ssuAI 의 첫 user system. JPA +
  H2 의존성 추가 (`spring-boot-starter-data-jpa`, `com.h2database:h2`
  runtimeOnly). `application.yml` 에 datasource (in-memory H2,
  PostgreSQL 호환 모드) + JPA properties (ddl-auto=update, open-in-view
  false) 추가. `domain/user/` 패키지 신설 — `Student` JPA entity
  (studentId PK, name/major/enrollmentStatus, createdAt/lastLoginAt),
  `StudentRepository`, `StudentService.upsertOnLogin` (재로그인 시 프로필
  refresh + lastLoginAt bump, 신규 로그인 시 row 생성). Clock 패턴은
  기존 LibrarySessionStore/LibraryBookCache 와 동일하게 default 생성자
  Clock.systemUTC() + test 생성자 fixed clock. PR 14b 의 나머지
  (JwtProvider/SaintSsoService/Controller) 는 별도 PR.
- 2026-05-16: Task 15 PR 15b 머지 — `RealLibraryBookConnector` (Pyxis
  JSON API, 익명 GET). RestClient + LibraryBookConnectorConfig 빈
  (libraryBookRestClient, timeout 적용), needLogin 응답을
  ConnectorParseException 으로 회귀 감지. fixture 3종 (python/empty/
  needLogin), MockRestServiceServer 기반 8 케이스 테스트. PR
  application-prod.yml 에 `library-book: real` 전환. PR #88 (원래 #87
  base 가 PR 15a 머지로 closed 돼 재오픈).

## 2026-05-15

- 2026-05-15: Task 15 PR 15a — 도서관 도서 검색 mock 슬라이스 end-to-end.
  DTO (LibraryBook/LibraryBookSearchResponse/BookStatus), Connector
  인터페이스 + 결정적 10권 더미 MockLibraryBookConnector, 60s TTL +
  LRU 200 cap + single-flight LibraryBookCache, 입력 검증 Service
  (query 1~64자, size cap 20), Controller (`GET /api/library/books`),
  McpTool `search_library_book`, LlmChatService SYSTEM_PROMPT/SCOPE/
  SECRET guidance + tool switch + JSON compaction, 챗봇 sample prompt
  "도서관에 파이썬 책 있어?" 추가. vision.md `search_library_book` 을
  Phase 2 mock 가동으로 표기. PR #86.
- 2026-05-15: Task 15 spec — `docs/tasks/15-library-book-search.md`.
  Phase 2 두 번째 슬라이스. oasis Pyxis 도서 검색 API spike 결과 익명
  GET 가능 + JSON + PII 없음 확정 (Task 13 좌석 API 의 전면 인증과
  정반대). endpoint `/pyxis-api/1/collections/2/search?all=k|a|<검색어>
  &isForPyxis3=true`. cStateCode → BookStatus (READY/LOAN/UNKNOWN)
  매핑. PR 15a (mock end-to-end) + PR 15b (real Pyxis JSON connector)
  로 분해. PR #85.
- 2026-05-15: Session handoff doc — `docs/handoff/2026-05-15-evening.md`.
  사용자가 다른 Claude 계정으로 세션 전환. 다음 세션이 즉시 이어받을 수
  있도록 정리 — 열린 PR 4개 (#80/#81/#82/#83) 상태, 이번 세션 critical
  발견 (Pyxis 인증이 cookie 아니라 header), TTL spike 진행 상황 (사용자 PC
  PowerShell 창에서 실행 중), §12 decision tree, 다음 세션 6단계 액션
  플랜, 보안 주의. 향후 핸드오프 파일은 `docs/handoff/` 에 누적.
- 2026-05-15: Task 13 §4/§5 디버깅 발견 정정. 실제 oasis Pyxis API 인증은
  **`Pyxis-Auth-Token` request header** 였음 (spec 가정의 ssotoken cookie X).
  Endpoint도 `/pyxis-api/api/smuf/reading-rooms` 가 아니라 실제 SPA가 호출하는
  `/pyxis-api/1/seat-rooms?smufMethodCode=PC&branchGroupId=1` 이 맞음. spec
  §4 endpoint table + §5 architecture diagram + PR 13b 헤더 설명 정정. PR
  13c는 manual paste MVP로 spec에 명시. spike script (PR #81)는 이미
  header-auth로 교체 push 완료. ADR 0013의 cookie 표기는 PR #82 follow-up
  으로 정정 예정.
- 2026-05-15: Task 13 §7 #1 spike resolved — **negative**. 브라우저 SOP가
  `ssuai.vercel.app` → `oasis.ssu.ac.kr` popup 의 `document.cookie`/
  `location.href` 모두 차단, `postMessage` 도 oasis 쪽에 listener 못 심어
  무용. ssutoday 의 RN WebView 패턴은 web 에서 안 됨. Task 13 §10 첫 번째
  stop-and-flag 발동. spec 에 §12 "capture-mechanism decision" 추가하고
  A(manual paste) / B(extension) / C(bookmarklet) / D(u-SAINT 우선) /
  E(mock 유지) 5 안 정리. PR 13a (backend session store) 는 mechanism 과
  독립이라 선행 진행. PR #80.
- 2026-05-15: Task 14 spec — `docs/tasks/14-usaint-session-auth.md`.
  ssutoday RN SSO-redirect 패턴의 web port. apiReturnUrl을 ssuAI
  backend로 설정 → SmartID가 우리 도메인으로 302 → sToken+sIdno이
  same-origin query param으로 도착 → SOP 우회 불필요. ssutoday
  `AuthServiceImpl.uSaintAuth` 의 2-phase scrape (validation + portal
  parse) verbatim 포팅 계획. ssuAI 최초의 user system (Student JPA +
  JwtProvider). Identity-only MVP — realtime 학적 데이터는 Task 15+.
  Critical spike: SmartID `apiReturnUrl` whitelist 여부 (없으면 §10
  stop-and-flag). Task 13과 sibling Phase 3 task로 자리매김.
- 2026-05-15: ADR 0013 — library session capture pattern. Phantom-token
  원칙을 legacy proprietary auth (Pyxis, OAuth 미지원)에 어댑팅한 방식
  문서화. MCP OAuth 2.1 spec / Manus Browser Operator / Bitwarden MCP /
  ssutoday reference 인용. Token boundary는 connector 레이어로 한정
  (LLM 절대 token 노출 안 됨), capture 메커니즘은 spec §12에서 user-
  decision pending, TTL은 PR #81 spike 결과 후 확정.
- 2026-05-15: Task 13 §7 #2 spike — `scripts/spike-ssotoken-ttl.{ps1,sh}`.
  oasis ssotoken TTL 측정. env var로만 토큰 받음, SHA-256 fingerprint만
  로그, `*.log` gitignore. 결과는 PR 13c (manual capture flow) 의 영구
  저장 정책 (in-memory vs AES-GCM persistent)을 결정. PR #81.
- 2026-05-15: Task 12 backend mock slice. `LibraryFloor` enum, `LibrarySeatZone`/
  `LibrarySeatStatusResponse` DTO, `LibrarySeatConnector` 인터페이스 + 결정적
  `MockLibrarySeatConnector`, 30s TTL + single-flight 캐시 `LibrarySeatCache`,
  `LibrarySeatService`, `GET /api/library/seats?floor=N` 컨트롤러, MCP 도구
  `get_library_seat_status` 까지 end-to-end 완성. `RealLibrarySeatConnector`
  는 upstream URL 확정 전까지 보류. ADR 0012 추가. PR #77.
- 2026-05-15: Codex hand-off workflow 폐기. `AGENTS.md` 삭제, `CLAUDE.md`/
  `docs/tasks/12-library-seat-status.md`/`.github/pull_request_template.md`/
  `README.md`/`TROUBLESHOOTING.md` 정비. `.codex/`, `.tmp/`, `exports/`
  로컬 폐기물도 제거. Claude 단독 구현자 체제로 전환. PR #76.
- 2026-05-15: Phase 2 시작점 task spec —
  `docs/tasks/12-library-seat-status.md`. read-only library seat
  status MCP tool. 인증 없는 공개 데이터만, 예약 액션은 Phase 4 별도.
  cache TTL 30s + single-flight 로 upstream 보호. 6 개 sub-task 로
  분해. PR #74.
- 2026-05-15: 라이브 챗봇 3종 회귀 fix. (1) 모호한 질문에 봇이
  되묻기만 함 ("학식 뭐?" → 6개 식당 나열하고 어느 거?) (2) 도구
  결과에 없는 브랜드명 환각 ("학교 편의점?" → 데이터엔 쿱스켓
  6개뿐인데 봇이 "CU 학생회관, GS25 본관" 발명) (3) "응응" 같은
  단답 follow-up 에서 컨텍스트 완전 손실. (1)(2) 는 SYSTEM_PROMPT
  강화 — default 가정 즉시 도구 호출, 도구 결과의 정확한 이름만
  사용 (브랜드명 추측 금지), follow-up 짧은 긍정 답변 처리.
  facility cap 6 → 10 으로 silent truncation 가장자리 회피. (3) 은
  `ChatConversationStore` + `ChatMemoryProperties` 신규 추가 —
  in-memory bounded multi-turn (TTL 30m, 12 messages cap,
  1000 conversations LRU). 비밀 입력은 history 에 절대 저장 안 함
  (secret 가드 케이스). frontend 는 이미 conversationId 보존 중이라
  무수정. PR #72 + #73.
- 2026-05-15: 배포 진단 후속 — `5be57d9` 가 `pullPolicy: Always` 우회로
  stale backend image 문제 해결한 사실을 `docs/deploy/pipeline-diagnosis-2026-05-14.md`
  에 Resolution 섹션으로 추가. Image Updater write-back 근본 fix 는
  deferred. PR #71.
- 2026-05-15: Repo 정리 — stale PR close (#65 README v2,
  #64 demo placeholder, #41 tailwind v3→v4 major bump 빌드 실패),
  `.codex/chatbot-openrouter-fallback-plan.md` 삭제 (ADR 0009 가
  source of truth), `.codex/codex-work-log.md` 를 `.codex/archive/` 로
  이동.

## 2026-05-14

- 2026-05-14: LLM 모드 + MCP self-dogfood 실서버 부팅 3중 장애 (RestClient.Builder
  / ObjectMapper / MCP client SSE chicken-and-egg) 해결. `LlmProviderConfig` 에
  명시적 `RestClient.Builder` + `@Primary ObjectMapper` 빈. `application.yml`
  에 `spring.ai.mcp.client.initialized: false` + `toolcallback.enabled: false`.
  `LlmChatService` 의 MCP client 주입에 `@Lazy`. `POST /api/chat` 에 실제
  Gemini + 실 학식 connector 로 end-to-end 응답 확인. TROUBLESHOOTING 항목 추가.

## 2026-05-13

- 2026-05-13: Chat tool 목록을 MCP server 의 `listTools()` 로 동적 발견. 정적
  `CHAT_TOOLS` 와 `createTools()` 헬퍼 제거. `McpSchema.Tool` → OpenAI tool
  매핑 + JVM-수명 cache. MCP `@Tool` annotation 이 single source of truth.
  ADR 0011.
- 2026-05-13: `McpSelfDogfoodTests` 추가. `@SpringBootTest(RANDOM_PORT)` 환경에서
  Spring AI MCP client 를 수동 build 해 자기 `/sse` 에 `listTools` + `callTool`
  라운드트립 검증. ADR 0010 의 "MCP server 가 자체 프로세스에서도 호출 가능"
  주장을 unit mock 이 아니라 실제 SSE 핸드셰이크로 보호.
- 2026-05-13: Chatbot self-dogfoods MCP server. `LlmChatService` 가 in-process
  `MealMcpTools/DormMcpTools/CampusMcpTools` 직접 호출 대신 Spring AI MCP client
  (`spring-ai-starter-mcp-client`) 로 자기 자신의 `/sse` 를 통해 4개 tool 을 호출.
  Tool 응답은 `JsonNode` 기반 compaction + 8KB cap 으로 LLM context 보호. ADR 0010.

## 2026-05-12

- 2026-05-12: Task 07 GitOps - ArgoCD + Helm + Image Updater 산출물 정리.
  `deploy/k8s` raw manifest 를 chart 로 이관하고, 실제 클러스터 sync 검증은
  ArgoCD CRD/PAT/클러스터 접근 준비 후 진행하도록 runbook 에 분리.
- 2026-05-12: Chatbot slice WIP 정리. `POST /api/chat`, `/chat` page,
  mock/LLM provider abstraction, bounded fallback budget, compact tool
  result 정책을 `feat/chatbot-slice` 브랜치 기준으로 문서화. ADR 0009 추가.

## 2026-05-10

- 2026-05-10: PR #11~#14 merged into `main` in order. Rebased PR #12 and
  #13 over the new secret-scanning/dependabot changes to preserve all
  `docs/dev-log.md` entries, then verified latest `main` locally and in CI.
- 2026-05-10: Task 07 direction settled as GitOps/ArgoCD. PR #10 was marked
  ready and merged, adding `docs/tasks/07-argocd-gitops.md` and
  `docs/adr/0008-gitops-argocd-helm.md`.
- 2026-05-10: README status refreshed. Task 06 deploy artifacts are merged,
  but live Oracle Cloud / DuckDNS / Vercel rollout remains an external
  operator step; candidate live URLs are still unreachable.

## 2026-05-07

- 2026-05-09: Task 09 secret scanning - `gitleaks` GitHub Actions job과
  optional `lefthook` pre-commit hook 추가. `.gitleaks.toml` 은 default rule
  pack을 확장하고 `sk-ant-api03` Anthropic key pattern 및 documentation /
  deploy secret example false positive allowlist 처리.
- 2026-05-09: Task 11 Dependabot - backend Gradle, frontend npm, GitHub
  Actions 3 ecosystem에 weekly update PR 설정. patch/minor는 ecosystem별로
  group 처리하고 major는 개별 PR로 남김.
- 2026-05-09: Task 10 frontend test infra - `@vitejs/plugin-react`, RTL,
  jest-dom, jsdom 기반 Vitest config 추가. `TodayMealCard`,
  `FacilitySearchCard`, `ErrorState` component test로 loading/success/error와
  debounce search 렌더링 경로 검증.
- 2026-05-07: Task 05 frontend MVP - Next.js dashboard + 4 cards, local
  integration. API envelope unwrap + per-card loading/error/empty states and
  dev-only CORS completed.
- 2026-05-07: PR #6 review polish — `getErrorStateDetails` 를 `error-state.utils.ts`
  로 분리해 vitest 가 JSX 안 거치고 unit test 가능하게 함 (`@vitejs/plugin-react`
  추가 회피). `INVALID_ENVELOPE` 한국어 메시지 추가 + `fetchJson` 네트워크
  실패 propagation 테스트 + `getErrorStateDetails` 4 케이스 unit test.

- meal fan-out 성능 개선: weekly export 1분 22초 → 26초.
  원인은 `RealMealConnector` 의 `synchronized` + 단일 `lastCallAtMs` 가 같은
  connector 인스턴스의 모든 식당 호출을 1초 간격으로 전역 직렬화한 것. rate-limit
  을 식당(rcd) 단위 `ConcurrentMap` 으로 분리하고 `MealService.getMeal` fan-out
  을 `parallelStream` 으로 바꿔서 해결.
- 같은 export runner 한 번 실행에 dorm weekly JSON 도 함께 산출. dorm connector
  의 실제 사이트 검증 누락도 같이 해결.
- `WeeklyMealExportService.exportWeeklyMeals` (fetch+write 합본) 가 production
  에서 호출자가 사라지고 테스트만 호출하는 안티패턴 정리. `fetchWeeklyMeals`
  public 격상 + `ObjectMapper` 의존성 제거.
- Spring AI MCP server starter (SSE / Streamable HTTP) 추가하고 4 tool
  (`get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`,
  `search_campus_facilities`) 노출. REST 와 같은 Spring Boot 프로세스에 공존.
  현업 self-hosted MCP 가 SSE 가 default 인 흐름 따름.
- MCP tool 입력 검증을 tool layer 에서 명시적으로 수행. `get_meal_by_date`
  의 빈/잘못된 date 가 raw `Text '' could not be parsed at index 0` 으로
  새던 것을 한국어 친절 메시지로 변환. 64자 query 길이 제한도 추가.
- `ConnectorException` 의 raw 메시지가 MCP client 에 그대로 새던 것을
  `ConnectorErrorMessages` 헬퍼로 ErrorCode 별 한국어 안내로 변환. 적용
  대상은 connector 호출하는 3 tool (`get_today_meal`, `get_meal_by_date`,
  `get_dorm_weekly_meal`) 만. `search_campus_facilities` 는 정적 데이터라 제외.
- REST 에 `GET /api/meals/weekly?startDate=YYYY-MM-DD` 추가. startDate
  생략 시 Asia/Seoul 이번 주 월요일 default. `IllegalArgumentException` 을
  `GlobalExceptionHandler` 에서 400 / `VALIDATION_FAILED` 로 매핑.
- `docs/mcp-tools.md` (0 bytes 였음) 첫 작성. 사용법 + Claude Desktop SSE
  직접 / `mcp-proxy` 어댑터 / Cursor 등록 + 트러블슈팅 + write tool 정책
  placeholder.
- ADR 0002~0005 retroactive 작성: meal fan-out 성능, MCP transport SSE 선택,
  MCP tool 입력 검증 정책, REST/MCP error UX 분리. 향후 큰 결정마다
  `docs/adr/` 에 archive 시작.
- 학식 + 기숙사 connector 트러블슈팅 7 사건 retroactive archive
  (`docs/troubleshooting/cafeteria-connector.md`). 사용자 기억이 흐려진
  상태라 git history 기반 narrative + 추정 부분 명시.
