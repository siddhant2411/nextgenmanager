package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * The client is the one place that actually talks to the Python agent over the network, so this
 * is where "does the bearer token really get relayed" and "does a connection failure become the
 * right exception type" need to be proven against real HTTP semantics -- a Mockito mock of
 * RestClient would just assert that the mock was called, not that the request was shaped right.
 */
class AiLeadAgentClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private AiLeadAgentClient client;

    private void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // The constructor calls builder.baseUrl(...).build() itself, so binding the mock server
        // to the same builder instance before construction is what lets it intercept the calls.
        client = new AiLeadAgentClient(builder, new AiLeadAgentProperties("http://agent.internal:8090"));
    }

    @Test
    void relaysTheCallersBearerTokenUnchanged() {
        setUp();
        server.expect(requestTo("http://agent.internal:8090/api/agent/stats"))
                .andExpect(header("Authorization", "Bearer abc123"))
                .andRespond(withSuccess("{\"review_required\":3}", MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = client.stats("Bearer abc123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("review_required");
        server.verify();
    }

    @Test
    void queueForwardsStatusPageAndSizeAsQueryParams() {
        setUp();
        server.expect(requestTo("http://agent.internal:8090/api/agent/queue?status=APPROVED&page=1&size=25"))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        client.queue("Bearer abc123", "APPROVED", 1, 25);

        server.verify();
    }

    @Test
    void queueActionPostsTheRawBodyToTheRightTask() {
        setUp();
        server.expect(requestTo("http://agent.internal:8090/api/agent/queue/42/action"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("{\"action\":\"APPROVE\"}"))
                .andRespond(withSuccess("{\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        client.queueAction("Bearer abc123", 42L, "{\"action\":\"APPROVE\"}");

        server.verify();
    }

    @Test
    void anErrorResponseFromTheAgentIsRelayedNotSwallowed() {
        // A task that does not exist -- the agent's own 404, not a connection problem. The
        // caller needs to see this exact status and body, not a generic 502.
        setUp();
        server.expect(requestTo("http://agent.internal:8090/api/agent/runs/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Review task 999 not found\"}"));

        assertThatThrownBy(() -> client.runs("Bearer abc123", 999L))
                .isInstanceOf(AiAgentResponseException.class)
                .satisfies(e -> {
                    AiAgentResponseException ex = (AiAgentResponseException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getBody()).contains("Review task 999 not found");
                });
    }

    @Test
    void aConnectionFailureBecomesAiAgentUnavailable() {
        // Real RestClient (not the mock server) pointed at a port nothing listens on -- the
        // "the agent process is simply not running" case the frontend needs to tell apart from
        // an ordinary error response.
        AiLeadAgentClient unreachable = new AiLeadAgentClient(RestClient.builder(), new AiLeadAgentProperties("http://127.0.0.1:1"));

        assertThatThrownBy(() -> unreachable.health("Bearer abc123"))
                .isInstanceOf(AiAgentUnavailableException.class);
    }
}
