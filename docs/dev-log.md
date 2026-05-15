# Dev Log

ssuAI 작업 진행 회고. 매 task 끝마다 한 줄씩 누적.
큰 결정은 별도로 `docs/adr/` 에 ADR 로 적는다.

## 2026-05-16

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
