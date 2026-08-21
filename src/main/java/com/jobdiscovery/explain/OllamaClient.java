package com.jobdiscovery.explain;

import com.jobdiscovery.explain.dto.OllamaChatRequest;
import com.jobdiscovery.explain.dto.OllamaChatResponse;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client over a local <a href="https://ollama.com">Ollama</a> server,
 * reached over the compose network.
 *
 * <p>Free, needs no API key, and the prompt never leaves the machine. That is
 * viable here because of how the pipeline is split: the rule engine has already
 * decided how good the match is, and the model only has to write that up. A
 * small instruct model is entirely sufficient for "turn these facts into two
 * sentences" — the hard part was never delegated to it.
 *
 * <p>Ollama runs behind the {@code llm} compose profile and its model must be
 * pulled once before first use, so "not running" and "model not pulled" are
 * both ordinary states rather than faults. Both surface as an
 * {@link ExplanationException}, which the explainer turns into a templated
 * answer rather than an outage.
 */
@Component
public class OllamaClient implements LlmClient {

    private final RestClient restClient;
    private final ExplanationProperties.Ollama properties;
    private final boolean enabled;

    public OllamaClient(ExplanationProperties parent) {
        this.properties = parent.ollama();
        this.enabled = parent.provider() == ExplanationProperties.Provider.OLLAMA;

        // A small model on CPU is not fast. The read timeout is generous
        // because a cold first call also pays for loading the model into RAM.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(120_000);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    /**
     * Whether Ollama is the selected provider. Note this does not probe the
     * server — it cannot, cheaply, on every request. An unreachable server is
     * discovered at call time and handled there.
     */
    @Override
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(properties.baseUrl());
    }

    @Override
    public String complete(String system, String userPrompt) {
        if (!isConfigured()) {
            throw new ExplanationException(ExplanationException.NOT_CONFIGURED,
                    "Ollama is not the selected provider. Set EXPLANATION_PROVIDER=ollama.");
        }

        OllamaChatRequest request = new OllamaChatRequest(
                properties.model(),
                List.of(OllamaChatRequest.Message.system(system),
                        OllamaChatRequest.Message.user(userPrompt)),
                false,
                new OllamaChatRequest.Options(properties.temperature(), properties.numPredict()));

        OllamaChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (RestClientResponseException e) {
            // A 404 here almost always means the model has not been pulled yet,
            // which is a one-command fix worth naming explicitly.
            String hint = e.getStatusCode().value() == 404
                    ? " Pull it first: docker exec jobdiscovery-ollama ollama pull "
                      + properties.model()
                    : "";
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Ollama returned HTTP %d for model '%s'.%s Body: %s"
                            .formatted(e.getStatusCode().value(), properties.model(), hint,
                                    e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Could not reach Ollama at " + properties.baseUrl()
                    + ". Start it with: docker compose --profile llm up -d ollama. Cause: "
                    + e.getMessage(), e);
        }

        if (response == null) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Ollama returned an empty body.");
        }
        // Ollama reports some failures as a 200 with an "error" field set.
        if (StringUtils.hasText(response.error())) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Ollama reported an error: " + response.error());
        }

        String text = response.text();
        if (!StringUtils.hasText(text)) {
            throw new ExplanationException(ExplanationException.UPSTREAM_FAILED,
                    "Ollama returned no text (done_reason: " + response.doneReason() + ").");
        }
        return text;
    }
}
