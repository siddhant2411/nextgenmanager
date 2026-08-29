package com.nextgenmanager.nextgenmanager.marketing.ailead;

/** The AI Lead Agent could not be reached at all -- refused, timed out, or unresolvable. */
public class AiAgentUnavailableException extends RuntimeException {
    public AiAgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
