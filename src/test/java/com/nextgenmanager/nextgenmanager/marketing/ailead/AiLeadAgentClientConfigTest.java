package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fix for the actual production incident: with no explicit timeout, a poll cycle that
 * ran long (classification/extraction against a local model, per message) timed out mid-flight.
 * The caller reported "AI Lead Agent is not running" for a request the agent was still quietly
 * completing -- confirmed by the queue reflecting the results moments later on a manual refresh.
 * If a future edit drops this customizer, that regression comes back silently; this test is what
 * would catch it.
 */
class AiLeadAgentClientConfigTest {

    @Test
    void readTimeoutIsGenerousEnoughForAFullPollCycle() {
        RestClientCustomizer customizer = new AiLeadAgentClientConfig().aiLeadAgentTimeoutCustomizer();

        RestClient.Builder builder = RestClient.builder();
        customizer.customize(builder);

        ClientHttpRequestFactory factory = extractRequestFactory(builder);
        assertThat(factory).isInstanceOf(SimpleClientHttpRequestFactory.class);

        int readTimeoutMs = (int) ReflectionTestUtils.getField(factory, "readTimeout");
        assertThat(Duration.ofMillis(readTimeoutMs))
                .as("read timeout must comfortably exceed how long a real poll cycle can take")
                .isGreaterThanOrEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void connectTimeoutStaysShortSoAGenuinelyDownAgentFailsFastNotAfterFiveMinutes() {
        RestClientCustomizer customizer = new AiLeadAgentClientConfig().aiLeadAgentTimeoutCustomizer();

        RestClient.Builder builder = RestClient.builder();
        customizer.customize(builder);

        ClientHttpRequestFactory factory = extractRequestFactory(builder);
        int connectTimeoutMs = (int) ReflectionTestUtils.getField(factory, "connectTimeout");
        assertThat(Duration.ofMillis(connectTimeoutMs)).isLessThanOrEqualTo(Duration.ofSeconds(10));
    }

    private static ClientHttpRequestFactory extractRequestFactory(RestClient.Builder builder) {
        // RestClient.Builder has no getter for what was configured -- the only way to observe it
        // is to build and inspect the resulting RestClient's own internal state.
        RestClient client = builder.build();
        return (ClientHttpRequestFactory) ReflectionTestUtils.getField(client, "clientRequestFactory");
    }
}
