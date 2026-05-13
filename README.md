# ssuAI

> 숭실대학교 학생을 위한 AI 캠퍼스 어시스턴트. 학식, 기숙사 식단, 캠퍼스
> 시설 같은 정보를 자연어 채팅 하나로 다 묻고 답받을 수 있도록 만든
> portfolio 프로젝트입니다.

[![CI](https://github.com/hoeongj/ssuAI/actions/workflows/ci.yml/badge.svg)](https://github.com/hoeongj/ssuAI/actions/workflows/ci.yml)

## 🌐 라이브 데모

<!-- markdownlint-disable MD013 MD060 -->

| Surface | URL |
|---|---|
| **웹 챗봇** | <https://ssuai.vercel.app/chat> |
| **웹 대시보드** | <https://ssuai.vercel.app/> |
| **REST API** | <https://ssumcp.duckdns.org> |
| **MCP SSE 엔드포인트** | <https://ssumcp.duckdns.org/sse> |
| **API 문서 (Swagger)** | <https://ssumcp.duckdns.org/swagger-ui.html> |

<!-- markdownlint-enable MD013 MD060 -->

챗봇 시연: `https://ssuai.vercel.app/chat` 에서 *"오늘 학식 뭐야?"*, *"학생식당
오늘 점심 메뉴 알려줘"*, *"캠퍼스에 카페 어디 있어?"* 같은 질문을
한국어로 입력하면 Gemini 가 MCP 서버에 학식/시설 도구를 호출해 실제
데이터로 답합니다.

---

## 📌 무엇을 만드는가

숭실대 학생은 매일 같은 정보를 찾기 위해 여러 곳을 들락거립니다.

- 학식 메뉴는 학교 학식 사이트 (`soongguri.com`)
- 기숙사 식단은 숭실대 레지던스홀 페이지
- 캠퍼스 시설(카페, 편의점, 복사실 등)은 학교 공식 페이지

각 페이지는 모바일 UI 가 불편하고, 공통된 검색·자연어 인터페이스가 없습니다.

**ssuAI 의 목표**는 이 모든 공개 데이터를 하나의 대화형 인터페이스로
모으고, 같은 데이터를 **REST API 와 MCP server 두 가지 surface 로 동시에
제공**하는 것입니다.

장기적으로는 u-SAINT / LMS 같은 인증이 필요한 학사 시스템까지 확장할 수
있도록 layer 와 connector 를 미리 분리해 두었지만, 현재 MVP 는 의도적으로
**공개 read-only 데이터만** 다룹니다.

---

## 🎯 현재 진행 상황

라이브에서 동작하는 것:

- ✅ **챗봇** — LLM(Gemini) + MCP self-dogfood. 학식/기숙사 식단/캠퍼스
  시설을 자연어로 묻고 답받음. 사용자가 특정 식당을 언급하면 그 식당만
  조회.
- ✅ **REST API** — 학식, 기숙사 식단, 캠퍼스 시설 조회 4개 엔드포인트.
  표준 `ApiResponse<T>` envelope (`data` / `error` / `traceId`).
- ✅ **MCP 서버** — read-only tool 4개 (`get_today_meal`,
  `get_meal_by_date`, `get_dorm_weekly_meal`, `search_campus_facilities`).
  Claude Desktop / Cursor / MCP Inspector 에서 사용 가능.
- ✅ **웹 대시보드** — Next.js + Tailwind 로 만든 4개 카드 (오늘 학식,
  주간 학식, 기숙사 주간 식단, 캠퍼스 시설 검색).
- ✅ **주간 학식 캐시** — 학식은 주 1회 갱신되는 데이터라 매 요청마다
  학교 사이트를 hit 하지 않고, 애플리케이션 시작 시점 + 매주 월요일
  06:00 KST 에 일괄 적재해 in-memory 캐시로 응답.
- ✅ **운영 배포** — Oracle Cloud Free Tier ARM64 위 k3s, Traefik
  ingress, Let's Encrypt TLS, GitHub Container Registry, Vercel
  frontend. 모두 라이브 동작 중.

다음 단계로 두고 있는 것:

- 🔜 챗봇 답변 streaming (지금은 한 번에 전부 받는 구조)
- 🔜 학사 정보(시간표, 과제, 성적) 통합을 위한 인증/credential 보호 layer
- 🔜 모바일 친화 UI 다듬기

상세 task 별 spec 은 [`docs/tasks/`](docs/tasks/) 에 있고, load-bearing
한 결정은 모두 [`docs/adr/`](docs/adr/) 에 ADR 로 남겼습니다.

---

## 🏗 아키텍처

```text
┌────────────────────────────────────────────────────────────────┐
│                        Clients                                 │
│   웹 챗봇 / 대시보드 (Next.js)   │   Claude Desktop · Cursor    │
└──────────────────┬────────────────┴─────────────────┬──────────┘
                   │ HTTPS                            │ MCP / SSE
                   ▼                                  ▼
┌────────────────────────────────────────────────────────────────┐
│       ssuAI Spring Boot 백엔드 (단일 JVM, Java 21)             │
│                                                                │
│   REST Controllers   ──┐                ┌── MCP Tools          │
│                        ▼                ▼                      │
│                    ┌─── Service layer ───┐                     │
│                    │  + WeeklyMealCache  │                     │
│                    └──────────┬──────────┘                     │
│                               ▼                                │
│              Connectors (mock / real)                          │
│            └── Jsoup HTTP scrape ──┐                           │
│                                    ▼                           │
└────────────────────────────────────┬───────────────────────────┘
                                     ▼
              soongguri.com · 레지던스홀 · 공식 시설 페이지
```

핵심 원칙:

- **단일 Spring Boot 프로세스**가 REST와 MCP 두 surface를 같은 Service
  layer 위에서 동시에 제공합니다. 챗봇은 같은 프로세스의 MCP server를
  HTTP/SSE 로 직접 호출하는 **self-dogfood** 구조 — 매 채팅 턴마다
  실제 MCP protocol을 사용합니다.
- **Connector boundary** — 학교 사이트의 HTML 모양은 connector 안에서만
  알고, 위로는 내부 DTO만 흐릅니다. `mock` ↔ `real` 을
  `@ConditionalOnProperty` 로 스위치하므로 CI 와 local dev 에서는
  외부 사이트를 절대 hit 하지 않습니다.
- **WeeklyMealCache** — 학식은 주 1회 갱신이라 매 요청 라이브 스크래핑
  대신 시작 시점 + 매주 월요일 새벽에 일괄 적재하고 그 후엔 in-memory
  로 응답. cache miss 발생 시 그 자리에서 connector fallback.

전체 시스템 다이어그램과 패키지 layout 은
[`docs/architecture.md`](docs/architecture.md) 참고.

---

## 🤖 MCP 서버

ssuAI 의 main deliverable 중 하나는 **MCP(Model Context Protocol) 서버**
구현입니다. Spring AI `spring-ai-starter-mcp-server-webmvc` 를 사용해
같은 Spring Boot 프로세스 안에서 SSE transport 로 동작합니다.

### 노출 도구

<!-- markdownlint-disable MD013 MD060 -->

| Tool | 설명 | 인자 |
|---|---|---|
| `get_today_meal` | 오늘 학식 메뉴 (전체 or 특정 식당) | `restaurant` (선택) |
| `get_meal_by_date` | 지정한 날짜의 학식 메뉴 | `date`: `yyyy-MM-dd`, `restaurant` (선택) |
| `get_dorm_weekly_meal` | 레지던스홀 이번 주 식단 | 없음 |
| `search_campus_facilities` | 카페, 편의점, 복사실 등 시설 검색 | `query` |

<!-- markdownlint-enable MD013 MD060 -->

`restaurant` 는 한국어 별칭을 지원합니다 — 학생식당 / 숭실도담식당 /
스낵코너 / 푸드코트 / THE KITCHEN / FACULTY LOUNGE.

### Self-dogfood: 챗봇이 자기 MCP 서버를 호출

```text
사용자 "학생식당 오늘 점심 메뉴 알려줘"
  └▶ /api/chat (Spring MVC)
      └▶ LlmChatService → Gemini "이 질문 + 도구 4개"
          └▶ Gemini: tool_call get_today_meal(restaurant="학생식당")
              └▶ McpSyncClient → http://localhost:8080/sse  (같은 JVM)
                  └▶ MealMcpTools → MealService
                      └▶ WeeklyMealCache.find(today, STUDENT)  ← 캐시 히트
              ←▶ MealResponse (학생식당만)
          ←▶ Gemini "오늘 학생식당 점심은 ..."
      ←▶ ChatResponse
```

이 구조를 자세히 다룬 ADR:

- [ADR 0010 — chatbot MCP self-dogfood](docs/adr/0010-chatbot-mcp-self-dogfood.md)
- [ADR 0011 — MCP tool dynamic discovery](docs/adr/0011-mcp-tool-dynamic-discovery.md)

### Claude Desktop / Cursor 연결법

라이브 SSE 직접 연결:

```json
{
  "mcpServers": {
    "ssuai": { "url": "https://ssumcp.duckdns.org/sse" }
  }
}
```

로컬 개발 서버 연결:

```json
{
  "mcpServers": {
    "ssuai": { "url": "http://localhost:8080/sse" }
  }
}
```

`mcp-proxy` 어댑터 등 더 자세한 절차는
[`docs/mcp-tools.md`](docs/mcp-tools.md) 참고.

---

## 🌐 REST API + 웹 대시보드 + 챗봇

### REST 엔드포인트

<!-- markdownlint-disable MD013 MD060 -->

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/meals/today` | 오늘 학식 (모든 식당) |
| GET | `/api/meals/weekly?startDate=yyyy-MM-dd` | 주간 학식 |
| GET | `/api/dorm/meals/this-week` | 레지던스홀 주간 식단 |
| GET | `/api/campus/facilities?query=...` | 캠퍼스 시설 검색 |
| POST | `/api/chat` | 챗봇 호출 (`{message}` → `{conversationId, reply}`) |

<!-- markdownlint-enable MD013 MD060 -->

응답은 모두 `ApiResponse<T>` envelope:

```json
{
  "data":    { /* ... */ },
  "error":   null,
  "traceId": "9ca70365-5797-4b9a-9df5-ec2568113c11"
}
```

에러도 같은 envelope 안에서 `error.code` / `error.message` 로 일관되게
반환합니다 (자세한 매핑은
[`docs/architecture.md`](docs/architecture.md) §6).

### 웹 대시보드 (Next.js)

- App Router + TypeScript (strict) + Tailwind + shadcn/ui
- TanStack Query v5 로 server state 관리, 카드별 loading / error / empty
  state 분리
- 백엔드 URL 은 `NEXT_PUBLIC_SSUAI_API_BASE` env로만 주입 (hard-coded
  host 없음)

### 챗봇

- 백엔드 측 `LlmChatService` 가 **OpenAI-compatible REST**로 LLM 호출
  (Spring AI ChatClient 대신 직접 호출 — 이유는
  [ADR 0009](docs/adr/0009-chatbot-stack.md))
- **Multi-provider fallback** — Gemini → Groq → OpenRouter → Cerebras →
  ... → Mistral 순서로 시도하다 성공한 첫 응답을 사용
- 단일 응답당 출력 토큰 cap (`SSUAI_LLM_MAX_TOKENS`) 및 turn 당 tool
  호출 횟수 cap (`SSUAI_LLM_MAX_TOOL_CALLS`)
- 학사 정보 / secret 입력 패턴 가드 — 비밀번호·학번·세션·토큰을
  사용자가 입력해도 저장·반복하지 않음

---

## 🚀 기술 스택

**Backend** — Java 21, Spring Boot 4.x, Spring AI 1.1.x (MCP server +
MCP client + webmvc), Jsoup, Gradle, springdoc-openapi

**Frontend** — Next.js 16 (App Router), TypeScript strict, Tailwind CSS,
shadcn/ui, TanStack Query v5, pnpm

**Infra** — Oracle Cloud Free Tier ARM Ampere A1, k3s 단일 노드,
Traefik ingress, cert-manager + Let's Encrypt, GitHub Container
Registry. Frontend 는 Vercel.

**개발 도구** — GitHub Actions CI (backend test, frontend test, ARM64
image build, gitleaks scan), Dependabot 자동 PR, lefthook pre-commit
훅 (gitleaks).

스택 선택 이유: [ADR 0006 — frontend stack](docs/adr/0006-frontend-stack.md),
[ADR 0007 — prod deploy](docs/adr/0007-prod-deploy-oracle-k3s.md).

---

## 💻 로컬 개발

### 필요 도구

- JDK 21 (Temurin)
- Node 20+, pnpm 9+
- Git

### 백엔드 (mock 모드, 외부 사이트 hit 없음)

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

- [`docs/product.md`](docs/product.md) — 제품 정의: 무엇을 만들고 무엇을
  안 만드는가
- [`docs/architecture.md`](docs/architecture.md) — 시스템 설계: layer,
  package, response envelope, connector pattern
- [`docs/security.md`](docs/security.md) — secret / logging / CORS /
  외부 응답 처리 정책
- [`docs/mcp-tools.md`](docs/mcp-tools.md) — MCP 서버 사용법, Claude
  Desktop / Cursor 등록법
- [`docs/adr/`](docs/adr/) — 모든 load-bearing 한 결정 (ADR 0001–0011)
- [`docs/tasks/`](docs/tasks/) — 구현 단위 task spec
- [`docs/dev-log.md`](docs/dev-log.md) — 누적 개발 로그
- [`docs/troubleshooting/`](docs/troubleshooting/) — 상세 회고
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — 최상위 트러블슈팅 로그

---

## ⚠️ 알려진 한계

- **인증 없음.** 모든 endpoint 는 공개 데이터만 다룹니다. u-SAINT / LMS
  통합과 함께 인증 layer 가 추가됩니다.
- **단일 노드 운영.** 라이브 데모는 Oracle Free Tier 단일 VM 위
  k3s 입니다. portfolio 시연 용도이고, 고부하 트래픽은 가정하지
  않습니다.
- **모바일 UI 미세조정 미완.** 동작은 하지만 데스크탑 우선으로
  설계되어 있습니다.
- **학교 사이트 변경에 취약.** Connector 가 학교 페이지 HTML 구조를
  파싱하기 때문에 학교 측에서 markup 을 바꾸면 동작이 깨질 수
  있습니다. 다만 connector boundary 가 격리되어 있어 connector swap
  만으로 복구할 수 있도록 설계되어 있습니다.

---

## 🛠 프로젝트 컨벤션

- **Conventional Commits** — `feat:`, `fix:`, `chore:`, `docs:`, `test:`,
  `refactor:` prefix 사용
- **한 PR = 한 개념** — 변경 사항이 명확하게 review 되도록 작게 쪼개
  제출
- **ADR** — load-bearing 한 결정은 모두 `docs/adr/` 에 기록
- **Dependabot + gitleaks** — 의존성과 secret 누출은 자동화로 감시
- **Two-agent 워크플로우** — Claude(아키텍트/리뷰어) + Codex(구현자)
  로 만든 portfolio 프로젝트. 자세한 hand-off 규약은
  [`AGENTS.md`](AGENTS.md), [`CLAUDE.md`](CLAUDE.md)

---

## 📜 라이선스

MIT — [`LICENSE`](LICENSE) 참고.
