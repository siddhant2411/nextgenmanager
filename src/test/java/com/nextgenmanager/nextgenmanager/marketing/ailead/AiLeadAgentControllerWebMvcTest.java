package com.nextgenmanager.nextgenmanager.marketing.ailead;

import com.nextgenmanager.nextgenmanager.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A real Spring MVC request/response cycle for the one detail the plain-Mockito controller test
 * cannot prove: whether {@code @RequestBody String} actually captures a POST body sent with
 * {@code Content-Type: application/json}, or whether Spring's message-converter negotiation
 * rejects it before the controller method ever runs. Calling the controller method directly (as
 * the other test does) skips HTTP binding entirely and would pass even if this were broken.
 *
 * Security filters are switched off here -- @RequiresSalesAccess's role check is a framework
 * concern proven correct in production by every other controller that already uses it, not
 * something this proxy needs to re-verify. What this test exists for is the binding, not the
 * gate.
 */
@WebMvcTest(
        controllers = AiLeadAgentController.class,
        // JwtAuthenticationFilter is picked up by the slice as a Filter bean even with
        // addFilters=false below (that only skips running it, not constructing it), and its own
        // dependency (JwtService, a @Service) is excluded from a web slice -- so without this the
        // context fails to start on an unrelated wiring gap that has nothing to do with what this
        // test is proving.
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
// The controller refuses to proxy at all unless an agent is configured, and a web slice does not
// component-scan @Component beans -- so the real properties bean is imported and given a value,
// rather than mocked. Mocking it would leave every assertion below sitting behind a gate that a
// default-false stub had already closed.
@Import(AiLeadAgentProperties.class)
@TestPropertySource(properties = "ai-agent.base-url=http://agent.internal:8090")
@AutoConfigureMockMvc(addFilters = false)
class AiLeadAgentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiLeadAgentClient client;

    @Test
    void jsonRequestBodyReachesTheControllerAsRawText() throws Exception {
        String body = "{\"action\":\"APPROVE\",\"notes\":\"looks right\"}";
        when(client.queueAction(eq("Bearer abc123"), eq(42L), eq(body)))
                .thenReturn(ResponseEntity.ok("{\"status\":\"APPROVED\"}"));

        mockMvc.perform(post("/api/ai-lead-agent/queue/42/action")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer abc123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"APPROVED\"}"));
    }

    @Test
    void queryParamsBindWithTheirDeclaredDefaults() throws Exception {
        when(client.queue("Bearer abc123", "PENDING", 0, 50))
                .thenReturn(ResponseEntity.ok("{\"content\":[]}"));

        mockMvc.perform(get("/api/ai-lead-agent/queue")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer abc123"))
                .andExpect(status().isOk());
    }

    // A "missing Authorization header" case is deliberately not covered here: with
    // addFilters=false (needed above just to avoid pulling in JwtAuthenticationFilter's
    // datasource-backed dependency at context-startup) the real security filter chain that would
    // normally reject an unauthenticated request with 401 before it ever reaches this controller
    // is not present, so that scenario cannot be represented faithfully in this slice.
}
