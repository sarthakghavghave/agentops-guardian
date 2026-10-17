package com.agentops.guardian.controller;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.CustomerSupportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.AgentCoordinator;
import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.PendingAgentTaskRegistry;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.config.GovernanceSettings;
import com.agentops.guardian.governance.context.ConversationHistoryAccessService;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowContextHolder;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.execution.GuardianToolCallingManager;
import com.agentops.guardian.governance.execution.ToolInvocationCorrelation;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.intervention.InterventionService;
import com.agentops.guardian.governance.intervention.dto.GovernanceInterventionResponse;
import com.agentops.guardian.governance.intervention.dto.InterventionResolutionRequest;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.policy.GovernancePolicyEngine;
import com.agentops.guardian.governance.risk.RiskEvaluator;
import com.agentops.guardian.governance.workflow.ToolCapabilityRegistry;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.agentops.guardian.tool.CommunicationTools;
import com.agentops.guardian.tool.GmailSmtpEmailAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.ObservationRegistry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApprovalResumeIntegrationTest {

    @Test
    void approvalEndpointExecutesExactCapturedActionThroughGuardianManager() throws Exception {
        String arguments = """
                {"recipient":"operator@example.test","subject":"Meeting invitation","body":"Please join our meeting tomorrow."}
                """;
        ResumeFixture fixture = new ResumeFixture(false);
        UUID conversationId = fixture.conversationId;
        GovernanceIntervention pending = fixture.interceptApproval(arguments);
        fixture.registerPendingTask(pending, arguments);

        GovernanceIntervention approved = pending.approve("reviewer", "Approved.", Instant.now());
        when(fixture.interventionService.approve(pending.interventionId(), "reviewer", "Approved."))
                .thenReturn(approved);

        GovernanceInterventionResponse response = fixture.controller().approve(
                pending.interventionId(),
                new InterventionResolutionRequest("reviewer", "Approved.")
        );

        assertEquals(GovernanceInterventionStatus.APPROVED, response.status());
        assertEquals("COMPLETED", response.taskStatus());
        assertTrue(response.taskMessage().contains("Gmail SMTP accepted"));
        verify(fixture.tools, org.mockito.Mockito.times(1)).sendEmail(
                "operator@example.test",
                "Meeting invitation",
                "Please join our meeting tomorrow."
        );
        verify(fixture.emailAdapter).send(
                "operator@example.test",
                "Meeting invitation",
                "Please join our meeting tomorrow.",
                null
        );
        verifyNoInteractions(fixture.decisionClient, fixture.toolClient);
        verify(fixture.interventionService).createPending(
                any(WorkflowContext.class),
                anyString(),
                anyString(),
                anyString(),
                any(WorkflowCapability.class),
                any()
        );
        verify(fixture.auditService).record(org.mockito.ArgumentMatchers.argThat(
                (AuditEvent event) -> event.eventType()
                        == com.agentops.guardian.governance.audit.AuditEventType.EXECUTION_OUTCOME
                        && event.executionStatus() == AuditEvent.ExecutionStatus.SUCCESS
                        && pending.interventionId().equals(event.interventionId())
                        && pending.toolCallId().equals(event.toolCallId())
                        && conversationId.equals(event.conversationId())
        ));
        assertEquals(3, fixture.policyEvaluationCount);
    }

    @Test
    void approvedEmailStillStopsWhenConversationHistoryPolicyBlocks() throws Exception {
        String arguments = """
                {"recipient":"operator@example.test","subject":"Meeting invitation","body":"Please join our meeting tomorrow."}
                """;
        ResumeFixture fixture = new ResumeFixture(true);
        UUID conversationId = fixture.conversationId;
        GovernanceIntervention pending = fixture.interceptApproval(arguments);
        fixture.registerPendingTask(pending, arguments);

        GovernanceIntervention approved = pending.approve("reviewer", "Approved.", Instant.now());
        when(fixture.interventionService.approve(pending.interventionId(), "reviewer", "Approved."))
                .thenReturn(approved);

        GovernanceInterventionResponse response = fixture.controller().approve(
                pending.interventionId(),
                new InterventionResolutionRequest("reviewer", "Approved.")
        );

        assertEquals("BLOCKED", response.taskStatus());
        verify(fixture.tools, org.mockito.Mockito.never()).sendEmail(anyString(), anyString(), anyString());
        verify(fixture.emailAdapter, never()).send(anyString(), anyString(), anyString(), any());
        assertEquals(2, fixture.policyEvaluationCount);
    }

    @Test
    void rejectedEmailInterventionNeverInvokesMailAdapter() throws Exception {
        String arguments = """
                {"recipient":"operator@example.test","subject":"Meeting invitation","body":"Please join our meeting tomorrow."}
                """;
        ResumeFixture fixture = new ResumeFixture(false);
        GovernanceIntervention pending = fixture.interceptApproval(arguments);
        fixture.registerPendingTask(pending, arguments);
        GovernanceIntervention rejected = pending.reject("reviewer", "Rejected.", Instant.now());
        when(fixture.interventionService.reject(pending.interventionId(), "reviewer", "Rejected."))
                .thenReturn(rejected);

        GovernanceInterventionResponse response = fixture.controller().reject(
                pending.interventionId(),
                new InterventionResolutionRequest("reviewer", "Rejected.")
        );

        assertEquals(GovernanceInterventionStatus.REJECTED, response.status());
        assertEquals("TERMINATED", response.taskStatus());
        verify(fixture.emailAdapter, never()).send(anyString(), anyString(), anyString(), any());
        verify(fixture.tools, never()).sendEmail(anyString(), anyString(), anyString());
    }

    private static final class ResumeFixture {
        private final UUID conversationId;
        private final boolean blockHistory;
        private final ConversationMemory memory = new ConversationMemory();
        private final WorkflowContextManager workflowContextManager = new WorkflowContextManager(
                new WorkflowContextHolder(), new WorkflowDefinitionRegistry()
        );
        private final InterventionService interventionService = mock(InterventionService.class);
        private final AuditService auditService = mock(AuditService.class);
        private final GovernancePolicyEngine policyEngine = mock(GovernancePolicyEngine.class);
        private final ApprovedActionGrant grant = new ApprovedActionGrant();
        private final GmailSmtpEmailAdapter emailAdapter = mock(GmailSmtpEmailAdapter.class);
        private final CommunicationTools tools = spy(new CommunicationTools(emailAdapter));
        private final ChatClient decisionClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        private final ChatClient toolClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        private final PendingAgentTaskRegistry pendingTasks = new PendingAgentTaskRegistry();
        private final GuardianToolCallingManager guardianManager;
        private int policyEvaluationCount;
        private final AgentCoordinator coordinator;

        private ResumeFixture(boolean blockHistory) throws Exception {
            this.conversationId = memory.open(null);
            this.blockHistory = blockHistory;
            doReturn("accepted-by-provider").when(emailAdapter)
                    .send(anyString(), anyString(), anyString(), any());
            when(policyEngine.evaluate(any(), any())).thenAnswer(invocation -> {
                ToolCallEvent event = invocation.getArgument(1);
                policyEvaluationCount++;
                boolean blocked = blockHistory
                        && ConversationHistoryAccessService.ACTION_NAME.equals(event.toolName());
                return new GovernanceDecision(
                        blocked ? GovernanceDecision.DecisionType.BLOCK : GovernanceDecision.DecisionType.ALLOW,
                        blocked ? "Conversation history access denied by test policy." : "Allowed for test.",
                        blocked ? "TEST_HISTORY_BLOCK" : "TEST_ALLOW"
                );
            });
            when(interventionService.createPending(any(), anyString(), anyString(), anyString(), any(), any()))
                    .thenAnswer(invocation -> {
                        WorkflowContext workflow = invocation.getArgument(0);
                        String toolCallId = invocation.getArgument(1);
                        String toolName = invocation.getArgument(2);
                        String digest = invocation.getArgument(3);
                        WorkflowCapability capability = invocation.getArgument(4);
                        com.agentops.guardian.governance.risk.RiskDecision risk = invocation.getArgument(5);
                        return new GovernanceIntervention(
                                "intervention-integration",
                                workflow.getWorkflowId(),
                                toolCallId,
                                workflow.getAgentName(),
                                workflow.getWorkflowType(),
                                capability,
                                risk.riskLevel(),
                                risk.intervention(),
                                GovernanceInterventionStatus.PENDING,
                                risk.reason(),
                                Instant.now(),
                                null,
                                null,
                                null,
                                workflow.getCurrentNodeId(),
                                workflow.getCurrentState(),
                                workflow.getCurrentDataClassification(),
                                workflow.getConversationId(),
                                toolName,
                                digest
                        );
                    });
            guardianManager = new GuardianToolCallingManager(
                    workflowContextManager,
                    policyEngine,
                    new ToolCapabilityRegistry(),
                    auditService,
                    new RiskEvaluator(),
                    interventionService,
                    new ToolInvocationCorrelation(ObservationRegistry.NOOP),
                    memory,
                    new GovernanceSettings(),
                    grant
            );
            ConversationHistoryAccessService historyAccessService = new ConversationHistoryAccessService(
                    workflowContextManager,
                    policyEngine,
                    auditService,
                    memory,
                    new GovernanceSettings(),
                    grant
            );
            ChatClient.Builder emailBuilder = mock(ChatClient.Builder.class);
            when(emailBuilder.defaultSystem(anyString())).thenReturn(emailBuilder);
            when(emailBuilder.defaultTools(any(Object[].class))).thenReturn(emailBuilder);
            when(emailBuilder.build()).thenReturn(decisionClient, toolClient);
            EmailAgent emailAgent = new EmailAgent(
                    emailBuilder,
                    tools,
                    workflowContextManager,
                    memory,
                    null,
                    historyAccessService,
                    guardianManager
            );
            ChatClient.Builder coordinatorBuilder = mock(ChatClient.Builder.class);
            when(coordinatorBuilder.build()).thenReturn(mock(ChatClient.class));
            coordinator = new AgentCoordinator(
                    coordinatorBuilder,
                    mock(CustomerReportAgent.class),
                    mock(CustomerSupportAgent.class),
                    emailAgent,
                    memory,
                    pendingTasks,
                    grant,
                    auditService
            );
        }

        private GovernanceIntervention interceptApproval(String arguments) {
            workflowContextManager.start(
                    "EmailAgent",
                    WorkflowType.EMAIL_COMMUNICATION,
                    "Initial email proposal.",
                    conversationId
            );
            AssistantMessage assistantMessage = new AssistantMessage("")
                    .mutate()
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "send-call-integration",
                            "function",
                            "sendEmail",
                            arguments
                    )))
                    .build();
            Prompt prompt = new Prompt(
                    List.of(new org.springframework.ai.chat.messages.UserMessage("Send this email.")),
                    org.springframework.ai.model.tool.ToolCallingChatOptions.builder()
                            .toolCallbacks(ToolCallbacks.from(tools))
                            .build()
            );
            com.agentops.guardian.governance.exception.ApprovalRequiredException exception =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            com.agentops.guardian.governance.exception.ApprovalRequiredException.class,
                            () -> guardianManager.executeToolCalls(
                                    prompt,
                                    new ChatResponse(List.of(new Generation(assistantMessage)))
                            )
                    );
            workflowContextManager.clear();
            assertEquals(arguments, exception.getProposedToolArguments());
            return exception.getIntervention();
        }

        private void registerPendingTask(GovernanceIntervention intervention, String arguments) {
            pendingTasks.register(
                    intervention,
                    new AgentCoordinatorRequest(
                            "Send the meeting invitation",
                            "operator@example.test",
                            null,
                            conversationId.toString(),
                            "Meeting invitation",
                            "Please join our meeting tomorrow."
                    ),
                    AgentCoordinator.IntentAction.EMAIL_REQUEST,
                    "EMAIL",
                    null,
                    arguments
            );
        }

        @SuppressWarnings("unchecked")
        private InterventionController controller() {
            ObjectProvider<AgentCoordinator> coordinatorProvider = mock(ObjectProvider.class);
            ObjectProvider<PendingAgentTaskRegistry> taskRegistryProvider = mock(ObjectProvider.class);
            when(coordinatorProvider.getIfAvailable()).thenReturn(coordinator);
            when(taskRegistryProvider.getIfAvailable()).thenReturn(pendingTasks);
            return new InterventionController(interventionService, coordinatorProvider, taskRegistryProvider);
        }
    }
}
