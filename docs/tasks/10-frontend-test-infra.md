# Task 10 — Frontend test infrastructure (vitest + React Testing Library)

> Hand-off spec for Codex CLI. Reply using the **Required Output Format**
> in `AGENTS.md`. Single small PR.

## Goal

Bring real component-level testing to the frontend. Today, vitest runs
in zero-config mode and only `.test.ts` files (no JSX) execute — the
`getErrorStateDetails` helper had to be extracted to a `.ts` module to
be tested, because there's no `@vitejs/plugin-react` in the project
(see PR #6 review and `docs/dev-log.md` 2026-05-07).

Add a proper vitest config + React Testing Library + jsdom so we can
write component tests for the four cards (`TodayMealCard`,
`WeeklyMealCard`, `DormWeeklyCard`, `FacilitySearchCard`) and for the
`/chat` page (Task 07).

## Why this slice

- Right now a regression in any of the cards (e.g., a broken empty
  state) is caught only by the developer manually opening the page.
  CI catches lint + typecheck + utility unit tests, not UI behaviour.
- ADR 0006 chose shadcn/ui + TanStack Query — both have established
  testing patterns. Adding the matching test stack is low-risk follow-
  through.
- Future Task 07 (chat page) will be much easier to land safely with
  RTL already in place. Doing this BEFORE Task 07 is best, but if Task
  07 lands first, this task adds the tests retroactively for both.

## Scope — in

1. Add devDependencies (pin to current minor versions):
   - `@vitejs/plugin-react`
   - `@testing-library/react`
   - `@testing-library/user-event`
   - `@testing-library/jest-dom`
   - `jsdom` (preferred over `happy-dom` — RTL maintainers test against
     jsdom; happy-dom occasionally diverges)
2. `vitest.config.ts` at `frontend/`:
   ```ts
   import { defineConfig } from "vitest/config";
   import react from "@vitejs/plugin-react";
   import path from "node:path";

   export default defineConfig({
     plugins: [react()],
     resolve: {
       alias: { "@": path.resolve(__dirname, ".") },
     },
     test: {
       environment: "jsdom",
       globals: true,
       setupFiles: ["./vitest.setup.ts"],
     },
   });
   ```
3. `vitest.setup.ts`: `import "@testing-library/jest-dom/vitest";`
4. `tsconfig.json`: ensure `"types": ["vitest/globals", "@testing-library/jest-dom"]`
   in `compilerOptions` (or per-file triple-slash refs — pick one).
5. **Three component tests** to prove the harness:
   - `frontend/components/meal/TodayMealCard.test.tsx` —
     loading skeleton, success state, error state (mock the API client
     via `vi.mock("@/lib/api/meal")`).
   - `frontend/components/facility/FacilitySearchCard.test.tsx` —
     types in the input, hits "검색", asserts results render.
   - `frontend/components/shared/ErrorState.test.tsx` (NEW; promote
     the existing `.test.ts` utility test alongside, OR replace it
     with a proper component test that renders `<ErrorState>` with
     each `code` and asserts the user-facing Korean message). Keep at
     least one of the two flavours green.
6. README: add a one-line note that `pnpm test` now runs RTL component
   tests; document `pnpm test --watch` for development.
7. `application-prod.yml`-equivalent caveat: jsdom is dev-only; bundle
   size is unaffected.

## Scope — out

- Playwright / Cypress E2E — separate larger task.
- Visual regression / snapshot testing — premature.
- CI matrix testing across Node versions — current CI uses Node 20,
  fine for now.
- Storybook — interesting portfolio artifact but a much larger task.

## Files to create / modify

```
frontend/package.json                              # MODIFY: add devDependencies
frontend/pnpm-lock.yaml                            # MODIFY: lockfile bump
frontend/vitest.config.ts                          # NEW
frontend/vitest.setup.ts                           # NEW
frontend/tsconfig.json                             # MODIFY: types
frontend/components/meal/TodayMealCard.test.tsx                  # NEW
frontend/components/facility/FacilitySearchCard.test.tsx         # NEW
frontend/components/shared/ErrorState.test.tsx                   # NEW (or evolve existing .test.ts)
frontend/README.md                                 # MODIFY: test command notes
docs/dev-log.md                                    # MODIFY: one line
```

## Implementation notes

### Why jsdom over happy-dom

happy-dom is faster but RTL's official maintenance target is jsdom.
Bug reports against happy-dom for shadcn (Radix-based) primitives
appear periodically. jsdom is the safer default for a portfolio
project; switch if/when test wall time becomes a problem.

### TanStack Query in tests

Tests that render a card need a `QueryClientProvider` wrapping the
component, with a fresh `QueryClient` per test (avoid cache leakage).
A small helper:

```tsx
// frontend/test-utils/render-with-providers.tsx
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

export function renderWithProviders(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>
  );
}
```

(`retry: false`, `gcTime: 0` — tests must be deterministic and
isolated. No background refetch, no shared cache.)

### Mocking the API client

`vi.mock("@/lib/api/meal", () => ({ getTodayMeal: vi.fn() }))` keeps
fetch out of the test entirely — the component's behavior is what's
under test, not the network. Each test sets the mock's return value
explicitly.

For the error path: `(getTodayMeal as Mock).mockRejectedValueOnce(new
ApiError("CONNECTOR_TIMEOUT", "Timed out", "trace-1", 504))` and assert
that the rendered `ErrorState` shows the Korean fallback for that code.

### `ErrorState` test — keep the helper test

The existing `error-state.utils.ts` unit test is still valuable
(four pure cases). Don't delete it — add the component-level test
alongside. The redundancy is intentional: the helper test catches
mapping regressions, the component test catches rendering regressions.

## Subtask breakdown

Two commits, in order:

1. `chore(frontend): add @vitejs/plugin-react + RTL + jsdom + vitest config`
   - Devdeps, `vitest.config.ts`, `vitest.setup.ts`, `tsconfig.json`
     types, `test-utils/render-with-providers.tsx`.
   - **No** new component tests yet — verify `pnpm test` runs the
     existing `error-state.utils` test through the new config.
2. `test(frontend): add component tests for cards + ErrorState`
   - Three test files. Card tests mock the API client; ErrorState test
     renders the component with each error code.
   - dev-log line.

## Verification before reporting done

1. `pnpm --dir frontend lint` green.
2. `pnpm --dir frontend typecheck` green (the test files are type-
   checked too — RTL types and jest-dom matchers must resolve).
3. `pnpm --dir frontend test` runs all tests; the three new component
   tests are listed and pass.
4. `pnpm --dir frontend build` green (test files and config don't
   leak into the bundle — they shouldn't, but verify).
5. CI green.

## Security notes

None new. devDependencies only; nothing reaches production. Confirm
none of the test fixtures contain anything that looks like a
real student name or email (the existing meal/dorm fixtures are
already mock data — reuse those, don't capture new ones).

## PR description draft

Title: `test(frontend): add @vitejs/plugin-react + RTL + jsdom`

```markdown
## What
- Devdeps: `@vitejs/plugin-react`, `@testing-library/react`,
  `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`.
- `vitest.config.ts` + `vitest.setup.ts` + tsconfig types.
- `test-utils/render-with-providers.tsx` (QueryClientProvider helper).
- Three component tests: `TodayMealCard`, `FacilitySearchCard`,
  `ErrorState`.

## Why
Today vitest runs zero-config and refuses JSX. PR #6's helper had to
be extracted to a `.ts` module to be tested. This unblocks proper
component testing — necessary before Task 07's `/chat` page lands
and useful for current cards.

## Test plan
- [ ] `pnpm test` runs all tests in jsdom, all green.
- [ ] Type-check passes for the test files.
- [ ] Build unchanged (test bundle exclusion verified).
```
