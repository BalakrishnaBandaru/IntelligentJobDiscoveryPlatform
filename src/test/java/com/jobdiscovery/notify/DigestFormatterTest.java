package com.jobdiscovery.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * The formatter is where a digest actually breaks. Telegram rejects a message
 * whose HTML entities do not parse and refuses one over 4096 characters, and job
 * titles arrive from the source APIs full of ampersands and angle brackets — so
 * both failures are reachable with real data, not hypothetical.
 */
class DigestFormatterTest {

    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    private final DigestFormatter formatter = new DigestFormatter();

    private JobScore match(String title, String company, double score, String explanation) {
        return new JobScore(1L, title, company, "Bangalore", "ADZUNA",
                "https://example.com/apply?a=1&b=2", NOW, score,
                List.of("Java"), List.of(), List.of(),
                SeniorityLevel.SENIOR, ExperienceRequirement.parse("", "5+ years"),
                List.of(ScoreComponent.of("skills", 35, 1.0, "matched 1 of 1")),
                explanation, explanation == null ? null : "templated", null);
    }

    @Test
    @DisplayName("ampersands and angle brackets in a title are escaped")
    void escapesJobText() {
        // "R&D" and "<Senior>" are the shapes that actually turn up. Unescaped,
        // Telegram rejects the whole message with "can't parse entities".
        String out = formatter.format(List.of(
                match("R&D Engineer <Java>", "Smith & Co", 75.0, null)));

        assertTrue(out.contains("R&amp;D Engineer &lt;Java&gt;"), out);
        assertTrue(out.contains("Smith &amp; Co"), out);
        assertFalse(out.contains("R&D "), "raw ampersand left in: " + out);
    }

    @Test
    @DisplayName("the apply URL is escaped too, since query strings carry ampersands")
    void escapesTheUrl() {
        String out = formatter.format(List.of(match("Java Developer", "Acme", 75.0, null)));

        assertTrue(out.contains("apply?a=1&amp;b=2"), out);
    }

    @Test
    @DisplayName("an explanation is included when one is present")
    void includesTheExplanation() {
        String out = formatter.format(List.of(
                match("Java Developer", "Acme", 75.0, "Strong on skills and seniority.")));

        assertTrue(out.contains("Strong on skills and seniority."), out);
    }

    @Test
    @DisplayName("without an explanation it falls back to the matched skills")
    void fallsBackToSkills() {
        String out = formatter.format(List.of(match("Java Developer", "Acme", 75.0, null)));

        assertTrue(out.contains("Matches Java"), out);
    }

    @Test
    @DisplayName("a long digest is truncated below Telegram's hard limit")
    void staysUnderTheLimit() {
        // Telegram refuses anything over 4096 characters outright, so this is a
        // send failure rather than an ugly message.
        List<JobScore> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(match("Senior Java Backend Engineer, Platform and Infrastructure Team "
                    + i, "A Company With A Fairly Long Name Ltd " + i, 70.0,
                    "A reasonably long explanation sentence that takes up space in the message."));
        }

        String out = formatter.format(many);

        assertTrue(out.length() <= DigestFormatter.TELEGRAM_MAX_CHARS,
                "message was " + out.length() + " characters");
        assertTrue(out.contains("more not shown"),
                "a truncated digest must say how many it left out");
    }

    @Test
    @DisplayName("the count in the header is the number of matches selected")
    void headerCountsMatches() {
        String out = formatter.format(List.of(
                match("Java Developer", "Acme", 75.0, null),
                match("Senior Java Developer", "Acme", 72.0, null)));

        assertTrue(out.startsWith("<b>2 new matches</b>"), out);
    }

    @Test
    @DisplayName("one match is singular")
    void singularForOne() {
        String out = formatter.format(List.of(match("Java Developer", "Acme", 75.0, null)));

        assertTrue(out.startsWith("<b>1 new match</b>"), out);
    }

    @Test
    @DisplayName("null text never reaches the message as the string 'null'")
    void handlesMissingFields() {
        JobScore sparse = new JobScore(1L, "Java Developer", null, null, "ADZUNA",
                null, NOW, 70.0, List.of(), List.of(), List.of(),
                SeniorityLevel.MID, null, List.of(), null, null, null);

        String out = formatter.format(List.of(sparse));

        assertFalse(out.contains("null"), out);
        assertTrue(out.contains("unknown company"), out);
    }

    @Test
    @DisplayName("escape leaves ordinary text alone")
    void escapeIsMinimal() {
        assertEquals("Java Developer (Senior)",
                DigestFormatter.escape("Java Developer (Senior)"));
        assertEquals("", DigestFormatter.escape(null));
    }
}
