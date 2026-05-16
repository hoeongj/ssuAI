package com.ssuai.domain.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end check that the chatbot's "talk to my own MCP server over SSE" claim
 * (ADR 0010) is actually wired: the running Spring Boot process must accept a
 * Spring AI MCP client connection on its own /sse endpoint, expose every tool
 * currently bundled in the server, and complete a real tool round-trip without
 * the in-process bean shortcut.
 *
 * The MCP client is built manually here rather than via auto-config to avoid a
 * Spring bean-vs-Tomcat startup race: the auto-configured client would try to
 * initialize during context refresh, before Tomcat has bound the random port.
 * Manual construction in the test body lets us connect after {@code @LocalServerPort}
 * is known. The auto-config wiring itself is exercised by {@code LlmChatServiceTests}
 * via constructor injection of {@code List<McpSyncClient>}.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpSelfDogfoodTests {

    @LocalServerPort
    private int serverPort;

    @Test
    void clientCanListEveryToolExposedByServer() {
        try (McpSyncClient client = openClient()) {
            client.initialize();

            McpSchema.ListToolsResult result = client.listTools();

            assertThat(result.tools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder(
                            "get_today_meal",
                            "get_meal_by_date",
                            "get_dorm_weekly_meal",
                            "search_campus_facilities",
                            "get_library_seat_status",
                            "search_library_book",
                            "get_my_schedule",
                            "get_my_grades"
                    );
        }
    }

    @Test
    void clientCanCallLibrarySeatStatusOverSse() {
        try (McpSyncClient client = openClient()) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(
                            "get_library_seat_status",
                            Map.of("floor", 4)));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String text = result.content().stream()
                    .filter(content -> content instanceof McpSchema.TextContent)
                    .map(content -> ((McpSchema.TextContent) content).text())
                    .findFirst()
                    .orElseThrow();
            assertThat(text)
                    .contains("\"floor\"")
                    .contains("\"availableSeats\"")
                    .contains("\"zones\"");
        }
    }

    @Test
    void clientCanCallTodayMealOverSse() {
        try (McpSyncClient client = openClient()) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("get_today_meal", Map.of()));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String text = result.content().stream()
                    .filter(content -> content instanceof McpSchema.TextContent)
                    .map(content -> ((McpSchema.TextContent) content).text())
                    .findFirst()
                    .orElseThrow();
            assertThat(text)
                    .contains("\"date\"")
                    .contains("\"meals\"");
        }
    }

    @Test
    void clientCanCallLibraryBookSearchOverSse() {
        try (McpSyncClient client = openClient()) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(
                            "search_library_book",
                            Map.of("query", "파이썬")));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String text = result.content().stream()
                    .filter(content -> content instanceof McpSchema.TextContent)
                    .map(content -> ((McpSchema.TextContent) content).text())
                    .findFirst()
                    .orElseThrow();
            assertThat(text)
                    .contains("\"total\"")
                    .contains("\"items\"")
                    .contains("\"title\"");
        }
    }

    @Test
    void clientCanCallFacilitySearchOverSse() {
        try (McpSyncClient client = openClient()) {
            client.initialize();

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(
                            "search_campus_facilities",
                            Map.of("query", "카페")));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String text = result.content().stream()
                    .filter(content -> content instanceof McpSchema.TextContent)
                    .map(content -> ((McpSchema.TextContent) content).text())
                    .findFirst()
                    .orElseThrow();
            assertThat(text).contains("\"facilities\"");
        }
    }

    private McpSyncClient openClient() {
        return McpClient.sync(
                        HttpClientSseClientTransport.builder("http://localhost:" + serverPort)
                                .sseEndpoint("/sse")
                                .connectTimeout(Duration.ofSeconds(5))
                                .build())
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }
}
