package com.jobdiscovery.explain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the layer that puts a match into words, bound from
 * {@code explanation.*}.
 *
 * <p>Three tiers, in order: the configured LLM provider, then the deterministic
 * templated explainer, then a clear error. The ranking never depends on any of
 * them — an explanation is commentary on a score that already exists.
 *
 * @param provider   {@code none}, {@code ollama} or {@code claude}. Defaults to
 *                   {@code none} so a fresh clone works with no key, no model
 *                   download and no account: {@code &explain=true} still returns
 *                   something useful, marked as templated
 * @param fallback   what to do when the provider is unavailable —
 *                   {@code templated} (default) or {@code error}. Choose
 *                   {@code error} when you would rather see an outage than a
 *                   quietly degraded answer
 * @param maxMatches how many ranked results get an explanation. Only a cost
 *                   control for the paid provider; the template is free
 */
@ConfigurationProperties(prefix = "explanation")
public record ExplanationProperties(
        @DefaultValue("none") Provider provider,
        @DefaultValue("templated") Fallback fallback,
        @DefaultValue("5") int maxMatches,
        @DefaultValue Claude claude,
        @DefaultValue Ollama ollama) {

    /** Where the prose comes from. */
    public enum Provider { NONE, OLLAMA, CLAUDE }

    /** What happens when the provider cannot answer. */
    public enum Fallback { TEMPLATED, ERROR }

    /**
     * Anthropic Messages API. Metered per token from a prepaid balance — a
     * Claude Pro subscription does not fund it.
     *
     * @param model  changing this is a deliberate decision, not a way to
     *               quietly save money
     * @param effort the evidence arrives pre-computed and only needs phrasing,
     *               so {@code low} is right here
     */
    public record Claude(
            String apiKey,
            @DefaultValue("https://api.anthropic.com") String baseUrl,
            @DefaultValue("claude-opus-5") String model,
            @DefaultValue("2048") int maxTokens,
            @DefaultValue("low") String effort) {
    }

    /**
     * A local Ollama server, reached over the compose network. Free, needs no
     * key, and keeps the prompt on this machine.
     *
     * @param model      a small instruct model is enough. The rule engine has
     *                   already done the judgement; this only writes it up
     * @param numPredict output token ceiling — two or three sentences
     */
    public record Ollama(
            @DefaultValue("http://ollama:11434") String baseUrl,
            @DefaultValue("llama3.2:3b") String model,
            @DefaultValue("0.3") double temperature,
            @DefaultValue("300") int numPredict) {
    }
}
