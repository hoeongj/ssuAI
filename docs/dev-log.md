# Dev Log

ssuAI 작업 진행 회고. 매 task 끝마다 한 줄씩 누적.
큰 결정은 별도로 `docs/adr/` 에 ADR 로 적는다.

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
