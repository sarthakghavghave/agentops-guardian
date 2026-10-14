package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.audit.NoOpAuditService;
import com.agentops.guardian.governance.audit.WorkflowNodeSnapshot;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.risk.RiskEvaluator;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.exception.ApprovalRequiredException;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.DataTransformation;
import com.agentops.guardian.governance.model.ToolInvocationOutcome;
import com.agentops.guardian.governance.model.WorkflowAction;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.coordinator.context.VerifiedCustomerReadEvidence;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class GuardianToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final GovernancePolicyEngine policyEngine;
    private final WorkflowContextManager workflowContextManager;
    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final AuditService auditService;
    private final RiskEvaluator riskEvaluator;
    private final InterventionService interventionService;
    private final ToolInvocationCorrelation toolInvocationCorrelation;
    private final SharedContextService sharedContextService;

    @Autowired
    public GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            AuditService auditService,
            RiskEvaluator riskEvaluator,
            InterventionService interventionService,
            ToolInvocationCorrelation toolInvocationCorrelation,
            SharedContextService sharedContextService) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                ToolCallingManager.builder()
                        .observationRegistry(toolInvocationCorrelation.observationRegistry())
                        .build(),
                auditService,
                riskEvaluator,
                interventionService,
                toolInvocationCorrelation,
                sharedContextService
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                new NoOpAuditService(),
                new RiskEvaluator(),
                null,
                new ToolInvocationCorrelation(io.micrometer.observation.ObservationRegistry.NOOP)
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                auditService,
                new RiskEvaluator(),
                null,
                new ToolInvocationCorrelation(io.micrometer.observation.ObservationRegistry.NOOP)
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService,
            RiskEvaluator riskEvaluator) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                auditService,
                riskEvaluator,
                null,
                new ToolInvocationCorrelation(io.micrometer.observation.ObservationRegistry.NOOP)
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService,
            RiskEvaluator riskEvaluator,
            InterventionService interventionService) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                auditService,
                riskEvaluator,
                interventionService,
                new ToolInvocationCorrelation(io.micrometer.observation.ObservationRegistry.NOOP),
                null
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService,
            RiskEvaluator riskEvaluator,
            InterventionService interventionService,
            ToolInvocationCorrelation toolInvocationCorrelation) {
        this(
                workflowContextManager,
                policyEngine,
                toolCapabilityRegistry,
                delegate,
                auditService,
                riskEvaluator,
                interventionService,
                toolInvocationCorrelation,
                null
        );
    }

    GuardianToolCallingManager(
            WorkflowContextManager workflowContextManager,
            GovernancePolicyEngine policyEngine,
            ToolCapabilityRegistry toolCapabilityRegistry,
            ToolCallingManager delegate,
            AuditService auditService,
            RiskEvaluator riskEvaluator,
            InterventionService interventionService,
            ToolInvocationCorrelation toolInvocationCorrelation,
            SharedContextService sharedContextService) {
        this.workflowContextManager = workflowContextManager;
        this.policyEngine = policyEngine;
        this.toolCapabilityRegistry = toolCapabilityRegistry;
        this.delegate = delegate;
        this.auditService = auditService;
        this.riskEvaluator = riskEvaluator;
        this.interventionService = interventionService;
        this.toolInvocationCorrelation = toolInvocationCorrelation;
        this.sharedContextService = sharedContextService;
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {

        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {

            var toolCalls = chatResponse.getResult().getOutput().getToolCalls();

            WorkflowContext workflowContext = workflowContextManager.current();
            List<ProposedToolCall> proposedToolCalls = proposedToolCalls(toolCalls);
            ChatResponse correlatedChatResponse = withGuardianToolCallIds(chatResponse, proposedToolCalls);
                long recognizedCapabilityCount = proposedToolCalls.stream()
                    .filter(proposedToolCall -> proposedToolCall.capability() != null)
                    .count();

                if (recognizedCapabilityCount > 1) {
                throw new GovernanceViolationException(
                    "Batch execution containing multiple workflow capabilities is not supported."
                );
                }

            List<ProposedCapability> proposedCapabilities = new ArrayList<>();

            for (ProposedToolCall proposedToolCall : proposedToolCalls) {
                var toolCall = proposedToolCall.toolCall();
                ToolCallEvent event = new ToolCallEvent(
                    proposedToolCall.guardianToolCallId(),
                        toolCall.name(),
                        toolCall.arguments(),
                        toolCall.type(),
                        Instant.now()
                );

                WorkflowCapability capability = proposedToolCall.capability();

                auditService.record(AuditEvent.proposedAction(
                        workflowContext.getWorkflowId(),
                        workflowContext.getAgentName(),
                        workflowContext.getWorkflowType(),
                        event.toolCallId(),
                        event.toolName(),
                        capability,
                        null
                ));

                workflowContext.addToolCall(event);

                GovernanceDecision decision = policyEngine.evaluate(workflowContext, event);

                auditService.record(AuditEvent.policyDecision(
                        workflowContext.getWorkflowId(),
                        workflowContext.getAgentName(),
                        workflowContext.getWorkflowType(),
                        event.toolCallId(),
                        event.toolName(),
                        capability,
                        decision
                ));

                if (decision.decision() == GovernanceDecision.DecisionType.BLOCK) {
                    auditService.record(AuditEvent.executionOutcome(
                            workflowContext.getWorkflowId(),
                            workflowContext.getAgentName(),
                            workflowContext.getWorkflowType(),
                            event.toolCallId(),
                            event.toolName(),
                            capability,
                            AuditEvent.ExecutionStatus.BLOCKED,
                            null,
                            decision.reason(),
                            null,
                            null,
                            null,
                            null
                    ));

                    System.out.println("[GUARDIAN] BLOCKED");
                    System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                    System.out.println("  Agent: " + workflowContext.getAgentName());
                    System.out.println("  Tool: " + event.toolName());
                    System.out.println("  Reason: " + decision.reason());

                    throw new GovernanceViolationException(decision.reason());
                }

                if (capability != null) {
                    workflowContext.validateCapabilityTransition(capability);

                    RiskDecision riskDecision = RiskDecision.from(riskEvaluator.assess(workflowContext, capability));
                    auditService.record(AuditEvent.riskAssessment(
                            workflowContext.getWorkflowId(),
                            workflowContext.getAgentName(),
                            workflowContext.getWorkflowType(),
                            event.toolCallId(),
                            event.toolName(),
                            capability,
                            workflowContext.nextActionSequence(),
                            riskDecision
                    ));

                    if (riskDecision.intervention() == GovernanceIntervention.REQUIRE_APPROVAL) {
                        if (interventionService == null) {
                            throw new IllegalStateException("Intervention service is required for approval decisions.");
                        }
                        com.agentops.guardian.governance.intervention.GovernanceIntervention intervention =
                            interventionService.createPending(
                                workflowContext,
                                event.toolCallId(),
                                capability,
                                riskDecision
                        );
                        throw new ApprovalRequiredException(intervention);
                    }
                    if (riskDecision.intervention() == GovernanceIntervention.BLOCK) {
                        throw new GovernanceViolationException(
                                "Risk intervention BLOCK: " + riskDecision.reason()
                        );
                    }

                    Integer sequence = workflowContext.nextActionSequence();

                    WorkflowAction action = WorkflowAction.create(
                            proposedToolCall.guardianToolCallId(),
                            sequence,
                            toolCall.name(),
                            capability,
                            toolCall.arguments()
                    );

                    workflowContext.addAction(action);
                    proposedCapabilities.add(new ProposedCapability(proposedToolCall.guardianToolCallId(), capability));
                }

                logToolCall(event);

                System.out.println("[GUARDIAN] Governance decision: ALLOW");
                System.out.println("  Workflow: " + workflowContext.getWorkflowId());
                System.out.println("  Tool: " + event.toolName());
                System.out.println("  Reason: " + decision.reason());
            }

            DataTransformation transformationBeforeExecution = workflowContext.getLastTransformation();
            ToolExecutionResult result;
            ToolInvocationCorrelation.InvocationBatch invocationBatch =
                    toolInvocationCorrelation.newInvocationBatch();
            boolean delegateCompleted = false;
            try {
                Prompt correlatedPrompt = withCorrelatedCallbacks(
                        prompt,
                        proposedToolCalls,
                        workflowContext,
                        invocationBatch
                );
                result = delegate.executeToolCalls(correlatedPrompt, correlatedChatResponse);
                delegateCompleted = true;
                Map<String, ToolInvocationOutcome<?>> invocationOutcomes =
                        invocationBatch.outcomesByToolCallId();
                Set<String> executedGuardianToolCallIds = successfulGuardianToolCallIds(
                        result,
                        proposedToolCalls,
                        invocationOutcomes
                );

                for (ProposedToolCall proposedToolCall : proposedToolCalls) {
                    String toolCallId = proposedToolCall.guardianToolCallId();
                    boolean executed = executedGuardianToolCallIds.contains(toolCallId);
                    WorkflowCapability capability = proposedToolCall.capability();
                    ToolInvocationOutcome<?> invocationOutcome = findCustomerDataOutcome(
                            invocationOutcomes,
                            proposedToolCall
                    );

                    if (!executed && invocationOutcome != null
                            && invocationOutcome.status() == ToolInvocationOutcome.Status.FAILED) {
                        WorkflowNodeSnapshot failedBefore = capability == null ? null : new WorkflowNodeSnapshot(
                                workflowContext.getCurrentNodeId(),
                                workflowContext.getCurrentState(),
                                capability
                        );
                        auditService.record(AuditEvent.executionOutcome(
                                workflowContext.getWorkflowId(),
                                workflowContext.getAgentName(),
                                workflowContext.getWorkflowType(),
                                toolCallId,
                                proposedToolCall.toolCall().name(),
                                capability,
                                AuditEvent.ExecutionStatus.FAILED,
                                invocationOutcome.failureType(),
                                "Tool callback failed before producing a typed customer-data result.",
                                failedBefore,
                                null,
                                failedBefore == null ? null : failedBefore.state(),
                                null
                        ));
                        continue;
                    }

                    if (executed) {
                        DataTransformation transformation = null;
                        if (capability == WorkflowCapability.GENERATE_REPORT) {
                            DataTransformation latestTransformation = workflowContext.getLastTransformation();
                            if (latestTransformation != transformationBeforeExecution) {
                                transformation = latestTransformation;
                            }
                        }

                        WorkflowNodeSnapshot beforeSuccess = capability == null ? null : new WorkflowNodeSnapshot(
                                workflowContext.getCurrentNodeId(),
                                workflowContext.getCurrentState(),
                                capability
                        );

                        auditService.record(AuditEvent.executionOutcome(
                                workflowContext.getWorkflowId(),
                                workflowContext.getAgentName(),
                                workflowContext.getWorkflowType(),
                                toolCallId,
                                proposedToolCall.toolCall().name(),
                                capability,
                                AuditEvent.ExecutionStatus.SUCCESS,
                                null,
                                null,
                                beforeSuccess,
                                null,
                                beforeSuccess == null ? null : beforeSuccess.state(),
                                null,
                                transformation == null ? null : transformation.sourceClassification(),
                                transformation == null ? null : transformation.resultClassification(),
                                transformation == null ? null : transformation.transformationType()
                        ));

                        advanceAndAuditTransition(workflowContext, proposedToolCall, capability);
                    }
                }

                recordVerifiedCustomerReadEvidence(workflowContext, invocationOutcomes);
                return result;
            } catch (RuntimeException ex) {
                if (delegateCompleted) {
                    throw ex;
                }
                Map<String, ToolInvocationOutcome<?>> invocationOutcomes =
                        invocationBatch.outcomesByToolCallId();
                for (ProposedToolCall proposedToolCall : proposedToolCalls) {
                    WorkflowCapability capability = proposedToolCall.capability();
                    String toolId = proposedToolCall.guardianToolCallId();
                    ToolInvocationOutcome<?> invocationOutcome =
                            invocationOutcomes.get(proposedToolCall.guardianToolCallId());
                    if (CustomerDataTools.TOOL_NAME.equals(proposedToolCall.toolCall().name())
                            && invocationOutcome != null
                            && invocationOutcome.status() == ToolInvocationOutcome.Status.SUCCEEDED) {
                        auditService.record(AuditEvent.executionOutcome(
                                workflowContext.getWorkflowId(),
                                workflowContext.getAgentName(),
                                workflowContext.getWorkflowType(),
                                toolId,
                                proposedToolCall.toolCall().name(),
                                capability,
                                AuditEvent.ExecutionStatus.SUCCESS,
                                null,
                                null,
                                capability == null ? null : new WorkflowNodeSnapshot(
                                        workflowContext.getCurrentNodeId(),
                                        workflowContext.getCurrentState(),
                                        capability
                                ),
                                null,
                                capability == null ? null : workflowContext.getCurrentState(),
                                null
                        ));
                        advanceAndAuditTransition(workflowContext, proposedToolCall, capability);
                        continue;
                    }
                    WorkflowNodeSnapshot failedBefore = capability == null ? null : new WorkflowNodeSnapshot(
                            workflowContext.getCurrentNodeId(),
                            workflowContext.getCurrentState(),
                            capability
                    );
                    auditService.record(AuditEvent.executionOutcome(
                            workflowContext.getWorkflowId(),
                            workflowContext.getAgentName(),
                            workflowContext.getWorkflowType(),
                            toolId,
                            proposedToolCall.toolCall().name(),
                            capability,
                            AuditEvent.ExecutionStatus.FAILED,
                            ex.getClass().getName(),
                            sanitizeError(ex),
                            failedBefore,
                            null,
                            failedBefore == null ? null : failedBefore.state(),
                            null
                    ));
                }
                recordVerifiedCustomerReadEvidence(workflowContext, invocationOutcomes);
                throw ex;
            }
        }

        return delegate.executeToolCalls(prompt, chatResponse);
    }

    private void advanceAndAuditTransition(
            WorkflowContext workflowContext,
            ProposedToolCall proposedToolCall,
            WorkflowCapability capability
    ) {
        if (capability == null) {
            return;
        }

        WorkflowNodeSnapshot nodeBefore = new WorkflowNodeSnapshot(
                workflowContext.getCurrentNodeId(),
                workflowContext.getCurrentState(),
                capability
        );
        workflowContext.advanceAfterCapability(capability);
        WorkflowNodeSnapshot nodeAfter = new WorkflowNodeSnapshot(
                workflowContext.getCurrentNodeId(),
                workflowContext.getCurrentState(),
                capability
        );
        auditService.record(AuditEvent.workflowTransition(
                workflowContext.getWorkflowId(),
                workflowContext.getAgentName(),
                workflowContext.getWorkflowType(),
                proposedToolCall.guardianToolCallId(),
                proposedToolCall.toolCall().name(),
                capability,
                nodeBefore,
                nodeAfter,
                nodeBefore.state(),
                nodeAfter.state()
        ));
    }

    private void recordVerifiedCustomerReadEvidence(
            WorkflowContext workflowContext,
            Map<String, ToolInvocationOutcome<?>> invocationOutcomes
    ) {
        if (sharedContextService == null || workflowContext.getConversationId() == null) {
            return;
        }

        invocationOutcomes.values().stream()
                .filter(outcome -> outcome.status() == ToolInvocationOutcome.Status.SUCCEEDED)
                .filter(outcome -> CustomerDataTools.TOOL_NAME.equals(outcome.toolName()))
                .filter(outcome -> outcome.typedResult() instanceof CustomerDataTools.CustomerDataSet)
                .map(outcome -> {
                    CustomerDataTools.CustomerDataSet data =
                            (CustomerDataTools.CustomerDataSet) outcome.typedResult();
                    List<String> customerEmails = data.customers() == null
                            ? List.of()
                            : data.customers().stream()
                            .map(CustomerDataTools.CustomerRecord::email)
                            .filter(email -> email != null && !email.isBlank())
                            .distinct()
                            .toList();
                    return new VerifiedCustomerReadEvidence(
                            workflowContext.getConversationId(),
                            workflowContext.getWorkflowId(),
                            outcome.toolCallId(),
                            WorkflowCapability.READ_CUSTOMER_DATA,
                            outcome.status(),
                            outcome.completedAt(),
                            customerEmails
                    );
                })
                .forEach(sharedContextService::recordVerifiedCustomerRead);
    }

    @Override
    public List<org.springframework.ai.tool.definition.ToolDefinition>
    resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    private void logToolCall(ToolCallEvent event) {

        WorkflowContext workflowContext = workflowContextManager.current();

        System.out.println(
                "[GUARDIAN] Tool call intercepted"
                        + "\n  Workflow: "
                        + (workflowContext != null ? workflowContext.getWorkflowId() : "NONE")
                        + "\n  Agent: "
                        + (workflowContext != null ? workflowContext.getAgentName() : "NONE")
                        + "\n  Tool: " + event.toolName()
                        + "\n  Call ID: " + event.toolCallId()
                        + "\n  Arguments: " + event.arguments()
                        + "\n  Type: " + event.toolType()
                        + "\n  Timestamp: " + event.timestamp()
        );
    }

    private Set<String> successfulGuardianToolCallIds(
            ToolExecutionResult result,
            List<ProposedToolCall> proposedToolCalls,
            Map<String, ToolInvocationOutcome<?>> invocationOutcomes
    ) {
        Set<String> successfulIds = new HashSet<>();
        invocationOutcomes.values().stream()
                .filter(outcome -> outcome.status() == ToolInvocationOutcome.Status.SUCCEEDED)
                .filter(outcome -> CustomerDataTools.TOOL_NAME.equals(outcome.toolName()))
                .map(ToolInvocationOutcome::toolCallId)
                .forEach(successfulIds::add);
        if (result == null || result.conversationHistory() == null) {
            return successfulIds;
        }

        List<ToolResponseMessage.ToolResponse> responses = result.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .toList();

        boolean[] matchedResponses = new boolean[responses.size()];
        for (ProposedToolCall proposedToolCall : proposedToolCalls) {
            if (CustomerDataTools.TOOL_NAME.equals(proposedToolCall.toolCall().name())) {
                continue;
            }
            for (int index = 0; index < responses.size(); index++) {
                if (matchedResponses[index]) {
                    continue;
                }
                ToolResponseMessage.ToolResponse response = responses.get(index);
                boolean guardianIdPresent = proposedToolCall.guardianToolCallId() != null
                        && !proposedToolCall.guardianToolCallId().isBlank();
                boolean blankProviderIdFallback = (response.id() == null || response.id().isBlank())
                        && (proposedToolCall.providerToolCallId() == null
                        || proposedToolCall.providerToolCallId().isBlank())
                        && proposedToolCalls.stream()
                        .filter(candidate -> candidate.providerToolCallId() == null
                                || candidate.providerToolCallId().isBlank())
                        .filter(candidate -> candidate.toolCall().name().equals(
                                proposedToolCall.toolCall().name()))
                        .count() == 1;
                boolean matches = guardianIdPresent
                        ? proposedToolCall.guardianToolCallId().equals(response.id()) || blankProviderIdFallback
                        : blankProviderIdFallback;
                if (matches) {
                    matchedResponses[index] = true;
                    successfulIds.add(proposedToolCall.guardianToolCallId());
                    break;
                }
            }
        }

        return successfulIds;
    }

    private List<ProposedToolCall> proposedToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        Map<String, Integer> providerIdCounts = new HashMap<>();
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (toolCall.id() != null && !toolCall.id().isBlank()) {
                providerIdCounts.merge(toolCall.id(), 1, Integer::sum);
            }
        }

        Set<String> reservedGuardianIds = new HashSet<>(providerIdCounts.keySet());
        return toolCalls.stream()
                .map(toolCall -> {
                    String providerToolCallId = toolCall.id();
                    boolean providerIdIsUnique = providerToolCallId != null
                            && !providerToolCallId.isBlank()
                            && providerIdCounts.get(providerToolCallId) == 1;
                    String guardianToolCallId;
                    if (providerIdIsUnique) {
                        guardianToolCallId = providerToolCallId;
                    } else {
                        do {
                            guardianToolCallId = UUID.randomUUID().toString();
                        } while (!reservedGuardianIds.add(guardianToolCallId));
                    }
                    return new ProposedToolCall(
                            toolCall,
                            toolCapabilityRegistry.getCapability(toolCall.name()),
                            providerToolCallId,
                            guardianToolCallId
                    );
                })
                .toList();
    }

    private ChatResponse withGuardianToolCallIds(
            ChatResponse chatResponse,
            List<ProposedToolCall> proposedToolCalls
    ) {
        if (proposedToolCalls.stream().noneMatch(proposed -> !proposed.guardianToolCallId()
                .equals(proposed.providerToolCallId()))) {
            return chatResponse;
        }

        List<Generation> generations = new ArrayList<>(chatResponse.getResults());
        Generation firstGeneration = generations.getFirst();
        AssistantMessage originalOutput = firstGeneration.getOutput();
        AssistantMessage normalizedOutput = originalOutput.mutate()
                .toolCalls(proposedToolCalls.stream()
                        .map(proposed -> new AssistantMessage.ToolCall(
                                proposed.guardianToolCallId(),
                                proposed.toolCall().type(),
                                proposed.toolCall().name(),
                                proposed.toolCall().arguments()
                        ))
                        .toList())
                .build();
        generations.set(0, new Generation(normalizedOutput, firstGeneration.getMetadata()));
        return new ChatResponse(generations, chatResponse.getMetadata());
    }

    private Prompt withCorrelatedCallbacks(
            Prompt prompt,
            List<ProposedToolCall> proposedToolCalls,
            WorkflowContext workflowContext,
            ToolInvocationCorrelation.InvocationBatch invocationBatch
    ) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions toolOptions)
                || toolOptions.getToolCallbacks() == null) {
            return prompt;
        }
        List<ToolInvocationCorrelation.InvocationReference> references = proposedToolCalls.stream()
                .map(proposed -> new ToolInvocationCorrelation.InvocationReference(
                        proposed.guardianToolCallId(),
                        proposed.providerToolCallId(),
                        proposed.toolCall().name()
                ))
                .toList();
        return new Prompt(
                prompt.getInstructions(),
                toolOptions.mutate()
                        .toolCallbacks(toolInvocationCorrelation.wrapCustomerDataCallbacks(
                                toolOptions.getToolCallbacks(),
                                references,
                                workflowContext,
                                invocationBatch
                        ))
                        .build()
        );
    }

    private ToolInvocationOutcome<?> findCustomerDataOutcome(
            Map<String, ToolInvocationOutcome<?>> invocationOutcomes,
            ProposedToolCall proposedToolCall
    ) {
        ToolInvocationOutcome<?> outcome = invocationOutcomes.get(proposedToolCall.guardianToolCallId());
        return outcome != null && outcome.toolName().equals(proposedToolCall.toolCall().name())
                ? outcome
                : null;
    }

    private String sanitizeError(RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private record ProposedToolCall(
            AssistantMessage.ToolCall toolCall,
            WorkflowCapability capability,
            String providerToolCallId,
            String guardianToolCallId
    ) {}

    private record ProposedCapability(String toolCallId, WorkflowCapability capability) {}
}