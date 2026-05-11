# Task 07 — Chatbot slice (Spring AI ChatClient + REST + frontend chat page)

> ⚠️ **PENDING REVISION — DO NOT EXECUTE YET (2026-05-09).**
> 이 spec 은 챗봇이 MCP tool 을 **in-process Java method** 로 호출하는
> 구조로 작성됨. 사용자가 그 후 architecture 명확화: **MCP server 는
> 메인 deliverable (LLM 없음, 데이터 어댑터), 챗봇은 그 MCP server 의
> consumer 로서 MCP CLIENT 프로토콜로 호출하는 dogfooding 구조**.
> Oracle 인프라 작업 끝난 뒤 Claude 와 함께 spec 재작성 예정. Codex 한테
> 이 파일 던지지 말 것 — 09/10/11 먼저.

> Hand-off spec for the implementer (Codex CLI). Read `docs/architecture.md`
> §3/§6/§11, `docs/security.md` §3/§4, and `docs/mcp-tools.md` first.
> Reply using the **Required Output Format** in `AGENTS.md`.

## Goal

Add the **MVP chatbot**: a `POST /api/chat` endpoint backed by Spring AI's
`ChatClient`, wired to call the existing read-only MCP tools
(`get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`,
`search_campus_facilities`) as tool callbacks. A new `/chat` page in the
frontend lets a user have a single-turn or short-thread conversation
about cafeteria, dorm, and campus facilities — using the same
`ApiResponse<T>` envelope and error UX the dashboard cards use.

After this task is merged, a user can ask "오늘 학식 뭐야?" or "도서관
근처 카페 알려줘" in the web chat and get a Korean answer that pulls
from the live (or mock) connectors via the chatbot's tool calls.

## Why this slice now

- Task 06 puts the project on real infrastructure. The MVP scope in
  `docs/product.md` §3 lists "basic chatbot" as the last MVP item — this
  ships it.
- The strongest portfolio narrative for this project is **"the same
  backend hosts an MCP server *and* a chatbot that consumes its own MCP
  tools"** — i.e., one set of business logic, three surfaces (REST, MCP,
  chat). This task makes that story true.
- Spring AI 1.0.0 GA ships with first-class `ChatClient` + tool calling
  + chat memory. No experimental dependencies required.

## Stack & rationale

| Choice | Why |
|---|---|
| Spring AI 1.0.0 (already in `dependencyManagement`) | BOM is already imported. No new BOM. |
| `spring-ai-starter-model-anthropic` (Claude) | The developer already uses Claude Code → has an Anthropic API key handy. Switching to OpenAI is a one-line dependency swap; the abstraction is `ChatClient`. |
| `claude-sonnet-4-6` model | Cheapest "smart enough" Claude tier for tool-using agents in 2026. Opus is overkill for cafeteria Q&A; Haiku struggles with tool reasoning. |
| In-process tool calling against existing `*McpTools` beans | The MCP tool methods are already `@Tool`-annotated and bean-managed. Spring AI's `MethodToolCallbackProvider` (already used for the MCP server) can hand the same callbacks to `ChatClient.builder().defaultToolCallbacks(...)`. **One source of truth for tool definitions.** |
| `MessageWindowChatMemory` (in-memory) | Architecture §11 forbids persistence we don't yet need. Window memory keeps the last N messages per `conversationId`. Redis comes when there are real users to scope memory to. |
| `mock` / `real` connector pattern reused for `ChatService` | Same pattern as `MealConnector` — `MockChatService` returns deterministic answers in dev/test, `RealChatService` calls the LLM. Keeps tests offline by default. |
| Non-streaming JSON response (this task) | Streaming SSE chat is a separate, smaller task once the contract is stable. Avoids designing the streaming envelope before the basic shape is proven. |
| New top-level `/chat` route in frontend, not a card | Chat needs vertical space and conversation state. Cramming it into a 4-card grid is bad UX. Dashboard gains a header link to `/chat`. |

## Scope — in

1. New `domain.chat` package (controller + service + dto).
2. `POST /api/chat` endpoint accepting `{conversationId?, message}` and
   returning `ApiResponse<{conversationId, reply, traceId}>`.
3. `ChatService` interface + `MockChatService` + `RealChatService`,
   profile-gated by `ssuai.connector.chat = mock | real`, mirroring the
   meal/dorm pattern. Default `mock` (matches `application.yml`).
4. Spring AI `ChatClient` configured with:
   - System prompt establishing scope (Korean answers, scope = cafeteria
     + dorm + campus facilities, refuse out-of-scope politely),
   - `defaultToolCallbacks(...)` referencing the existing
     `MealMcpTools`/`DormMcpTools`/`CampusMcpTools` beans,
   - `MessageWindowChatMemory` of last 10 messages keyed on
     `conversationId`.
5. Anthropic API key wired via `${SSUAI_ANTHROPIC_API_KEY}` env var (only
   read in `real` profile; missing key in `mock` is fine).
6. `application.yml` adds `ssuai.connector.chat: mock` default.
7. `application-prod.yml` flips to `real` and sets the model id.
8. Tests:
   - `ChatService` unit test (mock path, deterministic).
   - `ChatController` slice test (`@WebMvcTest`) for envelope + 400 on
     missing message.
   - **No live LLM calls in tests, ever** (security.md §13 / scope-out
     of test environment §13).
9. Frontend:
   - New `/chat` route (`frontend/app/chat/page.tsx`).
   - Chat UI: message list (user/assistant bubbles), input box, send
     button, error state per existing `ErrorState.tsx`, loading state per
     existing `Skeleton.tsx`.
   - `lib/api/chat.ts` client mirrors `lib/api/client.ts` patterns.
   - `app/page.tsx` header gets a link to `/chat`.
   - `conversationId` lives in component state only (no localStorage —
     security.md §4 — chat history can include incidental personal
     phrasing the user typed).
10. ADR 0008 capturing: Spring AI + Anthropic + MCP-tool-self-hosting
    decision. dev-log line.
11. README "Live demo" section gains a link to `/chat`.

## Scope — out (later tasks)

- **Streaming SSE chat** (`POST /api/chat/stream`) — separate task, after
  the non-streaming contract has settled. Reuses the same service.
- **Persisted history** in Postgres / Redis — needs ssuAI auth first.
- **Per-user authentication** — chat is anonymous for the MVP (no login
  yet). Rate-limit by IP if abuse appears.
- **Action / write tools** in chat (seat reservation, LMS submit) —
  requires the confirmation + audit log flow in security.md §6.
- **RAG / retrieval over school notices** — `docs/product.md` §4 future.
- **Per-user conversation listing** — same blocker as auth.
- **Multimodal input** (image upload, voice) — out of scope.
- **Cost / quota dashboards** — Task 08 observability.

## API design

### Request

```
POST /api/chat
Content-Type: application/json

{
  "conversationId": "c-9f...",     // optional; server returns one if absent
  "message": "오늘 학식 뭐야?"
}
```

Validation (Jakarta Bean Validation on the DTO):
- `message`: `@NotBlank`, `@Size(max = 1000)` — LLM input is the most
  abused vector; cap aggressively.
- `conversationId`: `@Size(max = 64)` if present, regex `[a-zA-Z0-9-]+`.

### Response (success)

```json
{
  "data": {
    "conversationId": "c-9fa1...",
    "reply": "오늘 학식은 …"
  },
  "error": null,
  "traceId": "f3c1...e9"
}
```

### Response (error)

Same shape as existing endpoints, via `GlobalExceptionHandler`. New
error code: `CHAT_UNAVAILABLE` for upstream LLM failures (timeout,
quota exceeded, transport error). Maps to HTTP 503.

Add `CHAT_UNAVAILABLE` to `ErrorCode.java` and to the frontend's
`getErrorStateDetails` helper user-message switch:
- `case "CHAT_UNAVAILABLE": return "대화 기능이 일시적으로 응답하지 못하고 있어요. 잠시 후 다시 시도해 주세요.";`

## Files to create / modify

```
backend/build.gradle                                      # MODIFY: add spring-ai-starter-model-anthropic

backend/src/main/java/com/ssuai/domain/chat/
├── controller/ChatController.java                        # NEW
├── service/ChatService.java                              # NEW (interface)
├── service/MockChatService.java                          # NEW (deterministic)
├── service/RealChatService.java                          # NEW (Spring AI ChatClient)
├── dto/ChatRequest.java                                  # NEW
└── dto/ChatResponse.java                                 # NEW

backend/src/main/java/com/ssuai/domain/chat/config/
└── ChatClientConfig.java                                 # NEW: builds ChatClient + ChatMemory + tool callbacks

backend/src/main/java/com/ssuai/global/exception/
└── ErrorCode.java                                        # MODIFY: add CHAT_UNAVAILABLE
└── ChatUnavailableException.java                         # NEW
└── GlobalExceptionHandler.java                           # MODIFY: map ChatUnavailableException to 503/CHAT_UNAVAILABLE

backend/src/main/resources/
├── application.yml                                       # MODIFY: ssuai.connector.chat: mock
└── application-prod.yml                                  # MODIFY: ssuai.connector.chat: real, model id, ${SSUAI_ANTHROPIC_API_KEY}

backend/src/test/java/com/ssuai/domain/chat/
├── service/MockChatServiceTest.java                      # NEW
└── controller/ChatControllerTest.java                    # NEW (@WebMvcTest)

deploy/k8s/
├── secret.example.yaml                                   # MODIFY: add SSUAI_ANTHROPIC_API_KEY placeholder (still not committed-real)
└── deployment.yaml                                       # MODIFY: envFrom secretRef now actually expected; document upgrade

frontend/
├── app/chat/page.tsx                                     # NEW
├── app/page.tsx                                          # MODIFY: add header link to /chat
├── components/chat/ChatPanel.tsx                         # NEW
├── components/chat/MessageBubble.tsx                     # NEW
├── lib/api/chat.ts                                       # NEW
├── components/shared/error-state.utils.ts                # MODIFY: add CHAT_UNAVAILABLE case
└── components/shared/ErrorState.tsx                      # MODIFY: same case in userMessage switch

docs/
├── adr/0008-chatbot-stack.md                             # NEW
├── dev-log.md                                            # MODIFY: append one line
└── mcp-tools.md                                          # MODIFY: §2 footnote — these tools are also called by /api/chat

README.md                                                 # MODIFY: Live demo section gets /chat link
```

## Backend implementation notes

### `ChatClientConfig` shape

```java
@Configuration
@ConditionalOnProperty(name = "ssuai.connector.chat", havingValue = "real")
class ChatClientConfig {

    @Bean
    ChatClient ssuaiChatClient(
            ChatClient.Builder builder,
            MealMcpTools mealTools,
            DormMcpTools dormTools,
            CampusMcpTools campusTools,
            ChatMemory chatMemory) {

        ToolCallbackProvider tools = MethodToolCallbackProvider.builder()
                .toolObjects(mealTools, dormTools, campusTools)
                .build();

        return builder
                .defaultSystem("""
                        너는 숭실대학교 학생을 돕는 한국어 비서다.
                        제공된 도구만 사용해서 학식, 기숙사 식단, 캠퍼스 시설 정보를 답한다.
                        도구 결과 외의 사실을 지어내지 말고, 모르면 모른다고 답한다.
                        학적, 성적, 로그인이 필요한 정보는 아직 지원하지 않는다고 안내한다.
                        """)
                .defaultToolCallbacks(tools)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(10).build();
    }
}
```

(Verify exact API names against Spring AI 1.0.0 — the spec is the
shape, not the literal class names if they shifted in a patch release.
If a name differs in the actual 1.0.0 release, follow the official
Spring AI reference and note the deviation in the PR description.)

### `RealChatService` shape

```java
@Service
@ConditionalOnProperty(name = "ssuai.connector.chat", havingValue = "real")
class RealChatService implements ChatService {

    private final ChatClient client;

    RealChatService(ChatClient ssuaiChatClient) {
        this.client = ssuaiChatClient;
    }

    @Override
    public ChatResponse reply(String conversationId, String message) {
        try {
            String text = client.prompt()
                    .user(message)
                    .advisors(a -> a.param(
                            ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return new ChatResponse(conversationId, text);
        } catch (Exception ex) {
            throw new ChatUnavailableException("LLM call failed", ex);
        }
    }
}
```

`MockChatService` returns:
- if message contains "학식" → `"[mock] 오늘 학식은 김치찌개입니다 (mock)."` plus the conversationId.
- if "기숙사" → `"[mock] 이번 주 기숙사 식단 (mock) ..."`.
- else → `"[mock] 도구 호출 없이 mock 응답입니다."`.

The mock must NEVER attempt a real network call. That keeps `dev`
and `test` fully offline.

### `ChatController` shape

```java
@RestController
@RequestMapping("/api/chat")
class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        String conversationId = (req.conversationId() == null || req.conversationId().isBlank())
                ? "c-" + UUID.randomUUID().toString().substring(0, 8)
                : req.conversationId();
        ChatResponse out = chatService.reply(conversationId, req.message());
        return ApiResponse.ok(out);
    }
}
```

### Logging rules (security.md §4)

- Log only: conversationId, message length, latency, tool calls invoked
  (names + count). **Never** log the user's message body, the LLM's
  reply, or tool outputs at INFO. DEBUG level may include them, and the
  prod profile MUST set `domain.chat` logger to INFO or higher.
- Include `traceId` (existing Micrometer-derived) on every log line.

### Configuration

`application.yml` (dev defaults):

```yaml
ssuai:
  connector:
    chat: mock
spring:
  ai:
    anthropic:
      api-key: ${SSUAI_ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-sonnet-4-6
```

(Empty default key — `mock` profile won't try to use it.)

`application-prod.yml`:

```yaml
ssuai:
  connector:
    chat: real
spring:
  ai:
    anthropic:
      api-key: ${SSUAI_ANTHROPIC_API_KEY}    # required, no default — fail fast
      chat:
        options:
          model: claude-sonnet-4-6
logging:
  level:
    com.ssuai.domain.chat: INFO
```

`deploy/k8s/secret.example.yaml` adds (still not committed-real, just
the template):

```yaml
stringData:
  SSUAI_ANTHROPIC_API_KEY: "REPLACE_AT_DEPLOY_TIME"
```

Operator workflow (the developer follows this manually after merge):

```bash
kubectl -n ssuai-prod create secret generic ssuai-secrets \
  --from-literal=SSUAI_ANTHROPIC_API_KEY=sk-ant-... \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n ssuai-prod rollout restart deploy/ssuai-backend
```

This is a `deploy/README.md` documentation update — the spec includes
that doc edit in the file list.

## Frontend implementation notes

### Route shape

`/chat` is a client component (uses TanStack Query mutation).

```tsx
// app/chat/page.tsx
"use client";
import { ChatPanel } from "@/components/chat/ChatPanel";
export default function ChatPage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-3xl flex-col gap-4 px-4 py-6">
      <header className="border-b border-border pb-5">
        <p className="text-sm font-medium text-muted-foreground">ssuAI</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-normal">대화</h1>
      </header>
      <ChatPanel />
    </main>
  );
}
```

`ChatPanel` keeps `messages: Message[]` and `conversationId: string | null`
in `useState`. `sendMessage` uses `useMutation` from TanStack Query.

### API client

`lib/api/chat.ts`:

```ts
import { fetchJson } from "@/lib/api/client";

export interface ChatRequestBody {
  conversationId: string | null;
  message: string;
}
export interface ChatReply {
  conversationId: string;
  reply: string;
}

export function postChat(body: ChatRequestBody): Promise<ChatReply> {
  return fetchJson<ChatReply>("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
```

`fetchJson` already unwraps the envelope and throws `ApiError` on the
error branch — reuse it.

### UI

- Two bubble styles — `bg-muted` for assistant, `bg-primary text-primary-foreground` for user, both rounded.
- Scroll-to-bottom on new message via `ref` + `scrollIntoView`.
- Loading: a "..." bubble while the mutation is pending.
- Error: existing `ErrorState` below the input, showing the new
  `CHAT_UNAVAILABLE` user message.
- Empty state: a sample-prompts strip (예: "오늘 학식 뭐야?", "도서관 근처 카페 알려줘") clickable to pre-fill the input.

### Header link from dashboard

`app/page.tsx` header gets a small link:

```tsx
<a href="/chat" className="text-sm text-muted-foreground hover:text-foreground">
  대화하러 가기 →
</a>
```

(Plain `<a>` — Next.js `Link` is fine too, either works for this scale.)

## Test plan

### Backend

1. `MockChatServiceTest` — three message buckets (학식 / 기숙사 / other),
   assert the deterministic mock replies. Confirms `ChatService`
   contract + dev-default works offline.
2. `ChatControllerTest` (`@WebMvcTest(ChatController.class)`):
   - `200` + envelope shape on valid body.
   - `400` + `VALIDATION_FAILED` on missing message.
   - `400` + `VALIDATION_FAILED` on `message` over 1000 chars.
   - `503` + `CHAT_UNAVAILABLE` when the service throws
     `ChatUnavailableException` (mock the service for this case).
3. `ChatClientConfig` is `@ConditionalOnProperty` for `real`, so the
   default test profile (mock) won't load it. Verify with a lightweight
   slice test that **no** Anthropic auto-config runs in `test` profile —
   the test must pass with no `SSUAI_ANTHROPIC_API_KEY` in env.

### Frontend

1. Reuse the existing utility-only test pattern: a unit test for the
   chat-specific message-shape helper if any (none required initially —
   `ChatPanel` state is straightforward).
2. **No RTL-based component tests in this task** — Task 10 introduces
   the test infra. This task adds a small unit test for the
   `error-state.utils.ts` `CHAT_UNAVAILABLE` case (one assertion in
   `ErrorState.test.ts`).
3. `pnpm --dir frontend lint`, `typecheck`, `build` all green.

### End-to-end (manual, after merge)

- `pnpm dev` + `gradlew.bat bootRun` (default mock profiles): open
  `localhost:3000/chat`, send "오늘 학식 뭐야?", get the mock reply.
- After Vercel + k3s deploy: same but against the production URLs,
  with the Anthropic key set, asking the same question yields a real
  Claude reply that called `get_today_meal`.

## Subtask breakdown (separate commits)

Codex commits in this order, one logical change per commit. Push after
each one — do not stack the whole thing.

1. `feat(backend): add ChatService interface + MockChatService + DTOs`
   - `ChatService.java`, `MockChatService.java`, `ChatRequest.java`,
     `ChatResponse.java`. No controller yet, no LLM call. Just the
     interface and offline mock.
   - Test: `MockChatServiceTest`.
2. `feat(backend): expose POST /api/chat with envelope + validation`
   - `ChatController.java`, `ErrorCode.CHAT_UNAVAILABLE`,
     `ChatUnavailableException.java`, `GlobalExceptionHandler` wiring.
   - `application.yml` — `ssuai.connector.chat: mock`.
   - Test: `ChatControllerTest`.
3. `feat(backend): wire Spring AI ChatClient + tool callbacks (real profile)`
   - `build.gradle` — add `spring-ai-starter-model-anthropic`.
   - `ChatClientConfig.java`, `RealChatService.java`.
   - `application-prod.yml` — flip to `real`, model id, env-var key
     reference.
   - `deploy/k8s/secret.example.yaml` — add the placeholder key.
   - `deploy/README.md` — operator step for creating the Secret.
   - No new tests in this commit — the `real` path is exercised via
     manual smoke test (no live LLM in CI).
4. `feat(frontend): add /chat page + ChatPanel + chat API client`
   - `app/chat/page.tsx`, `components/chat/{ChatPanel,MessageBubble}.tsx`,
     `lib/api/chat.ts`.
   - Header link in `app/page.tsx`.
   - `error-state.utils.ts` + `ErrorState.tsx` `CHAT_UNAVAILABLE`
     case, plus the one-line `ErrorState.test.ts` assertion.
5. `docs: ADR 0008 chatbot stack + dev-log + mcp-tools footnote + README`
   - `docs/adr/0008-chatbot-stack.md`.
   - One line in `docs/dev-log.md`.
   - `docs/mcp-tools.md` §2 footnote: "이 4개 tool 은 `/api/chat`
     이 같은 ToolCallback 으로 직접 호출하기도 한다."
   - `README.md` Live demo section: link `/chat`.

If any subtask is too large to land cleanly, split further — never
combine.

## Verification before reporting done

1. `./gradlew test` (or `gradlew.bat test`) green — all chat tests
   pass with **no** `SSUAI_ANTHROPIC_API_KEY` in the environment.
2. `pnpm --dir frontend lint && pnpm --dir frontend typecheck && pnpm --dir frontend test && pnpm --dir frontend build` all green.
3. `./gradlew bootRun` (default `dev` profile, no LLM key) — `POST
   /api/chat -d '{"message":"오늘 학식 뭐야?"}'` returns the mock
   envelope.
4. CI green on the PR.
5. `git log` after merge shows ~5 small commits, not a mega-commit.

## Security notes (from `docs/security.md`)

- LLM API key is class **Secret** (§2 / §3). Never committed; always
  read from env via `${SSUAI_ANTHROPIC_API_KEY}`. `application-prod.yml`
  must not have a default fallback for this var — fail fast on missing
  key in prod, do not silently boot.
- Logging (§4): never log the message text, the reply text, or the
  tool result at INFO. Log conversationId + message length + tool call
  names only.
- The system prompt explicitly refuses out-of-scope (login, grades,
  PII). Add a regression test asserting the system prompt string
  contains the relevant Korean phrases.
- Input validation (§9): `@NotBlank`, `@Size(max = 1000)` on the
  message — short cap because LLM input is the most abused vector.
- CORS: `WebCorsProdConfig` already allows the configured frontend
  origin — `/api/chat` is on the same controller layer, no extra CORS
  config needed.
- Rate limiting: deferred. If abuse appears, add a per-IP token bucket
  in a filter as a tiny follow-up — do not block this task on it.
- The chatbot must NOT call `kubectl` / shell / file system. The only
  tools registered are the four read-only MCP tools. Verify the tool
  list matches by reading the `MethodToolCallbackProvider` callsite.

## ADR 0008 outline

`docs/adr/0008-chatbot-stack.md`. Mirror the format of
`docs/adr/0007-prod-deploy-oracle-k3s.md`.

Sections:
- **Context** — MVP §3 item 7. Spring AI 1.0.0 GA available, MCP tools
  already exist as Spring beans.
- **Decision** — Spring AI ChatClient + Anthropic Claude
  (`claude-sonnet-4-6`) + reuse the same `*McpTools` beans as tool
  callbacks + in-memory `MessageWindowChatMemory` of last 10 messages.
- **Consequences** — pros (one source of truth for tools, offline tests
  via mock), cons (chat history not durable, no auth scope yet).
- **Alternatives considered**:
  - OpenAI `gpt-4.1-mini` — also fine; Anthropic chosen for developer's
    existing key + Claude's strength on tool calling at this size.
  - Local model (Ollama) — too heavy for the 24 GB ARM box and
    reviewer experience.
  - Re-implementing the tools as plain Spring beans (not MCP) — would
    duplicate tool definitions across MCP and chat. Direct reuse is the
    point.
  - Postgres-backed memory — premature without auth.

## PR description draft

Title: `feat(chat): MVP chatbot via Spring AI + reuse of MCP tools`

```markdown
## What
First chatbot slice: `POST /api/chat` backed by Spring AI's
`ChatClient`, with the same four read-only tools the MCP server exposes
(`get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`,
`search_campus_facilities`) registered as `ToolCallback`s. New `/chat`
page in the frontend.

- `mock` connector by default — local dev and CI run offline.
- `real` connector wired to Anthropic Claude (`claude-sonnet-4-6`) via
  `${SSUAI_ANTHROPIC_API_KEY}`. Prod profile only.
- In-memory `MessageWindowChatMemory` (10 messages, per
  `conversationId`).

## Why
Closes the MVP scope §3 item 7 and lets the same set of business tools
power three surfaces: REST cards, MCP server, chat. ADR 0008 captures
the stack reasoning.

## Notable decisions
- **Anthropic over OpenAI**: developer already operates against
  Anthropic for Claude Code; one fewer secret to manage. Switching is
  a starter-dependency swap.
- **In-memory chat memory**: durable history needs auth; auth is a
  separate task. Memory window is 10 messages.
- **Tools registered via `MethodToolCallbackProvider`**: same provider
  the MCP server uses, so the tool definitions stay in one place.

## Test plan
- [ ] Backend tests pass with no Anthropic key in env (mock profile).
- [ ] `POST /api/chat` round-trip locally (mock) — envelope correct.
- [ ] After deploy with the Secret applied: real Claude reply that
      invokes `get_today_meal`.
- [ ] `/chat` page renders, sample prompts work, error state on
      `CHAT_UNAVAILABLE` shows the new Korean message.

## Next
- Streaming `/api/chat/stream` (SSE) — separate small task once the
  contract is stable.
- Per-user persisted history — blocked on ssuAI auth.
```

## Out-of-scope reminders (FAQ)

- "Why no streaming?" — non-streaming proves the contract first; SSE
  reuses the same service in a follow-up.
- "Why no rate limit?" — no traffic yet; add a per-IP token bucket
  filter as a tiny follow-up if abuse appears.
- "Why no chat history sidebar?" — needs auth scope. The MVP UX is a
  single-thread page; the user starts a new thread on refresh.
- "Why register tools twice (MCP + chat)?" — they are NOT registered
  twice; both the MCP server and the chat `ChatClient` consume the
  same `MealMcpTools`/`DormMcpTools`/`CampusMcpTools` Spring beans via
  `MethodToolCallbackProvider`. The bean instances are shared.
