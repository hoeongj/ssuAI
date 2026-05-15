package com.ssuai.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import com.ssuai.domain.chat.config.ChatMemoryProperties;
import com.ssuai.domain.chat.config.LlmChatProperties;
import com.ssuai.domain.chat.dto.ChatResponse;
import com.ssuai.domain.chat.dto.OpenAiChatCompletionRequest;
import com.ssuai.domain.chat.dto.OpenAiChatCompletionResponse;
import com.ssuai.domain.chat.dto.OpenAiToolCall;
import com.ssuai.domain.chat.memory.ChatConversationStore;
import com.ssuai.domain.chat.service.llm.LlmCompletionRequest;
import com.ssuai.domain.chat.service.llm.LlmCompletionResult;
import com.ssuai.domain.chat.service.llm.LlmPrivacyMode;
import com.ssuai.domain.chat.service.llm.LlmProvider;
import com.ssuai.domain.chat.service.llm.LlmProviderException;

class LlmChatServiceTests {

    private static final String FACILITY_JSON_LIBRARY_CAFE = """
            {"facilities":[{
                "id":"library-soongsil-maru",
                "name":"도서관 커피점",
                "category":"CAFE",
                "categoryLabel":"카페",
                "location":"도서관 6층",
                "fax":null,
                "weekdayHours":["11:00~17:30"],
                "weekendHours":["휴무"],
                "notes":["공개 운영시간만 제공"],
                "aliases":["숭실마루","도서관 카페"]
            }]}
            """;

    private static final String FACILITY_JSON_EMPTY = "{\"facilities\":[]}";

    private final McpSyncClient mcpClient = mock(McpSyncClient.class);

    @Test
    void fallsBackAcrossProvidersWhenFirstProviderRateLimitIsExceeded() {
        FakeProvider gemini = new FakeProvider("gemini")
                .fail(new LlmProviderException("gemini", "quota", 429, "rate limit", true, null));
        FakeProvider groq = new FakeProvider("groq")
                .reply("groq-model", "fallback reply");
        LlmChatService chatService = chatService(List.of(gemini, groq), List.of("gemini", "groq"));

        ChatResponse response = chatService.reply("c-test", "오늘 학식 뭐야?");

        assertThat(response.reply()).isEqualTo("fallback reply");
        assertThat(gemini.callCount()).isEqualTo(1);
        assertThat(groq.callCount()).isEqualTo(1);
    }

    @Test
    void followsConfiguredProviderOrder() {
        FakeProvider gemini = new FakeProvider("gemini")
                .reply("gemini-model", "gemini reply");
        FakeProvider groq = new FakeProvider("groq")
                .reply("groq-model", "groq reply");
        LlmChatService chatService = chatService(List.of(gemini, groq), List.of("groq", "gemini"));

        ChatResponse response = chatService.reply("c-test", "오늘 학식 뭐야?");

        assertThat(response.reply()).isEqualTo("groq reply");
        assertThat(gemini.callCount()).isZero();
        assertThat(groq.callCount()).isEqualTo(1);
    }

    @Test
    void publicRequestFallsBackToPrivateProviderPoolWhenPublicProvidersAreExhausted() {
        FakeProvider publicProvider = new FakeProvider("public-provider")
                .fail(new LlmProviderException("public-provider", "quota", 429, "rate limit", true, null));
        FakeProvider privateProvider = new FakeProvider("private-provider")
                .reply("private-model", "private fallback reply");
        LlmChatService chatService = chatService(
                List.of(publicProvider, privateProvider),
                List.of("public-provider"),
                List.of("private-provider"),
                0
        );

        ChatResponse response = chatService.reply("c-test", "오늘 학식 뭐야?");

        assertThat(response.reply()).isEqualTo("private fallback reply");
        assertThat(publicProvider.callCount()).isEqualTo(1);
        assertThat(privateProvider.callCount()).isEqualTo(1);
        assertThat(privateProvider.lastPrivacyMode()).isEqualTo(LlmPrivacyMode.PRIVATE);
    }

    @Test
    void verificationPassRetriesProviderOrderFromTheBeginning() {
        FakeProvider gemini = new FakeProvider("gemini")
                .fail(new LlmProviderException("gemini", "quota", 429, "rate limit", true, null))
                .reply("gemini-model", "recovered reply");
        FakeProvider groq = new FakeProvider("groq")
                .fail(new LlmProviderException("groq", "quota", 429, "rate limit", true, null));
        LlmChatService chatService = chatService(
                List.of(gemini, groq),
                List.of("gemini", "groq"),
                List.of("missing-private-provider"),
                1
        );

        ChatResponse response = chatService.reply("c-test", "오늘 학식 뭐야?");

        assertThat(response.reply()).isEqualTo("recovered reply");
        assertThat(gemini.callCount()).isEqualTo(2);
        assertThat(groq.callCount()).isEqualTo(1);
    }

    @Test
    void skipsProvidersWithoutConfiguredApiKeys() {
        FakeProvider unconfigured = new FakeProvider("missing-key")
                .unconfigured();
        FakeProvider configured = new FakeProvider("configured")
                .reply("configured-model", "configured reply");
        LlmChatService chatService = chatService(
                List.of(unconfigured, configured),
                List.of("missing-key", "configured"),
                List.of(),
                0
        );

        ChatResponse response = chatService.reply("c-test", "오늘 학식 뭐야?");

        assertThat(response.reply()).isEqualTo("configured reply");
        assertThat(unconfigured.callCount()).isZero();
        assertThat(configured.callCount()).isEqualTo(1);
    }

    @Test
    void limitsConfiguredProviderAttempts() {
        FakeProvider first = new FakeProvider("first")
                .fail(new LlmProviderException("first", "quota", 429, "rate limit", true, null));
        FakeProvider second = new FakeProvider("second")
                .fail(new LlmProviderException("second", "quota", 429, "rate limit", true, null));
        FakeProvider third = new FakeProvider("third")
                .reply("third-model", "should not be called");
        LlmChatProperties properties = properties(List.of("first", "second", "third"), List.of(), 0);
        properties.setMaxProviderAttempts(2);
        LlmChatService chatService = chatService(List.of(first, second, third), properties);

        try {
            chatService.reply("c-test", "오늘 학식 뭐야?");
        } catch (RuntimeException ignored) {
            // Expected: the first two configured providers are exhausted.
        }

        assertThat(first.callCount()).isEqualTo(1);
        assertThat(second.callCount()).isEqualTo(1);
        assertThat(third.callCount()).isZero();
    }

    @Test
    void facilityToolResultIsCompactedBeforeFinalCompletion() {
        when(mcpClient.callTool(argThat(named("search_campus_facilities"))))
                .thenReturn(toolTextResult(FACILITY_JSON_LIBRARY_CAFE));
        FakeProvider provider = new FakeProvider("gemini")
                .toolCall("gemini-model", new OpenAiToolCall(
                        "call-1",
                        "function",
                        new OpenAiToolCall.FunctionCall("search_campus_facilities", "{\"query\":\"카페\"}")
                ))
                .reply("gemini-model", "도서관 커피점은 도서관 6층에 있어요.");
        LlmChatService chatService = chatService(List.of(provider), List.of("gemini"), List.of(), 0);

        ChatResponse response = chatService.reply("c-test", "카페 어디 있어?");

        assertThat(response.reply()).isEqualTo("도서관 커피점은 도서관 6층에 있어요.");
        assertThat(provider.callCount()).isEqualTo(2);
        verify(mcpClient, times(1))
                .callTool(argThat(request ->
                        "search_campus_facilities".equals(request.name())
                                && "카페".equals(request.arguments().get("query"))));
        String toolContent = provider.request(1).messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .findFirst()
                .orElseThrow()
                .content();
        assertThat(toolContent)
                .contains("\"resultCount\":1")
                .contains("\"name\":\"도서관 커피점\"")
                .doesNotContain("aliases")
                .doesNotContain("fax")
                .doesNotContain("\"id\":");
    }

    @Test
    void facilityToolRequiresQueryBeforeCallingMcp() {
        FakeProvider provider = new FakeProvider("gemini")
                .toolCall("gemini-model", new OpenAiToolCall(
                        "call-1",
                        "function",
                        new OpenAiToolCall.FunctionCall("search_campus_facilities", "{}")
                ))
                .reply("gemini-model", "시설 종류를 한 단어로 물어봐 주세요.");
        LlmChatService chatService = chatService(List.of(provider), List.of("gemini"), List.of(), 0);

        ChatResponse response = chatService.reply("c-test", "캠퍼스 시설 알려줘");

        assertThat(response.reply()).isEqualTo("시설 종류를 한 단어로 물어봐 주세요.");
        verify(mcpClient, never()).callTool(any());
        String toolContent = provider.request(1).messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .findFirst()
                .orElseThrow()
                .content();
        assertThat(toolContent).contains("검색어");
    }

    @Test
    void limitsExecutedToolCallsBeforeFinalCompletion() {
        when(mcpClient.callTool(argThat(named("search_campus_facilities"))))
                .thenReturn(toolTextResult(FACILITY_JSON_EMPTY));
        FakeProvider provider = new FakeProvider("gemini")
                .toolCalls("gemini-model", List.of(
                        new OpenAiToolCall(
                                "call-1",
                                "function",
                                new OpenAiToolCall.FunctionCall("search_campus_facilities", "{\"query\":\"카페\"}")
                        ),
                        new OpenAiToolCall(
                                "call-2",
                                "function",
                                new OpenAiToolCall.FunctionCall("get_dorm_weekly_meal", "{}")
                        )
                ))
                .reply("gemini-model", "카페 검색 결과만 먼저 확인했어요.");
        LlmChatProperties properties = properties(List.of("gemini"), List.of(), 0);
        properties.setMaxToolCalls(1);
        LlmChatService chatService = chatService(List.of(provider), properties);

        ChatResponse response = chatService.reply("c-test", "카페랑 기숙사 식단 알려줘");

        assertThat(response.reply()).isEqualTo("카페 검색 결과만 먼저 확인했어요.");
        verify(mcpClient, never())
                .callTool(argThat(named("get_dorm_weekly_meal")));
        verify(mcpClient, times(1)).callTool(any());
        List<String> toolContents = provider.request(1).messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .map(OpenAiChatCompletionRequest.Message::content)
                .toList();
        assertThat(toolContents).hasSize(2);
        assertThat(toolContents.get(0)).contains("\"resultCount\":0");
        assertThat(toolContents.get(1)).contains("도구 호출 수를 초과");
    }

    @Test
    void privateAcademicRequestReturnsScopeGuidanceWithoutCallingProviders() {
        FakeProvider gemini = new FakeProvider("gemini")
                .reply("gemini-model", "should not be called");
        LlmChatService chatService = chatService(List.of(gemini), List.of("gemini"));

        ChatResponse response = chatService.reply("c-test", "내 LMS 과제 알려줘");

        assertThat(response.reply()).contains("아직은 로그인 연동이 필요한 학사 정보");
        assertThat(gemini.callCount()).isZero();
        verify(mcpClient, never()).callTool(any());
    }

    @Test
    void secretLikeInputReturnsGuidanceWithoutCallingProviders() {
        FakeProvider gemini = new FakeProvider("gemini")
                .reply("gemini-model", "should not be called");
        LlmChatService chatService = chatService(List.of(gemini), List.of("gemini"));

        ChatResponse response = chatService.reply("c-test", "내 비밀번호는 1234야");

        assertThat(response.reply()).contains("비밀번호");
        assertThat(gemini.callCount()).isZero();
        verify(mcpClient, never()).callTool(any());
    }

    @Test
    void secondTurnRequestIncludesPriorUserAndAssistantHistory() {
        FakeProvider gemini = new FakeProvider("gemini")
                .reply("gemini-model", "학생식당과 숭실도담식당 중 어디가 궁금해?")
                .reply("gemini-model", "숭실도담식당은 오늘 후라이드치킨이 나와요.");
        LlmChatService chatService = chatService(List.of(gemini), List.of("gemini"));

        chatService.reply("c-multi", "오늘 학식 뭐야?");
        ChatResponse second = chatService.reply("c-multi", "도담");

        assertThat(second.reply()).isEqualTo("숭실도담식당은 오늘 후라이드치킨이 나와요.");
        assertThat(gemini.callCount()).isEqualTo(2);

        List<OpenAiChatCompletionRequest.Message> secondRequestMessages = gemini.request(1).messages();
        assertThat(secondRequestMessages).extracting(OpenAiChatCompletionRequest.Message::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(secondRequestMessages.get(1).content()).isEqualTo("오늘 학식 뭐야?");
        assertThat(secondRequestMessages.get(2).content()).isEqualTo("학생식당과 숭실도담식당 중 어디가 궁금해?");
        assertThat(secondRequestMessages.get(3).content()).isEqualTo("도담");
    }

    @Test
    void historyIsScopedPerConversationId() {
        FakeProvider gemini = new FakeProvider("gemini")
                .reply("gemini-model", "first conv reply")
                .reply("gemini-model", "second conv reply");
        LlmChatService chatService = chatService(List.of(gemini), List.of("gemini"));

        chatService.reply("c-one", "첫 대화");
        chatService.reply("c-two", "다른 대화");

        List<OpenAiChatCompletionRequest.Message> secondRequestMessages = gemini.request(1).messages();
        assertThat(secondRequestMessages).extracting(OpenAiChatCompletionRequest.Message::role)
                .containsExactly("system", "user");
        assertThat(secondRequestMessages.get(1).content()).isEqualTo("다른 대화");
    }

    @Test
    void secretLikeInputDoesNotPollutateHistory() {
        FakeProvider gemini = new FakeProvider("gemini")
                .reply("gemini-model", "직전 메시지에 비밀번호가 있었더라도 잊고 학식만 안내할게.");
        LlmChatService chatService = chatService(List.of(gemini), List.of("gemini"));

        chatService.reply("c-secret", "내 비밀번호는 1234야");
        ChatResponse second = chatService.reply("c-secret", "오늘 학식 뭐야?");

        assertThat(second.reply()).isEqualTo("직전 메시지에 비밀번호가 있었더라도 잊고 학식만 안내할게.");
        List<OpenAiChatCompletionRequest.Message> secondRequestMessages = gemini.request(0).messages();
        assertThat(secondRequestMessages).extracting(OpenAiChatCompletionRequest.Message::content)
                .doesNotContain("내 비밀번호는 1234야");
        assertThat(secondRequestMessages).extracting(OpenAiChatCompletionRequest.Message::content)
                .doesNotContain("비밀번호");
    }

    private static org.mockito.ArgumentMatcher<McpSchema.CallToolRequest> named(String toolName) {
        return request -> request != null && toolName.equals(request.name());
    }

    private static McpSchema.CallToolResult toolTextResult(String text) {
        return McpSchema.CallToolResult.builder()
                .content(List.<McpSchema.Content>of(new McpSchema.TextContent(text)))
                .isError(Boolean.FALSE)
                .build();
    }

    private LlmChatService chatService(List<LlmProvider> providers, List<String> providerOrder) {
        return chatService(providers, providerOrder, List.of("missing-private-provider"), 1);
    }

    private LlmChatService chatService(
            List<LlmProvider> providers,
            List<String> providerOrder,
            List<String> privateProviderOrder,
            int availabilityVerificationPasses
    ) {
        LlmChatProperties properties = new LlmChatProperties();
        properties.setProviderOrder(providerOrder);
        properties.setPrivateProviderOrder(privateProviderOrder);
        properties.setAvailabilityVerificationPasses(availabilityVerificationPasses);
        return chatService(providers, properties);
    }

    private LlmChatProperties properties(
            List<String> providerOrder,
            List<String> privateProviderOrder,
            int availabilityVerificationPasses
    ) {
        LlmChatProperties properties = new LlmChatProperties();
        properties.setProviderOrder(providerOrder);
        properties.setPrivateProviderOrder(privateProviderOrder);
        properties.setAvailabilityVerificationPasses(availabilityVerificationPasses);
        return properties;
    }

    private LlmChatService chatService(List<LlmProvider> providers, LlmChatProperties properties) {
        stubMcpToolDiscovery();
        return new LlmChatService(
                properties,
                providers,
                new ObjectMapper(),
                new ChatConversationStore(new ChatMemoryProperties()),
                List.of(mcpClient)
        );
    }

    private void stubMcpToolDiscovery() {
        doReturn(true).when(mcpClient).isInitialized();
        doReturn(canonicalListToolsResult()).when(mcpClient).listTools();
    }

    private static McpSchema.ListToolsResult canonicalListToolsResult() {
        return new McpSchema.ListToolsResult(
                List.of(
                        canonicalTool("get_today_meal",
                                "오늘 숭실대학교 학생식당 메뉴를 조회합니다.",
                                emptyObjectSchema()),
                        canonicalTool("get_meal_by_date",
                                "지정한 날짜의 숭실대학교 학생식당 메뉴를 조회합니다.",
                                requiredStringSchema("date", "yyyy-MM-dd 형식의 날짜")),
                        canonicalTool("get_dorm_weekly_meal",
                                "이번 주 숭실대학교 기숙사 식단을 조회합니다.",
                                emptyObjectSchema()),
                        canonicalTool("search_campus_facilities",
                                "숭실대학교 캠퍼스 시설을 검색합니다.",
                                requiredStringSchema("query", "검색어. 비워두지 마세요.")),
                        canonicalTool("get_library_seat_status",
                                "숭실대학교 중앙도서관의 좌석 현황을 층별로 조회합니다.",
                                requiredIntegerSchema("floor", "도서관 층 코드 (-1, 1, 2, 3, 4, 5, 6)")),
                        canonicalTool("search_library_book",
                                "숭실대학교 중앙도서관 소장 도서를 키워드로 검색합니다.",
                                requiredStringSchema("query", "검색어 (제목/저자/출판 키워드, 1~64자)"))
                ),
                null
        );
    }

    private static McpSchema.Tool canonicalTool(String name, String description, McpSchema.JsonSchema schema) {
        return new McpSchema.Tool(name, null, description, schema, null, null, null);
    }

    private static McpSchema.JsonSchema emptyObjectSchema() {
        return new McpSchema.JsonSchema("object", Map.of(), List.of(), Boolean.FALSE, null, null);
    }

    private static McpSchema.JsonSchema requiredStringSchema(String property, String description) {
        return new McpSchema.JsonSchema(
                "object",
                Map.of(property, Map.of("type", "string", "description", description)),
                List.of(property),
                Boolean.FALSE,
                null,
                null
        );
    }

    private static McpSchema.JsonSchema requiredIntegerSchema(String property, String description) {
        return new McpSchema.JsonSchema(
                "object",
                Map.of(property, Map.of("type", "integer", "description", description)),
                List.of(property),
                Boolean.FALSE,
                null,
                null
        );
    }

    private static final class FakeProvider implements LlmProvider {

        private final String name;
        private final Queue<Object> outcomes = new ArrayDeque<>();
        private final List<LlmPrivacyMode> privacyModes = new ArrayList<>();
        private final List<LlmCompletionRequest> requests = new ArrayList<>();
        private boolean configured = true;
        private int callCount;

        private FakeProvider(String name) {
            this.name = name;
        }

        private FakeProvider reply(String model, String content) {
            outcomes.add(new LlmCompletionResult(
                    name,
                    model,
                    new OpenAiChatCompletionResponse.Message("assistant", content, List.of())
            ));
            return this;
        }

        private FakeProvider toolCall(String model, OpenAiToolCall toolCall) {
            return toolCalls(model, List.of(toolCall));
        }

        private FakeProvider toolCalls(String model, List<OpenAiToolCall> toolCalls) {
            outcomes.add(new LlmCompletionResult(
                    name,
                    model,
                    new OpenAiChatCompletionResponse.Message("assistant", null, toolCalls)
            ));
            return this;
        }

        private FakeProvider fail(LlmProviderException exception) {
            outcomes.add(exception);
            return this;
        }

        private FakeProvider unconfigured() {
            configured = false;
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public LlmCompletionResult complete(LlmCompletionRequest request) {
            callCount++;
            privacyModes.add(request.privacyMode());
            requests.add(request);
            Object outcome = outcomes.remove();
            if (outcome instanceof LlmProviderException exception) {
                throw exception;
            }
            return (LlmCompletionResult) outcome;
        }

        private int callCount() {
            return callCount;
        }

        private LlmPrivacyMode lastPrivacyMode() {
            return privacyModes.get(privacyModes.size() - 1);
        }

        private LlmCompletionRequest request(int index) {
            return requests.get(index);
        }
    }
}
