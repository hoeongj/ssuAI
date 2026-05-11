# ssuAI 트러블슈팅 로그

이 파일은 포트폴리오에 넣기 좋은 장애 대응, 디버깅, 배포 문제 해결 기록을
모으는 최상위 로그입니다.

## 기록 규칙

- Codex와 Claude 모두 의미 있는 문제를 발견하거나 해결하면 이 파일에
  한국어로 누적합니다.
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
