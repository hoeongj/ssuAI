# ADR 0006 - Frontend MVP stack

- **Status**: Accepted
- **Date**: 2026-05-07
- **Scope**: `frontend/`

## Context

Task 05 adds the first web dashboard for the local MVP. The frontend must prove
the existing REST envelope end to end, stay small enough to review, and leave
production deployment decisions for Task 06.

The architecture document already names Next.js App Router, TypeScript,
Tailwind CSS, shadcn/ui, and TanStack Query as the intended frontend shape.
This ADR records that stack as the implementation decision for the MVP.

## Decision

Use:

- Next.js 15 App Router with TypeScript strict mode.
- Tailwind CSS for styling.
- shadcn/ui copy-in primitives for basic controls and cards.
- TanStack Query v5 for server state, retries, stale times, and independent
  card loading/error states.
- Direct browser fetches to the Spring Boot backend, with dev-profile CORS for
  `http://localhost:3000`.

The frontend reads `NEXT_PUBLIC_SSUAI_API_BASE` from env and unwraps the
backend `ApiResponse<T>` envelope in one typed API client.

## Consequences

**Good**

- The dashboard uses the same backend contract that future web and chatbot
  surfaces will depend on.
- Each card can fail, retry, and show `traceId` without breaking the rest of
  the page.
- shadcn components stay in the repository, so there is no UI framework runtime
  to configure or replace later.

**Cost**

- Next.js is heavier than a plain SPA for a local-only dashboard.
- Direct fetches make CORS visible in development, so production CORS must be
  handled deliberately in Task 06.
- shadcn copy-in components are source code the project owns; future changes
  require normal code review.

## Alternatives considered

- **Vite SPA** - smaller and fast for local development, but it would diverge
  from the architecture lock and defer decisions around routing, deployment,
  and server/client boundaries.
- **SWR instead of TanStack Query** - enough for simple fetches, but weaker for
  shared query keys, retry policy, and future invalidation once personalization
  arrives.
- **MUI or Chakra instead of shadcn/ui** - faster to assemble initially, but
  adds a heavier runtime design system and makes the portfolio code less
  explicit.
- **Next.js rewrites instead of CORS** - hides browser CORS during development,
  but it also hides the production policy that Task 06 must make explicit.
