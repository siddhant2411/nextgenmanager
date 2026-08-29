package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.springframework.http.HttpStatusCode;

/**
 * The AI Lead Agent answered, just with an error (its own 400/401/404/...).
 *
 * Kept separate from {@link AiAgentUnavailableException} because the two need different HTTP
 * responses from the proxy: a real answer from the agent should be relayed to the caller
 * verbatim, not collapsed into a generic "service unreachable" 502.
 */
public class AiAgentResponseException extends RuntimeException {

    private final HttpStatusCode status;
    private final String body;

    public AiAgentResponseException(HttpStatusCode status, String body) {
        super("AI Lead Agent returned " + status.value());
        this.status = status;
        this.body = body;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
