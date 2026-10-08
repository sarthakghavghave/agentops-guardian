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
   - `CustomerReportAgent` and `CustomerSupportAgent` create the AI chat client and attach tools.
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

## Future work

Some useful next steps are:

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

## Summary

AgentOps Guardian is a practical governance layer for AI-powered customer workflows. It helps prevent unsafe tool actions, tracks workflow state, evaluates policy and risk, and forces approvals when needed. The project is a solid example of AI tool guardrails built around workflow context, policy checks, and traceable human intervention.
