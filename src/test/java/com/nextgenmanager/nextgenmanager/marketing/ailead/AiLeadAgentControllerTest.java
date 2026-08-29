package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The controller's whole job is the relay: pass the caller's bearer token through unchanged, and
 * turn the client's two failure modes into the right HTTP response -- an agent-side error gets
 * relayed verbatim, a connection failure becomes a 502 rather than a stack trace reaching the
 * browser. The role gate (@RequiresSalesAccess) is a PreAuthorize annotation exercised by Spring
 * Security's method interceptor, not by calling the controller directly -- this file follows the
 * same plain-Mockito convention as the other controller tests in this module.
 */
@ExtendWith(MockitoExtension.class)
class AiLeadAgentControllerTest {

    @Mock
    private AiLeadAgentClient client;

    // A real value object rather than a mock: "is the agent configured" is a one-line rule over a
    // string, and stubbing it would let the tests below pass against a gate that never ran.
    @Spy
    private AiLeadAgentProperties properties = new AiLeadAgentProperties("http://agent.internal:8090");

    @InjectMocks
    private AiLeadAgentController controller;

    private static final String TOKEN = "Bearer abc123";

    @Test
    void statsRelaysTheCallersTokenAndTheAgentsResponseVerbatim() {
        when(client.stats(TOKEN)).thenReturn(ResponseEntity.ok("{\"review_required\":3}"));

        ResponseEntity<String> response = controller.stats(TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"review_required\":3}");
        verify(client).stats(TOKEN);
    }

    @Test
    void queueForwardsPaginationParamsToTheClient() {
        when(client.queue(TOKEN, "APPROVED", 2, 10)).thenReturn(ResponseEntity.ok("{\"content\":[]}"));

        controller.queue(TOKEN, "APPROVED", 2, 10);

        verify(client).queue(TOKEN, "APPROVED", 2, 10);
    }

    @Test
    void queueActionForwardsTheRawBodyAndTaskId() {
        String body = "{\"action\":\"APPROVE\",\"notes\":\"looks right\"}";
        when(client.queueAction(TOKEN, 42L, body)).thenReturn(ResponseEntity.ok("{\"status\":\"APPROVED\"}"));

        ResponseEntity<String> response = controller.queueAction(TOKEN, 42L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).queueAction(eq(TOKEN), eq(42L), bodyCaptor.capture());
        // Passed through untouched -- the controller does not parse or re-encode it. The
        // reviewer's identity inside this payload comes from the agent decoding the same
        // relayed token, not from anything this layer adds or trusts from the body.
        assertThat(bodyCaptor.getValue()).isEqualTo(body);
    }

    @Test
    void anAgentSideErrorIsRelayedWithItsOwnStatusAndBody() {
        // The agent's own 404 for an unknown task -- must reach the browser as a 404 with the
        // agent's message, not collapsed into a generic failure.
        when(client.runs(TOKEN, 999L))
                .thenThrow(new AiAgentResponseException(HttpStatus.NOT_FOUND, "{\"detail\":\"Review task 999 not found\"}"));

        ResponseEntity<String> response = controller.runs(TOKEN, 999L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Review task 999 not found");
    }

    @Test
    void anUnreachableAgentBecomesABadGatewayNotAStackTrace() {
        when(client.health(TOKEN))
                .thenThrow(new AiAgentUnavailableException("AI Lead Agent service is unreachable", new RuntimeException("connection refused")));

        ResponseEntity<String> response = controller.health(TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).contains("unreachable");
    }

    @Test
    void pollTriggersAnImmediateCycleAndRelaysTheCount() {
        when(client.poll(TOKEN)).thenReturn(ResponseEntity.ok("{\"emails_processed\":4}"));

        ResponseEntity<String> response = controller.poll(TOKEN);

        assertThat(response.getBody()).contains("emails_processed");
    }

    @Test
    void anUnconfiguredAgentIsNotProxiedAtAllAndSaysSoDistinctlyFrom502() {
        // No ai-agent.base-url on this deployment: the feature does not exist here, which is a
        // different answer from "it exists but is down" -- and the difference matters, because
        // only one of them is worth investigating. The client must not be touched: there is
        // nothing to dial.
        AiLeadAgentController disabled = new AiLeadAgentController(client, new AiLeadAgentProperties(""));

        ResponseEntity<String> response = disabled.stats(TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("Not Configured");
        verifyNoInteractions(client);
    }
}
