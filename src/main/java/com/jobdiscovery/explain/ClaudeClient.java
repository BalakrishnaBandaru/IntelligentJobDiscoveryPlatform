package com.jobdiscovery.explain;

import com.jobdiscovery.explain.dto.ClaudeMessageRequest;
import com.jobdiscovery.explain.dto.ClaudeMessageResponse;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client over the Anthropic Messages API, built the same way as
 * {@link com.jobdiscovery.source.adzuna.AdzunaClient} — a plain
 * {@link RestClient} against a documented REST endpoint, so the request that
 * goes over the wire is visible in one file.
 *
 * <p>An official Java SDK exists ({@code com.anthropic:anthropic-java}); direct
 * REST was chosen deliberately to keep this client the same shape as the three
 * job-source clients and to avoid a new dependency.
 */
@Component
public class ClaudeClient implements LlmClient {

    /** Required on every request; pins the wire format. */
    private static final String API_VERSION = "2023-06-01";

    /**
     * Opts into server-side refusal fallbacks. If a safety classifier declines
     * the request, the API routes to another model rather than returning
     * nothing — which for a job-explanation prompt should be vanishingly rare,
     * but costs nothing to enable.
     */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final RestClient restClient;
    private final ExplanationProperties.Claude properties;
    private final boolean enabled;

    public ClaudeClient(ExplanationProperties parent) {
        this.properties = parent.claude();
        this.enabled = parent.provider() == ExplanationProperties.Provider.CLAUDE;

        // Fail fast rather than tie up the request thread. Generation is slower
        // than a job-board lookup, so the read timeout is correspondingly longer.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(60_000);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "claude";
    }

    /** True when Claude is the selected provider and a key is present. */
    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(properties.apiKey());
    }

    /**
     * Sends one prompt and returns the visible answer.
     *
     * @throws ExplanationException if unconfigured, refused, or unreachable
     */
    @Override
    public String complete(String system, String userPrompt) {
        if (!isConfigured()) {
            throw new ExplanationException(ExplanationException.NOT_CONFIGURED,
                    "Claude is not configured. Set ANTHROPIC_API_KEY in your .env file and "
                    + "EXPLANATION_PROVIDER=claude, then recreate the app container. Note that "
                    + "the API is billed from a prepaid credit balance — a Claude Pro "
                    + "subscription does not fund it.");
        }

        ClaudeMessageRequest request = new ClaudeMessageRequest(
                properties.model(),
                properties.maxTokens(),
                system,
                List.of(ClaudeMessageRequest.Message.user(userPrompt)),
                new ClaudeMessageRequest.OutputConfig(properties.effort()),
                "default");

        ClaudeMessageResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .header("anthropic-beta", FALLBACK_BETA)
                    .body(request)
                    .retrieve()
                    .body(ClaudeMessageResponse.class);
        } catch (RestClientResponseException e) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Anthropic API returned HTTP %d. Check ANTHROPIC_API_KEY and the model id. Body: %s"
                            .formatted(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Could not reach the Anthropic API: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Anthropic API returned an empty body.");
        }

        // A refusal arrives as a perfectly ordinary HTTP 200 with no usable
        // content, so it has to be checked before the content is read.
        if ("refusal".equals(response.stopReason())) {
            String category = response.stopDetails() != null
                    ? response.stopDetails().category() : "unknown";
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "The model declined to answer (category: " + category + ").");
        }

        String text = response.text();
        if (!StringUtils.hasText(text)) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "The model returned no text (stop_reason: " + response.stopReason() + ").");
        }
        return text;
    }
}
