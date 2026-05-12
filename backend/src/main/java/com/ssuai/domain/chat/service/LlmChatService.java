package com.ssuai.domain.chat.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.ssuai.domain.chat.config.LlmChatProperties;
import com.ssuai.domain.chat.dto.ChatResponse;
import com.ssuai.domain.chat.dto.OpenAiChatCompletionRequest;
import com.ssuai.domain.chat.dto.OpenAiChatCompletionResponse;
import com.ssuai.domain.chat.dto.OpenAiToolCall;
import com.ssuai.domain.chat.service.llm.LlmCompletionRequest;
import com.ssuai.domain.chat.service.llm.LlmCompletionResult;
import com.ssuai.domain.chat.service.llm.LlmPrivacyMode;
import com.ssuai.domain.chat.service.llm.LlmProvider;
import com.ssuai.domain.chat.service.llm.LlmProviderException;
import com.ssuai.domain.campus.dto.CampusFacilityListResponse;
import com.ssuai.domain.campus.dto.CampusFacilityResponse;
import com.ssuai.domain.mcp.tool.CampusMcpTools;
import com.ssuai.domain.mcp.tool.DormMcpTools;
import com.ssuai.domain.mcp.tool.MealMcpTools;
import com.ssuai.domain.meal.dto.MealClosure;
import com.ssuai.domain.meal.dto.MealItem;
import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.dto.WeeklyMealResponse;
import com.ssuai.global.exception.ChatUnavailableException;

@Service
@ConditionalOnExpression("'${ssuai.connector.chat:mock}' == 'llm' or '${ssuai.connector.chat:mock}' == 'openrouter'")
public class LlmChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(LlmChatService.class);

    private static final String SYSTEM_PROMPT = """
            너는 숭실대 학생을 도와주는 ssuAI 챗봇이야.
            말투는 친근한 선배처럼 편하게, 기본은 한국어 존댓말로 답해.
            답변은 보통 1-2문장으로 짧게 하고, 사용자가 자세히 물어보면 더 설명해.

            지금 네가 다룰 수 있는 범위는 공개 데이터야:
            - 학생식당 메뉴
            - 기숙사 식단
            - 캠퍼스 시설 검색
            도구 결과에 없는 내용은 추측하지 마.
            로그인, 성적, 시간표, LMS 과제, u-SAINT, 개인정보는 아직 지원하지 않는다고 말해.
            비밀번호, 학번, 쿠키, 세션, API key 같은 비밀 정보를 요구하지 마.
            사용자가 민감정보를 입력하면 저장하거나 반복하지 말고, 필요한 경우 지우라고 안내해.
            """;

    private static final String SCOPE_GUIDANCE =
            "아직은 로그인 연동이 필요한 학사 정보는 못 가져와요. 지금은 학식, 기숙사 식단, 캠퍼스 시설만 도와줄 수 있어요.";

    private static final String SECRET_GUIDANCE =
            "비밀번호, 학번, 쿠키, 세션, API key 같은 비밀 정보는 입력하지 말아주세요. 지금은 학식, 기숙사 식단, 캠퍼스 시설만 도와줄 수 있어요.";

    private static final int MAX_CHAT_TOOL_FACILITY_RESULTS = 6;

    private final LlmChatProperties properties;
    private final Map<String, LlmProvider> providersByName;
    private final ObjectMapper objectMapper;
    private final MealMcpTools mealMcpTools;
    private final DormMcpTools dormMcpTools;
    private final CampusMcpTools campusMcpTools;

    public LlmChatService(
            LlmChatProperties properties,
            List<LlmProvider> providers,
            ObjectMapper objectMapper,
            MealMcpTools mealMcpTools,
            DormMcpTools dormMcpTools,
            CampusMcpTools campusMcpTools
    ) {
        this.properties = properties;
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::name, Function.identity()));
        this.objectMapper = objectMapper;
        this.mealMcpTools = mealMcpTools;
        this.dormMcpTools = dormMcpTools;
        this.campusMcpTools = campusMcpTools;
    }

    @Override
    public ChatResponse reply(String conversationId, String message) {
        if (looksLikeSecretInput(message)) {
            return new ChatResponse(conversationId, SECRET_GUIDANCE);
        }
        if (looksLikePrivateAcademicRequest(message)) {
            return new ChatResponse(conversationId, SCOPE_GUIDANCE);
        }

        Instant startedAt = Instant.now();
        int messageLength = message == null ? 0 : message.length();
        log.info("chat reply started: conversationId={} messageLength={}", conversationId, messageLength);

        try {
            ChatResponse response = callLlm(conversationId, message, LlmPrivacyMode.PUBLIC);
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info("chat reply completed: conversationId={} messageLength={} latencyMs={}",
                    conversationId, messageLength, latencyMs);
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

    private ChatResponse callLlm(String conversationId, String message, LlmPrivacyMode privacyMode) {
        List<OpenAiChatCompletionRequest.Message> baseMessages = List.of(
                OpenAiChatCompletionRequest.systemMessage(SYSTEM_PROMPT),
                OpenAiChatCompletionRequest.userMessage(message)
        );

        LlmCompletionResult firstResult = completeAcrossProviders(new LlmCompletionRequest(
                privacyMode,
                baseMessages,
                tools(),
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
        for (int index = 0; index < toolCalls.size(); index++) {
            OpenAiToolCall toolCall = toolCalls.get(index);
            String toolCallId = toolCall.id() == null || toolCall.id().isBlank()
                    ? "call_" + index
                    : toolCall.id();
            messages.add(OpenAiChatCompletionRequest.toolResultMessage(toolCallId, executeToolCall(toolCall)));
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

    private List<OpenAiChatCompletionRequest.Tool> tools() {
        return List.of(
                tool(
                        "get_today_meal",
                        "오늘 숭실대학교 학생식당 메뉴를 조회합니다.",
                        objectParameters(Map.of(), List.of())
                ),
                tool(
                        "get_meal_by_date",
                        "지정한 날짜의 숭실대학교 학생식당 메뉴를 조회합니다.",
                        objectParameters(
                                Map.<String, Object>of("date", property("string", "yyyy-MM-dd 형식의 날짜")),
                                List.of("date")
                        )
                ),
                tool(
                        "get_dorm_weekly_meal",
                        "이번 주 숭실대학교 기숙사 식단을 조회합니다.",
                        objectParameters(Map.of(), List.of())
                ),
                tool(
                        "search_campus_facilities",
                        "숭실대학교 캠퍼스 시설을 검색합니다. 식당, 카페, 편의점, 복사/출력 시설 등을 찾습니다.",
                        objectParameters(
                                Map.<String, Object>of("query", property("string", "검색어. 비우면 전체 시설 목록.")),
                                List.of("query")
                        )
                )
        );
    }

    private static OpenAiChatCompletionRequest.Tool tool(
            String name,
            String description,
            Map<String, Object> parameters
    ) {
        return new OpenAiChatCompletionRequest.Tool(
                "function",
                new OpenAiChatCompletionRequest.FunctionDefinition(name, description, parameters)
        );
    }

    private static Map<String, Object> objectParameters(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private String executeToolCall(OpenAiToolCall toolCall) {
        String toolName = toolCall.function() == null ? "" : toolCall.function().name();
        try {
            return switch (toolName) {
                case "get_today_meal" -> toolResult(mealMcpTools.getTodayMeal());
                case "get_meal_by_date" -> toolResult(mealMcpTools.getMealByDate(requiredArgument(toolCall, "date")));
                case "get_dorm_weekly_meal" -> toolResult(dormMcpTools.getDormWeeklyMeal());
                case "search_campus_facilities" -> {
                    String query = optionalArgument(toolCall, "query").trim();
                    if (query.isBlank()) {
                        yield toolError("시설 검색은 검색어가 필요합니다. 예: 카페, 복사, 편의점, 학생식당.");
                    }
                    yield toolResult(campusMcpTools.searchCampusFacilities(query));
                }
                default -> toolError("지원하지 않는 도구입니다: " + toolName);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return toolError(exception.getMessage());
        }
    }

    private String requiredArgument(OpenAiToolCall toolCall, String fieldName) {
        String value = optionalArgument(toolCall, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + ": 필수 인자입니다.");
        }
        return value;
    }

    private String optionalArgument(OpenAiToolCall toolCall, String fieldName) {
        JsonNode arguments = arguments(toolCall);
        JsonNode value = arguments.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
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

    private String toolResult(Object value) {
        try {
            return objectMapper.writeValueAsString(compactToolValue(value));
        } catch (JsonProcessingException exception) {
            throw new ChatUnavailableException(exception);
        }
    }

    private static Object compactToolValue(Object value) {
        if (value instanceof WeeklyMealResponse response) {
            return compactWeeklyMealResponse(response);
        }
        if (value instanceof MealResponse response) {
            return compactMealResponse(response);
        }
        if (value instanceof CampusFacilityListResponse response) {
            return compactFacilityListResponse(response);
        }
        return value;
    }

    private static Map<String, Object> compactWeeklyMealResponse(WeeklyMealResponse response) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("startDate", response.startDate());
        compact.put("endDate", response.endDate());
        compact.put("days", response.days().stream()
                .map(LlmChatService::compactMealResponse)
                .toList());
        return compact;
    }

    private static Map<String, Object> compactMealResponse(MealResponse response) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("date", response.date());
        compact.put("meals", response.meals().stream()
                .map(LlmChatService::compactMealItem)
                .toList());
        if (!response.closures().isEmpty()) {
            compact.put("closures", response.closures().stream()
                    .map(LlmChatService::compactMealClosure)
                    .toList());
        }
        return compact;
    }

    private static Map<String, Object> compactMealItem(MealItem item) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("restaurant", item.restaurant());
        compact.put("type", item.type());
        putIfPresent(compact, "corner", item.corner());
        compact.put("menu", item.menu());
        return compact;
    }

    private static Map<String, Object> compactMealClosure(MealClosure closure) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("restaurant", closure.restaurant());
        compact.put("reason", closure.reason());
        return compact;
    }

    private static Map<String, Object> compactFacilityListResponse(CampusFacilityListResponse response) {
        List<CampusFacilityResponse> facilities = response.facilities();
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("resultCount", facilities.size());
        compact.put("truncated", facilities.size() > MAX_CHAT_TOOL_FACILITY_RESULTS);
        compact.put("facilities", facilities.stream()
                .limit(MAX_CHAT_TOOL_FACILITY_RESULTS)
                .map(LlmChatService::compactFacility)
                .toList());
        return compact;
    }

    private static Map<String, Object> compactFacility(CampusFacilityResponse facility) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("name", facility.name());
        compact.put("category", facility.categoryLabel());
        compact.put("location", facility.location());
        putIfPresent(compact, "phone", facility.phone());
        putIfPresent(compact, "extension", facility.extension());
        putIfNotEmpty(compact, "weekdayHours", facility.weekdayHours());
        putIfNotEmpty(compact, "weekendHours", facility.weekendHours());
        putIfNotEmpty(compact, "notes", facility.notes());
        return compact;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static void putIfNotEmpty(Map<String, Object> target, String key, List<String> value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
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

    private static boolean looksLikePrivateAcademicRequest(String message) {
        String normalized = normalize(message);
        return containsAny(normalized, "성적", "학점", "gpa", "시간표", "lms", "과제", "usaint", "u-saint",
                "유세인트", "로그인", "개인정보", "수강신청", "졸업요건");
    }

    private static boolean looksLikeSecretInput(String message) {
        String normalized = normalize(message);
        return containsAny(normalized, "비밀번호", "password", "학번", "쿠키", "cookie", "세션", "session",
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
