package com.nextgenmanager.nextgenmanager.marketing.ailead;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Timeouts for the proxy to the Python AI Lead Agent.
 *
 * A poll cycle runs classification and extraction against a local model for every new message,
 * which is genuinely slow on CPU. Left unconfigured, this timed out mid-cycle: the caller
 * reported "agent unreachable" for a request that was still quietly working in the background
 * and would have succeeded with more patience -- confirmed by the queue reflecting the results
 * moments later on a manual refresh. Read is generous for exactly that reason. Connect stays
 * short: if the process genuinely isn't running, that should fail fast, not wait five minutes to
 * say so.
 *
 * Applied via a customizer on the shared auto-configured {@code RestClient.Builder} bean, not by
 * setting a request factory inside {@link AiLeadAgentClient} itself -- that bean is also the one
 * {@link AiLeadAgentClientTest} injects its own builder into ahead of
 * {@code MockRestServiceServer.bindTo(...)}, and a request factory set inside the client's
 * constructor would silently overwrite the mock server's own, breaking the test's interception.
 */
@Configuration
public class AiLeadAgentClientConfig {

    @Bean
    public RestClientCustomizer aiLeadAgentTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofMinutes(5));
            builder.requestFactory(factory);
        };
    }
}
