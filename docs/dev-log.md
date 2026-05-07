# Dev Log

ssuAI 작업 진행 회고. 매 task 끝마다 한 줄씩 누적.
큰 결정은 별도로 `docs/adr/` 에 ADR 로 적는다.

## 2026-05-07

- 2026-05-07: Task 05 frontend MVP - Next.js dashboard + 4 cards, local
  integration. API envelope unwrap + per-card loading/error/empty states and
  dev-only CORS completed.

- meal fan-out 성능 개선: weekly export 1분 22초 → 26초.
  원인은 `RealMealConnector` 의 `synchronized` + 단일 `lastCallAtMs` 가 같은
  connector 인스턴스의 모든 식당 호출을 1초 간격으로 전역 직렬화한 것. rate-limit
  을 식당(rcd) 단위 `ConcurrentMap` 으로 분리하고 `MealService.getMeal` fan-out
  을 `parallelStream` 으로 바꿔서 해결.
- 같은 export runner 한 번 실행에 dorm weekly JSON 도 함께 산출. dorm connector
  의 실제 사이트 검증 누락도 같이 해결.
- `WeeklyMealExportService.exportWeeklyMeals` (fetch+write 합본) 가 production
  에서 호출자가 사라지고 테스트만 호출하는 안티패턴 정리. `fetchWeeklyMeals`
  public 격상 + `ObjectMapper` 의존성 제거.
- Spring AI MCP server starter (SSE / Streamable HTTP) 추가하고 4 tool
  (`get_today_meal`, `get_meal_by_date`, `get_dorm_weekly_meal`,
  `search_campus_facilities`) 노출. REST 와 같은 Spring Boot 프로세스에 공존.
  현업 self-hosted MCP 가 SSE 가 default 인 흐름 따름.
- MCP tool 입력 검증을 tool layer 에서 명시적으로 수행. `get_meal_by_date`
  의 빈/잘못된 date 가 raw `Text '' could not be parsed at index 0` 으로
  새던 것을 한국어 친절 메시지로 변환. 64자 query 길이 제한도 추가.
- `ConnectorException` 의 raw 메시지가 MCP client 에 그대로 새던 것을
  `ConnectorErrorMessages` 헬퍼로 ErrorCode 별 한국어 안내로 변환. 적용
  대상은 connector 호출하는 3 tool (`get_today_meal`, `get_meal_by_date`,
  `get_dorm_weekly_meal`) 만. `search_campus_facilities` 는 정적 데이터라 제외.
- REST 에 `GET /api/meals/weekly?startDate=YYYY-MM-DD` 추가. startDate
  생략 시 Asia/Seoul 이번 주 월요일 default. `IllegalArgumentException` 을
  `GlobalExceptionHandler` 에서 400 / `VALIDATION_FAILED` 로 매핑.
- `docs/mcp-tools.md` (0 bytes 였음) 첫 작성. 사용법 + Claude Desktop SSE
  직접 / `mcp-proxy` 어댑터 / Cursor 등록 + 트러블슈팅 + write tool 정책
  placeholder.
- ADR 0002~0005 retroactive 작성: meal fan-out 성능, MCP transport SSE 선택,
  MCP tool 입력 검증 정책, REST/MCP error UX 분리. 향후 큰 결정마다
  `docs/adr/` 에 archive 시작.
- 학식 + 기숙사 connector 트러블슈팅 7 사건 retroactive archive
  (`docs/troubleshooting/cafeteria-connector.md`). 사용자 기억이 흐려진
  상태라 git history 기반 narrative + 추정 부분 명시.
