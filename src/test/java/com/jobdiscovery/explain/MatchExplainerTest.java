package com.jobdiscovery.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobdiscovery.explain.ExplanationProperties.Fallback;
import com.jobdiscovery.explain.ExplanationProperties.Provider;
import com.jobdiscovery.scoring.ExperienceRequirement;
import com.jobdiscovery.scoring.JobScore;
import com.jobdiscovery.scoring.ScoreComponent;
import com.jobdiscovery.scoring.SeniorityLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the explanation layer without touching the network.
 *
 * <p>The LLM tier is a stub, so what is asserted here is the part that is ours:
 * the prompt contract, the caching, the cost cap, and — most importantly — that
 * a provider failure degrades to the template instead of taking the shortlist
 * down with it. Whether a model writes a good sentence is not something a unit
 * test can decide.
 */
class MatchExplainerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    /** A provider that never calls out, and remembers what it was asked. */
    private static final class StubClient implements LlmClient {

        private final List<String> prompts = new ArrayList<>();
        private final boolean configured;
        private final RuntimeException failWith;

        StubClient(boolean configured, RuntimeException failWith) {
            this.configured = configured;
            this.failWith = failWith;
        }

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String complete(String system, String userPrompt) {
            prompts.add(userPrompt);
            if (failWith != null) {
                throw failWith;
            }
            return "Explanation #" + prompts.size();
        }
    }

    private static ExplanationProperties props(int maxMatches, Fallback fallback) {
        return new ExplanationProperties(Provider.NONE, fallback, maxMatches,
                new ExplanationProperties.Claude(null, "https://example.invalid",
                        "claude-opus-5", 2048, "low"),
                new ExplanationProperties.Ollama("http://example.invalid",
                        "llama3.2:3b", 0.3, 300));
    }

    private static MatchExplainer explainer(StubClient client, ExplanationProperties properties) {
        return new MatchExplainer(client == null ? List.of() : List.of(client),
                new TemplatedExplainer(), properties);
    }

    private static JobScore match(long id, double score) {
        return new JobScore(id, "Senior Java Developer", "Acme", "Bangalore, Karnataka",
                "ADZUNA", "https://example.com/apply", NOW, score,
                List.of("Java", "Spring Boot"), List.of("Kafka", "Docker"), List.of("backend"),
                SeniorityLevel.SENIOR, ExperienceRequirement.parse("", "5+ years of experience"),
                List.of(ScoreComponent.of("skills", 35, 0.55,
                                "matched 2 of 4 skills; posting text is truncated, so the 2 "
                                + "unmatched count as unknown rather than absent"),
                        ScoreComponent.of("location", 20, 1.0, "'Bangalore' matches preferred"),
                        ScoreComponent.notApplicable("preferredCompany", 5, "none set")),
                null, null);
    }

    // --- prompt contract ---------------------------------------------------

    @Test
    @DisplayName("the prompt carries the score and the evidence behind it")
    void promptCarriesTheEvidence() {
        String prompt = explainer(new StubClient(true, null), props(5, Fallback.TEMPLATED))
                .buildPrompt(match(1L, 68.8));

        assertTrue(prompt.contains("Java"), prompt);
        assertTrue(prompt.contains("Kafka"), "missing skills are evidence too");
        assertTrue(prompt.contains("SENIOR"));
        assertTrue(prompt.contains("skills"), "the breakdown must be included");
        // Dimensions arrive pre-judged, because a small model shown raw points
        // reasons about the arithmetic and gets it backwards.
        assertTrue(prompt.contains("STRONG") || prompt.contains("MODERATE")
                || prompt.contains("WEAK"), prompt);
        // The score is withheld on purpose: shown a number, small models recite
        // it back, and it is already displayed next to the job.
        assertFalse(prompt.contains("68.8"), "the score must not be in the prompt");
    }

    @Test
    @DisplayName("the prompt never contains the raw posting text")
    void promptWithholdsThePosting() {
        // The model is given the rule engine's findings, not the description. If
        // it could read the posting it could form its own view of the fit, which
        // is the one thing this design does not allow.
        String prompt = explainer(new StubClient(true, null), props(5, Fallback.TEMPLATED))
                .buildPrompt(match(1L, 68.8));

        assertFalse(prompt.toLowerCase().contains("description"), prompt);
    }

    // --- cost controls -----------------------------------------------------

    @Test
    @DisplayName("only the top max-matches results are explained")
    void explainsOnlyTheTopMatches() {
        StubClient client = new StubClient(true, null);
        List<JobScore> explained = explainer(client, props(2, Fallback.TEMPLATED))
                .explainAll(List.of(match(1L, 70), match(2L, 60), match(3L, 50), match(4L, 40)));

        assertEquals(4, explained.size(), "every match is returned, explained or not");
        assertNotNull(explained.get(0).explanation());
        assertNotNull(explained.get(1).explanation());
        assertNull(explained.get(2).explanation(), "past the cap, nothing should be produced");
        assertNull(explained.get(3).explanation());
        assertEquals(2, client.prompts.size(), "exactly two calls");
    }

    @Test
    @DisplayName("the same job at the same score is only paid for once")
    void cachesByScoreAndJob() {
        StubClient client = new StubClient(true, null);
        MatchExplainer explainer = explainer(client, props(5, Fallback.TEMPLATED));

        explainer.explainAll(List.of(match(1L, 70)));
        explainer.explainAll(List.of(match(1L, 70)));

        assertEquals(1, client.prompts.size(), "the second request must come from cache");
        assertEquals(1, explainer.cacheSize());
    }

    @Test
    @DisplayName("re-scoring the same job invalidates its cached explanation")
    void rescoringExpiresTheCacheEntry() {
        // Weights are meant to be re-tuned. Keying on the score means a changed
        // number cannot keep an explanation written for the old one.
        StubClient client = new StubClient(true, null);
        MatchExplainer explainer = explainer(client, props(5, Fallback.TEMPLATED));

        explainer.explainAll(List.of(match(1L, 70)));
        explainer.explainAll(List.of(match(1L, 76.7)));

        assertEquals(2, client.prompts.size());
    }

    // --- the three tiers ---------------------------------------------------

    @Test
    @DisplayName("with a working provider, the explanation is attributed to it")
    void llmTierIsLabelled() {
        JobScore explained = explainer(new StubClient(true, null), props(5, Fallback.TEMPLATED))
                .explainAll(List.of(match(1L, 70))).get(0);

        assertEquals("stub", explained.explanationSource());
        assertEquals("Explanation #1", explained.explanation());
    }

    @Test
    @DisplayName("with no provider at all, the template answers")
    void templateAnswersWhenNoProviderIsConfigured() {
        // This is the out-of-the-box state: no key, no model, and &explain=true
        // still returns something useful rather than a 503.
        JobScore explained = explainer(null, props(5, Fallback.TEMPLATED))
                .explainAll(List.of(match(1L, 76.7))).get(0);

        assertEquals(MatchExplainer.SOURCE_TEMPLATED, explained.explanationSource());
        assertTrue(explained.explanation().contains("76.7"), explained.explanation());
    }

    @Test
    @DisplayName("a provider that fails mid-flight degrades to the template, not a 502")
    void providerFailureDegradesToTemplate() {
        // A stopped Ollama container or an exhausted API balance must not take
        // the shortlist down with it — the ranking never needed the model.
        StubClient failing = new StubClient(true,
                new ExplanationException(ExplanationException.UPSTREAM_FAILED, "connection refused"));

        JobScore explained = explainer(failing, props(5, Fallback.TEMPLATED))
                .explainAll(List.of(match(1L, 70))).get(0);

        assertEquals(MatchExplainer.SOURCE_TEMPLATED, explained.explanationSource());
        assertNotNull(explained.explanation());
    }

    @Test
    @DisplayName("fallback=error surfaces the outage instead of hiding it")
    void fallbackErrorPropagates() {
        StubClient failing = new StubClient(true,
                new ExplanationException(ExplanationException.UPSTREAM_FAILED, "connection refused"));

        assertThrows(ExplanationException.class,
                () -> explainer(failing, props(5, Fallback.ERROR))
                        .explainAll(List.of(match(1L, 70))));
    }

    @Test
    @DisplayName("no provider and no fallback is a clear configuration error")
    void noProviderAndNoFallbackFailsLoudly() {
        ExplanationException thrown = assertThrows(ExplanationException.class,
                () -> explainer(null, props(5, Fallback.ERROR))
                        .explainAll(List.of(match(1L, 70))));

        assertEquals(ExplanationException.NOT_CONFIGURED, thrown.getCode());
        assertTrue(thrown.getMessage().contains("EXPLANATION_PROVIDER"),
                "the message should say exactly what to set");
    }

    @Test
    @DisplayName("withExplanation leaves the score and its evidence untouched")
    void explanationCannotAlterTheScore() {
        JobScore original = match(1L, 68.8);
        JobScore explained = original.withExplanation("a sentence", "stub");

        assertEquals(original.score(), explained.score(), 0.0001);
        assertEquals(original.matchedSkills(), explained.matchedSkills());
        assertEquals(original.components(), explained.components());
        assertNull(original.explanation(), "the original must stay unmodified");
        assertEquals("a sentence", explained.explanation());
    }
}
