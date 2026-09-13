package com.agentops.guardian.governance.exception;

public class GovernanceViolationException extends RuntimeException {
    public GovernanceViolationException(String message) {
        super(message);
    }
}