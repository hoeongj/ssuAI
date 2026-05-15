package com.ssuai.domain.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ssuai.domain.mcp.tool.CampusMcpTools;
import com.ssuai.domain.mcp.tool.DormMcpTools;
import com.ssuai.domain.mcp.tool.LibrarySeatMcpTool;
import com.ssuai.domain.mcp.tool.MealMcpTools;

@Configuration
class McpServerConfig {

    @Bean
    ToolCallbackProvider ssuaiMcpTools(
            MealMcpTools mealMcpTools,
            DormMcpTools dormMcpTools,
            CampusMcpTools campusMcpTools,
            LibrarySeatMcpTool libraryMcpTool
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mealMcpTools, dormMcpTools, campusMcpTools, libraryMcpTool)
                .build();
    }
}
