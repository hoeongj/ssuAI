# ssuAI

> **숭실대학교의 모든 캠퍼스 정보를 단일 MCP 서버로 표준화하고,
> 그 위에 웹·앱·챗봇·에이전트를 쌓아 학생이 자연어 하나로 학교 생활을
> 처리할 수 있게 하는 캠퍼스 어시스턴트.**

[![CI](https://github.com/hoeongj/ssuAI/actions/workflows/ci.yml/badge.svg)](https://github.com/hoeongj/ssuAI/actions/workflows/ci.yml)

> 📖 처음 오신 분은 **[비전 문서](docs/vision.md)** 를 먼저 읽어 주세요 —
> 무엇을 만들고 있고 어디까지 와 있는지 5분이면 파악됩니다.

---

## 🌐 라이브 데모

<!-- markdownlint-disable MD013 MD060 -->

| 항목 | URL |
|---|---|
| **웹 챗봇** (자연어로 학식·시설 질문) | <https://ssuai.vercel.app/chat> |
| **웹 대시보드** (오늘·주간 학식, 기숙사 식단, 시설 검색) | <https://ssuai.vercel.app/> |
| **REST API** | <https://ssumcp.duckdns.org> |
| **MCP SSE 엔드포인트** (Claude Desktop / Cursor 에서 사용) | <https://ssumcp.duckdns.org/sse> |
| **API 문서 (Swagger UI)** | <https://ssumcp.duckdns.org/swagger-ui.html> |

<!-- markdownlint-enable MD013 MD060 -->

**바로 체험**: <https://ssuai.vercel.app/chat> 에서
*"오늘 학식 뭐야?"*, *"학생식당 점심 메뉴 알려줘"*,
*"학교 안에 편의점 어디 있어?"* 같은 질문을 입력하세요.
Gemini 가 MCP 서버에 도구 호출을 보내서 실제 데이터로 답합니다.

---

## 🎯 무엇을 만드는가

숭실대 학생이 하루를 보내려면 학식 페이지·u-SAINT·LMS·도서관 사이트·
학교 공식 페이지를 모두 들락거려야 합니다. 각 사이트는 모바일 UI 가
불편하고, 자연어 검색이 없으며, 매번 따로 로그인해야 합니다.

ssuAI 는 이 모든 데이터를 **MCP 표준 도구**로 묶고, 그 위에 자체
클라이언트(웹·앱·챗봇·에이전트) 를 올려 학생이 *"오늘 학식 뭐고 내 다음
수업 시간은?"* 같은 질문을 한 번에 처리할 수 있게 합니다.

### 4-layer 시스템

```text
┌─────────────────────────────────────────────────────────────┐
│ Layer 4: AI 에이전트  (Phase 4 - 계획)                      │
│   도서관 자리 자동 예약 등 실제 액션 실행                   │
├─────────────────────────────────────────────────────────────┤
│ Layer 3: 챗봇  (Phase 1 ✓ 라이브)                           │
│   자연어로 MCP 도구 호출, LLM 답변 생성                     │
├─────────────────────────────────────────────────────────────┤
│ Layer 2: 자체 웹/앱 클라이언트  (web ✓ / app 🔜)            │
│   Next.js 웹 대시보드 라이브, Expo 모바일 앱 예정           │
├─────────────────────────────────────────────────────────────┤
│ Layer 1: 공개 MCP 서버  (공개 도구 ✓ / 개인 도구 📋 계획)   │
│   공개: 학식·기숙사·시설 (라이브) + 도서관 (예정)           │
│   개인: 시간표·성적·과제·대출 현황 (Phase 3 계획)           │
└─────────────────────────────────────────────────────────────┘
```

각 layer 의 구체적 도구 목록·정책·로드맵은 **[`docs/vision.md`](docs/vision.md)**
참고.

---

## 📊 현재 진행 상황

<!-- markdownlint-disable MD013 MD060 -->

| Phase | 내용 | 상태 |
|---|---|---|
| **Phase 1** | 공개 데이터 MVP — 학식·기숙사·시설 MCP 도구 + 웹 + 챗봇 | ✅ **라이브 운영 중** |
| **Phase 2** | 도서관 도구 (책 검색, 좌석 상태) + 모바일 UI | 🔜 다음 단기 목표 |
| **Phase 3** | u-SAINT / LMS 인증 + 개인 MCP 도구 | 📋 계획 |
| **Phase 4** | AI 에이전트 (도서관 좌석 자동 예약 등) | 📋 계획 |

<!-- markdownlint-enable MD013 MD060 -->

Phase 1 에서 완료된 것 (라이브):

- ✅ Spring Boot 단일 프로세스 — REST + MCP 서버 동시 운영
- ✅ MCP 도구 4개: `get_today_meal`, `get_meal_by_date`,
  `get_dorm_weekly_meal`, `search_campus_facilities`
- ✅ `WeeklyMealCache` — 학식은 주 1회 갱신이라 시작 시 + 매주 월요일
  06:00 KST 에 일괄 적재하고 in-memory 캐시로 응답
- ✅ MCP self-dogfood — 챗봇이 같은 JVM 의 MCP 서버를 SSE 로 호출
- ✅ 9개 LLM 프로바이더 fallback (Gemini → Groq → OpenRouter → ...)
- ✅ Oracle Cloud Free Tier ARM64 k3s 라이브 배포

---

## 🏗 아키텍처

```text
┌─────────────────────────────────────────────────────────────┐
│                          Clients                            │
│   웹 챗봇/대시보드 (Next.js)  │   Claude Desktop · Cursor   │
└──────────────────┬────────────┴───────────────┬─────────────┘
                   │ HTTPS                       │ MCP / SSE
                   ▼                             ▼
┌─────────────────────────────────────────────────────────────┐
│       ssuAI Spring Boot 백엔드 (단일 JVM, Java 21)          │
│                                                             │
│   REST Controllers   ──┐                ┌── MCP Tools       │
│                        ▼                ▼                   │
│                    ┌─── Service layer ───┐                  │
│                    │  + WeeklyMealCache  │                  │
│                    └──────────┬──────────┘                  │
│                               ▼                             │
│              Connectors (mock / real)                       │
│              └── Jsoup HTTP scrape ──┐                      │
│                                      ▼                      │
└──────────────────────────────────────┬──────────────────────┘
                                       ▼
                  soongguri.com · 레지던스홀 · 시설 페이지
```

핵심 설계 원칙:

- **단일 Spring Boot 프로세스** — REST 와 MCP 두 surface 가 같은
  Service layer 를 공유. 챗봇은 같은 프로세스의 MCP 서버를 SSE 로 직접
  호출하는 **self-dogfood** 구조.
- **Connector 경계** — 학교 사이트 HTML 모양은 connector 안에서만
  알고, 위로는 내부 DTO 만 흐름. `mock` ↔ `real` 을
  `@ConditionalOnProperty` 로 스위치. CI / local dev 는 외부 사이트
  hit 안 함.
- **WeeklyMealCache** — 갱신 주기 (주 1회) 와 호출 주기 (분당 N) 의
  격차가 크면 caching 이 자연스러운 답. cache miss 시 connector
  fallback 으로 회복력 유지.

시스템 다이어그램·패키지 layout·response envelope·error 매핑은
**[`docs/architecture.md`](docs/architecture.md)** 에 있습니다.

---

## 🤖 Layer 1 — 공개 MCP 서버

ssuAI 의 main deliverable. Spring AI `spring-ai-starter-mcp-server-webmvc`
로 같은 Spring Boot 프로세스 안에서 SSE transport 로 동작합니다.

### 현재 노출 도구

<!-- markdownlint-disable MD013 MD060 -->

| Tool | 설명 | 인자 |
|---|---|---|
| `get_today_meal` | 오늘 학식 메뉴 (전체 or 특정 식당) | `restaurant` (선택) |
| `get_meal_by_date` | 지정한 날짜의 학식 메뉴 | `date`: `yyyy-MM-dd`, `restaurant` (선택) |
| `get_dorm_weekly_meal` | 레지던스홀 이번 주 식단 | 없음 |
| `search_campus_facilities` | 카페, 편의점, 복사실 등 시설 검색 | `query` |

<!-- markdownlint-enable MD013 MD060 -->

`restaurant` 는 한국어 별칭 지원 — 학생식당 / 숭실도담식당 /
스낵코너 / 푸드코트 / THE KITCHEN / FACULTY LOUNGE.

### 예정된 도구

- 공개: `search_library_book`, `get_library_book_status`,
  `get_library_seat_status`
- 개인 (인증 필요): `get_my_schedule`, `get_my_grades`,
  `get_my_assignments`, `get_my_library_loans`

자세한 로드맵: [`docs/vision.md`](docs/vision.md).

### Claude Desktop / Cursor 연결

```json
{
  "mcpServers": {
    "ssuai": { "url": "https://ssumcp.duckdns.org/sse" }
  }
}
```

로컬 개발 서버로 연결하려면 URL 만 `http://localhost:8080/sse` 로
변경. `mcp-proxy` 어댑터 절차 포함 자세한 가이드는
[`docs/mcp-tools.md`](docs/mcp-tools.md).

---

## 🌐 Layer 2 — 웹 대시보드 + Layer 3 — 챗봇

### REST API

<!-- markdownlint-disable MD013 MD060 -->

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/meals/today` | 오늘 학식 (모든 식당) |
| GET | `/api/meals/weekly?startDate=yyyy-MM-dd` | 주간 학식 |
| GET | `/api/dorm/meals/this-week` | 레지던스홀 주간 식단 |
| GET | `/api/campus/facilities?query=...` | 캠퍼스 시설 검색 |
| POST | `/api/chat` | 챗봇 호출 (`{message}` → `{conversationId, reply}`) |

<!-- markdownlint-enable MD013 MD060 -->

응답은 모두 `ApiResponse<T>` envelope — `data` / `error` / `traceId`.

### 웹 대시보드 (Next.js)

- App Router + TypeScript strict + Tailwind + shadcn/ui
- TanStack Query v5 로 server state 관리, 카드별 loading / error /
  empty state 분리
- 백엔드 URL 은 `NEXT_PUBLIC_SSUAI_API_BASE` env 로만 주입

### 챗봇 — Self-dogfood 데이터 플로우

```text
사용자 "학생식당 오늘 점심 메뉴 알려줘"
  └▶ POST /api/chat
      └▶ LlmChatService → Gemini "이 질문 + 도구 4개"
          └▶ Gemini: tool_call get_today_meal(restaurant="학생식당")
              └▶ McpSyncClient → http://localhost:8080/sse  (같은 JVM)
                  └▶ MealMcpTools → MealService
                      └▶ WeeklyMealCache.find(today, STUDENT)  ← 캐시 히트
              ←▶ MealResponse (학생식당만)
          ←▶ Gemini "오늘 학생식당 점심은 ..."
      ←▶ ChatResponse
```

ADR 참고:
[0010 — chatbot MCP self-dogfood](docs/adr/0010-chatbot-mcp-self-dogfood.md),
[0011 — MCP tool dynamic discovery](docs/adr/0011-mcp-tool-dynamic-discovery.md).

### 챗봇 안전 가드

- Output token cap (`SSUAI_LLM_MAX_TOKENS`)
- Turn 당 도구 호출 횟수 cap (`SSUAI_LLM_MAX_TOOL_CALLS`)
- Secret 입력 감지 (비밀번호·학번·세션·토큰) — 저장·반복 안 함
- 학사·LMS·u-SAINT 같은 개인정보 질문 → 현재는 "Phase 3 에 추가됩니다" 안내

---

## 🚀 기술 스택

**Backend** — Java 21, Spring Boot 4.x, Spring AI 1.1.x (MCP server +
MCP client + webmvc), Jsoup, Gradle, springdoc-openapi

**Frontend** — Next.js 16 (App Router), TypeScript strict, Tailwind CSS,
shadcn/ui, TanStack Query v5, pnpm

**Infra** — Oracle Cloud Free Tier ARM Ampere A1, k3s 단일 노드,
Traefik ingress, cert-manager + Let's Encrypt, GitHub Container
Registry. Frontend 는 Vercel.

**자동화** — GitHub Actions CI (backend test + frontend test + ARM64
image build + gitleaks scan), Dependabot, lefthook pre-commit hook.

스택 선택 이유: [ADR 0006](docs/adr/0006-frontend-stack.md),
[ADR 0007](docs/adr/0007-prod-deploy-oracle-k3s.md),
[ADR 0009](docs/adr/0009-chatbot-stack.md).

---

## 💻 로컬 개발

### 필요 도구

- JDK 21 (Temurin 권장)
- Node 20+, pnpm 9+
- Git

### 백엔드 (mock 모드 — 외부 사이트 hit 없음)

```bash
# Windows
cd backend && .\gradlew.bat bootRun

# macOS / Linux
cd backend && ./gradlew bootRun
```

`:8080` 에서 동작합니다.

```bash
curl http://localhost:8080/api/meals/today
```

OpenAPI: <http://localhost:8080/v3/api-docs>,
Swagger UI: <http://localhost:8080/swagger-ui.html>.

### 백엔드 (real 데이터 + LLM 챗봇 모드)

```bash
cp backend/.env.example backend/.env
# backend/.env 에 SSUAI_GEMINI_API_KEY=... 입력
```

```powershell
# Windows
Push-Location backend
Get-Content .env | Where-Object { $_ -and $_ -notmatch '^\s*#' } |
    ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
$env:SSUAI_CONNECTOR_CHAT = "llm"
$env:SSUAI_CONNECTOR_MEAL = "real"
$env:SSUAI_CONNECTOR_DORM_MEAL = "real"
.\gradlew.bat bootRun
```

```bash
# macOS / Linux
cd backend
set -a; . ./.env; set +a
SSUAI_CONNECTOR_CHAT=llm \
SSUAI_CONNECTOR_MEAL=real \
SSUAI_CONNECTOR_DORM_MEAL=real \
./gradlew bootRun
```

챗봇 테스트:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"오늘 학식 뭐야?"}'
```

### 프론트엔드

```bash
cp frontend/.env.example frontend/.env.local
pnpm --dir frontend install
pnpm --dir frontend dev
```

<http://localhost:3000>.

### MCP 서버 단독 확인

```bash
npx @modelcontextprotocol/inspector
# Transport: SSE, URL: http://localhost:8080/sse → Connect → List Tools
```

자세한 절차: [`docs/mcp-tools.md`](docs/mcp-tools.md).

---

## 📚 문서 맵

각 문서가 무엇을 다루는지 한 줄씩 — 처음 보시는 분은 위에서 아래로 읽으면
됩니다.

<!-- markdownlint-disable MD013 MD060 -->

| 문서 | 무엇이 들어 있나 |
|---|---|
| **[`docs/vision.md`](docs/vision.md)** | 🎯 ssuAI 의 4-layer 최종 목표 + phase 별 로드맵 (**가장 먼저 읽어 보세요**) |
| [`docs/product.md`](docs/product.md) | 제품 정의 — 무엇을 만들고 무엇을 안 만드는가 |
| [`docs/architecture.md`](docs/architecture.md) | 시스템 설계 — 패키지·response envelope·connector pattern·caching |
| [`docs/security.md`](docs/security.md) | secret · credential · logging · CORS 정책 |
| [`docs/mcp-tools.md`](docs/mcp-tools.md) | MCP 서버 사용법, Claude Desktop / Cursor 등록법 |
| [`docs/adr/`](docs/adr/) | 모든 load-bearing 결정 (ADR 0001–0011) |
| [`docs/tasks/`](docs/tasks/) | 단기 구현 단위 task spec |
| [`docs/dev-log.md`](docs/dev-log.md) | 누적 개발 로그 (chronological) |
| [`docs/troubleshooting/`](docs/troubleshooting/) | 상세 회고 / postmortem |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | 최상위 트러블슈팅 로그 (한국어, portfolio 회고용) |
| [`docs/deploy/`](docs/deploy/) | 배포 파이프라인 진단 / 운영 노트 |
| [`AGENTS.md`](AGENTS.md), [`CLAUDE.md`](CLAUDE.md) | AI 협업 워크플로우 (Claude 아키텍트 + Codex 구현자) |

<!-- markdownlint-enable MD013 MD060 -->

---

## ⚠️ 알려진 한계

- **인증 미구현.** 현재 endpoint 는 모두 공개 데이터만. u-SAINT / LMS
  통합과 함께 인증 layer 가 Phase 3 에서 추가됩니다.
- **단일 노드 운영.** 라이브 데모는 Oracle Free Tier 단일 VM 위
  k3s. Portfolio 시연 용도이고 고부하 트래픽은 가정하지 않습니다.
- **모바일 UI 미세조정 미완.** 동작은 하지만 데스크탑 우선으로 설계.
  Phase 2 에서 다듬을 예정.
- **학교 사이트 변경에 취약.** Connector 가 학교 페이지 HTML 을
  파싱하므로 학교 markup 이 바뀌면 동작이 깨질 수 있음. Connector
  boundary 가 격리되어 있어 connector swap 만으로 복구 가능한 구조.

---

## 🛠 프로젝트 컨벤션

- **Conventional Commits** — `feat:`, `fix:`, `chore:`, `docs:`,
  `test:`, `refactor:` prefix
- **한 PR = 한 개념** — 변경 사항이 명확하게 review 되도록
- **ADR** — load-bearing 결정은 모두 `docs/adr/` 에
- **Dependabot + gitleaks** — 의존성·secret 자동 감시
- **AI 협업 워크플로우** — Claude (아키텍트/리뷰어) + Codex (구현자).
  자세한 hand-off 규약: [`AGENTS.md`](AGENTS.md), [`CLAUDE.md`](CLAUDE.md)

---

## 📜 라이선스

MIT — [`LICENSE`](LICENSE) 참고.
