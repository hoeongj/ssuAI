# Task 15 — Library book search (read-only MCP tool)

> Phase 2 두 번째 슬라이스. Task 12 (`get_library_seat_status`) 가
> 좌석 read-only 였다면 이건 도서 read-only. 책 *예약* / 대출 *액션* 은
> Phase 4 별도 task. 본 task 는 인증 없는 공개 검색만 다룬다.

## 1. Goal / Scope / Non-goals

### Goal

`search_library_book` MCP tool 을 노출해서 챗봇 사용자가
*"숭실대 도서관에 파이썬 책 있어?"* 같은 자연어 질문에 답할 수 있게
한다. 결과는 책 제목 · 저자 · 청구기호 (call number) · 소장 위치 ·
대출 상태 (가능/대출중) 까지 한 페이지 안에서 보여준다.

이게 풀리면 vision §3 Layer 1 의 공개 도구 항목 중
`search_library_book` / `get_library_book_status` 가 완성된다.

### In scope

- `LibraryBookConnector` 인터페이스 + 결정적 `MockLibraryBookConnector`
  (8~12권의 고정 더미 검색 결과)
- `LibraryBookConnector` 의 real 구현 (`RealLibraryBookConnector`) —
  spike 결과 익명 접근 가능하면 즉시, 아니면 §8 stop-and-flag
- `BookSearchQuery` + `LibraryBook` + `LibraryBookSearchResponse` DTO
- 짧은 TTL 캐시 (60s) + single-flight (Task 12 의 `LibrarySeatCache`
  패턴 재사용 가능하면 재사용, 아니면 generic 한 `BoundedTtlCache` 추출)
- REST 엔드포인트 `GET /api/library/books?query=...&page=...&size=...`
- MCP tool `search_library_book(query, page?, size?)`
- 연결기 + 서비스 + 컨트롤러 + MCP tool + 캐시 테스트
- 챗봇 sample prompts 에 *"도서관에 [책] 있어?"* 추가 (Task 12 의
  좌석 sample prompt 추가 패턴)

### Non-goals

- ❌ 책 예약 / 대기열 등록 / 대출 — Phase 4 별도 액션 도구
  (`borrow_library_book_hold` 등) 로 빠짐
- ❌ 사용자 인증. 도서 *검색* 은 익명이어야 한다 (spike 로 검증). 만약
  spike 에서 인증 게이트 발견되면 §8 stop-and-flag
- ❌ 책 상세 페이지 (개별 도서 메타데이터) — 검색 결과의 핵심 필드만
- ❌ 본인 대출 현황 (`get_my_library_loans`) — 그건 Phase 3 개인 도구
- ❌ 좌석 / 시설 / 학식 데이터와 통합 검색

## 2. Why now

vision.md §4 Phase 2 의 명시 항목 중 좌석(Task 12)에 이어 두 번째.
공개 도구이고 인증 spike 가 가벼우면 Task 12 보다 짧은 1~2 PR
스코프. Task 13/14 가 인증 미해결로 정체된 동안 데모 임팩트를
늘릴 수 있는 가장 빠른 슬라이스.

챗봇 데모 시나리오상 *"오늘 학식 뭐야"* 다음으로 자연스러운 질문이
*"~~책~~ 있어?"* 이고, 현재는 챗봇이 이 질문에 "데이터가 없습니다"
로만 답함.

## 3. Upstream investigation (spike 완료 — 2026-05-15)

### 3.1 확정 endpoint

```
GET https://oasis.ssu.ac.kr/pyxis-api/1/collections/2/search
    ?all=k|a|<검색어, percent-encoded>
    &facet=false
    &fuzzy=false
    &max=<페이지당, 사용자 입력 cap 20>
    &offset=<페이지 시작 index, 0-based>
    &isForPyxis3=true
```

- **인증 불필요**. `Pyxis-Auth-Token` header / `ssotoken` cookie 모두
  없이도 200 + `{"success":true,...}` 응답. Task 13 의 좌석 API
  ([`Library auth research`](../../C:\Users\akftj\.claude-work\projects\C--Users-akftj-ssuAI\memory\library_auth_research_2026_05_15.md))
  와 정반대 — 좌석은 전면 인증, 도서 검색은 익명 가능.
- `collections/2` 의 `2` 는 컬렉션 id (중앙도서관 통합 컬렉션 추정).
  다른 컬렉션 id 는 본 task 범위 밖, 일단 `2` hardcode.
- `all=k|a|<검색어>` 의 첫 두 토큰: `k` = keyword search 모드,
  `a` = all fields. 자연어 챗봇 시나리오엔 이 조합으로 충분. title-only
  / author-only 검색은 본 task 범위 밖.
- `isForPyxis3=true` 는 그대로 박는다 (Pyxis 3 호환 플래그).

### 3.2 응답 구조 (실제 spike 응답 발췌)

```json
{
  "success": true,
  "code": "success.retrieved",
  "message": "조회되었습니다.",
  "data": {
    "totalCount": 755,
    "offset": 0,
    "max": 4,
    "list": [
      {
        "id": 5006619,
        "titleStatement": "파이썬 : 기초와 활용",
        "author": "한정란",
        "publication": "파주 : 21세기사, 2023",
        "isbn": "9791168330702",
        "thumbnailUrl": "https://image.aladin.co.kr/.../cover/...jpg",
        "branchVolumes": [
          {
            "id": 1,
            "name": "중앙도서관",
            "volume": "005.133P9 한7362파",
            "cState": "대출가능",
            "cStateCode": "READY",
            "hasItem": true
          }
        ],
        "similars": [ ... ],
        "dateReceived": "2023-06-08"
      }
    ]
  }
}
```

### 3.3 필드 매핑 (Pyxis → 우리 DTO)

| 우리 DTO 필드 | Pyxis 응답 경로 | 비고 |
|---|---|---|
| `id` | `list[].id` | Phase 4 액션 도구 입력 |
| `title` | `list[].titleStatement` | 한국어 책 제목 |
| `author` | `list[].author` | nullable 가능 |
| `publication` | `list[].publication` | "출판지 : 출판사, 연도" |
| `isbn` | `list[].isbn` | nullable, 일부 책은 null |
| `thumbnailUrl` | `list[].thumbnailUrl` | 알라딘 등 외부 호스팅, UI 데모에 유리 |
| `location` | `list[].branchVolumes[0].name` | "중앙도서관" 등 |
| `callNumber` | `list[].branchVolumes[0].volume` | 청구기호 |
| `status` | `list[].branchVolumes[0].cStateCode` → enum | 매핑 §3.4 |
| `total` | `data.totalCount` | 페이지네이션 |
| `offset` | `data.offset` | |
| `max` | `data.max` | |

복본이 여러 권이면 `branchVolumes[]` 가 여러 개. MVP 는 첫 번째 권만
표시. 추후 확장 시 별도 DTO (`LibraryBookCopy[]`) 필요.

### 3.4 cStateCode → BookStatus 매핑

확인된 코드 (spike 응답 + Pyxis 일반 패턴):

| cStateCode | BookStatus | 한국어 cState 예 |
|---|---|---|
| `READY` | `AVAILABLE` | "대출가능" |
| `LOAN` / `CHECKED_OUT` (추정) | `CHECKED_OUT` | "대출중" (추정) |
| 그 외 / null | `UNKNOWN` | warn 로그 |

구현 PR 에서 *대출중* 책 한 권 추가 spike 해서 코드 확정. 매핑 못 한
값은 `UNKNOWN` + DEBUG 로그.

### 3.5 PII / 보안 점검

응답 sample 에 학번 · 대출자 이름 · 연락처 등 PII **없음**. 책 메타데이터
+ 청구기호 + 소장 위치만. fixture commit 안전. 단 *대출중* 책 응답에
대출자 식별 정보 있는지 spike 시 별도 확인 필요 (§7 보안 항목).

## 4. API design

### MCP tool

```text
search_library_book(query: string, page?: int = 0, size?: int = 10)
  → {
      "total": <int — Pyxis totalCount>,
      "page": <int>,
      "size": <int>,
      "items": [
        {
          "id": 5006619,
          "title": "파이썬 : 기초와 활용",
          "author": "한정란",
          "publication": "파주 : 21세기사, 2023",
          "isbn": "9791168330702",
          "thumbnailUrl": "https://image.aladin.co.kr/.../cover/...jpg",
          "callNumber": "005.133P9 한7362파",
          "location": "중앙도서관",
          "status": "AVAILABLE"
        }
      ]
    }
```

- query 길이 cap: 64자 (Task 12 의 입력 검증 정책 재사용)
- size cap: 20 (chat context 보호; LLM 에 50권 던지면 토큰 폭발)
- empty / whitespace-only query → 한국어 안내 메시지
- page < 0, size < 1 → 400
- 페이지네이션: `offset = page * size`, `max = size` 로 upstream 변환

### REST

```http
GET /api/library/books?query=파이썬&page=0&size=10
→ ApiResponse<LibraryBookSearchResponse>
```

### DTO

```java
// 도메인 도서 1건
public record LibraryBook(
    long id,              // Pyxis book id, Phase 4 액션 입력
    String title,         // titleStatement
    String author,        // nullable
    String publication,   // 출판 정보 원문
    String isbn,          // nullable
    String thumbnailUrl,  // nullable, 외부 호스팅
    String callNumber,    // 청구기호 = branchVolumes[0].volume
    String location,      // = branchVolumes[0].name (예: "중앙도서관")
    BookStatus status     // cStateCode 매핑
) {}

// 검색 응답
public record LibraryBookSearchResponse(
    int total,
    int page,
    int size,
    List<LibraryBook> items
) {}

// 상태
public enum BookStatus {
    AVAILABLE,    // cStateCode = READY
    CHECKED_OUT,  // 대출중 (cStateCode 확정은 구현 PR spike)
    UNKNOWN       // 매핑 안 된 코드, branchVolumes 비어있음 등
}
```

### 에러 매핑

- query 누락/빈값 → 400 + `VALIDATION_FAILED` + 한국어
- upstream timeout → 502 + `UPSTREAM_TIMEOUT`
- (spike 결과 인증 필요로 판명될 경우) → §8 stop-and-flag 트리거.
  `LIBRARY_SESSION_REQUIRED` 401 매핑은 Task 13 이미 만들어 둠 →
  재사용 가능

## 5. Package + class responsibilities

```
backend/src/main/java/com/ssuai/domain/library/
├── connector/
│   ├── LibraryBookConnector.java          # NEW 인터페이스
│   ├── MockLibraryBookConnector.java      # NEW 결정적 더미
│   └── RealLibraryBookConnector.java      # NEW (spike pending — Jsoup or RestClient)
├── controller/
│   └── LibraryBookController.java         # NEW — GET /api/library/books
├── dto/
│   ├── LibraryBook.java                   # NEW
│   ├── LibraryBookSearchResponse.java     # NEW
│   └── BookStatus.java                    # NEW enum
├── mcp/
│   └── LibraryBookMcpTool.java            # NEW — @Tool search_library_book
├── service/
│   ├── LibraryBookService.java            # NEW
│   └── LibraryBookCache.java              # NEW — 60s TTL + single-flight
```

`domain/library/connector/` 와 `domain/library/service/` 는 Task 12
가 이미 만들어둔 패키지. 그 안에 형제 클래스로 추가.

## 6. Caching strategy

| 항목 | 값 | 이유 |
|------|-----|------|
| TTL | 60s | 책 대출 상태 변하지만 좌석만큼 volatile 하진 않음. 좌석 30s 보다 길게. |
| Key | `(query, page, size)` normalized (lowercase + trim) | 같은 검색어 반복 호출 → 동일 캐시 hit |
| Capacity | 200 entries LRU | 챗봇이 같은 책 반복 요청 시 hit, 메모리 cap |
| Single-flight | 예 (`Task 12 LibrarySeatCache` 패턴) | 동시 5명이 같은 검색어 → upstream 1번만 호출 |

`LibrarySeatCache` 를 `BoundedTtlCache<K,V>` generic 으로 추출 후
seat / book 두 서비스가 공용. 단 generic 추출은 별도 PR 로 분리해도 됨.

## 7. Security

- 사용자 입력 query 는 **반드시** percent-encode 후 upstream 호출
  (Pyxis 가 SQL/XSS 에 취약할 가능성은 낮지만 URL injection 방어)
- 응답에 대출자 학번/이름 등 PII 가 섞여 있으면 우리 DTO 에 매핑하지
  *않음* (필드 화이트리스트 매핑)
- query / 응답 raw 를 INFO 레벨로 로깅 금지. DEBUG 레벨 + 운영에서는
  DEBUG OFF
- gitleaks rules: 도서관 응답 fixture 에 학번/대출자 이름 들어가지
  않게 스크럽. fixture commit 전 grep 으로 학번 패턴 (`\d{8}`) 검사

## 8. Stop and flag

Spike (§3) 로 첫 케이스는 이미 negative — 인증 불필요 + PII 없음 +
JSON API. 다음만 stop-and-flag 대상으로 남는다.

- *대출중* 책 응답에 대출자 PII 포함이 spike 시 확인되면 → 화이트
  리스트 매핑은 그대로지만 fixture commit 정책 ADR 필요 → 사용자 결정
- Pyxis 가 같은 IP 에서 빠른 반복 요청에 rate-limit (429 / IP 차단) →
  cache TTL 늘리거나 backoff 로직 추가 → 운영 결정
- `collections/2` 외 다른 컬렉션 (예: 학위논문) 도 챗봇 시나리오에
  필요해지면 → 별도 task 또는 본 task scope 확장

## 9. Test plan

### Unit

- `MockLibraryBookConnector` — 정해진 시드로 같은 query 에 항상 같은
  결과 (결정론) 검증
- `BoundedTtlCache` (또는 `LibraryBookCache`) — TTL 만료, capacity
  evict, single-flight 동시 호출 1번 upstream 만
- `LibraryBookService` — query 정규화 (trim, lowercase), size cap,
  page < 0 거부

### Integration

- `LibraryBookController` 슬라이스 테스트 — 정상 / 빈 query 400 /
  size > 20 → 20 cap / upstream 예외 → 502
- (real connector 가 본 task 에 들어오는 경우) WireMock 으로 upstream
  응답 fixture 박고 `RealLibraryBookConnector` parse 검증

### MCP

- `LibraryBookMcpTool` 단위 — input validation, output JSON shape
- `McpSelfDogfoodTests` 에 `search_library_book` 라운드트립 한 줄 추가

## 10. PR breakdown

Spike 결과 익명 GET 가능 + JSON API + PII 없음으로 확정. real
connector 본 task 안에서 진행한다. PR 두 개로 분리 (리뷰 가능 단위).

### PR 15a — Mock 슬라이스 end-to-end + 인터페이스

(Task 12 PR 1a 와 동일 패턴)

- DTO (`LibraryBook`, `LibraryBookSearchResponse`, `BookStatus`)
- `LibraryBookConnector` 인터페이스 + `MockLibraryBookConnector`
  (결정적 8~12권 더미)
- `LibraryBookCache` (또는 `BoundedTtlCache<K,V>` 추출 — 별도 PR 로
  뺄지 본 PR 에 포함할지는 구현 시 PR 크기 보고 결정)
- `LibraryBookService` + `LibraryBookController`
- `LibraryBookMcpTool`
- 챗봇 sample prompts 1개 추가 — *"도서관에 [책] 있어?"*
- 단위 + 컨트롤러 + MCP 라운드트립 테스트
- `vision.md` 의 `search_library_book` 표기를 *(Phase 2, mock 가동)*
  로 갱신
- 기본값은 `ssuai.connector.library-book=mock`

### PR 15b — Real connector (Pyxis JSON API)

- `RealLibraryBookConnector` — `RestClient` 로 §3.1 endpoint 호출
  - User-Agent / Accept header 자연스럽게 (Task 13 의 connector 와 동일
    톤). Pyxis-Auth-Token 안 보냄.
  - query percent-encoding + `k|a|` prefix 자동 부착
  - timeout 5s (Task 12 좌석 connector 동일)
- 응답 파싱 — `data.list[]` 매핑 (§3.3 표)
- *대출중* 책 한 권 추가 spike 해서 `cStateCode` 매핑 확정 (없으면
  코드 그대로 일단 `UNKNOWN`)
- JSON fixture `backend/src/test/resources/library/book-search-*.json`
  세 케이스: (1) 일반 검색 (2) totalCount=0 (3) branchVolumes 비어
  있는 책
- WireMock parse 테스트 + connector unit
- `ssuai.connector.library-book=real` 로 스위치
- 운영 환경 변수 (Helm values) 에 flag 추가

### PR 15c (선택) — `BoundedTtlCache<K,V>` 추출

PR 15a 에서 `LibrarySeatCache` 와 거의 동일한 캐시가 두 번째 생기면
이걸 generic 으로 빼는 PR. PR 15a/15b 와 독립이라 언제 해도 됨.

## 11. Out-of-scope work this enables

- **Phase 4 `borrow_library_book_hold`** — 책 예약 액션 도구. 본 task
  의 `LibraryBook.callNumber` / 식별자가 그 액션의 입력이 됨.
- **개인 도구 `get_my_library_loans`** — Phase 3 인증 결합. 본 task 의
  `BookStatus` enum 과 `LibraryBook` DTO 를 *내 대출* 표현에 재사용
  가능 (책 id + due date 필드 추가).
