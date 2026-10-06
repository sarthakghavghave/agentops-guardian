package com.agentops.guardian.governance.audit.live;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class LiveGovernanceEventConfiguration {
    @Bean("liveGovernanceEventExecutor")
    public TaskExecutor liveGovernanceEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2_000);
        executor.setThreadNamePrefix("governance-sse-");
        executor.setRejectedExecutionHandler((task, threadPoolExecutor) -> {
            throw new java.util.concurrent.RejectedExecutionException(
                    "Governance SSE delivery queue is full."
            );
        });
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
