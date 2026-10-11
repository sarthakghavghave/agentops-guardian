package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.agent.AgentResult;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AgentCoordinator {

    private static final String SYSTEM_PROMPT = """
            Classify the current user message into an appropriate routing action:
            REPORT_REQUEST, EMAIL_REQUEST, GENERAL_RESPONSE, or CLARIFICATION_REQUIRED.

            Registered agents:
            - CustomerReportAgent (REPORT_REQUEST): customer data analysis and report generation.
            - EmailAgent (EMAIL_REQUEST): email composition and sending.

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
    private final EmailAgent emailAgent;
    private final SharedContextService sharedContextService;

    public AgentCoordinator(
            ChatClient.Builder chatClientBuilder,
            CustomerReportAgent customerReportAgent,
            EmailAgent emailAgent,
            SharedContextService sharedContextService
    ) {
        this.intentClient = chatClientBuilder.build();
        this.customerReportAgent = customerReportAgent;
        this.emailAgent = emailAgent;
        this.sharedContextService = sharedContextService;
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
            case GENERAL_RESPONSE -> AgentCoordinatorResponse.completed(
                    IntentAction.GENERAL_RESPONSE,
                    firstNonBlank(decision.response(), "I can generate a customer report or send an email."),
                    conversationId.toString()
            );
            case CLARIFICATION_REQUIRED -> AgentCoordinatorResponse.clarification(
                    requireClarification(decision.clarificationQuestion()),
                    conversationId.toString()
            );
            case REPORT_REQUEST, EMAIL_REQUEST -> dispatchAgent(request, decision, conversationId);
        };

        sharedContextService.recordTurn(
                conversationId,
                request.message(),
                response.response()
        );
        return response;
    }

    private AgentCoordinatorResponse dispatchAgent(
            AgentCoordinatorRequest request,
            IntentDecision decision,
            UUID conversationId
    ) {
        AgentTarget target = resolveTargetAgent(decision);
        AgentResult result = executeAgent(target, request, conversationId);
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

    private AgentTarget resolveTargetAgent(IntentDecision decision) {
        AgentTarget target;
        if (decision.targetAgent() != null && !decision.targetAgent().isBlank()) {
            target = switch (decision.targetAgent().strip().toUpperCase(java.util.Locale.ROOT)) {
                case "CUSTOMER_REPORT", "CUSTOMERREPORTAGENT" -> AgentTarget.CUSTOMER_REPORT;
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
                || (decision.action() == IntentAction.EMAIL_REQUEST && target != AgentTarget.EMAIL)) {
            throw new IllegalArgumentException(
                    "Selected agent is incompatible with routing action " + decision.action() + "."
            );
        }
        return target;
    }

    private AgentResult executeAgent(
            AgentTarget target,
            AgentCoordinatorRequest request,
            UUID conversationId
    ) {
        return switch (target) {
            case CUSTOMER_REPORT -> customerReportAgent.execute(conversationId, request);
            case EMAIL -> emailAgent.execute(conversationId, request);
        };
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
        EMAIL_REQUEST,
        GENERAL_RESPONSE,
        CLARIFICATION_REQUIRED
    }

    private enum AgentTarget {
        CUSTOMER_REPORT,
        EMAIL
    }

    public record IntentDecision(
            IntentAction action,
            String response,
            String clarificationQuestion,
            String targetAgent
    ) {
        public IntentDecision(IntentAction action, String response, String clarificationQuestion) {
            this(action, response, clarificationQuestion, null);
        }
    }
}
