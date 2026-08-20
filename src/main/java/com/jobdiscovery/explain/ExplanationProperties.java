package com.jobdiscovery.explain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the LLM that puts a match into words, bound from
 * {@code explanation.*}. The API key comes from the environment via
 * application.yml, so it never reaches source control — same arrangement as the
 * Adzuna and Jooble keys.
 *
 * @param enabled    master switch. Off by default so the app runs, and the
 *                   smoke test passes, on a machine with no API key at all.
 * @param apiKey     Anthropic API key ({@code ANTHROPIC_API_KEY})
 * @param model      the model id. {@code claude-opus-5} is the current default
 *                   model; change it deliberately, not to save money by accident
 * @param maxTokens  ceiling for one explanation. Thinking tokens count towards
 *                   this as well as the visible answer, so it is not as tight as
 *                   a two-sentence reply would suggest
 * @param effort     {@code low}/{@code medium}/{@code high}/{@code xhigh}/{@code max}.
 *                   Low is right here: the evidence arrives pre-computed and the
 *                   model only has to phrase it
 * @param maxMatches how many of the ranked results get an explanation. Each one
 *                   is a separate API call, so this is the cost control
 */
@ConfigurationProperties(prefix = "explanation")
public record ExplanationProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("claude-opus-5") String model,
        @DefaultValue("2048") int maxTokens,
        @DefaultValue("low") String effort,
        @DefaultValue("5") int maxMatches) {
}
