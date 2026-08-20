package com.jobdiscovery.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * <p>{@link ClaudeClient} is subclassed with a recording stub, so what is
 * asserted here is the part that is ours: the prompt contract, the caching, and
 * the cost controls. Whether the model writes a good sentence is not something
 * a unit test can decide.
 */
class MatchExplainerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    /** A client that never calls out, and remembers what it was asked. */
    private static final class RecordingClient extends ClaudeClient {

        private final List<String> prompts = new ArrayList<>();
        private final boolean configured;

        RecordingClient(boolean configured) {
            super(new ExplanationProperties(configured, configured ? "test-key" : null,
                    "https://example.invalid", "claude-opus-5", 2048, "low", 5));
            this.configured = configured;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public String complete(String system, String userPrompt) {
            prompts.add(userPrompt);
            return "Explanation #" + prompts.size();
        }
    }

    private static ExplanationProperties props(int maxMatches) {
        return new ExplanationProperties(true, "test-key", "https://example.invalid",
                "claude-opus-5", 2048, "low", maxMatches);
    }

    private static JobScore match(long id, double score) {
        return new JobScore(id, "Senior Java Developer", "Acme", "Bangalore, Karnataka",
                "ADZUNA", "https://example.com/apply", NOW, score,
                List.of("Java", "Spring Boot"), List.of("Kafka", "Docker"), List.of("backend"),
                SeniorityLevel.SENIOR, ExperienceRequirement.parse("", "5+ years of experience"),
                List.of(ScoreComponent.of("skills", 35, 0.55, "matched 2 of 4 skills"),
                        ScoreComponent.notApplicable("preferredCompany", 5, "none set")),
                null);
    }

    // --- prompt contract ---------------------------------------------------

    @Test
    @DisplayName("the prompt carries the score and the evidence behind it")
    void promptCarriesTheEvidence() {
        MatchExplainer explainer = new MatchExplainer(new RecordingClient(true), props(5));
        String prompt = explainer.buildPrompt(match(1L, 68.8));

        assertTrue(prompt.contains("68.8"), "the score itself must be given, not re-derived");
        assertTrue(prompt.contains("Java"), prompt);
        assertTrue(prompt.contains("Kafka"), "missing skills are evidence too");
        assertTrue(prompt.contains("SENIOR"));
        assertTrue(prompt.contains("skills"), "the breakdown must be included");
    }

    @Test
    @DisplayName("the prompt never contains the raw posting text")
    void promptWithholdsThePosting() {
        // The model is given the rule engine's findings, not the description.
        // If it could read the posting it could form its own view of the fit,
        // which is the one thing this design does not allow.
        MatchExplainer explainer = new MatchExplainer(new RecordingClient(true), props(5));
        String prompt = explainer.buildPrompt(match(1L, 68.8));

        assertFalse(prompt.toLowerCase().contains("description"), prompt);
    }

    @Test
    @DisplayName("a match with no stated experience range says so rather than omitting it")
    void promptHandlesAbsentExperience() {
        JobScore noRange = new JobScore(2L, "Java Developer", "Acme", "Bangalore", "ADZUNA",
                "https://example.com", NOW, 50.0, List.of("Java"), List.of(), List.of(),
                SeniorityLevel.MID, null,
                List.of(ScoreComponent.of("skills", 35, 1.0, "matched 1 of 1")), null);

        String prompt = new MatchExplainer(new RecordingClient(true), props(5)).buildPrompt(noRange);
        assertTrue(prompt.contains("not stated"), prompt);
    }

    // --- cost controls -----------------------------------------------------

    @Test
    @DisplayName("only the top max-matches results are explained")
    void explainsOnlyTheTopMatches() {
        RecordingClient client = new RecordingClient(true);
        MatchExplainer explainer = new MatchExplainer(client, props(2));

        List<JobScore> explained = explainer.explainAll(
                List.of(match(1L, 70), match(2L, 60), match(3L, 50), match(4L, 40)));

        assertEquals(4, explained.size(), "every match is returned, explained or not");
        assertNotNull(explained.get(0).explanation());
        assertNotNull(explained.get(1).explanation());
        assertNull(explained.get(2).explanation(), "past the cap, no call should be made");
        assertNull(explained.get(3).explanation());
        assertEquals(2, client.prompts.size(), "exactly two billed calls");
    }

    @Test
    @DisplayName("the same job at the same score is only paid for once")
    void cachesByScoreAndJob() {
        RecordingClient client = new RecordingClient(true);
        MatchExplainer explainer = new MatchExplainer(client, props(5));

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
        RecordingClient client = new RecordingClient(true);
        MatchExplainer explainer = new MatchExplainer(client, props(5));

        explainer.explainAll(List.of(match(1L, 70)));
        explainer.explainAll(List.of(match(1L, 76.7)));

        assertEquals(2, client.prompts.size());
    }

    // --- not configured ----------------------------------------------------

    @Test
    @DisplayName("asking for explanations without a key fails clearly, not silently")
    void unconfiguredFailsLoudly() {
        MatchExplainer explainer = new MatchExplainer(new RecordingClient(false), props(5));

        ExplanationException thrown = assertThrows(ExplanationException.class,
                () -> explainer.explainAll(List.of(match(1L, 70))));

        assertEquals(ExplanationException.NOT_CONFIGURED, thrown.getCode());
        assertTrue(thrown.getMessage().contains("ANTHROPIC_API_KEY"),
                "the message should say exactly what to set");
    }

    @Test
    @DisplayName("withExplanation leaves the score and its evidence untouched")
    void explanationCannotAlterTheScore() {
        JobScore original = match(1L, 68.8);
        JobScore explained = original.withExplanation("a sentence about the match");

        assertEquals(original.score(), explained.score(), 0.0001);
        assertEquals(original.matchedSkills(), explained.matchedSkills());
        assertEquals(original.components(), explained.components());
        assertNull(original.explanation(), "the original must stay unmodified");
        assertEquals("a sentence about the match", explained.explanation());
    }
}
