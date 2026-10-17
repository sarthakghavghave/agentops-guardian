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

The `governance.evaluation` package supports manually performed, real demonstration scenarios. It records and derives evidence for the operational effect of AgentOps Guardian; it does not execute scenarios, simulate attacks, change governance decisions, persist synthetic results, or expose an evaluation API.

The primary quantitative comparison is an explicitly paired Governance ON versus Governance OFF run: total observed tool-execution duration, duration difference, and overhead percentage when both durations exist and the OFF duration is non-zero. Guardian evaluation and human-intervention durations are derived from available timestamps. Safety and outcome changes are reported from actual persisted audit/intervention evidence; a manually observed outcome is only used when persisted evidence cannot establish one. Missing evidence produces `UNKNOWN` outcomes or unavailable timing values; no baseline is fabricated from historical runs, and no prevention statistics or superficial aggregate counts are produced.

Audit timestamps currently mark proposed action, policy decision, intervention lifecycle, and execution outcome events. They do not separately persist tool execution start or internal Guardian processing start/completion. Execution-start timing must therefore be recorded for a demonstration if total tool duration is required; Guardian evaluation timing derived from proposal/policy audit timestamps is an observed event interval that includes audit persistence time, not isolated CPU time. Governance mode is not stored on audit events, so each demonstration observation must be explicitly labeled, and existing workflows cannot be assumed to be Governance OFF.

## Reporting and communication boundary

`CustomerReportAgent` is limited to `CustomerDataTools` and `ReportTools`. Analytical reports are generated as PDFs and stored locally under `agentops.reports.directory` (default `./data/reports`, configurable with `REPORTS_DIRECTORY`). A bounded in-memory artifact scoped to the current conversation retains the PDF file reference and metadata, not the full report text, and is registered only after the PDF is successfully written. A caller may also provide a report handoff directly; supplied content is marked separately from agent-generated reports.

`EmailAgent` is limited to `CommunicationTools`; it does not retrieve customer data or generate reports. Before the decision model receives shared conversation history, a governed `readConversationHistory` action is evaluated in an `EMAIL_COMMUNICATION` workflow. When verified customer-read evidence exists for that conversation, the policy blocks transcript access; the current request and registered report metadata remain available, and the normal report attachment handoff is unchanged. Governance is enabled by default. Set `GOVERNANCE_ENABLED=false` to disable policy, risk, approval-intervention, and conversation-history enforcement for the demo; ordinary application validation and tool execution remain active. The decision model interprets the available inputs and report artifacts into a structured send, clarification, or unsupported decision. Java validates the decision's recipient and selected report, then sends only through the governed communication-tool path. Persistent drafts and draft editing are not supported; draft-only requests receive a clarification and do not invoke the communication tool. Email submission uses Gmail SMTP. An outbound success response is returned only after Gmail SMTP submission succeeds and describes provider acceptance, not confirmed inbox delivery. Recipients must be explicitly listed in `EMAIL_ALLOWED_RECIPIENTS`; an empty allowlist fails closed. The distinct `EMAIL_COMMUNICATION` workflow identity includes governed history access and the `SEND_EMAIL` capability, while `CUSTOMER_REPORTING` grants customer-data retrieval and report generation.

### Gmail SMTP configuration

Set these environment variables for real email sending. Do not commit or log the Gmail App Password.

| Variable | Required | Purpose |
| --- | --- | --- |
| `GMAIL_USERNAME` | Yes | Gmail account used for SMTP authentication |
| `GMAIL_APP_PASSWORD` | Yes | Google App Password for that account |
| `GMAIL_SENDER` | Yes | Sender address shown on the message (normally the authenticated Gmail account) |
| `EMAIL_ALLOWED_RECIPIENTS` | Yes | Comma-separated exact recipient addresses permitted by the demo |
| `GMAIL_SMTP_HOST` | No | SMTP host; defaults to `smtp.gmail.com` |
| `GMAIL_SMTP_PORT` | No | SMTP port; defaults to `587` with authenticated STARTTLS |
| `GMAIL_SMTP_CONNECTION_TIMEOUT` | No | Connection timeout in milliseconds; defaults to `5000` |
| `GMAIL_SMTP_READ_TIMEOUT` | No | Read timeout in milliseconds; defaults to `5000` |
| `GMAIL_SMTP_WRITE_TIMEOUT` | No | Write timeout in milliseconds; defaults to `5000` |

For a local PowerShell session, set the values before starting the backend:

```powershell
$env:GMAIL_USERNAME = "your-account@gmail.com"
$env:GMAIL_APP_PASSWORD = "your-google-app-password"
$env:GMAIL_SENDER = "your-account@gmail.com"
$env:EMAIL_ALLOWED_RECIPIENTS = "your-account@gmail.com"
```

Create an App Password in the Google Account used for the demo, and keep it outside source control. To manually smoke-test, start the backend with the variables above and governance enabled, use the application to explicitly request an email to an allowlisted mailbox you control, and complete any resulting approval through the intervention UI/API. Verify acceptance in the recipient mailbox. Automated tests use a mocked `JavaMailSender` and never contact Gmail. SMTP acceptance does not guarantee inbox placement; a timeout may occur after Gmail accepted the message, so this implementation reports no success when the send call times out and does not retry automatically. The outcome can then be uncertain, and retrying manually may deliver a duplicate. `ExecutionRecord` and `AuditEvent.ExecutionStatus` currently have no indeterminate state; a small follow-up should add one and expose it to callers, while still avoiding automatic retries.

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
