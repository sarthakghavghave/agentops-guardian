package com.agentops.guardian.governance;

public class GovernanceViolationException extends RuntimeException {
    public GovernanceViolationException(String message) {
        super(message);
    }
}