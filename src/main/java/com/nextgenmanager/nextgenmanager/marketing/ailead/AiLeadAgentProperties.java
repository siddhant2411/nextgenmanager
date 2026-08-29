package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Whether this deployment has an AI Lead Agent at all.
 *
 * The agent is a separate Python service that most installations do not run, so the feature is
 * opt-in: it exists only when {@code ai-agent.base-url} is given a value in the configuration
 * file (or via the {@code AI_AGENT_URL} environment variable it defaults from). Left blank, the
 * proxy answers 503 and the CRM never renders the review tab -- rather than the tab being there
 * and every click failing against a service that was never installed.
 *
 * The default is deliberately empty rather than {@code http://localhost:8090}: a URL that
 * happens to be right on one developer's machine is not the same thing as the operator saying
 * they run this service.
 */
@Component
public class AiLeadAgentProperties {

    private final String baseUrl;

    public AiLeadAgentProperties(@Value("${ai-agent.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(baseUrl);
    }
}
