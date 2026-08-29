package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.function.Supplier;

/**
 * Server-to-server proxy to the Python AI Lead Agent.
 *
 * The browser never reaches the agent directly. Every call here forwards the caller's own bearer
 * token unchanged (token relay), so the agent's existing JWT verification does not need to
 * change -- it is still checking the same ERP-issued token, just relayed instead of sent straight
 * from the browser.
 */
@Component
public class AiLeadAgentClient {

    private final RestClient restClient;

    /**
     * The base URL comes from {@link AiLeadAgentProperties} rather than a second {@code @Value}
     * here so there is only one definition of "is the agent configured on this deployment" --
     * the same object the controller asks before it lets a call through at all. When it is
     * blank the feature is off and nothing reaches this client.
     */
    public AiLeadAgentClient(RestClient.Builder builder, AiLeadAgentProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public ResponseEntity<String> health(String authorization) {
        return get("/api/agent/health", authorization);
    }

    public ResponseEntity<String> stats(String authorization) {
        return get("/api/agent/stats", authorization);
    }

    public ResponseEntity<String> queue(String authorization, String status, int page, int size) {
        String uri = UriComponentsBuilder.fromPath("/api/agent/queue")
                .queryParam("status", status)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        return get(uri, authorization);
    }

    public ResponseEntity<String> queueAction(String authorization, Long taskId, String body) {
        return post("/api/agent/queue/" + taskId + "/action", authorization, body);
    }

    public ResponseEntity<String> poll(String authorization) {
        return post("/api/agent/poll", authorization, "{}");
    }

    public ResponseEntity<String> runs(String authorization, Long emailId) {
        return get("/api/agent/runs/" + emailId, authorization);
    }

    private ResponseEntity<String> get(String uri, String authorization) {
        return execute(() -> restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .toEntity(String.class));
    }

    private ResponseEntity<String> post(String uri, String authorization, String body) {
        return execute(() -> restClient.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class));
    }

    private ResponseEntity<String> execute(Supplier<ResponseEntity<String>> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            // The agent answered, just with an error -- relay its status and body verbatim
            // rather than collapsing every failure into one generic message.
            throw new AiAgentResponseException(HttpStatusCode.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            // Could not even connect: refused, DNS failure, timeout. This is the "agent is not
            // running" case the frontend needs to tell apart from an ordinary error response.
            throw new AiAgentUnavailableException("AI Lead Agent service is unreachable", e);
        }
    }
}
