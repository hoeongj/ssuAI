# Task 02 — Public Cafeteria Menu API (mock connector)

> Hand-off spec for the implementer (Codex CLI). Read `docs/architecture.md`
> §3–§6, §10 and `docs/security.md` first. This task must not contradict
> them. Reply using the Required Output Format in `AGENTS.md`.

## Goal

Build the first domain slice — `GET /api/meals/today` — using a **mock
connector**. After this task is merged, a fresh clone should:

1. Build with `gradlew.bat test` (Windows) without errors.
2. Run with `gradlew.bat bootRun` and serve `GET /api/meals/today` returning
   today's mock menu wrapped in the standard `ApiResponse<T>` envelope.
3. Establish the connector pattern (interface + mock impl + profile-based
   selection) so Task 03 (real connector) only needs to add
   `RealMealConnector`.

## Why this slice first

- No login, no PII → exercises the architecture without security risk.
- Public data → safe to log freely; matches `docs/security.md` Class
  "Public".
- Forces the Connector / Service / Controller separation end-to-end before
  it gets harder to undo.
- Useful for the chatbot tool layer later (`get_today_meal` MCP tool will
  call `MealService.getTodayMeal()`).

## Scope — in

1. New package `com.ssuai.domain.meal` with sub-packages `controller`,
   `service`, `dto`, `connector` (per `docs/architecture.md` §4).
2. `MealConnector` interface and `MockMealConnector` implementation.
3. `MealService` that delegates to the connector.
4. `MealController` exposing `GET /api/meals/today`.
5. DTOs: `MealResponse`, `MealItem`, `MealType` enum.
6. Connector selection via `@ConditionalOnProperty` keyed on
   `ssuai.connector.meal` (default `mock`).
7. `application.yml` entry for the connector property.
8. Tests: connector unit test, service unit test, controller slice test.
9. **Delete** the temporary `HelloController` and `HelloControllerTests`
   from Task 01.

## Scope — out (do NOT write)

- `RealMealConnector` (Jsoup/HTTP) — Task 03.
- Redis caching — deferred until the real connector lands; the service is
  a thin pass-through for now. Per `docs/architecture.md` §7, caching is a
  Service-layer concern and goes in when there's a real upstream to cache.
- Date query parameter (`?date=YYYY-MM-DD`) — only "today" for v1.
- Multiple cafeterias / campus selection — single default cafeteria for v1.
- Spring Security, auth, rate limiting.
- MCP tool wrapping (`get_today_meal`) — that's a later task.
- OpenAPI / springdoc.

If a requirement seems to need any of the above, stop and flag rather than
expanding scope.

## Files to create / modify

```
backend/src/main/java/com/ssuai/domain/meal/
├── controller/
│   └── MealController.java
├── service/
│   └── MealService.java
├── dto/
│   ├── MealResponse.java
│   ├── MealItem.java
│   └── MealType.java
└── connector/
    ├── MealConnector.java
    └── MockMealConnector.java

backend/src/main/resources/
└── application.yml                  (modified: add ssuai.connector.meal)

backend/src/test/java/com/ssuai/domain/meal/
├── controller/
│   └── MealControllerTests.java
├── service/
│   └── MealServiceTests.java
└── connector/
    └── MockMealConnectorTests.java

DELETE:
- backend/src/main/java/com/ssuai/global/web/HelloController.java
- backend/src/test/java/com/ssuai/global/web/HelloControllerTests.java
- (remove the now-empty global/web/ directory if your tooling allows; if
  not, leave it — Git will not track empty directories anyway)
```

## Contracts (these shapes must match exactly)

### Endpoint

`GET /api/meals/today`

- No request body, no query parameters.
- 200 success → `ApiResponse<MealResponse>`.
- 5xx on connector failure → handled by `GlobalExceptionHandler`. Mock
  connector should not fail under normal operation.

### `MealResponse`

```json
{
  "data": {
    "date": "2026-05-06",
    "meals": [
      { "type": "BREAKFAST", "menu": ["흰밥", "미역국", "계란말이", "김치"] },
      { "type": "LUNCH",     "menu": ["보리밥", "된장찌개", "제육볶음", "콩나물무침", "김치"] },
      { "type": "DINNER",    "menu": ["흰밥", "김치찌개", "고등어구이", "시금치나물", "김치"] }
    ]
  },
  "error": null,
  "traceId": "..."
}
```

Java shape — Java records:

- `MealResponse(LocalDate date, List<MealItem> meals)`
- `MealItem(MealType type, List<String> menu)`
- `MealType` enum: `BREAKFAST`, `LUNCH`, `DINNER`

`LocalDate` serializes to `YYYY-MM-DD` by default with Jackson's JSR-310
module — Spring Boot starter-web pulls it in. No custom serializer needed.

### `MealConnector` (interface)

```java
public interface MealConnector {
    MealResponse fetchMeal(LocalDate date);
}
```

- Returns the internal DTO directly. For Task 02 the API DTO and the
  connector DTO are the same type (`MealResponse`). When Task 03's real
  connector returns richer data than the API exposes, split into a
  separate internal DTO at that point — not now (YAGNI).
- Connectors throw `ApiException(ErrorCode.CONNECTOR_UNAVAILABLE)` or
  `ApiException(ErrorCode.CONNECTOR_TIMEOUT)` on failure. The mock
  doesn't fail; this is documentation for Task 03.

### `MockMealConnector`

- Annotated `@Component` and
  `@ConditionalOnProperty(name = "ssuai.connector.meal", havingValue = "mock", matchIfMissing = true)`.
- Returns the same fixture for any `LocalDate` — three meals
  (BREAKFAST / LUNCH / DINNER), each with a small Korean menu list (you
  may use the example values above verbatim).
- The returned `MealResponse.date` is the input `date`, not a hard-coded
  date.
- Class name and behavior must clearly be "mock" — `MockMealConnector` is
  the name; no need for an extra annotation.

### `MealService`

```java
@Service
public class MealService {
    private final MealConnector mealConnector;
    // constructor injection only

    public MealResponse getTodayMeal() {
        return mealConnector.fetchMeal(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }
}
```

- Constructor injection. No `@Autowired` on fields.
- `LocalDate.now(ZoneId.of("Asia/Seoul"))` — explicit timezone so behavior
  doesn't depend on JVM default. Soongsil is in Seoul time.
- For Task 02 the service is a thin pass-through. Caching, fallback, and
  retry land later.

### `MealController`

```java
@RestController
@RequestMapping("/api/meals")
public class MealController {
    private final MealService mealService;
    // constructor injection only

    @GetMapping("/today")
    public ApiResponse<MealResponse> getTodayMeal() {
        return ApiResponse.success(mealService.getTodayMeal());
    }
}
```

- No business logic. No `LocalDate` math. No connector calls.

## Configuration

Add to `application.yml` (shared):

```yaml
ssuai:
  connector:
    meal: mock        # mock | real (real lands in Task 03)
```

Do NOT add this entry to `application-dev.yml` or `application-test.yml` —
the shared default is `mock`, which is what both profiles want. Future
`application-prod.yml` will override to `real`.

## Test Requirements

All tests run under the `test` profile. No network, no DB, no Redis.

### `MockMealConnectorTests` (unit)
- Plain JUnit, no Spring context.
- `fetchMeal(LocalDate.of(2026, 5, 6))` returns a `MealResponse` whose
  `date` equals the input.
- The returned `meals` list has exactly 3 entries with types
  `BREAKFAST`, `LUNCH`, `DINNER` in that order.
- Each `MealItem.menu` is non-empty.

### `MealServiceTests` (unit)
- Mock the `MealConnector` (Mockito).
- Assert `MealService.getTodayMeal()` calls `connector.fetchMeal(...)`
  with a date in the Asia/Seoul zone (assert it equals
  `LocalDate.now(ZoneId.of("Asia/Seoul"))` at the moment of the test —
  acceptable to be loose: assert it's not null and within ±1 day of the
  test's "today").
- Assert the service returns whatever the connector returned (identity
  check on the mocked `MealResponse`).

### `MealControllerTests` (slice)
- `@WebMvcTest(MealController.class)` with `@MockBean` for `MealService`.
- One success test:
  - Given `mealService.getTodayMeal()` returns a fixed `MealResponse`,
  - perform `GET /api/meals/today`,
  - expect 200,
  - expect `$.data.date` non-empty, `$.data.meals[0].type == "BREAKFAST"`,
    `$.data.meals[0].menu` is a non-empty array,
  - expect `$.error` null,
  - expect `$.traceId` non-empty.

## Acceptance Criteria

- `gradlew.bat test` passes from a clean checkout. Old Task 01 tests
  (`SsuaiApplicationTests` context-load) still pass; `HelloControllerTests`
  is gone (deleted).
- `gradlew.bat bootRun` starts on port 8080.
- `curl http://localhost:8080/api/meals/today` returns 200 with the
  envelope shape above. The `data.date` is today's date in `Asia/Seoul`.
- `curl http://localhost:8080/api/hello?name=World` returns 404 with the
  `NOT_FOUND` error envelope (because the controller is gone — proves the
  delete succeeded).
- `GET /actuator/health` still returns `{"status":"UP"}`.
- Package layout matches `docs/architecture.md` §4 exactly. No code
  outside `domain.meal` other than the existing `global.*` and the
  `application.yml` edit.
- No new production dependencies in `build.gradle`.

## Style / conventions

- **Java records** for DTOs (`MealResponse`, `MealItem`).
- **Constructor injection only** — no `@Autowired` on fields.
- **No Lombok on DTOs**. `@RequiredArgsConstructor` and `@Slf4j` on
  services/connectors are fine.
- **Logging** — parameterized form only (`log.info("fetched meal date={}",
  date)`). Re-read `docs/security.md` §4 before adding any log line. Mock
  data is "Public" class so it's safe to log content, but get the habits
  right anyway.
- **Validation** — n/a for this endpoint (no inputs).
- Package-private classes/methods where they don't need to be public
  outside the slice.

## Hand-off checklist (the reviewer will check these)

- [ ] Package layout exactly matches `domain/meal/{controller,service,dto,connector}`.
- [ ] `MealConnector` is an interface; `MockMealConnector` is the only
      implementation in this PR.
- [ ] `@ConditionalOnProperty(... matchIfMissing = true)` on
      `MockMealConnector` so a fresh checkout boots without any extra
      config.
- [ ] `MealService` uses `LocalDate.now(ZoneId.of("Asia/Seoul"))`, not
      `LocalDate.now()`.
- [ ] `MealController` does not call the connector directly.
- [ ] `MealController` does not contain any date logic.
- [ ] Old `HelloController.java` and `HelloControllerTests.java` are
      deleted.
- [ ] `application.yml` has `ssuai.connector.meal: mock`.
- [ ] Three test classes pass; all run under the `test` profile.
- [ ] No new production dependencies added.
- [ ] No real student names, IDs, or grades in any fixture or log line.

## Verification commands (Windows, from `backend/`)

```
gradlew.bat test
gradlew.bat bootRun
```

Then in a second shell:

```
curl http://localhost:8080/api/meals/today
curl -i http://localhost:8080/api/hello?name=World
curl http://localhost:8080/actuator/health
```

Expected:

- Meals → 200 with the envelope shape above.
- Hello → 404 `NOT_FOUND` envelope.
- Health → 200 `{"status":"UP"}`.

## Commit guidance

Prefer focused commits, e.g.:

1. `feat(meal): add MealConnector interface and MockMealConnector with @ConditionalOnProperty`
2. `feat(meal): add MealService and MealController for /api/meals/today`
3. `test(meal): add connector / service / controller tests`
4. `chore(global): remove temporary HelloController after Task 01`

Squash on merge to `main` is fine — the per-commit history lives only on
the feature branch.

## Out-of-band notes for the reviewer (Claude)

When reviewing this task, check specifically:

1. **Connector boundary respected?** Controller → Service → Connector,
   never skip a layer. No `@Component` connector in the controller package,
   no controller talking to `LocalDate.now()` directly.
2. **Mock is clearly a mock?** Class name, package, and `@ConditionalOnProperty`
   value all say "mock". A future reader should not confuse this for the
   real impl.
3. **Timezone explicit?** `LocalDate.now(ZoneId.of("Asia/Seoul"))` is the
   only way "today" should be computed. A bare `LocalDate.now()` is a
   reviewer-block.
4. **Old HelloController really gone?** Including the test file. Including
   any references in CLAUDE.md or AGENTS.md (though those should not
   reference it).
5. **No premature DTO splitting.** A separate "internal DailyMeal" type vs
   "API MealResponse" type is overengineering for Task 02 — flag if
   present. Architecture.md §10 names them as conceptually separate, but
   YAGNI: split when there's a real divergence (Task 03 may introduce it).

Return at most 3 high-priority issues per the review style in `CLAUDE.md`.
