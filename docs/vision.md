# ssuAI 비전 — 최종 목표

이 문서는 ssuAI 가 궁극적으로 무엇이 되려고 하는지에 대한 source of truth
입니다. 단기 task spec 은 [`docs/tasks/`](tasks/) 에, 운영 중인 MVP 의
현재 상태는 [`README.md`](../README.md) 와 [`docs/product.md`](product.md)
에 있습니다.

---

## 1. 한 문장 요약

ssuAI 프로젝트는 **두 개의 분리된 제품**을 함께 만든다.

1. **숭실대 MCP 서버** — 학교의 모든 캠퍼스 정보 (공개: 학식·기숙사·시설·
   도서관, 개인: 시간표·성적·과제·도서관 대출 현황) 를 **MCP 표준 도구**
   로 노출하는 공개 서버. Claude Desktop · Cursor 등 어떤 MCP 클라이언트
   도 동일하게 사용 가능. → §3 Layer 1.
2. **ssuAI 웹/앱** — 위 MCP 서버를 소비하는 자체 클라이언트. 학생이 보기
   편한 카드형 대시보드 위에 **자연어 챗봇**과 **AI 에이전트** 기능을
   얹어, 자연어 한 마디로 학교 생활을 처리하게 한다. → §3 Layer 2~4.

두 제품의 정체성을 가장 잘 보여주는 사례는 ssuAI 의 **도서관 좌석 자동
예약 에이전트** — 챗봇이 실시간 도서관 좌석 데이터를 가지고 사용자와
대화하다가, "이 자리 예약해줘" 한 마디에 에이전트가 실제 도서관 사이트
에서 예약을 수행한다. ssuAI 웹/앱은 단순 "정보 조회 챗봇" 이 아니라
**실제 학교 시스템의 상태를 바꾸는 end-to-end AI 에이전트** 까지 가는
프로젝트다.

---

## 2. 왜 이걸 만드는가

현재 숭실대 학생이 하루를 보내려면 다음 사이트를 모두 들락거려야 합니다.

- 학식 페이지 (`soongguri.com`) — 메뉴 확인
- 레지던스홀 홈페이지 — 기숙사 식단
- u-SAINT — 시간표, 성적, 출결
- LMS — 과제, 강의자료, 공지
- 도서관 시스템 — 책 검색, 좌석 예약, 대출 현황
- 학교 공식 페이지 — 시설 위치, 시간

각 사이트는:

- 모바일 UI 가 불편하다
- 자연어 검색이 없다
- 매번 따로 로그인해야 한다
- 한 화면에서 종합 정보를 볼 수 없다

ssuAI 는 이 모든 데이터를 **MCP 표준 도구**로 묶고, 그 위에 자체 클라이언트
(웹·앱·챗봇·에이전트) 를 올려서 학생이 "오늘 학식 뭐고 내 다음 수업 시간은?"
같은 질문을 한 번에 처리할 수 있게 합니다.

---

## 3. 4-layer 시스템

Layer 1 (**숭실대 MCP 서버**) 과 Layer 2~4 (**ssuAI 웹/앱 + 챗봇 +
에이전트**) 는 §1 에서 정의한 두 제품을 그대로 매핑한 것이다. Layer 1
은 외부 MCP 클라이언트도 동일하게 소비할 수 있는 공개 서버, Layer 2~4
는 그 위에 ssuAI 자체 UX 를 얹은 클라이언트.

```text
┌───────────────────────────────────────────────────────────────┐
│ Layer 4: AI 에이전트                                          │
│   사용자 의도 → 실시간 데이터 조회 → 액션 실행                │
│   예: 도서관 자리 자동 예약, 과제 마감 알림                   │
└────────────────────────┬──────────────────────────────────────┘
                         │ 동일 MCP 서버의 액션 도구 호출
┌────────────────────────┴──────────────────────────────────────┐
│ Layer 3: 챗봇                                                 │
│   자연어로 모든 도구 호출. 공개+개인 정보 통합 대화.          │
│   예: "내일 1교시 뭐고 학식 메뉴는?"                          │
└────────────────────────┬──────────────────────────────────────┘
                         │ 챗봇은 web/app 안에 embedded
┌────────────────────────┴──────────────────────────────────────┐
│ Layer 2: 자체 클라이언트 (web · mobile app)                   │
│   ssuAI 가 만든 우리 자체의 web/app. 같은 MCP 를 소비.        │
│   학생이 MCP 클라이언트 설치 없이도 쓸 수 있게 함.            │
└────────────────────────┬──────────────────────────────────────┘
                         │ MCP 프로토콜 (SSE / Streamable HTTP)
┌───────────────────────────────────────────────────────────────┐
│ Layer 1: 숭실대 MCP 서버 (별도 제품)                          │
│   공개 도구 (인증 무) + 개인 도구 (인증 유) 를 표준 MCP 로 노출. │
│   Claude Desktop, Cursor, 그 외 MCP 클라이언트도 동일하게 사용. │
└───────────────────────────────────────────────────────────────┘
```

### Layer 1 — 숭실대 MCP 서버 (제품 1)

ssuAI 프로젝트의 두 deliverable 중 첫 번째. **ssuAI 웹/앱과 분리된 별도
제품**으로, 어떤 MCP 클라이언트든 이 서버에 붙으면 숭실대 정보를
자연어로 다룰 수 있게 됩니다.

#### 공개 도구 (인증 없음, 모두 사용 가능)

- `get_today_meal`, `get_meal_by_date` — 학식 메뉴 (특정 식당 필터 가능)
- `get_dorm_weekly_meal` — 기숙사 주간 식단
- `search_campus_facilities` — 카페·편의점·복사실 등 시설 검색
- `search_library_book` — 도서관 도서 검색 (Phase 2, mock 가동, real Pyxis JSON API 연결은 PR 15b)
- `get_library_book_status` — 책 보유/대출 상태 *(예정 — `search_library_book` 응답에 상태 포함, 단일 책 상세 조회는 별도)*
- `get_library_seat_status` — 도서관 좌석 실시간 잔여 (Phase 2 첫 슬라이스, mock 으로 가동)

#### 개인 도구 (사용자 인증 필요) *(예정)*

- `get_my_schedule` — 내 시간표
- `get_my_grades` — 내 성적
- `get_my_assignments` — 내 LMS 과제
- `get_my_attendance` — 내 출결
- `get_my_library_loans` — 내 도서관 대출 현황

### Layer 2~4 — ssuAI 웹/앱 + 챗봇 + 에이전트 (제품 2)

ssuAI 프로젝트의 두 번째 deliverable. **Layer 1 의 MCP 서버를 소비하는
자체 클라이언트** 로, MCP 클라이언트를 따로 설치 못 하는 일반 학생도
학교 정보에 접근할 수 있게 합니다. 웹/앱은 그릇이고 그 안에 챗봇과
에이전트가 들어갑니다.

#### Layer 2 — 자체 웹/앱 (그릇)

- **웹 대시보드 (Next.js, 라이브 운영 중)** — 데스크탑에서 카드 형식으로
  핵심 정보 빠르게 조회. 4개 카드 (오늘 학식 / 주간 학식 / 기숙사 / 시설
  검색) 가 이미 라이브.
- **모바일 앱 (Expo React Native, 예정)** — 등하굣길용. 푸시 알림으로
  과제·공지 마감 알림.

### Layer 3 — 챗봇

웹/앱 안의 `/chat` 페이지. LLM 이 MCP 서버의 모든 도구를 호출해서
사용자의 자연어 질문에 답합니다.

- **현재**: 공개 도구 (학식·기숙사·시설) 만 사용. Gemini → Groq → ...
  9-provider fallback. 라이브 운영 중.
- **앞으로**: 개인 도구가 layer 1 에 추가되면 챗봇이 자동으로
  "내 다음 수업"·"이번 주 과제" 같은 질문도 답할 수 있게 됨.

챗봇 자체가 MCP 클라이언트 — `LlmChatService` 가 같은 JVM 안의 MCP
서버를 SSE 로 호출하는 **self-dogfood** 구조. ADR
[0010](adr/0010-chatbot-mcp-self-dogfood.md),
[0011](adr/0011-mcp-tool-dynamic-discovery.md) 참고.

### Layer 4 — AI 에이전트 (**프로젝트의 flagship**)

챗봇이 "정보 조회 + 답변 생성" 까지라면, 에이전트는 "정보 조회 +
의사결정 + **실제 액션**" 까지 갑니다. ssuAI 의 가장 큰 차별점은 이
layer 가 존재한다는 것 — 단순 정보 챗봇이 아니라 **학교 시스템의 상태를
실제로 바꿀 수 있는** AI 에이전트입니다.

#### 도서관 좌석 자동 예약 — flagship 사용 사례

숭실대 도서관 사이트는 층별로 예약 가능한 좌석 정보를 제공하고, 좌석을
클릭하면 예약이 수행되는 구조입니다. ssuAI 의 도서관 에이전트는 이
인터랙션을 자동화합니다.

```text
[사용자] "지금 도서관 4층에 자리 있어?"

[Layer 3 챗봇] get_library_seat_status(floor=4) 도구 호출
  → MCP 서버가 도서관 사이트에서 실시간 좌석 상태 스크랩
  → 4층 좌석 36석 중 12석 잔여, 창가 자리 (412/415/418) 비어 있음
  → LLM: "4층에 12자리 비어 있어. 창가는 412, 415, 418번이야.
          어디로 예약할까?"

[사용자] "412번으로 4시간만 잡아줘"

[Layer 4 에이전트] 의사결정 + 액션
  1. confirmation: "412번 좌석을 지금부터 4시간 (~20:00) 예약할게요. 진행?"
  2. 사용자 yes 응답 감지
  3. reserve_library_seat(seat_id=412, duration=4h) 액션 도구 호출
     → MCP 서버가 사용자 도서관 credential 로 인증
     → 도서관 사이트의 좌석 클릭 액션을 자동 수행 (POST)
     → 예약 응답 파싱
  4. 결과 보고:
     - 성공: "412번 예약 완료. 20:00 까지 이용 가능합니다."
     - 좌석이 이미 다른 사람에게 예약된 race: "직전에 다른 학생이
       선점했어요. 415번으로 다시 진행할까요?"
     - 인증 실패: "도서관 계정 정보를 다시 확인해주세요."
```

핵심은 **사용자가 도서관 사이트에 로그인하거나 클릭할 필요가 전혀 없다는
점**. 챗봇에서 "예약해줘" 한 마디면 끝납니다. 동시에 모든 액션은
사용자 명시 confirmation 을 거치므로 의도하지 않은 예약·취소는 발생하지
않습니다.

#### 다른 후보 액션 도구 (Phase 4 후속)

- `cancel_library_seat_reservation` — 본인이 잡은 좌석 취소
- `extend_library_seat` — 사용 시간 연장
- `set_assignment_reminder` — LMS 과제 마감 알림 등록 (ssuAI 자체
  알림으로, LMS 시스템 상태는 안 바꿈)
- `borrow_library_book_hold` — 책 예약 (대출 대기열 등록)

write 성격의 액션 도구는 **모두** 다음 규약을 따릅니다.

- 사용자 명시 confirmation 필수 (한 번 받은 yes 를 다른 액션에
  재사용하지 않음)
- 실행 전 dry-run 결과 표시 (어떤 좌석, 어떤 시간대 등 영향 범위)
- 모든 요청·결과 audit log 에 기록 (action_audit 테이블)
- 실패·부분 성공·취소를 사용자에게 명확히 반환
- 비밀번호·세션·토큰 등 비밀 정보 log 에 절대 안 남김
- race condition 방지 — 같은 액션을 같은 사용자가 동시에 두 번 실행
  못 하도록 분산 lock (Redis SETNX 등)

자세한 액션 도구 정책은 [`docs/security.md`](security.md) 와
[`docs/mcp-tools.md`](mcp-tools.md) §8 참고. 도서관 예약 에이전트의
상세 설계 (좌석 id 추출, credential 위탁, race handling) 는 Phase 4
시작 시점에 별도 ADR 로 만듭니다.

---

## 4. 단계별 로드맵

<!-- markdownlint-disable MD013 MD060 -->

| Phase | 내용 | 상태 |
|---|---|---|
| **Phase 1** | 공개 데이터 MVP — 학식·기숙사·시설 MCP 도구 + 웹 대시보드 + 챗봇 | ✅ 완료 (라이브 운영 중) |
| **Phase 2** | 공개 도구 확장 — 도서관 도서/좌석 도구, 모바일 친화 UI | ✅ 도서관 도구 라이브 (Task 12 좌석 + Task 15 도서) / 모바일 CSS 잔여 |
| **Phase 3** | 개인 데이터 통합 — u-SAINT/LMS/도서관 인증, 개인 MCP 도구, 학생 credential 암호화 보관 | 🔜 진행 중 (Task 13 라이브러리 세션 · Task 14 u-SAINT SSO) |
| **Phase 4** | **도서관 좌석 자동 예약 AI 에이전트** (flagship) + 액션 도구 공용 인프라 | 📋 계획 중 |

<!-- markdownlint-enable MD013 MD060 -->

### Phase 1 (완료된 것)

- ✅ Spring Boot 단일 프로세스에서 REST + MCP 동시 운영
- ✅ MCP 도구 4개 (학식, 기숙사, 시설)
- ✅ `WeeklyMealCache` — 주 1회 batch + in-memory cache-aside
- ✅ Multi-provider LLM fallback 챗봇 (Gemini 외 9개)
- ✅ MCP self-dogfood (챗봇이 자기 MCP 서버를 SSE 로 호출)
- ✅ Next.js 웹 대시보드 + `/chat` 페이지
- ✅ Oracle Cloud Free Tier ARM64 k3s 운영 배포
  (Traefik + cert-manager + GHCR + Vercel)
- ✅ GitHub Actions CI/CD + Dependabot + gitleaks

### Phase 2 (도서관 도구 — 라이브)

- ✅ `LibraryBookConnector` (Pyxis JSON, 익명 GET) + `search_library_book`
  MCP 도구 — Task 15 (PR 15a/15b) 머지, prod `library-book: real` 적용
- ✅ `LibrarySeatConnector` + `get_library_seat_status` MCP 도구 — Task 12
  mock 슬라이스로 가동 (real connector 는 Task 13 라이브러리 세션 가시화
  후 합류)
- 🔜 모바일 친화 CSS 다듬기 (대시보드 + 챗봇)

### Phase 3 (개인 데이터 통합) — 진행 중

이 phase 의 가장 큰 도전은 **credential 보안**:

- ✅ ssuAI 자체 사용자 시스템 — Task 14 PR 14b-1/2 에서 `Student` JPA
  엔티티 + JWT infra (15m access / 14d refresh, `SSUAI_JWT_SECRET` env)
  머지 완료. 인증 방식은 JWT 채택.
- 🔜 u-SAINT 세션 인증 — Task 14 PR 14b-3 (`SaintSsoService`, saint.ssu.ac.kr
  2-phase) 머지 완료. PR 14b-4 (callback controller + `JwtAuthFilter`),
  PR 14c (frontend) 잔여. 코드 위치: `domain.auth.saint`.
- 🔜 도서관 세션 인증 — Task 13. PR 13a (백엔드 session store + 401 매핑)
  머지, PR 13c (manual paste UI) + TTL spike 결과 의존.
- 📋 LMS connector — Task 17 spec ([`docs/tasks/17-lms-integration.md`](tasks/17-lms-integration.md))
  pins the LMS host / auth shape spike + `LmsSessionStore` + the first
  authenticated LMS tool `get_my_assignments`. Auth shape (SmartID-fronted
  vs school-account form login) decided in the PR 17a spike.
- u-SAINT / LMS / 도서관 계정 정보 → 학생이 안전하게 위탁할 수 있도록
  AES-GCM 으로 암호화 저장 (`SSUAI_CREDENTIAL_ENCRYPTION_KEY`). 단,
  u-SAINT 는 비밀번호를 ssuAI 가 보지 않는 SmartID SSO 리다이렉트
  패턴이라 `sToken`/`sIdno` one-shot 토큰만 처리 후 폐기 (Task 14 spec).
- 개인 MCP 도구 — 사용자 인증 토큰을 MCP tool argument 로 받거나,
  ssuAI 가 발급한 API key 로 라우팅
- 개인정보 log 정책 ([`docs/security.md`](security.md) §4)

### Phase 4 (에이전트) — **프로젝트의 정체성을 완성하는 단계**

이 phase 가 ssuAI 를 "AI 정보 챗봇" 에서 "실제 동작하는 AI 에이전트"
로 바꿉니다.

- `get_library_seat_status(floor)` — 도서관 사이트에서 실시간 좌석
  데이터 스크랩 (층별·구역별)
- `reserve_library_seat(seat_id, duration)` — **flagship 액션 도구**.
  사용자 credential 로 도서관 사이트에 인증해서 좌석 클릭 POST 자동
  수행
- `cancel_library_seat_reservation`, `extend_library_seat` — 후속
  액션 도구
- 액션 도구 공용 인프라 — [ADR 0015](adr/0015-action-tool-infrastructure.md)
  에서 메커니즘 확정:
  - prepare + confirm 두 단계 MCP tool 분리 (confirmation 재사용 금지)
  - dry-run preview (`prepare_*` 가 반환하는 정확한 문구)
  - `action_audit` 테이블 (PREPARED → EXECUTING → SUCCESS/FAILURE\_\*/TIMEOUT/EXPIRED/CANCELLED)
  - 분산 lock (MVP in-process, 멀티 인스턴스 시 Redis SETNX 로 스왑)
  - race condition graceful 처리 — `FAILURE_RACE` 별도 outcome 코드
- 챗봇 위 agent loop — "도구 결과 → 다음 도구 호출 결정 → 액션 도구
  실행" 의 multi-turn reasoning 구현

---

## 5. 명시적으로 만들지 않을 것

vision 이 명확해야 무엇을 안 만들지도 명확합니다.

- ❌ **자동 수강신청** — 윤리·약관·수강 공정성 문제. 영원히 안 함.
- ❌ **학생 비밀번호 평문 저장** — 모든 credential 은 AES-GCM 암호화
  필수.
- ❌ **자동 LMS 과제 제출** — Phase 4 에이전트 범위에서도 제외.
  제출은 학생 본인이 직접 하는 것이 옳음. 에이전트는 마감 알림과
  "제출 전 체크리스트" 까지만.
- ❌ **public MCP 서버에서 익명 사용자가 개인 도구 호출** — 개인
  도구는 반드시 인증된 사용자만.
- ❌ **단일 LLM 프로바이더에 lock-in** — 9 provider fallback 으로
  multi-vendor 전략 유지.

---

## 6. 성공 기준 (각 phase 별)

<!-- markdownlint-disable MD013 MD060 -->

| Phase | 성공 기준 |
|---|---|
| Phase 1 | 라이브 챗봇에서 "오늘 학식" 묻고 한국어 자연어 답변 ← **달성됨** |
| Phase 2 | Claude Desktop 에서 ssuAI MCP 붙이고 "도서관 4층에 자리 있어?" 실시간 답변 |
| Phase 3 | 인증된 사용자가 챗봇에서 "내 다음 수업 뭐야?" 답변 받음 |
| **Phase 4** | **챗봇에서 도서관 좌석 추천 받고 "412번 예약해줘" → confirmation → 실제 학교 도서관 사이트에 예약 완료 (flagship)** |

<!-- markdownlint-enable MD013 MD060 -->

---

## 7. 관련 문서

- [`docs/product.md`](product.md) — 제품 정의 (이 vision 의 단기
  표현형)
- [`docs/architecture.md`](architecture.md) — 시스템 설계 (layer 가
  vision 의 4-layer 와 어떻게 매핑되는지)
- [`docs/security.md`](security.md) — secret / credential / logging
  정책 (Phase 3·4 의 안전 기반)
- [`docs/mcp-tools.md`](mcp-tools.md) — MCP 서버 현재 사용법
- [`docs/adr/`](adr/) — 모든 load-bearing 결정
- [`docs/tasks/`](tasks/) — 단기 task 큐
