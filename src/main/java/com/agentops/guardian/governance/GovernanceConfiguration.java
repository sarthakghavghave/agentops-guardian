package com.agentops.guardian.governance;

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GovernanceConfiguration {

    @Bean
    public ToolCallingAdvisor.Builder<?> toolCallingAdvisorBuilder(ToolCallingManager toolCallingManager) {
        return ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager);
    }
}