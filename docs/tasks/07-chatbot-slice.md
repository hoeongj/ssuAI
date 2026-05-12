# Chatbot slice - MVP implementation spec

> Revised on 2026-05-12. The earlier draft was marked "do not execute"
> because it assumed a different Spring AI/Anthropic shape. This revision
> documents the implementation now living on `feat/chatbot-slice`.

## Goal

Add the MVP text chatbot:

- `POST /api/chat` using the existing `ApiResponse<T>` envelope.
- A deterministic `mock` chat mode for dev/test.
- An `llm` chat mode that uses OpenAI-compatible provider APIs with
  bounded fallback.
- A `/chat` page in the Next.js frontend.
- Reuse of the existing read-only campus tool beans:
  `MealMcpTools`, `DormMcpTools`, `CampusMcpTools`.

The chatbot remains anonymous, non-streaming, read-only, and public-data-only.
It must not handle user accounts, LMS/u-SAINT login, grades, assignments, or
school credentials.

## Current decision

The MVP implementation uses direct OpenAI-compatible HTTP calls through
`RestClient`, not a model-specific Spring AI ChatClient starter. This keeps the
runtime dependency surface small and lets one code path support Gemini, Groq,
Cerebras, DeepInfra, SambaNova, Nscale, Fireworks, Hugging Face, Mistral, and
OpenRouter.

The chat service calls the same Spring tool beans that the MCP server exposes.
This preserves one business-logic source of truth. A future dogfooding task can
route chat through the MCP client protocol if that becomes important for the
portfolio story, but this slice prioritizes a small safe MVP.

## Scope in

1. Backend chat domain:
   - `ChatController`
   - `ChatService`
   - `MockChatService`
   - `LlmChatService`
   - OpenAI-compatible request/response DTOs
   - provider abstraction and provider-specific config
2. Error contract:
   - `CHAT_UNAVAILABLE`
   - `ChatUnavailableException`
   - 503 mapping in `GlobalExceptionHandler`
3. Config:
   - default `ssuai.connector.chat: mock`
   - prod `ssuai.connector.chat` reads `SSUAI_CONNECTOR_CHAT` and defaults to
     `mock`
   - env-driven provider API keys only
   - request-level fallback guardrails:
     - `SSUAI_LLM_AVAILABILITY_VERIFICATION_PASSES`
     - `SSUAI_LLM_MAX_PROVIDER_ATTEMPTS`
     - `SSUAI_LLM_MAX_MODELS_PER_PROVIDER`
4. Frontend:
   - `/chat` route
   - `ChatPanel`
   - `MessageBubble`
   - `lib/api/chat.ts`
   - dashboard link to `/chat`
   - `CHAT_UNAVAILABLE` error UX
5. Deployment templates:
   - LLM API key placeholders in `deploy/k8s/secret.example.yaml`
   - fallback budget env vars in `deploy/k8s/configmap.yaml`
6. Tests:
   - controller validation/envelope tests
   - mock service tests
   - provider fallback tests
   - no live LLM calls
   - frontend component/API error tests remain offline

## Scope out

- Streaming chat.
- Persisted chat history.
- Authentication or personalization.
- LMS/u-SAINT/grade/assignment access.
- Action/write tools.
- RAG over school notices.
- Auto-rotating provider keys or quota dashboards.

## Safety rules

- Never log user message text, LLM reply text, provider response body, or tool
  result at INFO.
- Reject or answer locally when the user appears to provide secrets
  (`password`, `cookie`, `token`, API key, etc.).
- Answer locally when the user asks for private academic data that the MVP does
  not support.
- Tests must not call real LLM providers, real u-SAINT, real LMS, or any
  authenticated school endpoint.
- Tool results sent back to the LLM must be compact. Do not send full REST/MCP
  DTOs when the answer only needs a subset.
- Facility search from chat requires a query. Empty query must not feed the
  full campus facility list into the prompt.

## Verification

Run from repo root unless noted:

```bash
backend/gradlew.bat test
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
```

Manual smoke, default mock mode:

```bash
cd backend
gradlew.bat bootRun
```

Then open the frontend and visit `/chat`, or call:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"오늘 학식 뭐야?\"}"
```

The response should use the standard envelope and return a mock reply unless
`SSUAI_CONNECTOR_CHAT=llm` is explicitly configured.

Production also defaults to `mock` in the committed manifests. Hosted LLM mode
is an explicit operator action: set `SSUAI_CONNECTOR_CHAT=llm` and provide at
least one provider API key in the Kubernetes Secret.

## Follow-ups

1. Add streaming after the non-streaming API has settled.
2. Add per-IP rate limiting if the public demo receives abuse.
3. Consider MCP-client dogfooding after GitOps is complete.
4. Add durable memory only after ssuAI user authentication exists.
