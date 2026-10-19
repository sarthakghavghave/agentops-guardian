# AgentOps Guardian

AgentOps Guardian is a Spring Boot project built to add governance and control around AI tool use in customer-facing workflows.

The main goal is simple: protect sensitive customer data and prevent unsafe tool actions before they happen. The project sits between the AI agent and the tool layer, checks the workflow state, applies policy rules, evaluates risk, and, when needed, requires human approval.

## Problem statement

AI agents can call tools quickly and without enough context. In customer workflows, this can lead to:

- raw customer data being used in unsafe tool calls
- external actions like email sending without review
- hidden policy violations or workflow misuse
- weak auditability of what happened during the interaction

This project adds a guardrail layer so that tool calls are checked before execution.

## Architecture

The project follows a simple layered design:

1. Agent layer
   - `CustomerReportAgent`, `CustomerSupportAgent`, and `EmailAgent` create the AI chat client and attach their scoped tools.
   - The agents start a workflow context before a request and clear it after completion.

2. Governance layer
   - `GuardianToolCallingManager` intercepts tool calls before execution.
   - It validates workflow transitions, policy decisions, and risk assessment.
   - It blocks high-risk or disallowed actions and creates intervention records when approval is required.

3. Policy and risk evaluation
   - `GovernancePolicyEngine` evaluates configured business rules.
   - `RiskEvaluator` scores actions based on data classification and workflow context.
   - `WorkflowContext` tracks workflow state, node position, and data sensitivity.

4. Intervention and approval
   - `InterventionService` stores pending approvals and resolves them with approve/reject logic.
   - `InterventionController` exposes approval endpoints.
   - This keeps approval decision-making separate from the actual tool execution.

5. Audit and traceability
   - `AuditService` records lifecycle events, risk events, and execution outcomes.
   - Workflow action and state transitions are tracked so the system remains inspectable.

6. Data and tools
   - Domain models cover customers, orders, products, and related records.
   - Tool classes wrap data access and external actions like email and reporting.
   - Dataset import services load sample data from CSV files for local testing.

7. Request coordination
   - `AgentCoordinator` uses the configured Gemini chat client only to classify user intent.
   - It dispatches report requests to `CustomerReportAgent`; it dispatches email requests to `EmailAgent` only when an explicit report handoff and recipient are supplied.
   - The coordinator has no tools and never executes model-generated methods or code.

## Project structure

```text
agentops-guardian/
├── data/
│   ├── customers.csv
│   ├── orders.csv
│   ├── order_items.csv
│   ├── products.csv
│   ├── reviews.csv
│   └── users.csv
|
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/agentops/guardian/
│   │   │       ├── agent/
│   │   │       ├── controller/
│   │   │       ├── coordinator/
│   │   │       ├── domain/
│   │   │       ├── governance/
│   │   │       ├── ingestion/
│   │   │       ├── repository/
│   │   │       ├── service/
│   │   │       ├── tool/
|   |   |       └──AgentOpsGuardianApplication.java
|   |   |
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── prompts/
│   │       └── static/
|   |
│   └── test/
│       └── java/
|
├── HELP.md
├── LICENSE
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

## Tech stack

- Java 25
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Google GenAI model integration
- Spring Data JPA
- PostgreSQL
- Maven
- CSV-based sample data loading

## Current status

This project is currently a working governance prototype for AI tool-use control.

It already includes:

- workflow-aware tool interception
- policy evaluation before execution
- risk scoring for tool actions
- approval-required intervention flow
- audit event recording
- basic workflow tracking and data classification checks
- REST endpoints for intervention review

This is not a full production governance platform yet. It is a strong foundation for a controlled AI workflow system with room for more production hardening.

## Evaluation foundation

The `governance.evaluation` package supports manually run demonstrations; it does not execute scenarios, simulate attacks, change governance decisions, persist synthetic results, or expose an evaluation API.

- Compare explicitly paired Governance ON/OFF runs using observed tool duration, duration difference, and overhead percentage (when both durations exist and OFF duration is non-zero).
- Derive Guardian evaluation and intervention durations from available timestamps. Report safety and outcomes from persisted audit/intervention evidence; use manually observed outcomes only when evidence cannot establish one.
- Missing evidence yields `UNKNOWN` outcomes or unavailable timings. No historical baseline or prevention statistics are fabricated.
- Audit events timestamp proposals, policy decisions, intervention lifecycle, and execution outcomes, but not tool-start or internal Guardian processing boundaries. Record tool start separately to measure total tool duration.
- Proposal-to-policy-decision timing includes audit persistence; it is not isolated Guardian CPU time. Governance mode is not stored in audit events, so label each observation explicitly—never assume an existing workflow was Governance OFF.

## Reporting and communication boundary

- **Report generation:** `CustomerReportAgent` uses `CustomerDataTools` and `ReportTools`. It creates PDF reports under `agentops.reports.directory` (default `./data/reports`; configure with `REPORTS_DIRECTORY`). After a successful write, a bounded, conversation-scoped in-memory artifact stores the PDF reference and metadata—not the full report text. Caller-supplied report handoffs are marked separately from generated reports.
- **Email and history:** `EmailAgent` uses only `CommunicationTools`; it does not retrieve customer data or generate reports. Before the decision model receives shared history, `readConversationHistory` is governed in the `EMAIL_COMMUNICATION` workflow. Verified customer-read evidence for that conversation blocks transcript access; the current request and registered report metadata remain available, and report attachment handoff is unchanged.
- **Decision and send:** The decision model returns a structured send, clarification, or unsupported decision. Java validates the recipient and selected report, then sends only through the governed communication-tool path. Drafts are not persisted or editable; draft-only requests receive clarification without invoking communication tools.
- **Governance:** Enabled by default. `GOVERNANCE_ENABLED=false` disables policy, risk, approval-intervention, and conversation-history enforcement for the demo; ordinary validation and tool execution remain active.
- **Email boundary:** Gmail SMTP submission success means provider acceptance, not confirmed inbox delivery. The recipient must exactly match an entry in `EMAIL_ALLOWED_RECIPIENTS`; an empty allowlist fails closed.
- **Workflow capabilities:** `EMAIL_COMMUNICATION` covers governed history access and `SEND_EMAIL`; `CUSTOMER_REPORTING` grants customer-data retrieval and report generation.

Configure Gmail SMTP credentials and `EMAIL_ALLOWED_RECIPIENTS` through environment variables to enable real email sending. Keep the App Password out of source control. SMTP acceptance does not guarantee inbox delivery; automated tests use a mocked mail sender.

`POST /api/agent-coordinator/run` accepts a JSON body with a required `message` and optional `conversationId`, `recipient`, `report`, `subjectInstructions`, and `bodyInstructions`. The first request creates an in-memory conversation and returns its UUID; later requests may send that UUID to continue. Conversation history uses a 12-message sliding window; report artifacts and execution records are isolated by conversation, expire after 30 minutes idle, and are capped at 500 active conversations per application instance. Report content is bounded to 30,000 characters, and email instructions to 4,000 characters. Unknown IDs return 404, expired IDs return 410, and malformed IDs return 400. Memory is process-local and is lost on restart.

When a tool action requires approval, the coordinator response includes `approvalRequired: true` and its `interventionId`. `POST /api/interventions/{id}/approve` persists the approval and re-enters the original agent operation through its normal path; policy and risk are evaluated again before tool execution. The one-use continuation only applies when the fresh proposal matches the approved tool and argument digest. It does not grant access to governed conversation history, so a history policy block remains authoritative. Rejection terminates the pending task. Continuations are in-memory, expire after 30 minutes, and cannot resume after process restart or eviction; in those cases approval is still recorded but the response reports that task context is unavailable.

Bounded user turns and appropriate assistant responses are retained in chat history. Email addresses and phone-like strings in those turns are redacted. Typed report/session state is kept separately from the transcript; prior assistant messages are never proof that an operation succeeded. Generic email does not require a report, and a draft request does not invoke communication tools. Persistent/shared context across application instances remains out of scope.

## Future work

Some useful next steps are:

- persistent conversation storage and shared agent session context, if required
- durable session context shared across application instances
- persist evaluation-run provenance and explicitly verified governance mode if durable comparison records are needed
- stronger RBAC and auth around approval operations
- better persistence and reporting for interventions and audit trails
- notification flow for pending approvals
- richer policy configuration and UI management
- expanded workflow graphs and tool coverage
- deployment-ready security and environment configuration

## How to run

From the project root:

```bash
./mvnw clean install
./mvnw spring-boot:run
```

Then use the app endpoints or local API calls to test prompts, workflows, and intervention behavior.

The Guardian dashboard's Control panel switch reads and updates the running governance setting through
`GET` and `PUT /api/governance/settings`. The change is runtime-only and resets to `GOVERNANCE_ENABLED`
when the backend restarts. This demo endpoint is not authenticated; keep it on a trusted local network
and do not expose it publicly.

## Summary

AgentOps Guardian is a practical governance layer for AI-powered customer workflows. It helps prevent unsafe tool actions, tracks workflow state, evaluates policy and risk, and forces approvals when needed. The project is a solid example of AI tool guardrails built around workflow context, policy checks, and traceable human intervention.
