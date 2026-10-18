package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.CustomerSupportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.context.Artifact;
import com.agentops.guardian.coordinator.context.ArtifactType;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import com.agentops.guardian.coordinator.dto.ReportArtifactHandoff;
import com.agentops.guardian.coordinator.agent.AgentResultStatus;
import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.audit.NoOpAuditService;
import com.agentops.guardian.governance.exception.ApprovalRequiredException;
import com.agentops.guardian.governance.exception.GovernanceViolationException;
import com.agentops.guardian.governance.execution.ApprovedActionGrant;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentCoordinator {

    private static final String SYSTEM_PROMPT = """
            Classify the current user message into an appropriate routing action:
            REPORT_REQUEST, REPORT_METADATA_REQUEST, EMAIL_REQUEST, GENERAL_RESPONSE,
            or CLARIFICATION_REQUIRED.

            Registered agents:
            - CustomerReportAgent (REPORT_REQUEST): create or regenerate analytical reports only.
            - CustomerSupportAgent (GENERAL_RESPONSE): normal conversation and supported customer,
              order, or product information retrieval.
            - EmailAgent (EMAIL_REQUEST): email composition and sending.

            Use REPORT_REQUEST only when the user explicitly asks to create, generate, or regenerate
            an analytical report. Requests to retrieve customer data or records, including requests
            such as "give me data of customers in North Michael", are GENERAL_RESPONSE, not report
            generation. CustomerSupportAgent can use its registered retrieval tools for these tasks.
            Questions about reports already created in this conversation, such as asking
            for a report ID, title, creation date, details, or whether a report is available,
            must use REPORT_METADATA_REQUEST. Never route those questions to report generation.
            Use REPORT_REQUEST only when the user is asking to create or regenerate a report.
            For EMAIL_REQUEST, set reportArtifactId only when the user requests attaching a
            registered generated PDF report. Choose an exact ID from the registered reports
            included in the routing context. If the intended report is ambiguous, leave it
            unset so EmailAgent can clarify. Never invent an artifact ID.

            Return typed intent fields only. Never choose tools, agents, classes,
            methods, or code. For GENERAL_RESPONSE and CLARIFICATION_REQUIRED,
            do not claim an operation succeeded. Email intent, authorization, clarification,
            and report selection are decided by EmailAgent using shared conversation context.
            Route messages that continue an email conversation to EMAIL_REQUEST so EmailAgent
            can interpret confirmations, refusals, and clarifications with that context.
            Prior assistant messages are not evidence that an operation succeeded.
            """;

    private final ChatClient intentClient;
    private final CustomerReportAgent customerReportAgent;
    private final CustomerSupportAgent customerSupportAgent;
    private final EmailAgent emailAgent;
    private final SharedContextService sharedContextService;
    private final PendingAgentTaskRegistry pendingAgentTaskRegistry;
    private final ApprovedActionGrant approvedActionGrant;
    private final AuditService auditService;

    @Autowired
    public AgentCoordinator(
            ChatClient.Builder chatClientBuilder,
            CustomerReportAgent customerReportAgent,
            CustomerSupportAgent customerSupportAgent,
            EmailAgent emailAgent,
            SharedContextService sharedContextService,
            PendingAgentTaskRegistry pendingAgentTaskRegistry,
            ApprovedActionGrant approvedActionGrant,
            AuditService auditService
    ) {
        this.intentClient = chatClientBuilder.build();
        this.customerReportAgent = customerReportAgent;
        this.customerSupportAgent = customerSupportAgent;
        this.emailAgent = emailAgent;
        this.sharedContextService = sharedContextService;
        this.pendingAgentTaskRegistry = pendingAgentTaskRegistry;
        this.approvedActionGrant = approvedActionGrant;
        this.auditService = auditService;
    }

    public AgentCoordinator(
            ChatClient.Builder chatClientBuilder,
            CustomerReportAgent customerReportAgent,
            CustomerSupportAgent customerSupportAgent,
            EmailAgent emailAgent,
            SharedContextService sharedContextService
    ) {
        this(
                chatClientBuilder,
                customerReportAgent,
                customerSupportAgent,
                emailAgent,
                sharedContextService,
                new PendingAgentTaskRegistry(),
                new ApprovedActionGrant(),
                new NoOpAuditService()
        );
    }

    public AgentCoordinatorResponse handle(AgentCoordinatorRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("A user message is required.");
        }

        if (request.recipient() != null && !request.recipient().isBlank()
                && !EmailAgent.isValidEmailAddress(request.recipient())) {
            throw new IllegalArgumentException("A valid email recipient is required.");
        }

        UUID conversationId = sharedContextService.open(request.conversationId());
        List<Message> messages = new ArrayList<>(sharedContextService.history(conversationId));
        String artifactContext = buildReportArtifactRoutingContext(conversationId);
        if (!artifactContext.isBlank()) {
            messages.add(new UserMessage(artifactContext));
        }
        messages.add(new UserMessage(request.message()));

        IntentDecision decision = intentClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(messages)
                .call()
                .entity(IntentDecision.class);
        if (decision == null || decision.action() == null) {
            throw new IllegalStateException("Coordinator returned no supported action.");
        }

        AgentCoordinatorResponse response = switch (decision.action()) {
            case GENERAL_RESPONSE -> generalResponse(request, conversationId);
            case CLARIFICATION_REQUIRED -> AgentCoordinatorResponse.clarification(
                    requireClarification(decision.clarificationQuestion()),
                    conversationId.toString()
            );
            case REPORT_METADATA_REQUEST -> reportMetadataResponse(conversationId);
            case REPORT_REQUEST, EMAIL_REQUEST -> dispatchAgent(request, decision, conversationId);
        };

        sharedContextService.recordTurn(
                conversationId,
                request.message(),
                response.response()
        );
        return response;
    }

    private AgentCoordinatorResponse generalResponse(AgentCoordinatorRequest request, UUID conversationId) {
        String supportResponse;
        try {
            supportResponse = customerSupportAgent.chat(conversationId, request.message());
        } catch (ApprovalRequiredException exception) {
            GovernanceIntervention intervention = exception.getIntervention();
            requirePersistedIntervention(intervention, exception);
            pendingAgentTaskRegistry.register(
                    intervention,
                    request,
                    IntentAction.GENERAL_RESPONSE,
                    AgentTarget.CUSTOMER_SUPPORT.name(),
                    null,
                    exception.getProposedToolArguments()
            );
            return AgentCoordinatorResponse.approvalRequired(
                    IntentAction.GENERAL_RESPONSE,
                    intervention.interventionId(),
                    conversationId.toString()
            );
        }
        if (supportResponse == null || supportResponse.isBlank()) {
            throw new IllegalStateException("Customer support agent returned no response.");
        }
        return AgentCoordinatorResponse.completed(
                IntentAction.GENERAL_RESPONSE,
                supportResponse,
                conversationId.toString()
        );
    }

    private AgentCoordinatorResponse reportMetadataResponse(UUID conversationId) {
        List<Artifact> reports = sharedContextService.getArtifacts(conversationId, ArtifactType.REPORT)
                .stream()
                .filter(artifact -> Artifact.GENERATED_PROVENANCE.equals(
                        artifact.metadata().get(Artifact.PROVENANCE_KEY)))
                .toList();

        if (reports.isEmpty()) {
            return AgentCoordinatorResponse.completed(
                    IntentAction.REPORT_METADATA_REQUEST,
                    "No generated report is available in this conversation.",
                    conversationId.toString()
            );
        }
        if (reports.size() > 1) {
            String candidates = reports.stream()
                    .map(artifact -> "'" + artifact.title() + "' (ID: " + artifact.id() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            return AgentCoordinatorResponse.clarification(
                    "Multiple reports are available. Which report do you mean? " + candidates,
                    conversationId.toString()
            );
        }

        Artifact report = reports.getFirst();
        StringBuilder response = new StringBuilder()
                .append("Report ID: ").append(report.id())
                .append("\nTitle: ").append(report.title())
                .append("\nCreated: ").append(report.createdAt())
                .append("\nType: ").append(report.metadata().getOrDefault(
                        Artifact.REPORT_TYPE_KEY, report.type().name()))
                .append("\nAvailability: available");
        String format = report.metadata().get(Artifact.FORMAT_KEY);
        if (format != null && !format.isBlank()) {
            response.append("\nFormat: ").append(format);
        }
        String customerCount = report.metadata().get("customerCount");
        if (customerCount != null && !customerCount.isBlank()) {
            response.append("\nCustomers analyzed: ").append(customerCount);
        }
        return AgentCoordinatorResponse.completed(
                IntentAction.REPORT_METADATA_REQUEST,
                response.toString(),
                conversationId.toString()
        );
    }

    private AgentCoordinatorResponse dispatchAgent(
            AgentCoordinatorRequest request,
            IntentDecision decision,
            UUID conversationId
    ) {
        AgentTarget target = resolveTargetAgent(decision);
        AgentResult result;
        try {
            if (target == AgentTarget.CUSTOMER_SUPPORT) {
                String supportResponse = customerSupportAgent.chat(conversationId, request.message());
                if (supportResponse == null || supportResponse.isBlank()) {
                    throw new IllegalStateException("Customer support agent returned no response.");
                }
                return AgentCoordinatorResponse.completed(
                        decision.action(), supportResponse, conversationId.toString()
                );
            }
            if (target == AgentTarget.EMAIL && decision.reportArtifactId() != null
                    && !decision.reportArtifactId().isBlank()) {
                Optional<ReportArtifactHandoff> reportHandoff = resolveReportHandoff(
                        conversationId,
                        decision.reportArtifactId()
                );
                if (reportHandoff.isEmpty()) {
                    return AgentCoordinatorResponse.clarification(
                            "The requested report is not available as a registered PDF in this conversation.",
                            conversationId.toString()
                    );
                }
                result = emailAgent.execute(conversationId, request, reportHandoff.get());
            } else if (target == AgentTarget.EMAIL) {
                result = emailAgent.execute(conversationId, request);
            } else {
                result = customerReportAgent.execute(conversationId, request);
            }
        } catch (ApprovalRequiredException exception) {
            GovernanceIntervention intervention = exception.getIntervention();
            requirePersistedIntervention(intervention, exception);
            pendingAgentTaskRegistry.register(
                    intervention,
                    request,
                    decision.action(),
                    target.name(),
                    decision.reportArtifactId(),
                    exception.getProposedToolArguments()
            );
            return AgentCoordinatorResponse.approvalRequired(
                    decision.action(),
                    intervention.toolName(),
                    intervention.interventionId(),
                    conversationId.toString()
            );
        }
        if (result == null || result.status() == null) {
            throw new IllegalStateException("Selected agent returned no valid execution status.");
        }
        return switch (result.status()) {
            case SUCCESS -> {
                if (result.response() == null || result.response().isBlank()) {
                    throw new IllegalStateException("Selected agent returned no successful response.");
                }
                yield AgentCoordinatorResponse.completed(
                        decision.action(), result.response(), conversationId.toString()
                );
            }
            case CLARIFICATION_REQUIRED -> AgentCoordinatorResponse.clarification(
                    requireClarification(result.clarificationQuestion()),
                    conversationId.toString()
            );
            case FAILED -> throw new IllegalStateException(
                    firstNonBlank(result.errorMessage(), "Selected agent execution failed.")
            );
            case NEEDS_DEPENDENCY -> throw new IllegalStateException(
                    "Agent dependency orchestration is not supported"
                            + (result.dependencyAgentId() == null || result.dependencyAgentId().isBlank()
                            ? "." : ": " + result.dependencyAgentId() + ".")
            );
        };
    }

    public TaskResumeResult resumeApproved(GovernanceIntervention intervention) {
        if (intervention == null || intervention.status() != GovernanceInterventionStatus.APPROVED) {
            throw new IllegalArgumentException("Only an approved intervention can resume a task.");
        }
        PendingAgentTaskRegistry.Claim claim =
                pendingAgentTaskRegistry.claim(intervention.interventionId());
        if (claim.status() != PendingAgentTaskRegistry.ClaimStatus.FOUND) {
            String reason = claim.status() == PendingAgentTaskRegistry.ClaimStatus.EXPIRED
                    ? "Pending task context expired before approval could resume it."
                    : "Pending task context is unavailable; the application may have restarted or evicted it.";
            recordResumeFailure(intervention, reason);
            return new TaskResumeResult("FAILED", reason, null);
        }

        PendingAgentTaskRegistry.PendingTask task = claim.task();
        if (!matches(intervention, task)) {
            String reason = "Pending task context does not match the approved intervention.";
            recordResumeFailure(intervention, reason);
            return new TaskResumeResult("FAILED", reason, null);
        }

        try {
            UUID conversationId = task.conversationId();
            AgentCoordinatorResponse response;
            if (AgentTarget.EMAIL.name().equals(task.targetAgent())) {
                Optional<ReportArtifactHandoff> reportHandoff = task.reportArtifactId() == null
                        || task.reportArtifactId().isBlank()
                        ? Optional.empty()
                        : resolveReportHandoff(conversationId, task.reportArtifactId());
                if (task.reportArtifactId() != null && !task.reportArtifactId().isBlank()
                        && reportHandoff.isEmpty()) {
                    return new TaskResumeResult(
                            "FAILED",
                            "The approved email's registered report attachment is no longer available.",
                            null
                    );
                }
                AgentResult result = approvedActionGrant.resume(
                        intervention,
                        () -> emailAgent.resumeApprovedAction(
                                conversationId,
                                task.sourceWorkflowId(),
                                intervention.interventionId(),
                                task.toolCallId(),
                                task.toolName(),
                                task.arguments(),
                                reportHandoff.orElse(null)
                        )
                );
                if (result == null || result.status() != AgentResultStatus.SUCCESS
                        || result.response() == null || result.response().isBlank()) {
                    throw new IllegalStateException("Approved email action did not complete successfully.");
                }
                response = AgentCoordinatorResponse.completed(
                        task.action(),
                        result.response(),
                        conversationId.toString()
                );
            } else {
                String reason = "Exact approved-action continuation is not supported for tool "
                        + task.toolName() + ".";
                recordResumeFailure(intervention, reason);
                return new TaskResumeResult("FAILED", reason, null);
            }
            sharedContextService.recordAssistantResponse(conversationId, response.response());
            String status;
            if (response.approvalRequired()) {
                status = response.interventionId() != null
                        && !response.interventionId().equals(intervention.interventionId())
                        ? "PENDING_APPROVAL"
                        : "FAILED";
            } else if (response.clarificationRequired()) {
                status = "CLARIFICATION_REQUIRED";
            } else {
                status = "COMPLETED";
            }
            return new TaskResumeResult(
                    status,
                    response.response(),
                    response
            );
        } catch (GovernanceViolationException exception) {
            return new TaskResumeResult("BLOCKED", "Resume stopped by governance policy.", null);
        } catch (RuntimeException exception) {
            recordResumeFailure(intervention, exception.getMessage());
            return new TaskResumeResult(
                    "FAILED",
                    firstNonBlank(exception.getMessage(), "Resumed task execution failed."),
                    null
            );
        }
    }

    private boolean matches(GovernanceIntervention intervention, PendingAgentTaskRegistry.PendingTask task) {
        return intervention.interventionId().equals(task.interventionId())
                && task.conversationId() != null
                && intervention.workflowId().equals(task.sourceWorkflowId())
                && intervention.workflowType() == task.workflowType()
                && intervention.agentName().equals(task.agentName())
                && intervention.toolCallId().equals(task.toolCallId())
                && intervention.capability() == task.capability()
                && java.util.Objects.equals(intervention.conversationId(), task.conversationId())
                && java.util.Objects.equals(intervention.toolName(), task.toolName())
                && java.util.Objects.equals(intervention.argumentsDigest(), task.argumentsDigest())
                && java.util.Objects.equals(
                        intervention.argumentsDigest(),
                        ApprovedActionGrant.argumentsDigest(task.arguments())
                );
    }

    private void recordResumeFailure(GovernanceIntervention intervention, String reason) {
        auditService.record(AuditEvent.executionOutcome(
                intervention.workflowId(),
                intervention.agentName(),
                intervention.workflowType(),
                intervention.toolCallId(),
                intervention.toolName(),
                intervention.capability(),
                AuditEvent.ExecutionStatus.FAILED,
                "RESUME_UNAVAILABLE",
                firstNonBlank(reason, "Resumed task execution failed."),
                null,
                null,
                null,
                null
        ).withConversationId(intervention.conversationId())
                .withInterventionId(intervention.interventionId()));
    }

    public record TaskResumeResult(String status, String message, AgentCoordinatorResponse response) {}

    private void requirePersistedIntervention(
            GovernanceIntervention intervention,
            ApprovalRequiredException exception
    ) {
        if (intervention == null) {
            throw new IllegalStateException(
                    "Approval interruption has no persisted intervention to resume.",
                    exception
            );
        }
    }

    private AgentTarget resolveTargetAgent(IntentDecision decision) {
        AgentTarget target;
        if (decision.targetAgent() != null && !decision.targetAgent().isBlank()) {
            target = switch (decision.targetAgent().strip().toUpperCase(java.util.Locale.ROOT)) {
                case "CUSTOMER_REPORT", "CUSTOMERREPORTAGENT" -> AgentTarget.CUSTOMER_REPORT;
                case "CUSTOMER_SUPPORT", "CUSTOMERSUPPORTAGENT" -> AgentTarget.CUSTOMER_SUPPORT;
                case "EMAIL", "EMAILAGENT" -> AgentTarget.EMAIL;
                default -> throw new IllegalArgumentException(
                        "Unknown or unsupported agent: " + decision.targetAgent()
                );
            };
        } else {
            target = switch (decision.action()) {
                case REPORT_REQUEST -> AgentTarget.CUSTOMER_REPORT;
                case EMAIL_REQUEST -> AgentTarget.EMAIL;
                default -> throw new IllegalStateException("No target agent for action " + decision.action());
            };
        }
        if ((decision.action() == IntentAction.REPORT_REQUEST && target != AgentTarget.CUSTOMER_REPORT)
                || (decision.action() == IntentAction.EMAIL_REQUEST && target != AgentTarget.EMAIL)
                || (decision.action() == IntentAction.GENERAL_RESPONSE && target != AgentTarget.CUSTOMER_SUPPORT)) {
            throw new IllegalArgumentException(
                    "Selected agent is incompatible with routing action " + decision.action() + "."
            );
        }
        return target;
    }

    private String buildReportArtifactRoutingContext(UUID conversationId) {
        List<Artifact> reports = sharedContextService.getArtifacts(conversationId, ArtifactType.REPORT)
                .stream()
                .filter(artifact -> Artifact.GENERATED_PROVENANCE.equals(
                        artifact.metadata().get(Artifact.PROVENANCE_KEY)))
                .filter(artifact -> "PDF".equalsIgnoreCase(artifact.metadata().get(Artifact.FORMAT_KEY)))
                .toList();
        if (reports.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder(
                "Registered generated PDF report artifacts available for possible email attachment "
                        + "(reference data only; select an exact artifact ID):\n"
        );
        for (Artifact report : reports) {
            context.append("ID: ").append(report.id())
                    .append("; title: ").append(report.title())
                    .append("; type: ").append(report.metadata().getOrDefault(
                            Artifact.REPORT_TYPE_KEY, "REPORT"))
                    .append("; created: ").append(report.createdAt())
                    .append('\n');
        }
        return context.toString();
    }

    private Optional<ReportArtifactHandoff> resolveReportHandoff(UUID conversationId, String artifactId) {
        return sharedContextService.getArtifact(conversationId, artifactId)
                .filter(artifact -> artifact.type() == ArtifactType.REPORT)
                .filter(artifact -> Artifact.GENERATED_PROVENANCE.equals(
                        artifact.metadata().get(Artifact.PROVENANCE_KEY)))
                .filter(artifact -> "PDF".equalsIgnoreCase(
                        artifact.metadata().get(Artifact.FORMAT_KEY)))
                .filter(artifact -> artifact.metadata().containsKey(Artifact.REPORT_TYPE_KEY))
                .filter(artifact -> {
                    String storageReference = artifact.metadata().get(Artifact.FILE_REFERENCE_KEY);
                    return storageReference != null && !storageReference.isBlank()
                            && storageReference.equals(artifact.content());
                })
                .map(artifact -> new ReportArtifactHandoff(
                        artifact.id(),
                        artifact.title(),
                        artifact.metadata().get(Artifact.REPORT_TYPE_KEY),
                        artifact.metadata().get(Artifact.FORMAT_KEY),
                        artifact.metadata().get(Artifact.FILE_REFERENCE_KEY),
                        artifact.createdAt(),
                        artifact.metadata()
                ));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String requireClarification(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalStateException("Coordinator returned no valid clarification question.");
        }
        return question;
    }

    public enum IntentAction {
        REPORT_REQUEST,
        REPORT_METADATA_REQUEST,
        EMAIL_REQUEST,
        GENERAL_RESPONSE,
        CLARIFICATION_REQUIRED
    }

    private enum AgentTarget {
        CUSTOMER_SUPPORT,
        CUSTOMER_REPORT,
        EMAIL
    }

    public record IntentDecision(
            IntentAction action,
            String response,
            String clarificationQuestion,
            String targetAgent,
            String reportArtifactId
    ) {
        public IntentDecision(IntentAction action, String response, String clarificationQuestion) {
            this(action, response, clarificationQuestion, null, null);
        }

        public IntentDecision(
                IntentAction action,
                String response,
                String clarificationQuestion,
                String targetAgent
        ) {
            this(action, response, clarificationQuestion, targetAgent, null);
        }
    }
}
