package com.ssuai.domain.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.ssuai.domain.chat.config.LlmChatProperties;
import com.ssuai.domain.chat.dto.ChatResponse;
import com.ssuai.domain.chat.dto.OpenAiChatCompletionRequest;
import com.ssuai.domain.chat.dto.OpenAiChatCompletionResponse;
import com.ssuai.domain.chat.dto.OpenAiToolCall;
import com.ssuai.domain.chat.memory.ChatConversationStore;
import com.ssuai.domain.chat.memory.ChatConversationStore.Turn;
import com.ssuai.domain.chat.service.llm.LlmCompletionRequest;
import com.ssuai.domain.chat.service.llm.LlmCompletionResult;
import com.ssuai.domain.chat.service.llm.LlmPrivacyMode;
import com.ssuai.domain.chat.service.llm.LlmProvider;
import com.ssuai.domain.chat.service.llm.LlmProviderException;
import com.ssuai.domain.library.mcp.LibraryToolContext;
import com.ssuai.domain.library.service.LibraryLoansService;
import com.ssuai.domain.lms.service.LmsAssignmentsService;
import com.ssuai.domain.saint.service.SaintGradesService;
import com.ssuai.domain.saint.service.SaintScheduleService;
import com.ssuai.global.exception.ChatUnavailableException;
import com.ssuai.global.exception.LibraryAuthRequiredException;
import com.ssuai.global.exception.LmsSessionExpiredException;
import com.ssuai.global.exception.SaintSessionExpiredException;

@Service
@ConditionalOnProperty(name = "ssuai.connector.chat", havingValue = "llm")
public class LlmChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);

    private static final String SYSTEM_PROMPT = """
            너는 숭실대 학생을 도와주는 ssuAI 챗봇이야.
            말투는 친근한 선배처럼 편하게, 기본은 한국어 존댓말로 답해.
            보통 짧게 답하지만, 도구 결과를 보여줄 때는 빠짐없이 정확하게 보여줘.

            다룰 수 있는 공개 데이터:
            - 학생식당 메뉴 (get_today_meal, get_meal_by_date)
            - 기숙사 식단 (get_dorm_weekly_meal)
            - 캠퍼스 시설 검색 (search_campus_facilities)
            - 중앙도서관 좌석 현황 (get_library_seat_status, floor 코드: -1 B1, 1~6 1~6층)
            - 중앙도서관 도서 검색 (search_library_book, 키워드로 제목/저자/출판 부분 일치)

            인증된 본인 데이터 (u-SAINT 로그인 필요):
            - 내 시간표 (get_my_schedule) — 학기별 강의 (요일·교시·과목·강의실).
              사용자가 "내 시간표", "다음 1교시", "수요일 강의" 같은 식으로 물으면 호출해.
            - 내 성적 (get_my_grades) — 누적 GPA + 학기별 과목 수.
              **중요**: 응답은 `{count, link}` 만 와. 절대 점수/등급/과목명/GPA 수치를
              만들어내지 마. 답은 "성적 페이지에서 N과목 확인 가능합니다 (링크 안내)"
              형태로만 해. 본문 데이터는 사용자가 controller 페이지에서 직접 확인해.

            인증된 본인 데이터 (LMS 로그인 필요):
            - 내 LMS 과제 (get_my_assignments) — 현재 학기 미제출 과제·퀴즈 목록.
              과목명·제목·유형·마감일이 포함. 사용자가 "과제", "LMS", "제출 안 한 거"
              같은 식으로 물으면 호출해. LMS 로그인이 안 된 경우 재로그인 안내.

            인증된 본인 데이터 (도서관 세션 연동 필요):
            - 내 도서관 대출 현황 (get_my_library_loans) — 현재 대출 도서 목록, 반납 기한,
              연장 가능 여부. 사용자가 "대출", "반납", "연장", "도서관 빌린 책"
              같은 식으로 물으면 호출해. 도서관 세션이 없는 경우 연동 방법 안내.

            행동 원칙:
            1. 모호한 질문도 일단 가장 그럴듯한 가정으로 즉시 도구를 불러. 되묻기는
               최후 수단이야. 예: "오늘 학식 뭐야?" → 식당을 안 골라줬다면 학생식당으로
               가정해서 바로 get_today_meal 호출, 그리고 답할 때 "다른 식당이 궁금하면
               알려줘"를 짧게 덧붙여.
            2. "응", "응응", "그래", "ㅇㅇ" 같은 짧은 긍정 답변이 들어오면 직전 턴에서
               네가 제안한 동작을 그대로 실행해. 다시 묻지 마.
            3. 도구 결과에 없는 정보는 절대 만들지 마. 특히 시설명, 브랜드명, 위치는
               도구가 반환한 그대로만 써. 예: 학교 편의점이 도구 결과에 "쿱스켓"으로
               나오면 "CU"나 "GS25" 같은 이름을 임의로 갖다 붙이지 마.
            4. 도구 결과가 N개 항목이면 N개를 모두 보여주거나, 일부만 보여줄 거면
               "총 N개 중 일부"라고 명시해.
            5. 도구가 빈 결과/에러를 반환하면 그대로 "지금은 그 정보가 없어요"라고
               말해. 다른 사이트 링크나 외부 추정 정보를 만들지 마.

            범위 밖 안내:
            - LMS 과제, 수강신청, 졸업요건 같은 시스템 연동은 아직 지원 안 함.
            - get_my_schedule / get_my_grades 는 인증된 chat 세션에서만 동작.
              로그인 안 한 사용자에게는 "u-SAINT 로그인이 필요합니다" 안내.
            - 비밀번호, 학번, 쿠키, 세션, API key 같은 비밀 정보는 요구하지도 받지도
              마. 사용자가 입력하면 저장/반복하지 말고 지우라고 안내해.
            """;

    private static final String SCOPE_GUIDANCE =
            "아직은 그 정보는 지원하지 않아요. 지금은 학식, 기숙사 식단, 캠퍼스 시설, 도서관 좌석/도서 검색, 그리고 (로그인된 경우) 본인 시간표·성적·LMS 과제·도서관 대출 현황을 도와줄 수 있어요.";

    private static final String SECRET_GUIDANCE =
            "비밀번호, 쿠키, 세션, API key 같은 비밀 정보는 입력하지 말아주세요. 지금은 학식, 기숙사 식단, 캠퍼스 시설, 도서관 좌석/도서 검색만 도와줄 수 있어요.";

    private static final String SAINT_SESSION_GUIDANCE =
            "u-SAINT 로그인이 필요한 정보예요. 먼저 SmartID 로 로그인하고 다시 물어봐 주세요.";

    private static final String SAINT_SESSION_EXPIRED_GUIDANCE =
            "u-SAINT 세션이 만료됐어요. SmartID 로 다시 로그인하고 물어봐 주세요.";

    private static final String LMS_SESSION_GUIDANCE =
            "LMS 로그인이 필요한 정보예요. 먼저 LMS(SmartID)로 로그인하고 다시 물어봐 주세요.";

    private static final String LMS_SESSION_EXPIRED_GUIDANCE =
            "LMS 세션이 만료됐어요. LMS(SmartID)로 다시 로그인하고 물어봐 주세요.";

    private static final String LIBRARY_SESSION_GUIDANCE =
            "도서관 세션 연동이 필요한 정보예요. 도서관 좌석 현황 카드에서 '도서관 연동' 버튼을 누르고 Pyxis-Auth-Token 을 붙여넣어 주세요.";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Locale KOREAN = Locale.KOREAN;

    private static final int MAX_CHAT_TOOL_FACILITY_RESULTS = 10;
    private static final int MAX_TOOL_CONTENT_BYTES = 8 * 1024;
    private static final String TOOL_TRUNCATION_MARKER = "...[truncated]";

    private final LlmChatProperties properties;
    private final Map<String, LlmProvider> providersByName;
    private final ObjectMapper objectMapper;
    private final ChatConversationStore conversationStore;
    private final SaintScheduleService scheduleService;
    private final SaintGradesService gradesService;
    private final LmsAssignmentsService lmsAssignmentsService;
    private final LibraryLoansService libraryLoansService;
    private final Clock clock;
    private volatile List<OpenAiChatCompletionRequest.Tool> cachedChatTools;

    private final List<McpSyncClient> mcpClients;

    @Autowired
    public LlmChatService(
            LlmChatProperties properties,
            List<LlmProvider> providers,
            ObjectMapper objectMapper,
            ChatConversationStore conversationStore,
            SaintScheduleService scheduleService,
            SaintGradesService gradesService,
            LmsAssignmentsService lmsAssignmentsService,
            LibraryLoansService libraryLoansService,
            @Lazy List<McpSyncClient> mcpClients
    ) {
        this(properties, providers, objectMapper, conversationStore,
                scheduleService, gradesService, lmsAssignmentsService, libraryLoansService,
                mcpClients, Clock.system(KST));
    }

    LlmChatService(
            LlmChatProperties properties,
            List<LlmProvider> providers,
            ObjectMapper objectMapper,
            ChatConversationStore conversationStore,
            SaintScheduleService scheduleService,
            SaintGradesService gradesService,
            LmsAssignmentsService lmsAssignmentsService,
            LibraryLoansService libraryLoansService,
            List<McpSyncClient> mcpClients,
            Clock clock
    ) {
        this.properties = properties;
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::name, Function.identity()));
        this.objectMapper = objectMapper;
        this.conversationStore = conversationStore;
        this.scheduleService = scheduleService;
        this.gradesService = gradesService;
        this.lmsAssignmentsService = lmsAssignmentsService;
        this.libraryLoansService = libraryLoansService;
        this.mcpClients = mcpClients;
        this.clock = clock;
    }

    /**
     * Emits a per-request system message giving the model today's KST date so it
     * can resolve relative date references ("어제", "내일", "이번 주") into a
     * concrete {@code yyyy-MM-dd} for tool calls like
     * {@code get_meal_by_date}. The static {@code SYSTEM_PROMPT} stays cacheable;
     * this short volatile slice is the only piece that changes day-to-day.
     */
    String buildTodayContextMessage() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, KOREAN);
        return String.format(
                "오늘은 %s (%s) 입니다 (Asia/Seoul 기준). 사용자가 \"어제\", \"오늘\", "
                        + "\"내일\", \"이번 주\" 같은 상대 날짜를 쓰면 이 날짜를 기준으로 "
                        + "해석해서 도구의 date 파라미터에 yyyy-MM-dd 형식으로 넘겨.",
                today, weekday);
    }

    private McpSyncClient mcpClient() {
        if (mcpClients == null || mcpClients.isEmpty()) {
            throw new IllegalStateException(
                    "LLM chat mode requires at least one Spring AI MCP client connection (spring.ai.mcp.client.sse.connections.*).");
        }
        return mcpClients.get(0);
    }

    @Override
    public ChatResponse reply(String conversationId, String message, String studentId) {
        if (looksLikeSecretInput(message)) {
            // Never persist the user message: it may contain secrets.
            return new ChatResponse(conversationId, SECRET_GUIDANCE);
        }
        if (looksLikeOutOfScopeRequest(message)) {
            conversationStore.appendUser(conversationId, message);
            conversationStore.appendAssistant(conversationId, SCOPE_GUIDANCE);
            return new ChatResponse(conversationId, SCOPE_GUIDANCE);
        }

        Instant startedAt = Instant.now();
        int messageLength = message == null ? 0 : message.length();
        boolean authenticated = studentId != null && !studentId.isBlank();
        log.info("chat reply started: conversationId={} messageLength={} authenticated={}",
                conversationId, messageLength, authenticated);

        List<Turn> history = conversationStore.history(conversationId);
        conversationStore.appendUser(conversationId, message);

        try {
            ChatResponse response = callLlm(conversationId, message, history,
                    LlmPrivacyMode.PUBLIC, studentId);
            conversationStore.appendAssistant(conversationId, response.reply());
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info("chat reply completed: conversationId={} messageLength={} latencyMs={} historyTurns={}",
                    conversationId, messageLength, latencyMs, history.size());
            return response;
        } catch (ChatUnavailableException exception) {
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.warn("chat reply failed: conversationId={} messageLength={} latencyMs={} failureType={}",
                    conversationId, messageLength, latencyMs, exception.getClass().getSimpleName());
            throw exception;
        } catch (RuntimeException exception) {
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.warn("chat reply failed: conversationId={} messageLength={} latencyMs={} failureType={}",
                    conversationId, messageLength, latencyMs, exception.getClass().getSimpleName());
            throw new ChatUnavailableException(exception);
        }
    }

    private ChatResponse callLlm(
            String conversationId,
            String message,
            List<Turn> history,
            LlmPrivacyMode privacyMode,
            String studentId
    ) {
        List<OpenAiChatCompletionRequest.Message> baseMessages = new ArrayList<>();
        baseMessages.add(OpenAiChatCompletionRequest.systemMessage(SYSTEM_PROMPT));
        baseMessages.add(OpenAiChatCompletionRequest.systemMessage(buildTodayContextMessage()));
        for (Turn turn : history) {
            if (ChatConversationStore.ROLE_USER.equals(turn.role())) {
                baseMessages.add(OpenAiChatCompletionRequest.userMessage(turn.content()));
            } else if (ChatConversationStore.ROLE_ASSISTANT.equals(turn.role())) {
                baseMessages.add(OpenAiChatCompletionRequest.assistantMessage(turn.content()));
            }
        }
        baseMessages.add(OpenAiChatCompletionRequest.userMessage(message));

        LlmCompletionResult firstResult = completeAcrossProviders(new LlmCompletionRequest(
                privacyMode,
                baseMessages,
                chatTools(),
                "auto"
        ));
        OpenAiChatCompletionResponse.Message firstMessage = firstResult.message();
        List<OpenAiToolCall> toolCalls = safeToolCalls(firstMessage.toolCalls());

        if (toolCalls.isEmpty()) {
            log.info("chat provider selected: conversationId={} provider={} model={} toolCalls=0",
                    conversationId, firstResult.providerName(), firstResult.model());
            return new ChatResponse(conversationId, requireContent(firstMessage.content()));
        }

        List<OpenAiChatCompletionRequest.Message> messages = new ArrayList<>(baseMessages);
        messages.add(OpenAiChatCompletionRequest.assistantToolCallMessage(firstMessage.content(), toolCalls));
        // Bind the authenticated student id to the chat thread for the
        // duration of the tool dispatch loop. The private MCP tools
        // ({@code get_my_schedule}, {@code get_my_grades}) read it from
        // SaintToolContext when invoked through the in-process MCP
        // callback path; chat's direct-service short-circuit uses the
        // local parameter, so the binding is also a safety net for
        // future loopback routing changes.
        try (com.ssuai.domain.saint.mcp.SaintToolContext.Scope ignored =
                     com.ssuai.domain.saint.mcp.SaintToolContext.withStudentId(studentId);
             com.ssuai.domain.lms.mcp.LmsToolContext.Scope ignoredLms =
                     com.ssuai.domain.lms.mcp.LmsToolContext.withStudentId(studentId);
             LibraryToolContext.Scope ignoredLibrary =
                     LibraryToolContext.withSessionKey(LibraryToolContext.currentSessionKey())) {
            for (int index = 0; index < toolCalls.size(); index++) {
                OpenAiToolCall toolCall = toolCalls.get(index);
                String toolCallId = toolCall.id() == null || toolCall.id().isBlank()
                        ? "call_" + index
                        : toolCall.id();
                String content = index < maxToolCalls()
                        ? executeToolCall(toolCall, studentId)
                        : toolError("한 번에 처리할 수 있는 도구 호출 수를 초과했습니다. 한두 가지씩 나눠서 물어봐 주세요.");
                messages.add(OpenAiChatCompletionRequest.toolResultMessage(toolCallId, content));
            }
        }

        LlmCompletionResult finalResult = completeAcrossProviders(new LlmCompletionRequest(
                privacyMode,
                messages,
                null,
                null
        ));
        log.info("chat provider selected: conversationId={} provider={} model={} toolCalls={}",
                conversationId, finalResult.providerName(), finalResult.model(), toolCalls.size());
        return new ChatResponse(conversationId, requireContent(finalResult.message().content()));
    }

    private int maxToolCalls() {
        return Math.max(1, properties.getMaxToolCalls());
    }

    private LlmCompletionResult completeAcrossProviders(LlmCompletionRequest request) {
        List<ProviderAttempt> attempts = providerAttempts(request.privacyMode());
        if (attempts.isEmpty()) {
            throw new ChatUnavailableException();
        }

        LlmProviderException lastFailure = null;
        int totalPasses = Math.max(1, properties.getAvailabilityVerificationPasses() + 1);
        for (int pass = 1; pass <= totalPasses; pass++) {
            for (ProviderAttempt attempt : attempts) {
                try {
                    return attempt.provider().complete(withPrivacyMode(request, attempt.privacyMode()));
                } catch (LlmProviderException exception) {
                    if (!exception.fallbackable()) {
                        throw new ChatUnavailableException(exception);
                    }
                    lastFailure = exception;
                    log.info("llm provider fallback: provider={} privacyMode={} pass={} statusCode={}",
                            exception.providerName(), attempt.privacyMode(), pass, exception.statusCode());
                }
            }
        }

        throw new ChatUnavailableException(lastFailure);
    }

    private List<ProviderAttempt> providerAttempts(LlmPrivacyMode privacyMode) {
        if (privacyMode == LlmPrivacyMode.PRIVATE) {
            return capProviderAttempts(orderedProviders(properties.getPrivateProviderOrder()).stream()
                    .map(provider -> new ProviderAttempt(provider, LlmPrivacyMode.PRIVATE))
                    .toList());
        }

        List<ProviderAttempt> attempts = new ArrayList<>();
        orderedProviders(properties.getProviderOrder()).stream()
                .map(provider -> new ProviderAttempt(provider, LlmPrivacyMode.PUBLIC))
                .forEach(attempts::add);
        orderedProviders(properties.getPrivateProviderOrder()).stream()
                .map(provider -> new ProviderAttempt(provider, LlmPrivacyMode.PRIVATE))
                .forEach(attempts::add);
        return capProviderAttempts(attempts);
    }

    private List<LlmProvider> orderedProviders(List<String> providerOrder) {
        if (providerOrder == null || providerOrder.isEmpty()) {
            return providersByName.values().stream()
                    .filter(LlmProvider::isConfigured)
                    .toList();
        }

        return providerOrder.stream()
                .map(providersByName::get)
                .filter(provider -> provider != null && provider.isConfigured())
                .toList();
    }

    private List<ProviderAttempt> capProviderAttempts(List<ProviderAttempt> attempts) {
        int maxAttempts = Math.max(1, properties.getMaxProviderAttempts());
        if (attempts.size() <= maxAttempts) {
            return attempts;
        }
        return attempts.subList(0, maxAttempts);
    }

    private static LlmCompletionRequest withPrivacyMode(
            LlmCompletionRequest request,
            LlmPrivacyMode privacyMode
    ) {
        if (request.privacyMode() == privacyMode) {
            return request;
        }
        return new LlmCompletionRequest(
                privacyMode,
                request.messages(),
                request.tools(),
                request.toolChoice()
        );
    }

    private record ProviderAttempt(
            LlmProvider provider,
            LlmPrivacyMode privacyMode
    ) {
    }

    private List<OpenAiChatCompletionRequest.Tool> chatTools() {
        List<OpenAiChatCompletionRequest.Tool> snapshot = cachedChatTools;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (cachedChatTools == null) {
                cachedChatTools = discoverChatTools();
            }
            return cachedChatTools;
        }
    }

    private List<OpenAiChatCompletionRequest.Tool> discoverChatTools() {
        try {
            McpSyncClient client = mcpClient();
            if (!client.isInitialized()) {
                client.initialize();
            }
            McpSchema.ListToolsResult listing = client.listTools();
            List<OpenAiChatCompletionRequest.Tool> tools = listing.tools().stream()
                    .filter(Objects::nonNull)
                    .map(this::mapMcpToolToOpenAi)
                    .toList();
            log.info("mcp chat tools discovered: count={}", tools.size());
            return tools;
        } catch (RuntimeException exception) {
            log.warn("mcp listTools failed: error={}", exception.getClass().getSimpleName());
            throw new ChatUnavailableException(exception);
        }
    }

    private OpenAiChatCompletionRequest.Tool mapMcpToolToOpenAi(McpSchema.Tool tool) {
        String description = tool.description() == null ? "" : tool.description();
        return new OpenAiChatCompletionRequest.Tool(
                "function",
                new OpenAiChatCompletionRequest.FunctionDefinition(
                        tool.name(),
                        description,
                        mapInputSchema(tool.inputSchema())
                )
        );
    }

    private static Map<String, Object> mapInputSchema(McpSchema.JsonSchema schema) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        if (schema == null) {
            mapped.put("type", "object");
            mapped.put("properties", Map.of());
            mapped.put("additionalProperties", false);
            return mapped;
        }
        mapped.put("type", schema.type() == null ? "object" : schema.type());
        mapped.put("properties", schema.properties() == null ? Map.of() : schema.properties());
        if (schema.required() != null && !schema.required().isEmpty()) {
            mapped.put("required", schema.required());
        }
        mapped.put("additionalProperties",
                schema.additionalProperties() == null ? Boolean.FALSE : schema.additionalProperties());
        return mapped;
    }

    private String executeToolCall(OpenAiToolCall toolCall, String studentId) {
        String toolName = toolCall.function() == null ? "" : toolCall.function().name();
        try {
            return switch (toolName) {
                case "get_today_meal" -> callMcp(toolName, restaurantArgs(toolCall));
                case "get_meal_by_date" -> {
                    LinkedHashMap<String, Object> args = new LinkedHashMap<>();
                    args.put("date", requiredArgument(toolCall, "date"));
                    args.putAll(restaurantArgs(toolCall));
                    yield callMcp(toolName, args);
                }
                case "get_dorm_weekly_meal" -> callMcp(toolName, Map.of());
                case "search_campus_facilities" -> {
                    String query = optionalArgument(toolCall, "query").trim();
                    if (query.isBlank()) {
                        yield toolError("시설 검색은 검색어가 필요합니다. 예: 카페, 복사, 편의점, 학생식당.");
                    }
                    yield callMcp(toolName, Map.of("query", query));
                }
                case "get_library_seat_status" -> {
                    int floor = requiredIntArgument(toolCall, "floor");
                    yield callMcp(toolName, Map.of("floor", floor));
                }
                case "search_library_book" -> {
                    String query = optionalArgument(toolCall, "query").trim();
                    if (query.isBlank()) {
                        yield toolError("도서 검색은 검색어가 필요합니다. 예: 파이썬, 이펙티브 자바.");
                    }
                    LinkedHashMap<String, Object> args = new LinkedHashMap<>();
                    args.put("query", query);
                    optionalIntArgument(toolCall, "page").ifPresent(value -> args.put("page", value));
                    optionalIntArgument(toolCall, "size").ifPresent(value -> args.put("size", value));
                    yield callMcp(toolName, args);
                }
                case "get_my_schedule" -> dispatchPrivateSaintTool(
                        toolName, studentId, () -> scheduleService.fetchSchedule(studentId));
                case "get_my_grades" -> dispatchPrivateSaintTool(
                        toolName, studentId, () -> gradesService.fetchGrades(studentId));
                case "get_my_assignments" -> dispatchPrivateLmsTool(
                        toolName, studentId, () -> lmsAssignmentsService.fetchAssignments(studentId));
                case "get_my_library_loans" -> dispatchPrivateLibraryTool(
                        toolName, () -> libraryLoansService.getLoansForSession(
                                LibraryToolContext.currentSessionKey()));
                default -> toolError("지원하지 않는 도구입니다: " + toolName);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return toolError(exception.getMessage());
        }
    }

    /**
     * Run a u-SAINT private tool against the in-process service directly,
     * bypassing the MCP SSE round-trip. The loopback {@code mcpClient}
     * would dispatch the call to a separate servlet thread where
     * {@code SaintToolContext} 's thread-local cannot reach; calling the
     * service in-line keeps the authenticated student id local to the
     * chat thread. Compact policy ({@code compactAndCap}) still runs on
     * the way out, so the LLM never sees raw grade rows / schedule
     * professor names.
     */
    private String dispatchPrivateSaintTool(
            String toolName,
            String studentId,
            java.util.function.Supplier<Object> serviceCall
    ) {
        if (studentId == null || studentId.isBlank()) {
            log.info("chat private tool refused: tool={} reason=unauthenticated", toolName);
            return toolError(SAINT_SESSION_GUIDANCE);
        }
        // Audit log (Task 16 spec §8 #6): pin the intent — who asked for
        // what — before the connector runs. studentFp is a SHA-256 prefix,
        // never the raw student id; tool name is one of the literal
        // enum-like strings ("get_my_schedule" / "get_my_grades"). The
        // response payload (course names, grade letters, etc.) MUST NOT
        // appear in any log line on this code path; the only other log
        // below is post-fetch completion which again uses studentFp only.
        String studentFp = com.ssuai.domain.auth.saint.SaintSessionStore.fingerprint(studentId);
        log.info("chat private tool requested: tool={} studentFp={}", toolName, studentFp);
        try {
            Object response = serviceCall.get();
            String json = objectMapper.writeValueAsString(response);
            log.info("chat private tool completed: tool={} studentFp={}", toolName, studentFp);
            return compactAndCap(toolName, json);
        } catch (SaintSessionExpiredException exception) {
            log.info("chat private tool expired: tool={} studentFp={}", toolName, studentFp);
            return toolError(SAINT_SESSION_EXPIRED_GUIDANCE);
        } catch (JsonProcessingException exception) {
            throw new ChatUnavailableException(exception);
        }
    }

    private String dispatchPrivateLmsTool(
            String toolName,
            String studentId,
            java.util.function.Supplier<Object> serviceCall
    ) {
        if (studentId == null || studentId.isBlank()) {
            log.info("chat private tool refused: tool={} reason=unauthenticated", toolName);
            return toolError(LMS_SESSION_GUIDANCE);
        }
        String studentFp = com.ssuai.domain.auth.lms.LmsSessionStore.fingerprint(studentId);
        log.info("chat private tool requested: tool={} studentFp={}", toolName, studentFp);
        try {
            Object response = serviceCall.get();
            String json = objectMapper.writeValueAsString(response);
            log.info("chat private tool completed: tool={} studentFp={}", toolName, studentFp);
            return compactAndCap(toolName, json);
        } catch (LmsSessionExpiredException exception) {
            log.info("chat private tool expired: tool={} studentFp={}", toolName, studentFp);
            return toolError(LMS_SESSION_EXPIRED_GUIDANCE);
        } catch (JsonProcessingException exception) {
            throw new ChatUnavailableException(exception);
        }
    }

    private String dispatchPrivateLibraryTool(
            String toolName,
            java.util.function.Supplier<Object> serviceCall
    ) {
        String sessionKey = LibraryToolContext.currentSessionKey();
        if (sessionKey == null || sessionKey.isBlank()) {
            log.info("chat private tool refused: tool={} reason=no-library-session", toolName);
            return toolError(LIBRARY_SESSION_GUIDANCE);
        }
        String sessionFp = com.ssuai.domain.library.auth.LibrarySessionStore.fingerprint(sessionKey);
        log.info("chat private tool requested: tool={} sessionFp={}", toolName, sessionFp);
        try {
            Object response = serviceCall.get();
            String json = objectMapper.writeValueAsString(response);
            log.info("chat private tool completed: tool={} sessionFp={}", toolName, sessionFp);
            return compactAndCap(toolName, json);
        } catch (LibraryAuthRequiredException exception) {
            log.info("chat private tool auth: tool={} sessionFp={}", toolName, sessionFp);
            return toolError(LIBRARY_SESSION_GUIDANCE);
        } catch (JsonProcessingException exception) {
            throw new ChatUnavailableException(exception);
        }
    }

    private Map<String, Object> restaurantArgs(OpenAiToolCall toolCall) {
        String restaurant = optionalArgument(toolCall, "restaurant").trim();
        if (restaurant.isBlank()) {
            return Map.of();
        }
        return Map.of("restaurant", restaurant);
    }

    private String callMcp(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result;
        try {
            result = mcpClient().callTool(new McpSchema.CallToolRequest(toolName, arguments));
        } catch (RuntimeException exception) {
            log.warn("mcp tool call failed: tool={} error={}", toolName, exception.getClass().getSimpleName());
            return toolError("도구 호출에 실패했습니다.");
        }

        String text = extractText(result.content());
        if (Boolean.TRUE.equals(result.isError())) {
            return toolError(text.isBlank() ? "도구 실행에 실패했습니다." : text);
        }
        if (text.isBlank()) {
            return toolError("도구 응답이 비어 있습니다.");
        }
        return compactAndCap(toolName, text);
    }

    private static String extractText(List<McpSchema.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        for (McpSchema.Content content : contents) {
            if (content instanceof McpSchema.TextContent textContent) {
                buffer.append(textContent.text());
            }
        }
        return buffer.toString();
    }

    String compactAndCap(String toolName, String rawJsonText) {
        try {
            JsonNode tree = objectMapper.readTree(rawJsonText);
            JsonNode compacted = switch (toolName) {
                case "get_today_meal", "get_meal_by_date" -> compactMealNode(tree);
                case "get_dorm_weekly_meal" -> compactWeeklyMealNode(tree);
                case "search_campus_facilities" -> compactFacilityListNode(tree);
                case "get_library_seat_status" -> compactLibrarySeatNode(tree);
                case "search_library_book" -> compactLibraryBookSearchNode(tree);
                case "get_my_schedule" -> compactScheduleNode(tree);
                case "get_my_grades" -> compactGradesNode(tree);
                case "get_my_assignments" -> compactAssignmentsNode(tree);
                case "get_my_library_loans" -> compactLoansNode(tree);
                default -> tree;
            };
            return capLength(objectMapper.writeValueAsString(compacted));
        } catch (JsonProcessingException exception) {
            return capLength(rawJsonText);
        }
    }

    private static String capLength(String value) {
        if (value.length() <= MAX_TOOL_CONTENT_BYTES) {
            return value;
        }
        return value.substring(0, MAX_TOOL_CONTENT_BYTES) + TOOL_TRUNCATION_MARKER;
    }

    private ObjectNode compactMealNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "date");
        compact.set("meals", filterArray(node.get("meals"), this::compactMealItemNode));
        JsonNode closures = node.get("closures");
        if (closures != null && closures.isArray() && !closures.isEmpty()) {
            compact.set("closures", filterArray(closures, this::compactClosureNode));
        }
        return compact;
    }

    private ObjectNode compactWeeklyMealNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "startDate");
        copyTextIfPresent(node, compact, "endDate");
        compact.set("days", filterArray(node.get("days"), this::compactMealNode));
        return compact;
    }

    private ObjectNode compactFacilityListNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        JsonNode facilities = node.get("facilities");
        int total = facilities != null && facilities.isArray() ? facilities.size() : 0;
        compact.put("resultCount", total);
        compact.put("truncated", total > MAX_CHAT_TOOL_FACILITY_RESULTS);
        ArrayNode trimmed = objectMapper.createArrayNode();
        if (facilities != null && facilities.isArray()) {
            int limit = Math.min(total, MAX_CHAT_TOOL_FACILITY_RESULTS);
            for (int index = 0; index < limit; index++) {
                trimmed.add(compactFacilityNode(facilities.get(index)));
            }
        }
        compact.set("facilities", trimmed);
        return compact;
    }

    private ObjectNode compactMealItemNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "restaurant");
        copyTextIfPresent(node, compact, "type");
        copyTextIfPresent(node, compact, "corner");
        if (node.hasNonNull("menu")) {
            compact.set("menu", node.get("menu"));
        }
        return compact;
    }

    private ObjectNode compactClosureNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "restaurant");
        copyTextIfPresent(node, compact, "reason");
        return compact;
    }

    private ObjectNode compactLibrarySeatNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyIntIfPresent(node, compact, "floor");
        copyTextIfPresent(node, compact, "floorLabel");
        copyIntIfPresent(node, compact, "totalSeats");
        copyIntIfPresent(node, compact, "availableSeats");
        copyIntIfPresent(node, compact, "reservedSeats");
        copyIntIfPresent(node, compact, "outOfServiceSeats");
        JsonNode zones = node.get("zones");
        if (zones != null && zones.isArray() && !zones.isEmpty()) {
            compact.set("zones", filterArray(zones, this::compactLibrarySeatZoneNode));
        }
        return compact;
    }

    private ObjectNode compactLibrarySeatZoneNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "label");
        copyIntIfPresent(node, compact, "total");
        copyIntIfPresent(node, compact, "available");
        copyNonEmptyArray(node, compact, "seatIds");
        return compact;
    }

    /**
     * Schedule rows are allowed in LLM prompts in a compact row format
     * (Task 16 spec §6 #6 — "월 1교시 알고리즘 / 정보과학관 401"). Strip
     * fields the chat answer never needs (dayLabel — derivable from
     * dayOfWeek, timeRange — derivable from period, professor — not
     * required to answer "내일 1교시 뭐야?"). Keeping the input format
     * tight makes the LLM prompt budget predictable and limits the
     * cross-trust-boundary surface area.
     */
    private ObjectNode compactScheduleNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyIntIfPresent(node, compact, "enrollmentYear");
        copyIntIfPresent(node, compact, "currentYear");
        copyIntIfPresent(node, compact, "currentTerm");
        JsonNode terms = node.get("terms");
        if (terms != null && terms.isArray()) {
            compact.set("terms", filterArray(terms, this::compactTermScheduleNode));
        }
        return compact;
    }

    private ObjectNode compactTermScheduleNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyIntIfPresent(node, compact, "year");
        copyIntIfPresent(node, compact, "term");
        JsonNode entries = node.get("entries");
        if (entries != null && entries.isArray()) {
            compact.set("entries", filterArray(entries, this::compactScheduleEntryNode));
        }
        return compact;
    }

    private ObjectNode compactScheduleEntryNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyIntIfPresent(node, compact, "dayOfWeek");
        copyIntIfPresent(node, compact, "period");
        copyTextIfPresent(node, compact, "course");
        copyTextIfPresent(node, compact, "room");
        return compact;
    }

    /**
     * Grades NEVER cross into LLM prompts (Task 16 spec §6 #6 locked-in
     * decision + security checklist §8). The chat path answers grade
     * questions with a citation — "성적 페이지에서 N과목 확인 가능합니다"
     * + a link — never the rows themselves. This compact branch enforces
     * that: regardless of what the upstream tool returned, only
     * {@code count} (total course rows across every term) and {@code link}
     * (the deep link the controller serves on) reach the LLM. Per-term
     * GPA, course names, scores, grade letters, professor names — all
     * dropped here, by design.
     */
    private ObjectNode compactGradesNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("count", countGradeCourses(node));
        compact.put("link", "/grades");
        return compact;
    }

    private static int countGradeCourses(JsonNode node) {
        if (node == null) {
            return 0;
        }
        JsonNode detailsByTerm = node.get("detailsByTerm");
        if (detailsByTerm == null || !detailsByTerm.isObject()) {
            return 0;
        }
        int total = 0;
        for (Iterator<JsonNode> iterator = detailsByTerm.elements(); iterator.hasNext(); ) {
            JsonNode rows = iterator.next();
            if (rows != null && rows.isArray()) {
                total += rows.size();
            }
        }
        return total;
    }

    private ObjectNode compactAssignmentsNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("termId", node.path("termId").asLong());
        JsonNode items = node.path("items");
        if (items.isArray()) {
            ArrayNode compacted = objectMapper.createArrayNode();
            for (JsonNode item : items) {
                ObjectNode ci = objectMapper.createObjectNode();
                copyTextIfPresent(item, ci, "courseName");
                copyTextIfPresent(item, ci, "title");
                copyTextIfPresent(item, ci, "type");
                if (item.hasNonNull("dueDate")) {
                    copyTextIfPresent(item, ci, "dueDate");
                }
                compacted.add(ci);
            }
            compact.set("items", compacted);
        }
        return compact;
    }

    private ObjectNode compactLoansNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("total", node.path("total").asInt(0));
        JsonNode loans = node.path("loans");
        if (loans.isArray()) {
            compact.set("loans", filterArray(loans, this::compactLoanItemNode));
        }
        return compact;
    }

    private ObjectNode compactLoanItemNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "title");
        copyTextIfPresent(node, compact, "dueDate");
        JsonNode overdue = node.get("isOverdue");
        if (overdue != null && !overdue.isNull()) {
            compact.put("isOverdue", overdue.asBoolean(false));
        }
        JsonNode renewable = node.get("isRenewable");
        if (renewable != null && !renewable.isNull()) {
            compact.put("isRenewable", renewable.asBoolean(false));
        }
        return compact;
    }

    private ObjectNode compactLibraryBookSearchNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyIntIfPresent(node, compact, "total");
        copyIntIfPresent(node, compact, "page");
        copyIntIfPresent(node, compact, "size");
        JsonNode items = node.get("items");
        if (items != null && items.isArray() && !items.isEmpty()) {
            compact.set("items", filterArray(items, this::compactLibraryBookItemNode));
        }
        return compact;
    }

    private ObjectNode compactLibraryBookItemNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "title");
        copyTextIfPresent(node, compact, "author");
        copyTextIfPresent(node, compact, "publication");
        copyTextIfPresent(node, compact, "callNumber");
        copyTextIfPresent(node, compact, "location");
        copyTextIfPresent(node, compact, "status");
        return compact;
    }

    private ObjectNode compactFacilityNode(JsonNode node) {
        ObjectNode compact = objectMapper.createObjectNode();
        copyTextIfPresent(node, compact, "name");
        if (node.hasNonNull("categoryLabel")) {
            compact.put("category", node.get("categoryLabel").asText());
        } else if (node.hasNonNull("category")) {
            compact.put("category", node.get("category").asText());
        }
        copyTextIfPresent(node, compact, "location");
        copyTextIfPresent(node, compact, "phone");
        copyTextIfPresent(node, compact, "extension");
        copyNonEmptyArray(node, compact, "weekdayHours");
        copyNonEmptyArray(node, compact, "weekendHours");
        copyNonEmptyArray(node, compact, "notes");
        return compact;
    }

    private static void copyTextIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull() && !value.asText("").isBlank()) {
            target.put(field, value.asText());
        }
    }

    private static void copyIntIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull() && value.isNumber()) {
            target.put(field, value.asInt());
        }
    }

    private static void copyNonEmptyArray(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isArray() && !value.isEmpty()) {
            target.set(field, value);
        }
    }

    private ArrayNode filterArray(JsonNode source, Function<JsonNode, ObjectNode> mapper) {
        ArrayNode array = objectMapper.createArrayNode();
        if (source == null || !source.isArray()) {
            return array;
        }
        for (Iterator<JsonNode> iterator = source.elements(); iterator.hasNext(); ) {
            array.add(mapper.apply(iterator.next()));
        }
        return array;
    }

    private String requiredArgument(OpenAiToolCall toolCall, String fieldName) {
        String value = optionalArgument(toolCall, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + ": 필수 인자입니다.");
        }
        return value;
    }

    private int requiredIntArgument(OpenAiToolCall toolCall, String fieldName) {
        JsonNode value = arguments(toolCall).get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(fieldName + ": 필수 인자입니다.");
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(fieldName + ": 정수여야 합니다.", exception);
            }
        }
        throw new IllegalArgumentException(fieldName + ": 정수여야 합니다.");
    }

    private String optionalArgument(OpenAiToolCall toolCall, String fieldName) {
        JsonNode arguments = arguments(toolCall);
        JsonNode value = arguments.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private Optional<Integer> optionalIntArgument(OpenAiToolCall toolCall, String fieldName) {
        JsonNode value = arguments(toolCall).get(fieldName);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.canConvertToInt()) {
            return Optional.of(value.asInt());
        }
        if (value.isTextual()) {
            String text = value.asText("").trim();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private JsonNode arguments(OpenAiToolCall toolCall) {
        String rawArguments = toolCall.function() == null ? null : toolCall.function().arguments();
        if (rawArguments == null || rawArguments.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(rawArguments);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("도구 인자를 JSON으로 해석하지 못했습니다.", exception);
        }
    }

    private String toolError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "error", message == null || message.isBlank() ? "도구 실행에 실패했습니다." : message
            ));
        } catch (JsonProcessingException exception) {
            throw new ChatUnavailableException(exception);
        }
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ChatUnavailableException();
        }
        return content.trim();
    }

    private static List<OpenAiToolCall> safeToolCalls(List<OpenAiToolCall> toolCalls) {
        return toolCalls == null ? List.of() : toolCalls;
    }

    /**
     * Catches messages the chatbot can't usefully serve from any of its
     * tools — LMS / 수강신청 / 졸업요건 / 개인정보 — and short-circuits
     * to {@link #SCOPE_GUIDANCE} so the LLM doesn't hallucinate. Note
     * that "성적", "시간표", "GPA" are intentionally **not** in this list:
     * those are now real tools ({@code get_my_schedule},
     * {@code get_my_grades}) and the LLM is allowed to call them. The
     * authenticated-vs-anonymous gating happens inside
     * {@code executeToolCall} which returns {@link #SAINT_SESSION_GUIDANCE}
     * when the chat lacks a student id.
     */
    private static boolean looksLikeOutOfScopeRequest(String message) {
        String normalized = normalize(message);
        return containsAny(normalized, "수강신청", "졸업요건", "개인정보");
    }

    private static boolean looksLikeSecretInput(String message) {
        String normalized = normalize(message);
        return containsAny(normalized, "비밀번호", "password", "쿠키", "cookie", "세션", "session",
                "api key", "apikey", "토큰", "token", "jwt");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
