# Task 08 — OpenAPI / Swagger UI via springdoc-openapi

> Hand-off spec for Codex CLI. Reply using the **Required Output Format**
> in `AGENTS.md`. Small, single-PR task.

## Goal

Expose a typed OpenAPI 3 spec at `/v3/api-docs` and a Swagger UI at
`/swagger-ui.html` for the ssuAI backend, covering all REST endpoints
(meal, dorm-meal, campus-facility, chat once Task 07 lands). The spec
is the single source of truth for any future TypeScript client
generation, and the UI is a portfolio-friendly "click and try" surface.

## Why this slice

- Reviewers and recruiters expect Swagger UI on a Java backend portfolio.
  Cost of adding it is one dependency + a tiny config; payoff is high.
- Once the OpenAPI JSON is generated, future TypeScript client
  generation (TanStack Query + `openapi-typescript-codegen`) becomes a
  one-command operation. Frontend currently hand-writes types — fine for
  4 endpoints, bad at 10+.
- Forces explicit schema names on DTOs, which doubles as documentation.

## Scope — in

1. Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` (currently
   the canonical starter for Spring Boot 3.x; pin a 2.x release that
   matches Boot 3.3.x — verify on `central.sonatype.com`).
2. `OpenApiConfig.java` with `@OpenAPIDefinition` setting:
   - `info.title = "ssuAI Backend API"`,
   - `info.version` derived from `build.gradle` `version` (or hardcoded
     `0.0.1` for now),
   - `info.description` linking to `docs/product.md`.
3. Annotate each existing controller with `@Tag` (e.g.,
   `@Tag(name = "meal")`) and each endpoint with `@Operation(summary
   = ...)`. Korean summaries are fine — that's our user audience.
4. `application.yml`: `springdoc.api-docs.path=/v3/api-docs`,
   `springdoc.swagger-ui.path=/swagger-ui.html` (those are defaults but
   pin them so a future starter version doesn't move them silently).
5. **Disable Swagger UI in `prod` profile** (`springdoc.swagger-ui.enabled
   =false` in `application-prod.yml`) — avoid exposing schema-level
   information to the public internet. Keep `/v3/api-docs` accessible
   in prod (the JSON spec itself isn't sensitive and it's useful for
   monitoring tools).
6. README: a "API docs" line linking `http://localhost:8080/swagger-ui.html`
   for local dev.

## Scope — out

- TypeScript client code generation in the frontend — separate task
  once the spec is stable.
- API versioning (`/v1/...`) — premature; revisit when a breaking
  change is on the horizon.
- Auth requirements in the spec — no auth yet.

## Files to create / modify

```
backend/build.gradle                                              # MODIFY: add springdoc starter
backend/src/main/java/com/ssuai/global/config/OpenApiConfig.java  # NEW
backend/src/main/java/com/ssuai/domain/meal/controller/MealController.java                # MODIFY: @Tag + @Operation per method
backend/src/main/java/com/ssuai/domain/dorm/controller/DormMealController.java            # MODIFY: same
backend/src/main/java/com/ssuai/domain/campus/controller/CampusFacilityController.java    # MODIFY: same
backend/src/main/java/com/ssuai/domain/chat/controller/ChatController.java                # MODIFY (only if Task 07 already merged; otherwise skip)
backend/src/main/resources/application.yml                        # MODIFY
backend/src/main/resources/application-prod.yml                   # MODIFY
README.md                                                         # MODIFY: API docs section
docs/dev-log.md                                                   # MODIFY: append one line
```

## Implementation notes

- Use the Korean summary style from `docs/mcp-tools.md` for consistency
  (e.g., "오늘 캠퍼스 식당 메뉴 조회"). Long descriptions can stay in
  English when they describe types or shapes.
- `ApiResponse<T>` envelope — make sure the generated schema reflects
  the wrapper. springdoc handles generic response types automatically
  if the controller method signature is `ApiResponse<MealResponse>`
  (which is already the case).
- Don't add a CORS config change for `/v3/api-docs` — the existing
  prod CORS allowlist intentionally limits to the frontend origin, and
  a separate concern (monitoring) can be added later.
- Validate the generated spec by visiting `/swagger-ui.html` and
  expanding each endpoint to confirm DTO field documentation appears.

## Subtask breakdown

Two commits, in order:

1. `chore(backend): add springdoc-openapi starter + OpenApiConfig`
   - `build.gradle` dependency, `OpenApiConfig.java`, `application.yml`
     and `application-prod.yml` config, README API docs line.
   - At this point `/swagger-ui.html` works but endpoint annotations
     are minimal. Verify locally.
2. `docs(backend): annotate controllers with @Tag and @Operation`
   - All controllers gain Korean `@Tag` + `@Operation(summary)` per
     method. DTO field-level descriptions are nice-to-have but not
     required for this task.
   - dev-log line.

## Verification before reporting done

1. `./gradlew test` green (no test changes — annotations don't touch
   behavior).
2. `./gradlew bootRun` → `curl http://localhost:8080/v3/api-docs` returns
   a non-empty JSON with `paths` containing every controller route.
3. Browser → `http://localhost:8080/swagger-ui.html` shows all
   endpoints grouped under their tags.
4. With `--spring.profiles.active=prod` (and the required env vars
   stubbed), `curl /swagger-ui.html` returns 404 (UI disabled), but
   `/v3/api-docs` still returns 200 JSON.
5. CI green.

## Security notes

- Swagger UI off in prod (§5 above) — schema enumeration is a low-grade
  recon vector for a public API. Cost of disabling: zero.
- The OpenAPI JSON itself doesn't contain secrets, only schemas. Safe
  to expose.
- No auth schemas to add yet — when ssuAI auth lands, return here and
  add `@SecurityScheme` for the JWT/session.

## PR description draft

Title: `chore(backend): expose OpenAPI 3 spec + Swagger UI (dev only)`

```markdown
## What
- Add `springdoc-openapi-starter-webmvc-ui`.
- `OpenApiConfig` with title + version + description.
- `@Tag` + `@Operation` annotations on existing controllers (Korean
  summaries to match the user audience).
- `/v3/api-docs` JSON exposed in all profiles.
- `/swagger-ui.html` enabled in dev/test, disabled in prod.

## Why
Reviewer/portfolio expectation; doubles as the source for future TS
client generation.

## Test plan
- [ ] `/swagger-ui.html` renders all routes locally.
- [ ] `/v3/api-docs` JSON valid in all profiles.
- [ ] `/swagger-ui.html` 404 under `prod` profile.
```
