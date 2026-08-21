package com.jobdiscovery.explain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobdiscovery.scoring.ExperienceRequirement;
import com.jobdiscovery.scoring.JobScore;
import com.jobdiscovery.scoring.ScoreComponent;
import com.jobdiscovery.scoring.SeniorityLevel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The templated explainer's job is to be <i>accurate</i>, not eloquent. These
 * tests hold it to that: it must state what the evidence says, never overclaim,
 * and in particular never turn a truncated posting into a statement about what
 * the employer does not want.
 */
class TemplatedExplainerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private final TemplatedExplainer explainer = new TemplatedExplainer();

    private JobScore match(double score, List<String> matched, List<String> missing,
                           List<ScoreComponent> components) {
        return new JobScore(1L, "Senior Java Developer", "Acme", "Bangalore", "ADZUNA",
                "https://example.com", NOW, score, matched, missing, List.of(),
                SeniorityLevel.SENIOR, ExperienceRequirement.parse("", "5+ years"),
                components, null, null, null);
    }

    private ScoreComponent skills(double value, String detail) {
        return ScoreComponent.of("skills", 35, value, detail);
    }

    @Test
    @DisplayName("leads with the band and the strongest dimension")
    void leadsWithTheVerdict() {
        String text = explainer.explain(match(76.7, List.of("Java"), List.of(),
                List.of(skills(0.5, "matched 1 of 1"),
                        ScoreComponent.of("location", 20, 1.0, "'Bangalore' matches preferred"))));

        assertTrue(text.startsWith("A strong match at 76.7 out of 100"), text);
        assertTrue(text.contains("the location works"), text);
    }

    @Test
    @DisplayName("a truncated posting is never described as not wanting a skill")
    void neverOverclaimsOnTruncatedText() {
        // The whole truncation finding rests on this distinction. Saying "does
        // not require it" about a 500-character preview would simply be false.
        String text = explainer.explain(match(60.0, List.of("Java"), List.of("Kafka", "Docker"),
                List.of(skills(0.4, "matched 1 of 3 skills; posting text is truncated, so the 2 "
                        + "unmatched count as unknown rather than absent"))));

        assertTrue(text.contains("cut short"), text);
        assertFalse(text.toLowerCase().contains("does not require"), text);
        assertFalse(text.toLowerCase().contains("not needed"), text);
    }

    @Test
    @DisplayName("complete text gets no truncation caveat")
    void noCaveatWhenTextIsComplete() {
        String text = explainer.explain(match(60.0, List.of("Java"), List.of("Kafka"),
                List.of(skills(0.5, "matched 1 of 2 skills: Java"))));

        assertTrue(text.contains("does not mention Kafka"), text);
        assertFalse(text.contains("cut short"), text);
    }

    @Test
    @DisplayName("zero matched skills is stated plainly, not dressed up")
    void handlesNoMatchedSkills() {
        String text = explainer.explain(match(32.1, List.of(), List.of("Java", "Kafka"),
                List.of(skills(0.0, "matched 0 of 2 skills"))));

        assertTrue(text.startsWith("A weak match"), text);
        assertTrue(text.contains("None of your listed skills appear"), text);
    }

    @Test
    @DisplayName("long missing lists are summarised rather than dumped")
    void summarisesLongMissingLists() {
        String text = explainer.explain(match(50.0, List.of("Java"),
                List.of("Kafka", "Docker", "AWS", "MySQL", "PostgreSQL"),
                List.of(skills(0.2, "matched 1 of 6 skills"))));

        assertTrue(text.contains("Kafka, Docker, AWS and 2 others"), text);
    }

    @Test
    @DisplayName("a caveat only appears when a dimension is actually weak")
    void caveatOnlyWhenWarranted() {
        String strong = explainer.explain(match(90.0, List.of("Java"), List.of(),
                List.of(skills(1.0, "matched 1 of 1"),
                        ScoreComponent.of("location", 20, 1.0, "matches preferred"))));
        assertFalse(strong.contains("The main caveat"), strong);

        String weak = explainer.explain(match(50.0, List.of("Java"), List.of(),
                List.of(skills(1.0, "matched 1 of 1"),
                        ScoreComponent.of("location", 20, 0.0, "'Pune' is not a preferred location"))));
        assertTrue(weak.contains("The main caveat is 'Pune' is not a preferred location"), weak);
    }

    @Test
    @DisplayName("the skills dimension is never repeated as the caveat")
    void skillsIsNotRepeatedAsACaveat() {
        // Sentence two already covers skills in full. Letting it also be the
        // caveat states the same truncation point twice in a row.
        String text = explainer.explain(match(70.8, List.of("Java"), List.of("Kafka", "Docker"),
                List.of(skills(0.4, "matched 1 of 3 skills; posting text is truncated, so the 2 "
                                + "unmatched count as unknown rather than absent"),
                        ScoreComponent.of("location", 20, 1.0, "matches preferred"))));

        assertFalse(text.contains("The main caveat"), text);
    }

    @Test
    @DisplayName("a dimension that does not apply is never used as a caveat")
    void ignoresInapplicableDimensions() {
        // preferredCompany scores 0 when unset, but it dropped out of the
        // divisor — holding it against the job in prose would contradict that.
        String text = explainer.explain(match(70.0, List.of("Java"), List.of(),
                List.of(skills(1.0, "matched 1 of 1"),
                        ScoreComponent.notApplicable("preferredCompany", 5, "none set"))));

        assertFalse(text.contains("none set"), text);
    }
}
