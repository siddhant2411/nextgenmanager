package com.nextgenmanager.nextgenmanager.common.controller;

import com.nextgenmanager.nextgenmanager.marketing.ailead.AiLeadAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The UI decides whether to render a whole screen off this response, so what matters is that the
 * flag tracks the configuration rather than being hardcoded either way -- a payload that always
 * said true would pass a single-case test and still hide nothing.
 */
class FeatureControllerTest {

    @Test
    void aiLeadAgentIsOnOnlyWhenABaseUrlIsConfigured() {
        ResponseEntity<Map<String, Boolean>> configured =
                new FeatureController(new AiLeadAgentProperties("http://agent.internal:8090")).features();

        assertThat(configured.getBody()).containsEntry("aiLeadAgent", true);
    }

    @Test
    void aiLeadAgentIsOffWhenTheBaseUrlIsBlank() {
        // Blank, not merely absent: an env-var placeholder that nobody filled in resolves to the
        // empty string, which is the common way this ends up unconfigured in a real deployment.
        ResponseEntity<Map<String, Boolean>> blank =
                new FeatureController(new AiLeadAgentProperties("   ")).features();

        assertThat(blank.getBody()).containsEntry("aiLeadAgent", false);
    }
}
