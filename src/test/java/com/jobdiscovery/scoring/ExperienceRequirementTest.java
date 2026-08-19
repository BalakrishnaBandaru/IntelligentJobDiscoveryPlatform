package com.jobdiscovery.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExperienceRequirementTest {

    @Test
    @DisplayName("'5+ years of experience' is an open-ended floor")
    void parsesOpenEndedFloor() {
        ExperienceRequirement requirement =
                ExperienceRequirement.parse("Java Developer", "5+ years of experience required");
        assertNotNull(requirement);
        assertEquals(5, requirement.minYears());
        assertNull(requirement.maxYears());
    }

    @Test
    @DisplayName("'3-6 years of experience' is a closed range")
    void parsesHyphenatedRange() {
        ExperienceRequirement requirement =
                ExperienceRequirement.parse(null, "Looking for 3-6 years of experience");
        assertNotNull(requirement);
        assertEquals(3, requirement.minYears());
        assertEquals(6, requirement.maxYears());
    }

    @Test
    @DisplayName("'5 to 8 years experience' is a closed range")
    void parsesWordyRange() {
        ExperienceRequirement requirement =
                ExperienceRequirement.parse(null, "5 to 8 years experience in backend systems");
        assertNotNull(requirement);
        assertEquals(5, requirement.minYears());
        assertEquals(8, requirement.maxYears());
    }

    @Test
    @DisplayName("a bare number with no experience context is not a requirement")
    void requiresExperienceContext() {
        // Without the context check this reads as "requires 5 years".
        assertNull(ExperienceRequirement.parse(null,
                "Founded 5 years ago, we build payment infrastructure."));
    }

    @Test
    @DisplayName("null and blank text yield no requirement")
    void handlesNull() {
        assertNull(ExperienceRequirement.parse(null, null));
        assertNull(ExperienceRequirement.parse("", "   "));
    }

    @Test
    @DisplayName("a two-digit requirement parses")
    void parsesTwoDigits() {
        ExperienceRequirement requirement =
                ExperienceRequirement.parse(null, "minimum 10 years of experience");
        assertNotNull(requirement);
        assertEquals(10, requirement.minYears());
    }

    @Test
    @DisplayName("isSatisfiedBy respects both bounds")
    void satisfaction() {
        ExperienceRequirement closed = new ExperienceRequirement(3, 6);
        assertFalse(closed.isSatisfiedBy(2));
        assertTrue(closed.isSatisfiedBy(3));
        assertTrue(closed.isSatisfiedBy(6));
        assertFalse(closed.isSatisfiedBy(7));

        ExperienceRequirement open = new ExperienceRequirement(8, null);
        assertFalse(open.isSatisfiedBy(7));
        assertTrue(open.isSatisfiedBy(8));
        assertTrue(open.isSatisfiedBy(25));
    }

    @Test
    @DisplayName("describe() reads the way the posting phrased it")
    void describes() {
        assertEquals("5+ years", new ExperienceRequirement(5, null).describe());
        assertEquals("3-6 years", new ExperienceRequirement(3, 6).describe());
        assertEquals("4 years", new ExperienceRequirement(4, 4).describe());
    }
}
