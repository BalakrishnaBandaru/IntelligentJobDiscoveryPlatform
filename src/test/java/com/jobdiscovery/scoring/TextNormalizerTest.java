package com.jobdiscovery.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    @DisplayName("strips HTML tags and entities from source descriptions")
    void stripsHtml() {
        assertEquals("we need a java developer",
                TextNormalizer.normalize("<p>We need a <b>Java</b>&nbsp;developer</p>"));
    }

    @Test
    @DisplayName("lower-cases and collapses punctuation to single spaces")
    void collapsesPunctuation() {
        assertEquals("senior java developer bengaluru",
                TextNormalizer.normalize("  Senior Java Developer -- Bengaluru!  "));
    }

    @Test
    @DisplayName("keeps + and # so C++ and C# survive normalisation")
    void keepsPlusAndHash() {
        assertEquals("c++ and c#", TextNormalizer.normalize("C++ and C#"));
    }

    @Test
    @DisplayName("null and blank input normalise to empty, never NPE")
    void handlesNull() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize("   "));
        assertEquals(List.of(), TextNormalizer.tokenize(null));
    }

    @Test
    @DisplayName("Java does not match JavaScript")
    void javaDoesNotMatchJavaScript() {
        // The bug this whole class exists to prevent: a substring match would
        // score a JavaScript role as a Java match and poison the ranking.
        List<String> tokens = TextNormalizer.tokenize("Senior JavaScript Developer");
        assertFalse(TextNormalizer.contains(tokens, "Java"));
        assertTrue(TextNormalizer.contains(tokens, "JavaScript"));
    }

    @Test
    @DisplayName("multi-word skills match as a phrase, not as scattered words")
    void matchesPhrases() {
        assertTrue(TextNormalizer.contains(
                TextNormalizer.tokenize("Built with Spring Boot 3 and Kafka"), "Spring Boot"));
        assertFalse(TextNormalizer.contains(
                TextNormalizer.tokenize("spring cleaning, then boot the server"), "Spring Boot"));
    }

    @Test
    @DisplayName("a slash-separated skill matches either alternative")
    void matchesSkillAlternatives() {
        List<String> tokens = TextNormalizer.tokenize("Experience with Hibernate required");
        assertTrue(TextNormalizer.contains(tokens, "JPA/Hibernate"));
    }

    @Test
    @DisplayName("tolerates a plural s so 'REST APIs' matches 'REST API'")
    void tolerantOfPlurals() {
        assertTrue(TextNormalizer.contains(
                TextNormalizer.tokenize("designing a REST API"), "REST APIs"));
    }

    @Test
    @DisplayName("short acronyms are not de-pluralised into nonsense")
    void doesNotStripAcronyms() {
        // "aws" must not be treated as the plural of "aw".
        assertFalse(TextNormalizer.contains(TextNormalizer.tokenize("we use aw tooling"), "AWS"));
        assertTrue(TextNormalizer.contains(TextNormalizer.tokenize("we use AWS"), "AWS"));
    }

    @Test
    @DisplayName("blank or null search terms never match")
    void blankTermsDoNotMatch() {
        List<String> tokens = TextNormalizer.tokenize("Java developer");
        assertFalse(TextNormalizer.contains(tokens, null));
        assertFalse(TextNormalizer.contains(tokens, "  "));
    }

    @Test
    @DisplayName("detects Adzuna's truncation marker (a trailing ellipsis character)")
    void detectsAdzunaTruncation() {
        assertTrue(TextNormalizer.isTruncated(
                "Design and build backend services in Java and Spring Boot, working with…"));
    }

    @Test
    @DisplayName("detects Jooble's truncation marker behind a trailing &nbsp;")
    void detectsJoobleTruncation() {
        // Jooble's snippets end "...&nbsp;", so the marker is not the last thing
        // in the string — the entity has to be stripped before checking.
        assertTrue(TextNormalizer.isTruncated(
                "Experience in Java, Spring/ Spring Boot,...&nbsp;"));
        assertTrue(TextNormalizer.isTruncated("Working experience with... &nbsp; \r\n"));
    }

    @Test
    @DisplayName("complete text is not reported as truncated")
    void completeTextIsNotTruncated() {
        assertFalse(TextNormalizer.isTruncated(
                "We are hiring a Java developer with Spring Boot and Kafka experience."));
        // A sentence that merely contains an ellipsis mid-string is complete.
        assertFalse(TextNormalizer.isTruncated("Java, Spring… and more, all in one team."));
    }

    @Test
    @DisplayName("null or blank text is not truncated")
    void blankTextIsNotTruncated() {
        assertFalse(TextNormalizer.isTruncated(null));
        assertFalse(TextNormalizer.isTruncated("   "));
    }
}
