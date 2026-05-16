package com.ssuai.domain.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ssuai.domain.mcp.tool.CampusMcpTools;
import com.ssuai.domain.mcp.tool.DormMcpTools;
import com.ssuai.domain.mcp.tool.LibraryBookMcpTool;
import com.ssuai.domain.mcp.tool.LibrarySeatMcpTool;
import com.ssuai.domain.mcp.tool.MealMcpTools;
import com.ssuai.domain.mcp.tool.SaintGradesMcpTool;
import com.ssuai.domain.mcp.tool.SaintScheduleMcpTool;

@Configuration
class McpServerConfig {

    @Bean
    ToolCallbackProvider ssuaiMcpTools(
            MealMcpTools mealMcpTools,
            DormMcpTools dormMcpTools,
            CampusMcpTools campusMcpTools,
            LibrarySeatMcpTool libraryMcpTool,
            LibraryBookMcpTool libraryBookMcpTool,
            SaintScheduleMcpTool saintScheduleMcpTool,
            SaintGradesMcpTool saintGradesMcpTool
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        mealMcpTools,
                        dormMcpTools,
                        campusMcpTools,
                        libraryMcpTool,
                        libraryBookMcpTool,
                        saintScheduleMcpTool,
                        saintGradesMcpTool)
                .build();
    }
}
