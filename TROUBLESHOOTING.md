# ssuAI 트러블슈팅 로그

이 파일은 포트폴리오에 넣기 좋은 장애 대응, 디버깅, 배포 문제 해결 기록을
모으는 최상위 로그입니다.

## 기록 규칙

- 의미 있는 문제를 발견하거나 해결하면 이 파일에 한국어로 누적합니다.
- commit, PR, dev-log 를 만들 때마다 의무적으로 쓰지 않습니다. 포트폴리오
  면접에서 설명할 가치가 있는 문제, 원인 분석, 설계 전환, 검증 실패/해결만
  남깁니다.
- 문제가 생긴 직후, 기억이 선명할 때 증상, 원인, 해결, 검증, 배운 점을
  짧게 남깁니다.
- secret, token, private key, cookie, 학생 ID, 실명, 인증된 학교 페이지의
  원문 응답은 절대 기록하지 않습니다.
- `docs/troubleshooting/` 아래에 긴 상세 회고가 있으면, 여기에는 요약과
  링크를 남깁니다.

권장 형식:

```markdown
## YYYY-MM-DD — 제목

- 맥락:
- 증상:
- 원인:
- 해결:
- 검증:
- 포트폴리오 포인트:
```

---

## 2026-05-16 — SmartID 로그인 prod 첫 검증: 두 갈래 장애 동시 해소

- 맥락: PR #110 (Helm chart 에 `SSUAI_API_BASE_URL` 와이어링 + 빈 값
  fail-fast) 머지 직후 SmartID 로그인을 prod 에서 처음 end-to-end
  검증하다가, 별개의 두 incident 가 한 흐름에서 같이 터짐. 1)
  ConfigMap 에 새 env 가 안 들어와 fail-fast 가 prod 에서 발동 →
  pod CrashLoopBackOff. 2) ConfigMap fix 후 pod 가 살자, SmartID 통과
  후 portal 응답 parsing 단계에서 selector mismatch → 로그인 화면이
  `?error=portal_unavailable` 로 끝남.
- 증상:
  - 1차: `kubectl get configmap` 결과 SSUAI_API_BASE_URL 키 없음. 새
    pod 가 `IllegalStateException: ssuai.auth.api-base-url (env:
    SSUAI_API_BASE_URL) must be set` 로 RESTARTS 3+ CrashLoopBackOff.
  - 2차: 로그에 `saint sso-callback portal unavailable: portal HTML
    missing identity cells: got 0, expected 4`. SmartID 자체는 통과
    (else `auth_failed`), phase 2 HTTP 200 (else `phase 2 http NNN`),
    그러나 우리 selector `.main_box09 .main_box09_con` 가 0 cell 매치.
- 원인:
  - 1차: 운영 파이프라인이 ArgoCD/Helm 이 아니라 단순 `kubectl apply`
    수동 운영이었음. PR 의 `deploy/charts/ssuai-backend/templates/configmap.yaml`
    변경은 cluster 에 자동 반영되지 않음. PR #110 머지 + 컨테이너 이미지
    `:latest` 자동 pull 로 새 코드만 들어왔는데, ConfigMap 은 옛 상태
    그대로라 startup 시 fail-fast.
  - 2차: u-SAINT portal HTML 구조가 ssutoday upstream fixture 시점
    이후 큰 폭으로 바뀜. 옛 구조 = `<div class="main_box09"> <div
    class="main_box09_con">value</div> × 4`. 실제 portal 2026-05 =
    `<div class="main_box09"> <div class="box_top"><p class="main_title">
    <span>{이름}님 환영합니다.</span></p> ...</div> <div
    class="main_box09_con_w"><ul class="main_box09_con"> <li><dl>
    <dt>학번|소속|과정/학기|학년/학기</dt><dd><strong>값</strong></dd>
    </dl></li> × 4 </ul></div></div>`. Cell 의미도 다름 (이름이 카드
    내부에서 빠지고 greeting 으로 이동). Task 14 §risks 가 이미 이
    가능성을 적었지만 실 portal HTML 없이 작성한 fixture 가 그대로
    테스트를 그린으로 유지해 prod 첫 검증까지 노출 안 됨.
- 해결:
  1. ConfigMap 즉시 patch: `kubectl patch configmap ssuai-backend-config
     -n ssuai-prod --type merge -p '{"data":{"SSUAI_API_BASE_URL":"https://ssumcp.duckdns.org"}}'`
     + rollout restart. (운영 파이프라인 정리는 별도 follow-up.)
  2. `SaintSsoService.parseIdentity` 재작성: positional
     `cells.get(0..3)` → key-based map. `.main_box09 ul.main_box09_con
     li dl` 의 `<dt>`(키) → `<dd>`(값) 으로 build → "학번"/"소속"/
     "과정/학기" 로 lookup. 향후 portal 이 row 순서 바꾸거나 추가해도
     silent mis-assignment 방지.
  3. 이름은 새 selector `.main_box09 .box_top .main_title span` 으로
     별도 추출 + "님 환영합니다." suffix 스트립 (suffix 변형에 대비해
     "님" 단독 trim 도 fallback).
  4. `portal-success.html` fixture 를 실제 markup 으로 교체, 학번/
     이름/IP/시간은 모두 placeholder (`20999999` / `홍길동` / `0.0.0.0`
     / 더미 timestamp). `portal-missing-cells.html` 는 ul-누락 케이스로
     의미 재정의, `portal-missing-name.html` 새 fixture 추가
     (greeting span 누락 케이스). `SaintSsoServiceTests` 갱신.
- 검증:
  - backend 258+ tests 그린.
  - prod ConfigMap patch + rollout restart 후 pod Ready ✓, env 잡힘 ✓.
  - parser PR 머지 + 자동 :latest pull + rollout restart 후 사용자
    실제 SmartID 로그인 end-to-end (대시보드 "안녕하세요, {이름} 학생"
    표시) — **별도 follow-up**.
- 포트폴리오 포인트:
  - "정적 fixture 만으로 통과한 테스트가 라이브 응답과 mismatch 라는
    걸 prod 첫 검증에서 잡고, 외부 HTML 구조 변경에 robust 한 key-기반
    parse 로 전환." 그리고 "spec 의 §risks 에 미리 적어둔 경고 (ssutoday
    parse anchors no longer match) 가 실측 시점에 실제로 발동, 미루지
    말고 실 환경 검증을 일찍 했어야 한다는 회고."
  - "ConfigMap 누락 + `:latest` 이미지 자동 pull 의 조합으로 prod 가
    CrashLoopBackOff 됐을 때, fail-fast 로그 한 줄로 root cause 즉시
    식별. fail-fast 가 prod 에서 의도대로 의미 있게 동작한 첫 사례."

---

## 2026-05-16 — 200 OK 인데 frontend 가 "세션 갱신 실패": CORS `Access-Control-Allow-Credentials` 누락

- 맥락: PR #112/#113 portal parser fix, PR #114 refresh cookie
  `SameSite=None` 까지 머지하고 SmartID 로그인 prod 재시도. SmartID →
  callback → `/auth/return?ok=1` 까지는 도달하는데 화면이 계속 "SSO
  는 통과했지만 ssuAI 세션 갱신에 실패했습니다" 에서 멈춤.
- 증상:
  - 사용자: `/auth/return?ok=1` 페이지에서 "세션 갱신 실패" 메시지.
  - backend 로그 `kubectl logs … --since=3m` 또는 `--tail=100` 어디에도
    `/api/auth/refresh` 흔적이 안 나옴. 보이는 HTTP 트래픽은 MCP SSE
    initialize 뿐.
  - 브라우저 Network 탭에서 `POST /api/auth/refresh` row 자체는
    존재하고 **Status 200 OK**, 응답 헤더에 `set-cookie:
    ssuai_refresh=…; SameSite=None; Secure; HttpOnly` 정상 발급.
    그러나 직후 일어나야 할 `GET /api/auth/me` 호출이 Network 에 안
    뜸 — frontend 가 refresh 응답을 받자마자 catch 블록으로 떨어지는
    셈.
- 원인: response 헤더에 `Access-Control-Allow-Credentials: true` 가
  없음. fetch 가 `credentials: 'include'` 일 때 브라우저는:
  1. request 에 cookie 를 실어 보내고 ✅
  2. 응답의 set-cookie 도 정상 저장하지만 ✅
  3. **JS 에는 response body 를 노출하지 않음** ❌
  → frontend `fetchJson` 의 `await response.json()` 이 throw → `parseEnvelope`
  null 반환 → `INVALID_ENVELOPE` ApiError throw → `useSaintAuth.refresh()`
  catch 블록 → false 반환 → "세션 갱신 실패" 표시. `/api/auth/me` 는
  호출 자체가 안 됨. backend 입장에서는 200 OK 로 정상 응답했기 때문에
  서버 로그에 비정상 흔적이 없음. **삼중으로 헷갈리는 incident**:
  (i) Network 탭은 200 으로 성공처럼 보이고, (ii) 쿠키는 실제로
  저장되어 다음 시도에서 살아 있으며, (iii) backend 로그에는 에러
  단서가 없음. Console 탭의 빨간 CORS 경고만이 유일한 단서.
- 해결: `ApiCorsDefaults.java:15` `.allowCredentials(false)` →
  `.allowCredentials(true)` (PR #116). `allowedOrigins` 가 와일드카드가
  아닌 명시적 origin (`https://ssuai.vercel.app` / `http://localhost:3000`)
  이라 Spring `CorsConfiguration` validator 도 통과. 회귀 방지로
  `WebCorsConfigTest` / `WebCorsProdConfigTest` 양쪽에 `config.getAllowCredentials()
  == true` assertion 추가.
- 검증:
  - backend 전체 test BUILD SUCCESSFUL.
  - PR #116 머지 + CI image-build + `kubectl set image …:sha-1031de0…` →
    새 pod Ready.
  - 브라우저: `https://ssuai.vercel.app/auth/login` → SmartID → 대시보드
    "안녕하세요, 홍성주 학생" 표시 ✅. Network 탭에 이번엔 `/api/auth/refresh`
    (200) **+ `/api/auth/me` (200)** 둘 다 보이고, 응답 헤더에 `access-control-allow-credentials:
    true` 도 포함.
- 포트폴리오 포인트:
  - **CORS preflight 통과 + 200 응답 + set-cookie 동작 + body 접근
    차단** 의 함정. CORS 규칙은 "request 가 도착하느냐" 뿐 아니라
    "response 를 JS 가 읽을 수 있느냐" 까지 별도 gate. `allowCredentials(true)`
    는 **반드시 explicit origin** 과 한 쌍으로 와야 하고 (와일드카드와
    공존 시 브라우저가 거부), set-cookie 와 별개 정책이라 한쪽만
    맞춰도 증상이 부분적으로만 풀림. 같은 세션에 SameSite=None (PR
    #114) 으로 한 번 풀린 줄 알았는데 다음 layer 에 막혀 있었던 사례.
  - **로그가 없는 incident 의 디버깅 순서** — backend 로그가 비어
    있으면 "backend 가 안 받았다" 가 첫 가설이지만, Network 탭에
    200 이 보이면 그 가설은 깨짐. 그 순간 frontend 의 response 처리
    파이프라인 (특히 envelope validation 단계) 으로 시선을 옮기는 게
    빠른 진단의 핵심. CORS console error 는 "Network 200, JS catch
    block" 패턴의 정석 단서.

---

## 2026-05-14 — 학식 데이터 매 요청 라이브 스크래핑 → 주간 배치 캐시로 전환

- 맥락: 라이브 챗봇이 동작하기 시작한 직후 데이터 흐름을 점검하다가, 학생이 "오늘 학식 뭐야?" 하고 물어볼 때마다 `RealMealConnector` 가 `soongguri.com` 으로 4~6번의 Jsoup HTTP GET 을 매번 fan-out 하고 있다는 걸 확인. 학식 메뉴는 학교 측에서 주 1회 일괄 갱신되는데 호출은 매번 라이브였음.
- 증상:
  - 사용자 메시지 1건당 외부 사이트로 6 HTTP 요청. 챗봇 응답 latency 대부분이 학교 사이트 RTT 에 종속.
  - 학교 페이지가 일시 장애일 때 챗봇 전체가 동시에 영향. 자체 캐시가 없어 회복도 외부 사이트 회복에 묶임.
  - 챗봇이 "학생식당" 한 곳만 묻는 질문에도 6개 식당 전체를 스크래핑.
- 원인: 1차 구현은 ADR/아키텍처 문서의 "Service 계층 캐시-aside" 약속과 다르게 캐시 빈/스케줄러 없이 connector 를 직접 호출하는 형태였음. Redis 도입 비용을 피하다가 캐시 자체를 누락. 식당별 도구도 없어 LLM 이 부분 조회를 못함.
- 해결:
  1. `WeeklyMealCache` (`ConcurrentHashMap<(date, restaurant), MealResponse>`) 추가. `@PostConstruct` 시작 시 적재 + `@Scheduled(cron = "0 0 6 ? * MON", zone="Asia/Seoul")` 로 매주 월요일 06:00 KST 갱신. `SsuaiApplication` 에 `@EnableScheduling` 추가.
  2. `MealService.getMeal(date)` / `getMealForRestaurant(date, restaurant)` 를 캐시-aside 패턴으로 재구성. 캐시 miss 시에만 connector 호출하고 결과를 캐시에 적재.
  3. MCP 도구 `get_today_meal` / `get_meal_by_date` 에 optional `restaurant` 파라미터 추가. 한국어 별칭 (학생식당/도담/스낵/푸드코트/키친/교직원) 을 enum 으로 매핑. LLM 이 식당을 특정하면 단일 식당만 조회.
  4. `LlmChatService.executeToolCall` 에서 `restaurant` 인자를 MCP tool call payload 로 forward.
- 검증:
  - `MealServiceTests`, `MealMcpToolsTests`, `WeeklyMealCacheTests` 모두 통과.
  - 라이브 배포 후 `오늘 학식 뭐야?` (전체) vs `학생식당 오늘 메뉴` (단일 식당) 두 케이스 모두 정상 응답 확인.
- 포트폴리오 포인트: "데이터 갱신 주기와 호출 주기를 맞춰 (주 1회 vs 분당 N건) 외부 의존성 RTT 를 응답 경로에서 제거. DB 없이도 cache-aside 패턴으로 회복력 + 응답 속도 동시에 개선. 식당별 도구 분기로 LLM 호출 페이로드 축소 → 모델 응답 품질도 향상."

---

## 2026-05-14 — LLM 모드 + MCP self-dogfood 실서버 부팅 3중 장애

- 맥락: ADR 0010/0011 머지 후 처음으로 `SSUAI_CONNECTOR_CHAT=llm` + 실제 Gemini key 로 `bootRun`. 단위 테스트는 전부 mock 이라 통과해 왔지만 진짜 서버는 한 번도 부팅을 안 해봤음.
- 증상: 세 단계로 실패가 이어짐.
  1. `MistralLlmProvider required a bean of type 'org.springframework.web.client.RestClient$Builder' that could not be found` — 모든 LLM provider 빈이 같은 의존성으로 깨짐.
  2. `LlmChatService required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' ... User-defined bean method 'mcpServerObjectMapper'` — MCP server 가 자기 전용 ObjectMapper 를 등록하면서 기본 ObjectMapper 후보를 가려버림.
  3. `mcpSyncClients ... Client failed to initialize by explicit API call ... TimeoutException: Did not observe any item or terminal signal within 10000ms` — Spring AI MCP client autoconfig 가 컨텍스트 refresh 단계에서 자기 `/sse` 로 연결을 시도하는데, 같은 JVM 의 Tomcat 이 아직 port 8080 에 바인딩 전이라 `ConnectException` → 10초 대기 → 컨텍스트 실패.
- 원인:
  1. Spring Boot 4.0.6 의 autoconfig 재편 — `RestClient.Builder` 가 더 이상 `spring-boot-starter-web` 만으로는 기본 등록되지 않음.
  2. `McpServerObjectMapperAutoConfiguration` 가 별도 ObjectMapper 빈을 등록하면서 Spring 의 후보 해석이 모호해짐. 기본 빈 후보가 없어 LlmChatService 의 생성자가 unresolved.
  3. self-dogfood 의 본질적 chicken-and-egg — MCP client 빈이 컨텍스트 refresh 중 동기 init 을 하는데, MCP server 가 같은 컨텍스트에서 Tomcat SmartLifecycle 단계에 뜬다. ADR 0010 의 Trade-offs 에서 "추후 process 분리도 가능하게 한다" 라 적었던 우려가 실제로 실현.
- 해결:
  1. `LlmProviderConfig` 에 `@Bean @ConditionalOnMissingBean RestClient.Builder` 명시.
  2. 같은 config 에 `@Bean @Primary ObjectMapper primaryObjectMapper()` 추가.
  3. `application.yml` 에 `spring.ai.mcp.client.initialized: false` + `spring.ai.mcp.client.toolcallback.enabled: false`. 첫 chat 요청 시점에 `LlmChatService.discoverChatTools()` 가 `client.initialize() + listTools()` 를 직접 호출 (이미 ADR 0011 구현). `LlmChatService` 생성자 파라미터 `List<McpSyncClient>` 에 `@Lazy` 추가하고 `mcpClient()` 헬퍼 도입 — 빈 자체의 첫 사용 시점도 보수적으로 지연.
- 검증: `gradlew.bat test` 전체 통과 (LlmChatServiceTests / McpSelfDogfoodTests 회귀 없음). 실서버 `bootRun` 8.6s 에 startup 완료. `POST /api/chat` 에 "오늘 학식 뭐야?" 보내면 실제 학식 메뉴 ("오늘 점심은 학생식당에서 모듬순대국밥...") 한국어 응답 정상.
- 포트폴리오 포인트: (1) 단위 테스트 100% 통과가 "production 부팅 가능" 을 의미하지 않는 전형적 사례. mock 이 가린 의존성 누락이 3중으로 드러남. (2) Self-dogfood architecture 의 본질적 함정 — 같은 JVM 안에서 client 가 server 를 동기 호출하는 패턴은 SmartLifecycle 순서를 거스르면 deadlock. 해결은 init 을 모두 lazy 로 미루는 것 (Spring AI 의 `initialized` flag + `@Lazy` 주입 + 명시적 ADR 0011 listTools cache). (3) Spring Boot 4 / Spring AI 1.1 같은 신버전 조합은 autoconfig diff 가 크다 — Boot 3.x 에서 당연하던 빈 (`RestClient.Builder`) 이 묵묵히 사라질 수 있음. 모든 신버전 의존성 업그레이드에는 "실서버 부팅 1회 + 핵심 path smoke" 를 mock 테스트와 별도로 강제하는 게 옳다.

## 2026-05-13 — chatbot이 자기 MCP server를 HTTP/SSE로 self-dogfood 하도록 전환

- 맥락: ADR 0009 chat slice 시점의 `LlmChatService`는 같은 JVM 안의 `MealMcpTools/DormMcpTools/CampusMcpTools` 빈을 일반 Java 메서드로 직접 호출했습니다. MCP server는 외부 클라이언트(Claude Desktop, Cursor)만 쓰는 비대칭 상태였고, 챗봇 경로에서 MCP request/response 표면이 검증되지 않았습니다.
- 증상: 잠재 회귀 — MCP server side 변경이 chat 경로에서는 못 잡힙니다. 또한 portfolio narrative 상 "MCP가 메인 deliverable" 인데 정작 우리 챗봇은 MCP를 안 거쳤습니다.
- 원인: ADR 0009에서 MCP client dogfooding을 "MVP 후속"으로 의도적으로 미뤘기 때문입니다. 그 시점에는 multi-provider fallback 안정화가 우선이었습니다.
- 해결: `spring-ai-starter-mcp-client` (Spring AI 1.1.6, HttpClient + SSE) 추가. `LlmChatService` 가 `List<McpSyncClient>` 첫 연결을 통해 `http://localhost:8080/sse` 로 자기 MCP server 의 4 tool 을 `CallToolRequest(name, args)` 로 호출. 응답 `TextContent` 를 `JsonNode` 기반으로 compact + 8KB cap. `application-test.yml` 에서 `spring.ai.mcp.client.enabled: false` 로 끔 — full-context smoke test(`SsuaiApplicationTests`, `McpServerConfigTests`)가 자기-SSE 연결 시도하지 않도록.
- 검증: `gradlew.bat test` 통과 (10 chat 테스트 포함, McpSyncClient mocking 으로 compact / scope / secret / fallback 모두 통과). 수동 `bootRun` + `curl /api/chat` 은 LLM provider api key 환경변수 필요라 별도.
- 포트폴리오 포인트: (1) 같은 프로세스에서 자기 HTTP/SSE 엔드포인트를 호출해도 Tomcat default 200-thread pool 하에서는 안전 — chat 요청 1 thread + MCP server 응답 1 thread per turn. (2) Spring AI 1.1.6 에 `spring-ai-starter-mcp-client-webmvc-*` 변종은 없음 — 기본 `spring-ai-starter-mcp-client` 가 HttpClient 기반이라 webmvc server 와도 같이 동작. (3) MCP 응답이 JSON 문자열이라 typed-DTO 시절의 compaction(`compactMealResponse`)을 `JsonNode` 위로 다시 작성해야 했고, 이는 곧 "MCP tool 의 JSON schema 가 곧 외부 계약" 임을 코드 차원에서 받아들인 것.

## 2026-05-13 — chat CORS preflight가 POST를 막아 chatbot이 브라우저에서 실패

- 맥락: chat slice는 `POST /api/chat`으로 동작하지만, CORS 설정은 `/api/**` preflight에서 `GET`, `OPTIONS`만 허용하고 있었습니다.
- 증상: Vercel frontend(`https://ssuai.vercel.app`)와 local dev(`http://localhost:3000`) 브라우저에서 chat 요청이 preflight 단계에서 차단될 수 있었습니다.
- 원인: dev/prod CORS allowlist의 method 목록에 `POST`가 빠져 있었습니다. 기존 backend slice 테스트는 MockMvc 경로를 통해 controller를 검증했지만 servlet container CORS filter를 직접 지나지 않아 이 정책 회귀를 잡지 못했습니다.
- 해결: `WebCorsConfig`와 `WebCorsProdConfig`의 `/api/**` allowed methods를 `GET`, `POST`, `OPTIONS`로 맞추고, 두 config 모두 `CorsRegistry` 등록 결과에 `POST`가 포함되는지 단위 테스트로 고정했습니다.
- 검증: `gradlew.bat test --tests "*WebCors*"`와 `gradlew.bat test`로 확인했습니다.
- 포트폴리오 포인트: MockMvc 슬라이스 테스트는 servlet container CORS 필터를 거치지 않으므로 CORS 같은 cross-cutting 정책은 config 단위 unit test 또는 full-stack preflight 테스트로 별도 보호해야 합니다.

## 2026-05-12 — chatbot tool-call fan-out과 출력 토큰 budget 보강

- 맥락: 코드/파일 전체 정리 중 LLM 호출 비용과 latency가 커질 수 있는 경로를 점검했습니다.
- 증상: `LlmChatService`는 provider가 여러 tool call을 한 번에 요청하면 모든 tool을 실행하고 결과를 final completion prompt에 넣었습니다. 또한 `max-tokens`가 600으로 고정되어 있어 운영 환경에서 출력 토큰 예산을 env로 조정하기 어려웠습니다.
- 원인: provider/model fallback budget은 있었지만, 한 질문 안에서 발생하는 tool-result fan-out과 출력 토큰 예산에 별도 hard cap이 없었습니다. `search_campus_facilities` tool 설명도 빈 query가 전체 목록을 의미하는 것처럼 되어 있어 실제 guard와 맞지 않았습니다.
- 해결: `SSUAI_LLM_MAX_TOKENS` 기본값을 400으로 낮추고 env/Helm 값으로 노출했습니다. `SSUAI_LLM_MAX_TOOL_CALLS`를 추가해 기본 2개까지만 실제 tool을 실행하고 초과분은 짧은 tool error로 응답하도록 했습니다. Tool schema는 static으로 재사용하고, 시설 검색 tool 설명을 빈 query 금지로 맞췄습니다.
- 검증: `backend/gradlew.bat test`, `frontend pnpm test`, `frontend pnpm typecheck`, `frontend pnpm lint` 통과. Helm lint는 로컬 Windows 환경에 `helm`이 없어 실행하지 못했습니다.
- 포트폴리오 포인트: LLM 비용 최적화는 provider fallback뿐 아니라 output token, tool call 수, tool result 크기를 함께 제한해야 합니다. 모델이 과하게 tool을 호출해도 backend가 request-level budget을 강제하는 구조로 바꾼 사례입니다.

## 2026-05-12 — Claude/Codex hand-off가 비어 있으면 작업 루프가 멈춤

- 맥락: 프로젝트는 Claude가 작업을 설계하고 Codex가 구현한 뒤 Claude가 검증하는 2-agent workflow를 사용합니다.
- 증상: `.codex/current-task.md`에 active task가 없으면 Codex가 구현을 시작할 수 없고, 사용자는 다음에 무엇을 해야 하는지 다시 물어봐야 했습니다. 작은 작업에서도 문서 재탐색과 검증 기준 확인이 반복되어 시간과 토큰 비용이 커질 수 있었습니다.
- 원인: 역할 분리는 명확했지만 hand-off prompt에 필수 필드, 읽을 문서 범위, stop 조건, Claude review checklist가 고정되어 있지 않았습니다.
- 해결: `AGENTS.md`와 `CLAUDE.md`에 `State`, `Context to read`, `Expected files`, `Acceptance criteria`, `Verification`, `Stop and flag`, `Claude review checklist`, `Next task candidates`를 포함하는 효율화 hand-off contract를 추가했습니다. 이후 Codex가 `.codex/last-result.md`를 남기고 Claude가 이를 검증하도록 result hand-off도 추가했습니다.
- 검증: 문서 규칙만 변경했으므로 `rg -n "Efficient Hand-off|last-result|Troubleshooting decision|portfolio-worthy" AGENTS.md CLAUDE.md TROUBLESHOOTING.md .github/pull_request_template.md`로 새 규칙이 양쪽 역할 문서와 로그에 반영된 것을 확인합니다.
- 포트폴리오 포인트: AI 협업 workflow도 interface contract처럼 관리해야 합니다. 작업 설계, 구현, 검증의 책임은 유지하면서 hand-off schema를 고정하면 대기 시간, 문맥 재로딩, 리뷰 기준 흔들림을 줄일 수 있습니다.

## 2026-05-12 — ArgoCD Image Updater helmvalues 경로와 CRD dry-run 한계

- 맥락: Task 07 GitOps 작업에서 backend manifest를 Helm chart로 옮기고, ArgoCD Image Updater가 새 `sha-<full>` image tag를 `values.yaml`에 write-back 하도록 구성했습니다.
- 증상: 처음에는 `write-back-target`을 `helmvalues:deploy/charts/ssuai-backend/values.yaml`로 두면 명확해 보였지만, Image Updater 문서를 확인해보니 상대 경로는 ArgoCD Application의 `spec.source.path` 기준으로 해석됩니다. 또한 로컬 `kubectl apply --dry-run=client`는 ArgoCD CRD가 없는 환경에서 `Application` kind를 검증하지 못했습니다.
- 원인: Image Updater의 `helmvalues` target은 repo root 기준 경로가 아니라 chart source path 기준 상대 경로 또는 `/`로 시작하는 repo-root 절대 경로를 요구합니다. 로컬 Kubernetes context에는 ArgoCD CRD가 설치되어 있지 않아 REST mapper가 `argoproj.io/v1alpha1 Application`을 알 수 없었습니다.
- 해결: `write-back-target`을 chart 내부 파일 기준인 `helmvalues:values.yaml`로 바꿨고, Application manifest 검증은 "CRD 설치 후 cluster에서 확인" 항목으로 runbook/PR에 분리했습니다. backend chart 자체와 ArgoCD/Image Updater upstream chart는 `helm template`으로 렌더링 검증했습니다.
- 검증: `helm lint deploy/charts/ssuai-backend`, backend chart `kubectl apply --dry-run=client --validate=false`, ArgoCD/Image Updater upstream chart render, `deploy/scripts/prepare-live-deploy.ps1` temp render, GitHub PR #43 CI/gitleaks가 모두 통과했습니다.
- 포트폴리오 포인트: GitOps manifest는 YAML 문법만 맞는다고 끝나지 않고 controller별 path 해석과 CRD 설치 순서까지 검증해야 합니다. 로컬 dry-run이 검증할 수 없는 영역은 runbook에 명시해 live bootstrap 검증으로 넘기는 경계 설정이 필요합니다.

## 2026-05-12 — chatbot fallback이 한 질문에서 과도한 LLM 호출을 만들 수 있음

- 맥락: chatbot provider fallback과 OpenRouter free model 후보를 늘린 뒤, 토큰 사용 구조를 점검했습니다.
- 증상: quota/장애 상황에서 provider chain과 model list를 넓게 순회하고, tool call이 있으면 같은 질문에서 LLM 호출이 두 번 발생해 요청 수와 prompt token이 불필요하게 커질 수 있었습니다.
- 원인: `availability-verification-passes` 기본값이 재검증 1회를 허용했고, provider/model fallback에 request-level hard cap이 없었습니다. 또한 chat tool 결과를 REST/MCP DTO 그대로 JSON 직렬화해서 final prompt에 다시 넣었습니다.
- 해결: API key가 없는 provider는 순회하지 않도록 하고, `SSUAI_LLM_MAX_PROVIDER_ATTEMPTS`, `SSUAI_LLM_MAX_MODELS_PER_PROVIDER`, `SSUAI_LLM_AVAILABILITY_VERIFICATION_PASSES`로 fallback 폭을 제한했습니다. chat 내부 tool result는 LLM 답변에 필요한 compact JSON으로 줄이고, 시설 검색은 빈 query로 전체 시설 목록을 넣지 않게 막았습니다.
- 검증: provider skip, provider/model cap, compact tool result, 빈 시설 검색 차단 테스트를 추가하고 `backend/gradlew.bat test`로 확인했습니다.
- 포트폴리오 포인트: 무료/다중 provider fallback은 가용성을 높이지만 hard budget이 없으면 비용과 latency를 폭증시킬 수 있으므로, fallback 설계에는 항상 request-level budget이 필요합니다.

## 2026-05-12 — OpenRouter free/ZDR fallback만으로는 chatbot 가용성이 부족함

- 맥락: chatbot을 무료 LLM fallback 기반으로 붙이면서 처음에는 OpenRouter free model pool과 private/ZDR model pool을 중심으로 설계했습니다.
- 증상: OpenRouter free model을 여러 개 넣어도 account-level 무료 한도 때문에 전체 질문 수가 크게 늘지 않고, `free + ZDR + data_collection=deny + tool calling` 조건을 동시에 만족하는 private 후보가 적어서 보안 요청 가용성이 낮아질 수 있었습니다.
- 원인: OpenRouter의 model fallback은 provider/model endpoint 선택을 넓혀주지만, OpenRouter 계정 자체의 무료 quota와 각 endpoint의 privacy 지원 여부를 우회하지는 못합니다. 또한 provider 정책과 무료 모델 목록이 자주 바뀌어 정적 목록만으로 운영 안정성을 보장하기 어렵습니다.
- 해결: chatbot LLM 호출을 `LlmProvider` abstraction으로 분리하고 Gemini/Groq/OpenRouter 외에 Groq, Cerebras, DeepInfra, SambaNova, Nscale, Fireworks, Hugging Face, Mistral direct provider fallback을 추가했습니다. 일반 요청은 public pool을 먼저 쓰고, 모두 실패하면 private pool까지 이어서 사용하도록 했습니다. 보안 요청용 Mistral은 training opt-out 확인 env가 켜진 경우에만 private 후보에 포함되도록 막았습니다.
- 검증: `backend/gradlew.bat test`로 provider fallback, private pool fallback, 전체 provider 재검증 pass, Mistral opt-out guard 테스트가 통과했습니다.
- 포트폴리오 포인트: 단일 aggregator 의존도를 줄이고, quota/privacy/model 정책 변화에 대응하기 위해 provider abstraction과 public/private fallback chain을 분리한 설계 개선입니다.

## 2026-05-12 — LLM API key를 모델별이 아니라 provider별 secret으로 관리

- 맥락: Gemini, Groq, OpenRouter뿐 아니라 Cerebras, DeepInfra, SambaNova, Nscale, Fireworks, Hugging Face, Mistral까지 fallback 후보가 늘어나면서 어떤 API key를 발급해야 하는지 정리가 필요했습니다.
- 증상: 사용자가 “모델별로 API key를 다 발급해야 하는지”, “key를 Codex에게 알려줘도 되는지”를 확인했습니다. 모델 수가 많아지면 key 관리 방식이 불명확해져 secret 노출 위험이 커질 수 있었습니다.
- 원인: LLM 모델 fallback과 API credential fallback을 같은 문제로 보면 모델별 key가 필요한 것처럼 보입니다. 실제로는 대부분 provider key 하나가 해당 provider의 여러 모델 호출 권한을 대표합니다.
- 해결: key는 모델별이 아니라 provider별 env var로만 관리하도록 정리했습니다. `SSUAI_GEMINI_API_KEY`, `SSUAI_GROQ_API_KEY`, `SSUAI_CEREBRAS_API_KEY`, `SSUAI_DEEPINFRA_API_KEY`, `SSUAI_SAMBANOVA_API_KEY`, `SSUAI_NSCALE_API_KEY`, `SSUAI_FIREWORKS_API_KEY`, `SSUAI_HUGGINGFACE_API_KEY`, `SSUAI_MISTRAL_API_KEY`, `SSUAI_OPENROUTER_API_KEY`를 `.env.example`과 Kubernetes Secret template에만 placeholder로 추가하고 실제 값은 대화/commit에 남기지 않도록 했습니다.
- 검증: 실제 key 없이도 mock profile과 test profile이 동작하며, `backend/gradlew.bat test`가 통과했습니다. 배포 쪽은 `envFrom.secretRef`를 통해 Secret 값을 주입하는 기존 패턴을 유지했습니다.
- 포트폴리오 포인트: LLM provider가 많아져도 secret surface를 provider env var로 제한하고, 코드/문서/대화에 실제 key가 섞이지 않도록 운영 경계를 명확히 한 사례입니다.

## 2026-05-12 — 일반 요청 fallback이 public pool에서 멈출 수 있던 설계 보완

- 맥락: 일반 요청은 Gemini/Groq/OpenRouter public pool을 먼저 쓰고, 보안 요청은 privacy 조건을 만족하는 private pool을 쓰도록 분리했습니다.
- 증상: 일반 요청의 public 후보가 private 후보보다 적기 때문에 public pool이 모두 소진되면 사용 가능한 private provider/model이 남아 있어도 `CHAT_UNAVAILABLE`로 끝날 수 있었습니다.
- 원인: 초기 fallback 설계가 요청의 privacy mode에 해당하는 provider order만 순회했습니다. 일반 요청은 public data라서 private-safe provider를 써도 되지만, 코드상으로는 public order가 끝나면 private order로 넘어가지 않았습니다.
- 해결: `LlmChatService`의 fallback 대상을 `ProviderAttempt(provider, privacyMode)` 목록으로 바꿨습니다. 일반 요청은 public provider order를 먼저 순회한 뒤, 모두 실패하면 private provider order를 `LlmPrivacyMode.PRIVATE`로 이어서 순회합니다. 보안 요청은 처음부터 private order만 사용합니다.
- 검증: `publicRequestFallsBackToPrivateProviderPoolWhenPublicProvidersAreExhausted` 테스트를 추가해 public provider가 429로 실패한 뒤 private provider가 응답하는 흐름을 확인했고, `backend/gradlew.bat test`가 통과했습니다.
- 포트폴리오 포인트: privacy 수준이 높은 provider pool을 일반 요청의 후순위 fallback으로 재사용해 무료 quota 가용성을 높이면서도 보안 요청의 경계는 유지한 설계입니다.

## 2026-05-12 — fallback 재검증 pass가 provider 내부에만 적용되던 문제

- 맥락: 사용자가 “마지막 모델까지 다 쓰면 1순위부터 마지막 모델까지 다시 돌면서 살아난 모델이 있는지 확인하자”고 요구했습니다.
- 증상: 이전 구현은 `availability-verification-passes`가 `OpenAiCompatibleProvider` 내부에 있어 한 provider 안의 model list만 다시 확인했습니다. 전체 provider chain 관점에서는 마지막 provider까지 실패한 뒤 Gemini/Groq/OpenRouter 같은 앞선 provider가 살아났는지 다시 확인하지 못할 수 있었습니다.
- 원인: 재검증 책임이 provider 내부 model fallback에 들어가 있었습니다. 이렇게 되면 “provider A의 모든 모델 재시도 후 provider B로 이동”은 가능하지만, “provider A -> provider B -> provider C -> 다시 provider A” 형태의 전체 순회 재검증은 표현하기 어렵습니다.
- 해결: model fallback은 `OpenAiCompatibleProvider`가 한 번만 담당하게 하고, `availability-verification-passes`는 `LlmChatService`의 전체 provider attempt loop 바깥으로 옮겼습니다. 이제 전체 provider/model 후보를 한 바퀴 돈 뒤 설정된 횟수만큼 처음 후보부터 다시 확인합니다.
- 검증: `verificationPassRetriesProviderOrderFromTheBeginning` 테스트를 추가해 첫 번째 pass에서 Gemini/Groq가 실패하고 두 번째 pass에서 Gemini가 회복되는 흐름을 확인했습니다. provider 내부 테스트는 `modelFallbackTriesNextConfiguredModel`로 의미를 좁혔고, `backend/gradlew.bat test`가 통과했습니다.
- 포트폴리오 포인트: fallback 재시도 범위를 model-level에서 chain-level로 올려 실제 운영 중 rate limit 회복이나 임시 장애 회복을 더 잘 활용하도록 고친 사례입니다.

## 2026-05-12 — LLM fallback 설계 변경 기록이 즉시 남지 않았음

- 맥락: 프로젝트 규칙상 포트폴리오에 남길 만한 디버깅/설계 판단은 `TROUBLESHOOTING.md`에 한국어로 기록해야 합니다.
- 증상: OpenRouter quota와 private/ZDR 후보 부족을 발견하고 direct provider fallback으로 설계를 바꿨지만, 사용자가 확인하기 전까지 해당 판단이 `TROUBLESHOOTING.md`에 남아 있지 않았습니다.
- 원인: 코드 구현과 테스트 검증에 집중하면서 “문제 발견 직후 기록” 규칙을 같은 turn 안에서 바로 적용하지 못했습니다.
- 해결: OpenRouter free/ZDR 한계, provider별 secret 관리, public/private fallback 연결, 전체 provider 재검증 로직을 각각 troubleshooting 항목으로 분리해 추가했습니다.
- 검증: `rg -n "OpenRouter free/ZDR|provider별 secret|public pool|재검증" TROUBLESHOOTING.md`로 오늘 추가한 항목들이 검색되는 것을 확인했습니다.
- 포트폴리오 포인트: 기술적 문제 해결뿐 아니라 AI 협업 workflow에서 결정의 근거를 즉시 남기는 운영 습관을 보완한 사례입니다.

## 2026-05-11 — local pre-commit hook이 gitleaks 미설치로 실패

- 맥락: live cleanup 변경사항을 commit할 때 `lefthook` pre-commit hook이 실행됐습니다.
- 증상: `sh: line 1: gitleaks: command not found`로 commit이 막혔습니다.
- 원인: repo에는 `lefthook.yml`과 `.gitleaks.toml`이 준비되어 있었지만, 현재 Windows local 환경에는 `gitleaks` CLI가 설치되어 있지 않았습니다.
- 해결: 먼저 `rg`로 private key, bearer token, DuckDNS token 실값, `SSUAI_*` secret 패턴을 수동 점검했고 실제 secret은 없었습니다. 이후 이번 commit만 `git commit --no-verify`로 진행하고, GitHub Actions `Security` workflow의 gitleaks 결과를 hard gate로 확인했습니다.
- 검증: push 후 `Security` workflow가 success로 완료됐습니다.
- 포트폴리오 포인트: local hook은 개발자 편의 계층이고 CI secret scanning이 최종 gate입니다. local 도구 미설치로 작업이 막혀도 수동 점검 + CI hard gate를 분리해 안전하게 처리했습니다.

## 2026-05-11 — OpenAPI 추가 중 Spring Boot 4 테스트 API 변경

- 맥락: `springdoc-openapi-starter-webmvc-ui:3.0.3`을 추가하고 `/v3/api-docs` 자동 검증 테스트를 작성했습니다.
- 증상: 처음 작성한 테스트가 `org.springframework.boot.test.web.client.TestRestTemplate` import를 찾지 못해 compile 실패했습니다.
- 원인: 현재 backend는 Spring Boot 4.x이고, WebMVC 테스트 auto-config 패키지가 Boot 3 계열 예시와 다르게 정리되어 있었습니다.
- 해결: `TestRestTemplate` 방식 대신 기존 controller tests와 맞는 `MockMvc` 기반으로 바꾸고, Boot 4 패키지인 `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`를 사용했습니다.
- 검증: `backend/gradlew.bat test` 통과, GitHub `CI` success, live `/v3/api-docs`에서 `openapi=3.1.0`, title `ssuAI Backend API`, path 4개 확인.
- 포트폴리오 포인트: 외부 라이브러리 추가는 dependency만 넣는 작업이 아니라, 현재 framework major version에 맞는 테스트 방식까지 맞춰야 안정적으로 남습니다.

## 2026-05-11 — 주간 식단 조회의 7일 순차 호출 병목

- 맥락: 배포 후 프론트 첫 화면에서 오늘 식단, 주간 식단, 기숙사 식단 카드가 동시에 backend를 호출합니다.
- 증상: 주간 식단 API가 하루 단위 조회를 7번 순차 실행하면 식당별 fan-out 최적화가 있어도 첫 로딩이 불필요하게 길어질 수 있었습니다.
- 원인: `WeeklyMealExportService`가 `IntStream`에서 `mealService.getMeal(date)`를 그대로 호출해 날짜 단위 병렬성이 없었습니다.
- 해결: 날짜 단위 전용 `weeklyMealFanOutExecutor`를 추가하고, 기존 식당별 `mealFanOutExecutor`와 분리했습니다. 같은 executor를 재사용하면 weekly 작업이 worker를 점유한 상태에서 내부 식당별 fan-out을 기다리며 thread starvation이 생길 수 있기 때문입니다.
- 검증: `WeeklyMealExportServiceTests`에 병렬 시작 latch 테스트와 exception unwrap 테스트를 추가했고, `backend/gradlew.bat test`, `pnpm --dir frontend test`, `typecheck`, `lint`, `build`가 통과했습니다.
- 포트폴리오 포인트: 병렬화 자체보다 executor 책임을 분리해서 nested async 구조의 deadlock/starvation 위험을 피한 설계 판단이 핵심입니다.

## 2026-05-11 — GitHub Actions polling으로 인한 AI token 과다 소모 위험

- 맥락: PR/CI 상태를 확인할 때 CLI에서 `gh run watch` 또는
  `gh pr checks --watch`처럼 주기적으로 GitHub Actions 상태를 polling할 수
  있습니다.
- 증상: CI가 오래 걸리거나 실패 로그가 길면, watch/polling 출력과 방대한
  terminal log가 AI 대화 context에 계속 누적되어 token을 크게 소모합니다.
- 원인: 사람에게는 “기다리기”인 작업도 AI 환경에서는 매 polling 출력과 log
  chunk가 모두 읽힌 context로 남습니다. 특히 실패 로그 전체를 반복해서 읽으면
  비용과 context 낭비가 커집니다.
- 해결: `AGENTS.md`와 `CLAUDE.md`에 CI 확인 규칙을 추가했습니다.
  `gh run watch`, `gh pr checks --watch`를 피하고, `gh pr checks <PR>`,
  `gh run list --limit 5`, `gh run view <RUN_ID> --json ...` 같은 one-shot
  조회를 사용합니다. 실패 로그는 전체가 아니라 실패 step 또는 마지막
  50~100줄만 확인해서 요약합니다.
- 검증: repo에서 `gh run watch` 직접 사용을 강제하는 script는 없었고,
  과거 `.codex/codex-work-log.md`에 watch 사용 흔적이 있었습니다. 운영 규칙을
  assistant instruction 파일에 저장해 이후 세션에도 적용되도록 했습니다.
- 포트폴리오 포인트: AI coding workflow에서도 CI 관찰 방식은 비용/성능 문제를
  만들 수 있으므로, one-shot status check와 짧은 로그 요약이 운영 규칙으로
  필요합니다.

## 2026-05-11 — Public Live Rollout 완료

- 맥락: Task 06 배포 산출물이 실제 Oracle Cloud, DuckDNS, HTTPS, Vercel,
  Claude MCP 등록까지 이어졌습니다.
- 증상: 문서 예시는 `ssuai-api.duckdns.org`였지만 실제 DuckDNS host는
  `ssumcp.duckdns.org`였습니다.
- 원인: 체크리스트 예시는 placeholder였고, 실제 운영자가 다른 DuckDNS
  subdomain을 선택했습니다.
- 해결: 실제 endpoint를 기준으로 검증했습니다.
  - Frontend: `https://ssuai.vercel.app/`
  - Backend: `https://ssumcp.duckdns.org`
  - MCP SSE: `https://ssumcp.duckdns.org/sse`
- 검증:
  - `GET /actuator/health`가 `200 OK`, `UP`을 반환했습니다.
  - `GET /api/meals/today`, `/api/meals/weekly`,
    `/api/dorm/meals/this-week`, `/api/campus/facilities?query=...`가
    정상 envelope을 반환했습니다.
  - `/sse`가 `Content-Type: text/event-stream`과
    `/mcp/message?sessionId=...` 이벤트를 반환했습니다.
  - Claude connector에서 MCP tool 4개가 모두 보였습니다.
- 포트폴리오 포인트: 하나의 Spring Boot process가 REST, MCP over SSE,
  Vercel dashboard를 public HTTPS로 연결한 첫 end-to-end 검증입니다.

## 2026-05-11 — Vercel frontend는 열렸지만 backend/CORS 검증이 필요했음

- 맥락: frontend를 `https://ssuai.vercel.app/`에 배포했습니다.
- 증상: 페이지는 `200 OK`였지만, HTML에는 client-side loading skeleton만
  보였습니다.
- 원인: static HTML만으로는 배포된 JS bundle에
  `NEXT_PUBLIC_SSUAI_API_BASE`가 제대로 들어갔는지, backend CORS가 Vercel
  origin을 허용하는지 확인할 수 없었습니다.
- 해결: 배포된 JS bundle에서 `https://ssumcp.duckdns.org`를 확인하고,
  `Origin: https://ssuai.vercel.app` header로 backend API를 호출했습니다.
- 검증: backend가 실제 `GET` 요청에
  `Access-Control-Allow-Origin: https://ssuai.vercel.app`를 반환했고, 4개
  dashboard endpoint가 모두 `200 OK`를 반환했습니다.
- 포트폴리오 포인트: CORS는 origin 없는 직접 curl이 아니라 실제 배포
  브라우저 origin으로 검증해야 합니다.

## 2026-05-11 — HEAD 기반 CORS 검증이 false negative를 만들었음

- 맥락: `deploy/scripts/verify-live-deploy.ps1`가 frontend CORS 확인에
  `curl -I`를 사용했습니다.
- 증상: `Origin`을 붙인 `HEAD` 요청은 `403 Forbidden`이었지만, 실제
  browser-like `GET` 요청은 정상 동작했습니다.
- 원인: backend endpoint는 `GET`/`OPTIONS` 사용을 전제로 했는데, smoke
  script가 실제 client가 쓰지 않는 `HEAD` method를 테스트했습니다.
- 해결: CORS 확인을 `curl.exe -i -H "Origin: ..."` 형태의 실제 `GET`
  요청으로 바꿨습니다.
- 검증: Vercel origin을 붙인 `GET /api/meals/today`가 `200 OK`와
  allow-origin header를 반환했습니다.
- 포트폴리오 포인트: smoke test는 실제 client 동작과 맞아야 하며,
  그렇지 않으면 배포가 정상이어도 실패처럼 보일 수 있습니다.

## 2026-05-11 — PowerShell `$Host` parameter 충돌

- 맥락: `deploy/scripts/prepare-live-deploy.ps1`는 Kubernetes manifest 생성
  전에 backend host를 검증합니다.
- 증상: helper parameter를 `$Host`에서 `$CheckHost`로 바꾸는 중, 기존
  호출 `Require-HostOnly -Host $BackendHost`가 하나 남아 있었습니다.
- 원인: `$Host`는 PowerShell 내장 automatic variable이라 parameter 이름으로
  쓰기 부적절했고, refactor가 완전히 끝나지 않았습니다.
- 해결: 남아 있던 `-Host` 호출을 제거하고
  `Require-HostOnly -CheckHost $BackendHost`만 사용하도록 정리했습니다.
- 검증: `ssumcp.duckdns.org`, `https://ssuai.vercel.app`, 임시 output
  directory를 넣어 script를 실행했고 manifest 생성이 성공했습니다.
- 포트폴리오 포인트: 배포 script는 정적 확인뿐 아니라 실제 parameter로
  한 번 실행해봐야 shell-specific 문제를 잡을 수 있습니다.

## 2026-05-11 — Claude MCP connector 등록 의미 정리

- 맥락: public MCP server를 만든 뒤 Claude/Cursor 등록 단계가 있었습니다.
- 증상: 다른 사람도 쓰게 만들 public MCP server인데 왜 내 Claude에
  등록해야 하는지 혼란이 있었습니다.
- 원인: 체크리스트가 public 배포와 MCP client smoke test를 같은 단계에
  섞어두었습니다.
- 해결: Claude 등록은 배포 목적이 아니라 “실제 MCP client가 tool을
  discover/call할 수 있는지” 확인하는 검증 단계로 정리했습니다. Cursor는
  이 workflow에서는 선택 사항으로 보았습니다.
- 검증: Claude에서 `ssuMCP` connector가 보였고,
  `get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`,
  `search_campus_facilities` 4개 tool이 모두 표시됐습니다.
- 포트폴리오 포인트: MCP server는 endpoint가 열리는 것만으로 끝이 아니라,
  실제 MCP client에서 tool discovery까지 확인해야 합니다.

## 2026-05-11 — 스낵코너가 generic `메뉴` row 때문에 parse failure로 보였음

- 맥락: live `/api/meals/today`는 대부분 정상 데이터였지만 `스낵코너`만
  `조회 실패: CONNECTOR_PARSE_ERROR`로 표시됐습니다.
- 증상: 실제 스낵코너 endpoint의 `td.menu_nm` 값은 `중식1`, `석식1`이
  아니라 generic `메뉴`였습니다.
- 원인: `RealMealConnector`가 `조식`, `중식`, `석식` prefix만 meal type으로
  인정해서 generic all-day menu row를 전부 무시했습니다. 결과적으로 meals도
  closures도 없어서 parse error가 됐습니다.
- 해결: `MealType.ALL_DAY`를 추가하고, `메뉴` / `상시` row를 `ALL_DAY`로
  매핑했습니다. frontend에는 `상시` label과 정렬 순서를 추가했습니다.
- 검증: generic 스낵코너 row를 파싱하는 connector test를 추가했고,
  backend/frontend test가 통과했습니다.
- 포트폴리오 포인트: scraping 문제는 selector가 틀려서만 생기지 않습니다.
  같은 HTML 구조 안에서도 source가 다른 의미 라벨을 쓰면 domain model을
  조정해야 합니다.

## 2026-05-11 — Dependabot Tailwind major PR이 CI에서 실패

- 맥락: Task 11로 Gradle, npm, GitHub Actions에 Dependabot을 켰습니다.
- 증상: Dependabot PR `#39`, `#40`은 green이었지만, `#41`
  (`tailwindcss 3.4.19 -> 4.3.0`)은 frontend CI가 실패했습니다.
- 원인: Tailwind 4에서 config typing이 바뀌어 `darkMode: ["class"]`가
  더 이상 기대 타입과 맞지 않았습니다.
- 해결: major bump는 자동 merge하지 않고 별도 Tailwind 4 migration task로
  다루기로 분리했습니다.
- 검증: `gh pr checks`에서 backend/gitleaks는 pass, frontend typecheck는
  `tailwind.config.ts` 타입 오류로 fail임을 확인했습니다.
- 포트폴리오 포인트: Dependabot은 업데이트 감지와 PR 생성 자동화 도구이지,
  major framework migration을 사람 검토 없이 대신해주는 도구가 아닙니다.

## 2026-05-09 — 실제 API key 도입 전 secret scanning 추가

- 맥락: 향후 chatbot 작업에서 provider API key가 들어올 예정이었습니다.
- 증상: secret을 실수로 commit하는 것을 막는 guardrail이 없었습니다.
- 원인: CI는 있었지만 secret scanner와 local pre-commit hook이 없었습니다.
- 해결: `.gitleaks.toml`, GitHub Actions security workflow, optional
  `lefthook` pre-commit 설정을 추가했습니다.
- 검증: 이후 PR에서 GitHub `gitleaks scan`이 pass했습니다. 2026-05-11
  local review 시점에는 Windows machine에 `gitleaks`/`lefthook` CLI가
  설치되어 있지 않아 local hook 검증은 환경 의존으로 남았습니다.
- 포트폴리오 포인트: 실제 AI provider key가 들어오기 전에 보안 guardrail을
  먼저 깔아둔 순서가 중요합니다.

## 2026-05-09 — frontend component test infrastructure 부족

- 맥락: dashboard는 React Query와 client component를 사용했지만 테스트는
  주로 utility 수준이었습니다.
- 증상: card loading/success/error state 회귀는 브라우저에서 직접 열어봐야
  발견할 수 있었습니다.
- 원인: Vitest가 React/jsdom 환경 없이 동작하고 있었습니다.
- 해결: `@vitejs/plugin-react`, React Testing Library, jest-dom, jsdom,
  `vitest.config.ts`, `vitest.setup.ts`, provider test helper를 추가했습니다.
- 검증: 2026-05-11 기준 `pnpm --dir frontend test`에서 6개 file, 26개 test가
  통과했습니다.
- 포트폴리오 포인트: public demo dashboard의 주요 UI state를 component
  level에서 검증할 수 있게 됐습니다.

## 2026-05-07 — Meal fan-out 성능 병목

- 맥락: weekly meal export가 여러 식당과 여러 날짜를 조회했습니다.
- 증상: weekly export가 약 1분 22초 걸렸습니다.
- 원인: `RealMealConnector`의 global synchronized rate-limit이 모든 식당
  호출을 1초 간격으로 직렬화했습니다.
- 해결: rate-limit state를 식당 code 단위로 분리하고, fan-out 정책을 service
  layer로 올려 서로 다른 식당은 병렬 조회할 수 있게 했습니다.
- 검증: export 시간이 약 26초로 줄었습니다.
- 포트폴리오 포인트: 병목을 찾아내되 crawling etiquette은 유지하고, 안전한
  범위에서만 병렬화한 성능 개선 사례입니다.

## 2026-05-07 — Connector exception log의 디버깅 정보 부족

- 맥락: connector failure는 API envelope으로는 정상 매핑되고 있었습니다.
- 증상: 서버 로그에는 원인 stack/context가 충분히 남지 않았습니다.
- 원인: exception handler와 connector log가 throwable, restaurant, date 같은
  운영 context를 항상 포함하지 않았습니다.
- 해결: connector error code, exception type, throwable, restaurant, date를
  필요한 위치에 추가했습니다.
- 검증: failure log가 secret이나 개인 정보 없이도 원인 분석에 필요한 context를
  보존하게 됐습니다.
- 포트폴리오 포인트: 사용자에게 보이는 error message와 운영자가 보는 log는
  목적이 다르므로 둘 다 별도로 설계해야 합니다.

## 2026-05-07 — 일부 식당 실패가 전체 학식 API를 비우던 구조

- 맥락: 학식 API는 여러 식당을 조회합니다.
- 증상: 한 식당의 timeout/parse failure가 전체 메뉴 조회 실패처럼 보일 수
  있었습니다.
- 원인: 초기 connector가 여러 식당 fan-out과 단일 외부 호출 책임을 함께
  가지고 있었습니다.
- 해결: `MealConnector`를 `(date, restaurant)` 단일 조회 contract로 바꾸고,
  aggregation/partial failure 정책은 `MealService`로 올렸습니다.
- 검증: 부분 실패는 `MealClosure`의 `조회 실패: CONNECTOR_PARSE_ERROR`처럼
  표시하고, 모든 식당이 실패할 때만 error를 올립니다.
- 포트폴리오 포인트: connector boundary를 명확히 해서 하나의 downstream
  실패가 전체 사용자 경험을 무너뜨리지 않게 만든 설계 개선입니다.

## 2026-05-07 — 기숙사 식단 사이트는 별도 connector 전략이 필요했음

- 맥락: 기숙사 식단은 학식과 같은 “식단” 도메인이지만 source가 달랐습니다.
- 증상: 기숙사 페이지는 EUC-KR, weekly table, 다른 selector를 사용했습니다.
- 원인: 학식 connector 추상화에 억지로 맞추면 source별 차이를 숨기면서
  코드가 복잡해질 수 있었습니다.
- 해결: `DormMealConnector`를 별도로 만들고 `fetchThisWeekMeal()` contract,
  EUC-KR parsing, row/column mapping, closure handling을 구현했습니다.
- 검증: fixture와 MockWebServer test가 encoding, weekly rows, closure marker,
  HTTP failure mapping을 검증합니다.
- 포트폴리오 포인트: premature abstraction을 피해서 connector를 단순하고
  testable하게 유지한 사례입니다.

## 2026-05-07 — Export runner가 API server를 실수로 종료할 위험

- 맥락: `WeeklyMealExportRunner`는 JSON을 쓰고 Spring process를 종료하는
  one-shot batch입니다.
- 증상: 잘못된 runtime에서 켜지면 API server가 외부 사이트를 호출하고 파일을
  쓴 뒤 종료될 수 있었습니다.
- 원인: runner 등록 조건이 주로 enabled flag 하나에 의존했습니다.
- 해결: `@Profile("export")`와 `ssuai.meal.export.enabled=true`를 둘 다
  요구하도록 gate를 강화했습니다.
- 검증: 일반 dev/prod API profile에서는 one-shot runner가 등록되지 않습니다.
- 포트폴리오 포인트: process를 종료하는 batch job은 단일 boolean보다 강한
  실행 gate가 필요합니다.

## 2026-05-07 — Windows MockWebServer timeout flake

- 맥락: parse failure test는 `ConnectorParseException`을 기대했습니다.
- 증상: Windows에서 같은 test가 `ConnectorTimeoutException`으로 실패할 수
  있었습니다.
- 원인: MockWebServer cold start와 반복 순차 request가 timeout boundary에
  너무 가까웠습니다.
- 해결: parse failure test의 timeout을 늘리고, 불필요한 artificial response
  delay를 제거했습니다.
- 검증: timeout 동작은 별도 timeout 전용 test가 검증하고, parse test는
  machine speed에 덜 의존하게 됐습니다.
- 포트폴리오 포인트: test 이름이 검증하는 실패 모드와 실제 먼저 발생하는
  실패 모드가 일치해야 합니다.

## 2026-05-07 — 학식 HTML defensive parsing 필요

- 맥락: 첫 real cafeteria connector는
  `https://soongguri.com/m/m_req/m_menu.php`를 대상으로 했습니다.
- 증상: 메뉴 HTML에 `td.menu_nm`, `td.menu_list`, nested tag, 가격, category,
  알러지/원산지 metadata, comma, closure row가 섞여 있었습니다.
- 원인: source가 안정된 JSON API가 아니라 CMS형 HTML이었습니다.
- 해결: selector 기반 row discovery에 token cleanup을 결합했습니다.
  metadata 제거, 가격 suffix 제거, comma/line split, closure keyword 탐지를
  적용했습니다.
- 검증: fixture test가 일반 학식 row, nested Dodam menu, holiday closure,
  empty HTML parse failure, HTTP failure를 검증합니다.
- 포트폴리오 포인트: connector boundary 덕분에 messy source-specific parsing이
  controller, service, MCP tool, frontend로 새지 않았습니다.

상세 historical writeup:
[`docs/troubleshooting/cafeteria-connector.md`](docs/troubleshooting/cafeteria-connector.md).
