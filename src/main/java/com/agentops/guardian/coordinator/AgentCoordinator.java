package com.agentops.guardian.coordinator;

import com.agentops.guardian.agent.CustomerReportAgent;
import com.agentops.guardian.agent.EmailAgent;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.coordinator.dto.AgentCoordinatorResponse;
import com.agentops.guardian.tool.ReportTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AgentCoordinator {

    private static final String SYSTEM_PROMPT = """
            Classify the user's message into exactly one supported action:
            REPORT_REQUEST, EMAIL_REQUEST, GENERAL_RESPONSE, or CLARIFICATION_REQUIRED.

            Return a structured response with the action and only the matching text field:
            - REPORT_REQUEST when the user clearly asks for a customer report.
            - EMAIL_REQUEST when the user clearly asks to email a report.
            - GENERAL_RESPONSE for a clear request that is not a report or email request.
            - CLARIFICATION_REQUIRED when intent is ambiguous or the request cannot be routed.

            For GENERAL_RESPONSE, provide a concise response without claiming any action was run.
            For CLARIFICATION_REQUIRED, ask a targeted question.
            Do not claim a report was generated or an email was sent. Do not select tools,
            agents, class names, methods, or code.
            """;

    private final ChatClient intentClient;
    private final CustomerReportAgent customerReportAgent;
    private final EmailAgent emailAgent;

    public AgentCoordinator(
            ChatClient.Builder chatClientBuilder,
            CustomerReportAgent customerReportAgent,
            EmailAgent emailAgent
    ) {
        this.intentClient = chatClientBuilder.build();
        this.customerReportAgent = customerReportAgent;
        this.emailAgent = emailAgent;
    }

    public AgentCoordinatorResponse handle(AgentCoordinatorRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("A user message is required.");
        }

        IntentDecision decision = intentClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(request.message())
                .call()
                .entity(IntentDecision.class);
        if (decision == null || decision.action() == null) {
            throw new IllegalStateException("Coordinator returned no supported action.");
        }

        return switch (decision.action()) {
            case REPORT_REQUEST -> AgentCoordinatorResponse.completed(
                    decision.action(),
                    requireAgentResult(customerReportAgent.generateReport(request.message()), "Report agent")
            );
            case EMAIL_REQUEST -> routeEmail(request);
            case GENERAL_RESPONSE -> AgentCoordinatorResponse.completed(
                    decision.action(),
                    requireAgentResult(decision.response(), "Coordinator")
            );
            case CLARIFICATION_REQUIRED -> AgentCoordinatorResponse.clarification(
                    requireAgentResult(decision.clarificationQuestion(), "Coordinator")
            );
        };
    }

    private AgentCoordinatorResponse routeEmail(AgentCoordinatorRequest request) {
        if (request.report() == null && isMissing(request.recipient())) {
            return AgentCoordinatorResponse.clarification(
                    "Please provide the report to email and the recipient's email address."
            );
        }
        if (request.report() == null) {
            return AgentCoordinatorResponse.clarification("Please provide the report to email.");
        }
        if (isMissing(request.recipient())) {
            return AgentCoordinatorResponse.clarification(
                    "Which email address should receive the supplied report?"
            );
        }

        AgentCoordinatorRequest.ReportHandoff handoff = request.report();
        if (handoff.type() == null || handoff.content() == null || handoff.content().isBlank()) {
            return AgentCoordinatorResponse.clarification(
                    "Please provide the report type and non-empty report content."
            );
        }
        if (handoff.customerCount() < 0) {
            throw new IllegalArgumentException("Report customer count cannot be negative.");
        }

        ReportTools.Report report = new ReportTools.Report(
                handoff.type(),
                handoff.redacted(),
                handoff.customerCount(),
                handoff.content()
        );
        String result = emailAgent.emailReport(report, request.recipient());
        return AgentCoordinatorResponse.completed(
                IntentAction.EMAIL_REQUEST,
                requireAgentResult(result, "Email agent")
        );
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private String requireAgentResult(String result, String source) {
        if (result == null || result.isBlank()) {
            throw new IllegalStateException(source + " returned no result.");
        }
        return result;
    }

    public enum IntentAction {
        REPORT_REQUEST,
        EMAIL_REQUEST,
        GENERAL_RESPONSE,
        CLARIFICATION_REQUIRED
    }

    public record IntentDecision(
            IntentAction action,
            String response,
            String clarificationQuestion
    ) {
    }
}
