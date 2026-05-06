# Task 04 — Dormitory Cafeteria Menu Connector

> Hand-off spec. Read `docs/architecture.md` §5, §6 / `docs/security.md`
> §4, §11 / `docs/adr/0001-meal-response-closures.md` first.
>
> **Status: IMPLEMENTED** — fixture analyzed, 1단계 호출 / EUC-KR / 끼니별
> closure / 공휴일 키워드 처리까지 모두 반영. 본 spec 은 "어떻게 만들었나"
> 의 영구 기록입니다.

## Goal

레지던스홀(기숙사) 식당의 **주간 식단**을 외부 게시판에서 긁어 MCP·REST
양쪽에 노출합니다. 학식 connector(Task 03 + P3 분해)와 동일한 connector
패턴을 두 번째 외부 사이트에 적용해 패턴의 재사용성을 입증하는 것이
부수 목표.

## Why this slice

- `CampusFacilityService.java` 의 `residence-hall-cafeteria` 항목은
  메뉴 페이지 URL 한 줄만 갖고 있어 MCP 클라이언트가 *"오늘 기숙사
  저녁 뭐야?"* 에 답할 데이터 자체가 시스템 안에 없었습니다.
- P3 분해로 `MealConnector` 가 단일 식당 단위가 됐지만 connector
  구현체는 여전히 학식 1개. 두 번째 connector 가 얹혀야 분해의 가치가
  회수됩니다.
- 데이터는 "Public" 클래스 — 보안 영향 작음.

## Pre-requisites (실측 결과)

| Variable | 값 |
|---|---|
| `DORM_MEAL_PAGE_URL` | `https://ssudorm.ssu.ac.kr:444/SShostel/mall_main.php?viewform=B0001_foodboard_list&board_no=1` — **단일 호출만으로 1주일치 응답 (목록→상세 분리 불필요).** |
| `DORM_MEAL_FIXTURE` | `backend/src/test/resources/fixtures/meal/dorm-week-success.html` (UTF-8 디스크 저장, 실제 응답은 EUC-KR) |
| `DORM_MEAL_TABLE_SELECTOR` | `table.boxstyle02 tbody tr` — 7개 row (월~일) |
| `DORM_MEAL_DATE_HEADER_SELECTOR` | row 의 `th` (텍스트 패턴 `YYYY-MM-DD (요일)`, 정규식 `(\\d{4}-\\d{2}-\\d{2})` 로 추출) |
| `DORM_MEAL_CELL_SELECTOR` | row 의 `td` 4개 — index 0=조식, 1=중식, 2=석식, 3=중.석식(현 fixture 항상 빈 칸이라 무시) |
| `DORM_MEAL_MENU_SEPARATOR` | `<br />` — `cellText()` 가 `<br>` 을 `\n` 으로 치환 후 `wholeText()` |
| `DORM_MEAL_PAGE_ENCODING` | **`EUC-KR`** — `<meta charset=euc-kr>`. Jsoup 의 자동 감지를 신뢰하지 않고 `Jsoup.parse(InputStream, "EUC-KR", baseUri)` 로 강제 |
| `DORM_MEAL_PAGE_IS_STATIC` | `true` — 메뉴 텍스트가 응답 HTML 에 그대로 들어있음 |
| `robots.txt` | (확인 필요 — 사람이 한 번 더) |

## Scope — in (실제 구현된 것)

### 1) 인터페이스

```java
public interface DormMealConnector {
    WeeklyMealResponse fetchThisWeekMeal();
}
```

`dateInWeek` 파라미터를 두는 옵션도 검토했으나, v1 connector 가 항상
"현재 주" 만 반환하므로 시그니처에 거짓 약속을 두지 않기 위해 인자
없는 메서드로 결정. 임의 주차 조회는 v2 (별도 메서드 추가).

### 2) 구현 2종

- `MockDormMealConnector` — `@ConditionalOnProperty
  ssuai.connector.dorm-meal=mock`, `matchIfMissing=true`. 호출 시점의
  현재 주 월~일 7일치 mock 응답 (모든 일자 조식 미운영 closure +
  중식/석식 mock 메뉴).
- `RealDormMealConnector` — `@ConditionalOnProperty ... =real`. 단일
  HTTP 호출 → EUC-KR 강제 파싱 → 표 순회 → 끼니별 closure/menu 분기.

### 3) 휴무 처리 — 사용자 강조 사항

각 셀 텍스트를 다음 분기로:
- **빈 셀** → 조용히 skip (예: 중.석식 컬럼은 항상 빈 칸)
- **휴무 키워드 포함** → `MealClosure(restaurant, "<끼니명> <셀텍스트>")`
  로 흡수. reason 형식 예시:
  - `"조식 미운영"` (가장 흔함)
  - `"중식 어린이날 휴무"` (공휴일 등 명시 사유 보존)
  - `"석식 운영하지 않습니다"`
- **그 외** → `<br>` split → trim → 빈 토큰 제거 → `MealItem(restaurant,
  type, corner, menu)` 추가

휴무 키워드 셋: `미운영, 휴무, 쉽니다, 공휴일, 운영하지, 운영 안, 어린이날`
(`RealDormMealConnector.CLOSURE_KEYWORDS`, 학식 connector 의
`isClosureReason` 와 동일 set + `미운영`).

**일자 전체가 휴무인 경우** — 한 row 의 모든 셀이 휴무 키워드면 그
일자에 closures 가 3개 (조/중/석) 들어감. 통합하지 않고 그대로 유지 —
클라이언트(MCP 포함)가 "이 날 closures 가 다 미운영 = 공휴일이구나"
자연스럽게 판단 가능. 통합하면 정보 손실.

### 4) Service / Controller

```java
@Service
public class DormMealService {
    public WeeklyMealResponse getThisWeekMeal();
}

@RestController
@RequestMapping("/api/dorm/meals")
public class DormMealController {
    @GetMapping("/this-week")
    public ApiResponse<WeeklyMealResponse> getThisWeekMeal();
}
```

학식 `MealService` 와 분리 — 기숙사는 식당 1개라 fan-out 불필요, 책임
섞이면 학식 분해(P3) 의도 무너짐.

### 5) 설정

`application.yml`:
```yaml
ssuai:
  connector:
    meal: mock
    dorm-meal: mock # mock | real (real implemented in Task 04)
```

`application-prod.yml`:
```yaml
ssuai:
  connector:
    meal: real
    dorm-meal: real
```

### 6) `CampusFacilityService` 갱신

`residence-hall-cafeteria.notes` 의 첫 줄에 `"실시간 메뉴: GET
/api/dorm/meals/this-week"` 추가. 기존 식단 페이지 URL 은 보조 정보로
유지.

## Scope — out

- 학식 (`MealConnector` / `MealService` / `RealMealConnector`) — 손대지
  않음. 두 도메인 완전 분리.
- Redis 캐싱 — 별도 ticket. 게시글 한 개가 7일치이므로 매 호출마다
  외부 hit 는 비용이지만, MVP 는 그대로.
- 게시글 첨부 이미지 / 알러지 정보 / 영양 정보 — 메뉴 텍스트만.
- 임의 주차 조회 (`?date=YYYY-MM-DD`) — 게시판 prev/next 링크
  (`?gyear=YYYY&gmonth=MM&gday=DD`) 로 구현 가능하지만 v2.
- 중.석식 컬럼 처리 — 현 fixture 항상 빈 칸. 들어오기 시작하면 그때.
- `WeeklyMealExportRunner` 에 기숙사 추가 — 별도 ticket (export 모델
  자체가 P5 에서 재검토 대상).
- MCP tool wrapping (`get_dorm_weekly_meal`) — MCP 슬라이스 task.

## Files

```
backend/src/main/java/com/ssuai/
├── domain/dorm/                                  (NEW package)
│   ├── connector/
│   │   ├── DormMealConnector.java
│   │   ├── MockDormMealConnector.java
│   │   └── RealDormMealConnector.java
│   ├── service/
│   │   └── DormMealService.java
│   └── controller/
│       └── DormMealController.java
└── domain/campus/service/
    └── CampusFacilityService.java                (notes 갱신)

backend/src/main/resources/
├── application.yml                               (dorm-meal 토글)
└── application-prod.yml                          (dorm-meal: real)

backend/src/test/java/com/ssuai/domain/dorm/
├── connector/
│   ├── MockDormMealConnectorTests.java
│   ├── RealDormMealConnectorParseTests.java
│   └── RealDormMealConnectorHttpTests.java
├── service/
│   └── DormMealServiceTests.java
└── controller/
    └── DormMealControllerTests.java

backend/src/test/resources/fixtures/meal/
└── dorm-week-success.html                        (UTF-8, PII redacted)
```

## Contracts

### Endpoint

```
GET /api/dorm/meals/this-week
```

응답:
```json
{
  "data": {
    "startDate": "2026-05-04",
    "endDate":   "2026-05-10",
    "days": [
      {
        "date": "2026-05-04",
        "meals": [
          { "restaurant": "레지던스홀 기숙사 식당", "type": "LUNCH", "corner": "중식", "menu": ["순두부찌개", "쌀밥&흑미밥", ...] },
          { "restaurant": "레지던스홀 기숙사 식당", "type": "DINNER", "corner": "석식", "menu": [...] }
        ],
        "closures": [
          { "restaurant": "레지던스홀 기숙사 식당", "reason": "조식 미운영" }
        ]
      },
      ...
    ]
  },
  "error": null,
  "traceId": "..."
}
```

### Exception mapping

학식 connector 와 동일 — `ConnectorTimeoutException` (504),
`ConnectorUnavailableException` (503), `ConnectorParseException` (502).
부분 일자 실패는 흡수하지 않음 (전체 응답이 한 호출에 묶여 있어 분리
불가) — 호출 자체가 실패하면 5xx.

## Security

- [x] User-Agent: `ssuAI/0.1 (+akftjdwn@gmail.com)`
- [x] Outbound interval ≥ 1초 (`waitForRateLimit`, 학식과 별도 인스턴스
      가 가짐)
- [x] read/connect timeout 10초
- [x] HTML 응답 본문을 로그에 남기지 않음 (요약 메트릭만)
- [x] Fixture PII 검수 통과 (작성자/댓글/학번/이메일 없음)
- [x] `error.message` 에 내부 예외 메시지 노출 금지
- [x] 새 production dependency 없음 (Jsoup 이미 있음)
- [ ] `robots.txt` 사람이 한 번 더 확인

## Test plan (구현됨)

- `MockDormMealConnectorTests` (1) — 7일·월~일·식당명·조식 미운영
  closure 검증.
- `RealDormMealConnectorParseTests` (5) — fixture 직접 파싱 / 7일 추출
  / `<br>` split / 공휴일 키워드 셀 휴무 분기 / 빈 HTML 시 parse 예외.
- `RealDormMealConnectorHttpTests` (4) — MockWebServer 로 EUC-KR
  byte 응답 / 503 / timeout / 빈 HTML.
- `DormMealServiceTests` (2) — connector 위임 / 예외 전파.
- `DormMealControllerTests` (1) — `@WebMvcTest`, envelope/traceId 검증.
- `CampusFacilityServiceTests` 갱신 — residence-hall-cafeteria notes 의
  새 endpoint 안내 추가 검증.

## Acceptance Criteria

- [x] `gradlew.bat test` 통과
- [ ] `gradlew.bat bootRun` (dev/mock) → `curl /api/dorm/meals/this-week`
      이 mock 7일 응답 (사용자 검증 필요)
- [ ] prod profile 실행 시 실제 게시판 메뉴 (네트워크 / 사용자 검증)
- [x] 새 production dependency 없음
- [x] `CampusFacilityService` notes 갱신 + 테스트 동기화

## Out-of-band notes for the reviewer

리뷰 시 특히 확인:

1. **도메인 분리** — `domain/dorm/` 가 `domain/meal/` 의 dto 만 import
   하는지 (`MealResponse`/`MealItem`/`MealClosure`/`MealType`/
   `WeeklyMealResponse`). connector·service·controller 클래스는
   import 하면 안 됨.
2. **EUC-KR 강제 파싱** — `RealDormMealConnector` 가 Jsoup 의 charset
   자동 감지에 의존하지 않고 `Jsoup.parse(InputStream, "EUC-KR", url)`
   로 명시 호출하는지. (HttpTests 에서 EUC-KR bytes 로 응답 검증.)
3. **휴무 키워드 셋** — 학식 `RealMealConnector.isClosureReason` 와
   기숙사 `RealDormMealConnector.CLOSURE_KEYWORDS` 가 lockstep 인지.
   하나만 추가되면 일관성 깨짐.
4. **`MealClosure.reason` prefix 규칙** — 항상 `"<끼니명> <원본 사유>"`
   형식. 클라이언트가 이걸 키로 분기 가능한지 (예: `startsWith("조식")`).
