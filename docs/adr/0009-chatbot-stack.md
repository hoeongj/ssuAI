# ADR 0009 - MVP chatbot stack and fallback budget

- **Status**: Proposed (accepted when `feat/chatbot-slice` merges)
- **Date**: 2026-05-12
- **Scope**: `backend/src/main/java/com/ssuai/domain/chat/`,
  `frontend/app/chat/`, LLM provider configuration, chat tool usage.

## Context

The MVP product scope includes a basic text chatbot for public campus data:
cafeteria menus, dorm meals, and campus facilities. The project already has
REST endpoints and an MCP server backed by the same service layer. The chatbot
should reuse that work without introducing authenticated school data or write
tools.

The first draft assumed one Anthropic/Spring AI ChatClient path. During
implementation, provider quota and privacy constraints made a single-provider
choice fragile for a public demo. The implementation therefore needs a small
provider abstraction and explicit request-level budget controls.

## Decision

Use a custom OpenAI-compatible provider layer over Spring `RestClient`.

- `MockChatService` is the default for `dev` and `test`.
- `LlmChatService` is enabled by `ssuai.connector.chat=llm`.
- Provider order is config-driven.
- Providers without API keys are skipped before any request is attempted.
- Fallback is bounded by:
  - `SSUAI_LLM_AVAILABILITY_VERIFICATION_PASSES`
  - `SSUAI_LLM_MAX_PROVIDER_ATTEMPTS`
  - `SSUAI_LLM_MAX_MODELS_PER_PROVIDER`
- Existing `MealMcpTools`, `DormMcpTools`, and `CampusMcpTools` beans are
  called directly from the chat service.
- Tool results are compacted before being sent back to the LLM.

## Consequences

Good:

- CI and local dev stay fully offline by default.
- One chat implementation can use multiple OpenAI-compatible providers.
- Provider failures are isolated and observable without logging message bodies
  or secrets.
- Request-level caps prevent one user question from causing unbounded
  provider/model fallback.
- The chatbot and MCP server still share the same service-backed tool beans.

Tradeoffs:

- This does not yet dogfood the MCP client protocol from the chatbot.
- Provider-specific behavior still has to be normalized in code.
- There is no durable chat memory; conversation state is only the
  `conversationId` round trip and current message.

## Alternatives Considered

- **Spring AI ChatClient + Anthropic only**: simpler API, but one key/quota
  becomes the single point of failure for the demo.
- **OpenRouter only**: easy fallback syntax, but account-level free quota and
  privacy/ZDR support still limit availability.
- **MCP client dogfooding inside the same JVM**: stronger portfolio story, but
  larger moving part for the MVP. Keep it as a follow-up after GitOps.
- **Local model**: avoids hosted provider data policy concerns, but is too
  heavy for the current free-tier infrastructure and reviewer experience.
