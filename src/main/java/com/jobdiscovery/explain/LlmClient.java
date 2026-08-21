package com.jobdiscovery.explain;

/**
 * One LLM provider, reduced to the only thing this project asks of a model:
 * given a system prompt and a user prompt, return some prose.
 *
 * <p>Deliberately narrow. The rule engine has already decided how good the match
 * is; a provider here cannot influence that, only phrase it. That is why
 * swapping a frontier model for a 3-billion-parameter one running on the same
 * laptop is a configuration change rather than a redesign.
 */
public interface LlmClient {

    /** Which provider this is, for {@code explanationSource} on the response. */
    String name();

    /** True when this provider has everything it needs to be called. */
    boolean isConfigured();

    /**
     * Sends one prompt and returns the visible answer.
     *
     * @throws ExplanationException if unconfigured, refused, or unreachable
     */
    String complete(String system, String userPrompt);
}
