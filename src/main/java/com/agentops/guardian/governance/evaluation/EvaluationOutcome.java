package com.agentops.guardian.governance.evaluation;

public enum EvaluationOutcome {
    ACTION_EXECUTED,
    ACTION_BLOCKED,
    APPROVAL_REQUIRED,
    APPROVED_AND_EXECUTED,
    REJECTED_AND_PREVENTED,
    FAILED,
    UNKNOWN
}
