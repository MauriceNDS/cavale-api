package com.cavale.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the coach tools with the MCP server (the starter picks up any
 * ToolCallbackProvider bean and exposes its tools over /mcp).
 */
@Configuration
public class McpConfig {

    @Bean
    ToolCallbackProvider coachToolCallbacks(CoachTools coachTools) {
        return MethodToolCallbackProvider.builder().toolObjects(coachTools).build();
    }
}
