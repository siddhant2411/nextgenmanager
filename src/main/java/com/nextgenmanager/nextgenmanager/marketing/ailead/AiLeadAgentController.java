package com.nextgenmanager.nextgenmanager.marketing.ailead;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

/**
 * Thin proxy to the Python AI Lead Agent so the browser never talks to it directly.
 *
 * Every method forwards the caller's own bearer token unchanged -- the agent verifies it
 * independently against the same ERP JWT secret, so this controller's only job is the role gate
 * every other sales screen already has ({@link RequiresSalesAccess}), plus turning "the agent is
 * unreachable" into a clean 502 instead of a stack trace.
 *
 * The agent is optional (see {@link AiLeadAgentProperties}). On a deployment that does not
 * configure one, every route here answers 503 "not configured" rather than 502 "unreachable":
 * the two mean different things to whoever is looking at the response, and only one of them is
 * something to go and investigate. The UI hides the review tab off the same fact, read from
 * {@code /api/features}, so a user should not reach these routes at all -- this is the gate that
 * makes that true rather than merely apparent.
 */
@RestController
@RequestMapping("/api/ai-lead-agent")
@RequiresSalesAccess
public class AiLeadAgentController {

    private final AiLeadAgentClient client;
    private final AiLeadAgentProperties properties;

    public AiLeadAgentController(AiLeadAgentClient client, AiLeadAgentProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return relay(() -> client.health(authorization));
    }

    @GetMapping("/stats")
    public ResponseEntity<String> stats(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return relay(() -> client.stats(authorization));
    }

    @GetMapping("/queue")
    public ResponseEntity<String> queue(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return relay(() -> client.queue(authorization, status, page, size));
    }

    @PostMapping("/queue/{taskId}/action")
    public ResponseEntity<String> queueAction(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long taskId,
            @RequestBody String body) {
        return relay(() -> client.queueAction(authorization, taskId, body));
    }

    @PostMapping("/poll")
    public ResponseEntity<String> poll(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return relay(() -> client.poll(authorization));
    }

    @GetMapping("/runs/{emailId}")
    public ResponseEntity<String> runs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long emailId) {
        return relay(() -> client.runs(authorization, emailId));
    }

    private ResponseEntity<String> relay(Supplier<ResponseEntity<String>> call) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"AI Lead Agent Not Configured\","
                            + "\"message\":\"This deployment does not run an AI Lead Agent. "
                            + "Set ai-agent.base-url in application.properties to enable it.\"}");
        }
        try {
            ResponseEntity<String> response = call.get();
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (AiAgentResponseException e) {
            return ResponseEntity.status(e.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getBody());
        } catch (AiAgentUnavailableException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"AI Lead Agent Unavailable\",\"message\":\"AI Lead Agent service is unreachable\"}");
        }
    }
}
