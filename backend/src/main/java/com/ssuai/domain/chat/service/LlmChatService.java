package com.ssuai.domain.chat.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
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
import com.ssuai.global.exception.ChatUnavailableException;

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
            - 로그인, 성적, 시간표, LMS 과제, u-SAINT, 개인정보는 아직 지원 안 함.
            - 비밀번호, 학번, 쿠키, 세션, API key 같은 비밀 정보는 요구하지도 받지도
              마. 사용자가 입력하면 저장/반복하지 말고 지우라고 안내해.
            """;

    private static final String SCOPE_GUIDANCE =
            "아직은 로그인 연동이 필요한 학사 정보는 못 가져와요. 지금은 학식, 기숙사 식단, 캠퍼스 시설만 도와줄 수 있어요.";

    private static final String SECRET_GUIDANCE =
            "비밀번호, 학번, 쿠키, 세션, API key 같은 비밀 정보는 입력하지 말아주세요. 지금은 학식, 기숙사 식단, 캠퍼스 시설만 도와줄 수 있어요.";

    private static final int MAX_CHAT_TOOL_FACILITY_RESULTS = 10;
    private static final int MAX_TOOL_CONTENT_BYTES = 8 * 1024;
    private static final String TOOL_TRUNCATION_MARKER = "...[truncated]";

    private final LlmChatProperties properties;
    private final Map<String, LlmProvider> providersByName;
    private final ObjectMapper objectMapper;
    private volatile List<OpenAiChatCompletionRequest.Tool> cachedChatTools;

    private final List<McpSyncClient> mcpClients;

    public LlmChatService(
            LlmChatProperties properties,
            List<LlmProvider> providers,
            ObjectMapper objectMapper,
            @Lazy List<McpSyncClient> mcpClients
    ) {
        this.properties = properties;
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::name, Function.identity()));
        this.objectMapper = objectMapper;
        this.mcpClients = mcpClients;
    }

    private McpSyncClient mcpClient() {
        if (mcpClients == null || mcpClients.isEmpty()) {
            throw new IllegalStateException(
                    "LLM chat mode requires at least one Spring AI MCP client connection (spring.ai.mcp.client.sse.connections.*).");
        }
        return mcpClients.get(0);
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
        for (int index = 0; index < toolCalls.size(); index++) {
            OpenAiToolCall toolCall = toolCalls.get(index);
            String toolCallId = toolCall.id() == null || toolCall.id().isBlank()
                    ? "call_" + index
                    : toolCall.id();
            String content = index < maxToolCalls()
                    ? executeToolCall(toolCall)
                    : toolError("한 번에 처리할 수 있는 도구 호출 수를 초과했습니다. 한두 가지씩 나눠서 물어봐 주세요.");
            messages.add(OpenAiChatCompletionRequest.toolResultMessage(toolCallId, content));
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

    private String executeToolCall(OpenAiToolCall toolCall) {
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
                default -> toolError("지원하지 않는 도구입니다: " + toolName);
            };
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return toolError(exception.getMessage());
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

    private String compactAndCap(String toolName, String rawJsonText) {
        try {
            JsonNode tree = objectMapper.readTree(rawJsonText);
            JsonNode compacted = switch (toolName) {
                case "get_today_meal", "get_meal_by_date" -> compactMealNode(tree);
                case "get_dorm_weekly_meal" -> compactWeeklyMealNode(tree);
                case "search_campus_facilities" -> compactFacilityListNode(tree);
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
