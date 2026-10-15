package com.agentops.guardian.coordinator;

import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

@Component
public class PendingAgentTaskRegistry {

    private static final int MAX_PENDING_TASKS = 500;
    private static final Duration TASK_TTL = Duration.ofMinutes(30);

    private final Clock clock;
    private final Duration taskTtl;
    private final int maxPendingTasks;
    private final LinkedHashMap<String, PendingTask> tasks = new LinkedHashMap<>();

    public PendingAgentTaskRegistry() {
        this(Clock.systemUTC(), TASK_TTL, MAX_PENDING_TASKS);
    }

    PendingAgentTaskRegistry(Clock clock, Duration taskTtl, int maxPendingTasks) {
        if (clock == null || taskTtl == null || taskTtl.isNegative() || taskTtl.isZero() || maxPendingTasks < 1) {
            throw new IllegalArgumentException("Valid pending task registry settings are required.");
        }
        this.clock = clock;
        this.taskTtl = taskTtl;
        this.maxPendingTasks = maxPendingTasks;
    }

    public synchronized void register(
            GovernanceIntervention intervention,
            AgentCoordinatorRequest request,
            AgentCoordinator.IntentAction action,
            String targetAgent,
            String reportArtifactId,
            String proposedToolArguments
    ) {
        if (intervention == null || request == null || action == null || targetAgent == null
                || targetAgent.isBlank() || intervention.conversationId() == null
                || intervention.toolName() == null || intervention.toolName().isBlank()
                || intervention.argumentsDigest() == null || intervention.argumentsDigest().isBlank()
                || proposedToolArguments == null
                || !intervention.argumentsDigest().equals(
                        ApprovedActionGrant.argumentsDigest(proposedToolArguments))) {
            throw new IllegalArgumentException("Pending task context is incomplete.");
        }
        Instant now = clock.instant();
        purgeExpired(now);
        AgentCoordinatorRequest requestWithConversation = new AgentCoordinatorRequest(
                request.message(),
                request.recipient(),
                request.report(),
                intervention.conversationId() == null ? null : intervention.conversationId().toString(),
                request.subjectInstructions(),
                request.bodyInstructions()
        );
        tasks.remove(intervention.interventionId());
        tasks.put(intervention.interventionId(), new PendingTask(
                intervention.interventionId(),
                intervention.conversationId(),
                requestWithConversation,
                action,
                targetAgent,
                reportArtifactId,
                intervention.workflowId(),
                intervention.workflowType(),
                intervention.agentName(),
                intervention.toolCallId(),
                intervention.toolName(),
                intervention.argumentsDigest(),
                proposedToolArguments,
                intervention.capability(),
                now.plus(taskTtl)
        ));
        while (tasks.size() > maxPendingTasks) {
            tasks.remove(tasks.keySet().iterator().next());
        }
    }

    public synchronized Claim claim(String interventionId) {
        PendingTask task = tasks.remove(interventionId);
        if (task == null) {
            return new Claim(ClaimStatus.MISSING, null);
        }
        if (!task.expiresAt().isAfter(clock.instant())) {
            return new Claim(ClaimStatus.EXPIRED, null);
        }
        return new Claim(ClaimStatus.FOUND, task);
    }

    public synchronized void terminate(String interventionId) {
        tasks.remove(interventionId);
    }

    private void purgeExpired(Instant now) {
        tasks.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public enum ClaimStatus {
        FOUND,
        MISSING,
        EXPIRED
    }

    public record Claim(ClaimStatus status, PendingTask task) {}

    public record PendingTask(
            String interventionId,
            UUID conversationId,
            AgentCoordinatorRequest request,
            AgentCoordinator.IntentAction action,
            String targetAgent,
            String reportArtifactId,
            String sourceWorkflowId,
            WorkflowType workflowType,
            String agentName,
            String toolCallId,
            String toolName,
            String argumentsDigest,
            String arguments,
            WorkflowCapability capability,
            Instant expiresAt
    ) {}
}
